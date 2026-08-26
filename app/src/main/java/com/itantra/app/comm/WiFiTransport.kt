package com.itantra.app.comm

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

/**
 * Wi-Fi implementation of the Transport interface using TCP Sockets.
 * Supports Host (Server) and Client modes on a local network.
 */
class WiFiTransport : Transport {
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    override val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var writer: PrintWriter? = null
    private var receiverJob: Job? = null
    
    private var onMessageReceived: ((P2PMessage) -> Unit)? = null
    
    private val transportScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val PORT = 8888

    companion object {
        private const val TAG = "WiFiTransport"
    }

    override fun connect(targetId: String?) {
        if (_connectionState.value != ConnectionState.DISCONNECTED) return
        
        _connectionState.value = ConnectionState.CONNECTING
        _lastError.value = null

        transportScope.launch {
            try {
                if (targetId.isNullOrBlank()) {
                    // Host mode: Start ServerSocket
                    startServer()
                } else {
                    // Client mode: Connect to IP
                    startClient(targetId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Connection failed", e)
                _lastError.value = e.message
                _connectionState.value = ConnectionState.ERROR
                cleanup()
            }
        }
    }

    private suspend fun startServer() = withContext(Dispatchers.IO) {
        serverSocket = ServerSocket(PORT).apply {
            reuseAddress = true
        }
        Log.d(TAG, "Server started on port $PORT, waiting for connection...")
        
        val socket = serverSocket?.accept()
        if (socket != null) {
            socket.tcpNoDelay = true
            setupConnection(socket)
        }
    }

    private suspend fun startClient(ip: String) = withContext(Dispatchers.IO) {
        Log.d(TAG, "Connecting to $ip:$PORT...")
        val socket = Socket(ip, PORT).apply {
            tcpNoDelay = true // Disable Nagle's algorithm for lower latency
        }
        setupConnection(socket)
    }

    private fun setupConnection(socket: Socket) {
        socket.tcpNoDelay = true
        clientSocket = socket
        writer = PrintWriter(socket.getOutputStream(), true)
        _connectionState.value = ConnectionState.CONNECTED
        Log.i(TAG, "Connected to ${socket.inetAddress.hostAddress}")

        startReceiving(socket)
    }

    private fun startReceiving(socket: Socket) {
        receiverJob = transportScope.launch {
            try {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                while (isActive) {
                    val line = reader.readLine() ?: break
                    try {
                        val message = Json.decodeFromString<P2PMessage>(line)
                        onMessageReceived?.invoke(message)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse message: $line", e)
                    }
                }
            } catch (e: Exception) {
                if (isActive) {
                    Log.e(TAG, "Receiver error", e)
                    _lastError.value = "Connection lost: ${e.message}"
                    _connectionState.value = ConnectionState.ERROR
                }
            } finally {
                disconnect()
            }
        }
    }

    override fun sendMessage(message: P2PMessage) {
        transportScope.launch {
            try {
                val json = Json.encodeToString(message)
                writer?.println(json)
                Log.d(TAG, "Sent message: ${message.messageId}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send message", e)
                _lastError.value = "Send failed: ${e.message}"
            }
        }
    }

    override fun setOnMessageReceivedListener(callback: (P2PMessage) -> Unit) {
        this.onMessageReceived = callback
    }

    override fun disconnect() {
        if (_connectionState.value == ConnectionState.DISCONNECTED && clientSocket == null) return
        
        Log.d(TAG, "Disconnecting...")
        _connectionState.value = ConnectionState.DISCONNECTED
        cleanup()
    }

    private fun cleanup() {
        receiverJob?.cancel()
        receiverJob = null
        
        try {
            writer?.close()
            clientSocket?.close()
            serverSocket?.close()
            Log.d(TAG, "Sockets and streams closed.")
        } catch (e: Exception) {
            Log.w(TAG, "Error during cleanup", e)
        } finally {
            writer = null
            clientSocket = null
            serverSocket = null
        }
    }
}
