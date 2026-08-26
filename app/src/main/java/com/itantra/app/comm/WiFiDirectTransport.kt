package com.itantra.app.comm

import android.net.wifi.p2p.WifiP2pInfo
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket

/**
 * Wi-Fi Direct implementation of the Transport interface.
 * Wraps WiFiDirectManager for connection and uses TCP Sockets for data.
 */
class WiFiDirectTransport(
    private val wifiDirectManager: WiFiDirectManager
) : Transport {
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

    private var connectionMonitorJob: Job? = null

    init {
        // Monitor WiFiDirectManager connection info
        connectionMonitorJob = transportScope.launch {
            wifiDirectManager.connectionInfo.collect { info ->
                handleConnectionInfo(info)
            }
        }
    }

    private fun handleConnectionInfo(info: WifiP2pInfo?) {
        if (info == null) {
            disconnect()
            return
        }

        if (info.groupFormed) {
            _connectionState.value = ConnectionState.CONNECTING
            transportScope.launch {
                try {
                    if (info.isGroupOwner) {
                        startServer()
                    } else {
                        val host = info.groupOwnerAddress?.hostAddress
                        if (host != null) {
                            startClient(host)
                        } else {
                            throw Exception("Group owner address unknown")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Socket setup failed", e)
                    _lastError.value = e.message
                    _connectionState.value = ConnectionState.ERROR
                }
            }
        }
    }

    override fun connect(targetId: String?) {
        // targetId is the device address (MAC) if we are client
        // If null, we just wait for connection (start discovery/advertising)
        _connectionState.value = ConnectionState.CONNECTING
        _lastError.value = null
        
        if (targetId != null) {
            val peer = wifiDirectManager.peers.value.find { it.deviceAddress == targetId }
            if (peer != null) {
                wifiDirectManager.connect(peer)
            } else {
                _lastError.value = "Peer not found"
                _connectionState.value = ConnectionState.ERROR
            }
        } else {
            // As host, we just start discovery to be found
            wifiDirectManager.startDiscovery()
        }
    }

    private suspend fun startServer() = withContext(Dispatchers.IO) {
        try {
            cleanupSockets()
            serverSocket = ServerSocket(PORT).apply { reuseAddress = true }
            Log.d(TAG, "Server waiting on port $PORT...")
            val socket = serverSocket?.accept()
            if (socket != null) setupConnection(socket)
        } catch (e: Exception) {
            if (isActive) throw e
        }
    }

    private suspend fun startClient(host: String) = withContext(Dispatchers.IO) {
        cleanupSockets()
        Log.d(TAG, "Connecting to $host:$PORT...")
        // Retry a few times as the server might not be ready yet
        var socket: Socket? = null
        for (i in 1..5) {
            try {
                socket = Socket(host, PORT)
                break
            } catch (e: Exception) {
                if (i == 5) throw e
                delay(1000L)
            }
        }
        if (socket != null) setupConnection(socket)
    }

    private fun setupConnection(socket: Socket) {
        socket.tcpNoDelay = true
        clientSocket = socket
        writer = PrintWriter(socket.getOutputStream(), true)
        _connectionState.value = ConnectionState.CONNECTED
        Log.i(TAG, "Socket connected to ${socket.inetAddress.hostAddress}")
        startReceiving(socket)
    }

    private fun startReceiving(socket: Socket) {
        receiverJob?.cancel()
        receiverJob = transportScope.launch {
            try {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                while (isActive) {
                    val line = reader.readLine() ?: break
                    try {
                        val message = Json.decodeFromString<P2PMessage>(line)
                        onMessageReceived?.invoke(message)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse message", e)
                    }
                }
            } catch (e: Exception) {
                if (isActive) {
                    Log.e(TAG, "Receiver error", e)
                    _lastError.value = "Connection lost"
                    _connectionState.value = ConnectionState.ERROR
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
            } catch (e: Exception) {
                Log.e(TAG, "Send failed", e)
            }
        }
    }

    override fun setOnMessageReceivedListener(callback: (P2PMessage) -> Unit) {
        this.onMessageReceived = callback
    }

    override fun disconnect() {
        Log.d(TAG, "Disconnecting...")
        _connectionState.value = ConnectionState.DISCONNECTED
        transportScope.launch {
            cleanupSockets()
            wifiDirectManager.disconnect()
        }
    }

    private fun cleanupSockets() {
        receiverJob?.cancel()
        try {
            writer?.close()
            clientSocket?.close()
            serverSocket?.close()
        } catch (e: Exception) {
            // Ignored
        } finally {
            writer = null
            clientSocket = null
            serverSocket = null
        }
    }

    fun release() {
        connectionMonitorJob?.cancel()
        transportScope.cancel()
        disconnect()
    }

    companion object {
        private const val TAG = "WiFiDirectTransport"
    }
}
