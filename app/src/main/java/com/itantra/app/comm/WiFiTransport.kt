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
        val isHosting = targetId.isNullOrBlank()
        
        if (!isHosting) {
            // Client mode: Only allow if disconnected or error
            if (_connectionState.value == ConnectionState.CONNECTING) {
                Log.w(TAG, "Ignoring connect request: already in state ${_connectionState.value}")
                return
            }
            val prev = _connectionState.value
            _connectionState.value = ConnectionState.CONNECTING
            Log.i(TAG, "[WIFI-STATE] selectedTransport=WIFI connectionState=CONNECTING previousState=$prev newState=CONNECTING reason=CONNECT_REQUESTED")
        } else {
            // Host mode: Don't set CONNECTING state so the UI remains responsive for outbound connects.
            if (_connectionState.value == ConnectionState.CONNECTED) return
            _connectionState.value = ConnectionState.DISCONNECTED
            Log.i(TAG, "[WIFI-STATE] selectedTransport=WIFI connectionState=DISCONNECTED reason=HOST_STARTED")
        }
        
        _lastError.value = null

        transportScope.launch {
            try {
                if (isHosting) {
                    startServer()
                } else {
                    withTimeout(10000L) {
                        startClient(targetId)
                    }
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Connection timed out after 10s")
                _lastError.value = "Connection timed out"
                if (!isHosting) {
                    _connectionState.value = ConnectionState.ERROR
                    Log.i(TAG, "[WIFI-STATE] selectedTransport=WIFI connectionState=ERROR previousState=CONNECTING newState=ERROR reason=TIMEOUT")
                    cleanup()
                    // Auto-recover to DISCONNECTED per Phase 12C specification so CONNECT is immediately enabled again
                    _connectionState.value = ConnectionState.DISCONNECTED
                    Log.i(TAG, "[WIFI-STATE] selectedTransport=WIFI connectionState=DISCONNECTED previousState=ERROR newState=DISCONNECTED reason=RECOVERED_TO_DISCONNECTED")
                } else {
                    cleanup()
                    _connectionState.value = ConnectionState.DISCONNECTED
                }
            } catch (e: Exception) {
                if (isActive) {
                    Log.e(TAG, "Connection failed: ${e.message}", e)
                    _lastError.value = e.message
                    if (!isHosting) {
                        _connectionState.value = ConnectionState.ERROR
                        Log.i(TAG, "[WIFI-STATE] selectedTransport=WIFI connectionState=ERROR previousState=CONNECTING newState=ERROR reason=CONNECTION_FAILED")
                        cleanup()
                        // Auto-recover to DISCONNECTED per Phase 12C specification so CONNECT is immediately enabled again
                        _connectionState.value = ConnectionState.DISCONNECTED
                        Log.i(TAG, "[WIFI-STATE] selectedTransport=WIFI connectionState=DISCONNECTED previousState=ERROR newState=DISCONNECTED reason=RECOVERED_TO_DISCONNECTED")
                    } else {
                        cleanup()
                        _connectionState.value = ConnectionState.DISCONNECTED
                    }
                }
            }
        }
    }

    private suspend fun startServer() = withContext(Dispatchers.IO) {
        try {
            try {
                serverSocket?.close()
            } catch (ignored: Exception) {}
            serverSocket = ServerSocket().apply {
                reuseAddress = true
                bind(java.net.InetSocketAddress(PORT))
            }
            Log.d(TAG, "Server started on port $PORT, waiting for connection...")
            
            val socket = serverSocket?.accept()
            if (socket != null) {
                socket.tcpNoDelay = true
                setupConnection(socket)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start server on port $PORT", e)
            throw e
        }
    }

    private suspend fun startClient(ip: String) = withContext(Dispatchers.IO) {
        Log.d(TAG, "Connecting to $ip:$PORT...")
        val socket = Socket()
        // Use a connect timeout on the socket itself in addition to withTimeout
        socket.connect(java.net.InetSocketAddress(ip, PORT), 10000)
        socket.tcpNoDelay = true
        setupConnection(socket)
    }

    private fun setupConnection(socket: Socket) {
        val prev = _connectionState.value
        socket.tcpNoDelay = true
        clientSocket = socket
        writer = PrintWriter(socket.getOutputStream(), true)
        _connectionState.value = ConnectionState.CONNECTED
        Log.i(TAG, "[WIFI-STATE] selectedTransport=WIFI connectionState=CONNECTED previousState=$prev newState=CONNECTED reason=SOCKET_CONNECTED")
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
                    Log.i(TAG, "[WIFI-STATE] selectedTransport=WIFI connectionState=ERROR previousState=CONNECTED newState=ERROR reason=CONNECTION_LOST")
                }
            } finally {
                disconnect()
            }
        }
    }

    override fun getConnectedPeerId(): String? = clientSocket?.inetAddress?.hostAddress

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
        val prev = _connectionState.value
        Log.d(TAG, "Disconnecting (previousState=$prev)...")
        _connectionState.value = ConnectionState.DISCONNECTED
        cleanup()
        Log.i(TAG, "[WIFI-STATE] selectedTransport=WIFI connectionState=DISCONNECTED previousState=$prev newState=DISCONNECTED reason=DISCONNECT")
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
