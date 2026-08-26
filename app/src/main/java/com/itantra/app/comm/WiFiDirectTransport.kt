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
import java.net.InetSocketAddress
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
    private var connectionAttemptJob: Job? = null
    
    private var onMessageReceived: ((P2PMessage) -> Unit)? = null
    private val transportScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val PORT = 8888
    private val CONNECTION_TIMEOUT_MS = 15000L

    private var connectionMonitorJob: Job? = null

    init {
        Log.i(TAG, "Initializing WiFiDirectTransport")
        // Monitor WiFiDirectManager connection info
        connectionMonitorJob = transportScope.launch {
            wifiDirectManager.connectionInfo.collect { info ->
                Log.d(TAG, "Observed connection info change: groupFormed=${info?.groupFormed}")
                handleConnectionInfo(info)
            }
        }
    }

    private fun handleConnectionInfo(info: WifiP2pInfo?) {
        if (info == null) {
            if (_connectionState.value != ConnectionState.DISCONNECTED) {
                Log.d(TAG, "Connection info cleared, disconnecting transport")
                disconnect()
            }
            return
        }

        if (info.groupFormed) {
            if (_connectionState.value == ConnectionState.CONNECTED) {
                Log.d(TAG, "Already connected, ignoring redundant group formed info")
                return
            }

            Log.i(TAG, "P2P Group Formed. IsOwner=${info.isGroupOwner}, Owner=${info.groupOwnerAddress?.hostAddress}")
            _connectionState.value = ConnectionState.CONNECTING
            
            connectionAttemptJob?.cancel()
            connectionAttemptJob = transportScope.launch {
                try {
                    withTimeout(CONNECTION_TIMEOUT_MS) {
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
                    }
                } catch (e: TimeoutCancellationException) {
                    Log.e(TAG, "Socket connection timed out")
                    _lastError.value = "Connection timed out"
                    _connectionState.value = ConnectionState.ERROR
                } catch (e: Exception) {
                    Log.e(TAG, "Socket setup failed: ${e.message}", e)
                    _lastError.value = e.message ?: "Socket connection failed"
                    _connectionState.value = ConnectionState.ERROR
                }
            }
        }
    }

    override fun connect(targetId: String?) {
        Log.i(TAG, "Connect requested. Target: ${targetId ?: "LISTEN"}")
        _connectionState.value = ConnectionState.CONNECTING
        _lastError.value = null
        
        if (targetId != null) {
            val peer = wifiDirectManager.peers.value.find { it.deviceAddress == targetId }
            if (peer != null) {
                wifiDirectManager.connect(peer)
            } else {
                Log.e(TAG, "Peer with address $targetId not found in discovered list")
                _lastError.value = "Peer not found"
                _connectionState.value = ConnectionState.ERROR
            }
        } else {
            // As host, we just start discovery to be found
            Log.d(TAG, "No target ID, starting discovery to wait for incoming connections")
            wifiDirectManager.startDiscovery()
        }
    }

    private suspend fun startServer() = withContext(Dispatchers.IO) {
        try {
            cleanupSockets()
            Log.d(TAG, "Starting server socket on port $PORT...")
            serverSocket = ServerSocket(PORT).apply { reuseAddress = true }
            
            // Wait for client to connect
            val socket = serverSocket?.accept()
            if (socket != null && isActive) {
                Log.i(TAG, "Accepted incoming connection from ${socket.inetAddress.hostAddress}")
                setupConnection(socket)
            }
        } catch (e: Exception) {
            if (isActive) {
                Log.e(TAG, "Server socket error", e)
                throw e
            }
        }
    }

    private suspend fun startClient(host: String) = withContext(Dispatchers.IO) {
        cleanupSockets()
        Log.d(TAG, "Connecting to server at $host:$PORT...")
        
        // Retry a few times as the server might not be ready yet
        var socket: Socket? = null
        var lastEx: Exception? = null
        
        for (i in 1..10) {
            if (!isActive) break
            try {
                socket = Socket()
                socket.connect(InetSocketAddress(host, PORT), 2000)
                Log.i(TAG, "Successfully connected to $host:$PORT on attempt $i")
                break
            } catch (e: Exception) {
                lastEx = e
                Log.w(TAG, "Connection attempt $i failed: ${e.message}")
                socket?.close()
                socket = null
                delay(1000L)
            }
        }
        
        if (socket != null && isActive) {
            setupConnection(socket)
        } else if (isActive) {
            throw lastEx ?: Exception("Failed to connect to $host")
        }
    }

    private fun setupConnection(socket: Socket) {
        socket.tcpNoDelay = true
        socket.keepAlive = true
        clientSocket = socket
        writer = PrintWriter(socket.getOutputStream(), true)
        
        Log.i(TAG, "Transport connected. Socket: ${socket.inetAddress.hostAddress}")
        _connectionState.value = ConnectionState.CONNECTED
        _lastError.value = null
        
        startReceiving(socket)
    }

    private fun startReceiving(socket: Socket) {
        receiverJob?.cancel()
        receiverJob = transportScope.launch {
            try {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                Log.d(TAG, "Receiver thread started")
                while (isActive) {
                    val line = reader.readLine() ?: break
                    try {
                        val message = Json.decodeFromString<P2PMessage>(line)
                        onMessageReceived?.invoke(message)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse incoming message: ${e.message}")
                    }
                }
                Log.i(TAG, "Receiver thread: connection closed by peer")
            } catch (e: Exception) {
                if (isActive) {
                    Log.e(TAG, "Receiver error: ${e.message}")
                    _lastError.value = "Connection lost"
                    _connectionState.value = ConnectionState.ERROR
                }
            } finally {
                if (isActive) {
                    Log.d(TAG, "Receiver thread finished, disconnecting")
                    disconnect()
                }
            }
        }
    }

    override fun getConnectedPeerId(): String? = clientSocket?.inetAddress?.hostAddress

    override fun sendMessage(message: P2PMessage) {
        if (_connectionState.value != ConnectionState.CONNECTED || writer == null) {
            Log.w(TAG, "Cannot send message: Not connected")
            return
        }
        
        transportScope.launch {
            try {
                val json = Json.encodeToString(message)
                writer?.println(json)
                Log.v(TAG, "Sent message ${message.messageId}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send message ${message.messageId}: ${e.message}")
            }
        }
    }

    override fun setOnMessageReceivedListener(callback: (P2PMessage) -> Unit) {
        this.onMessageReceived = callback
    }

    override fun disconnect() {
        Log.i(TAG, "Disconnecting WiFiDirectTransport...")
        _connectionState.value = ConnectionState.DISCONNECTED
        
        connectionAttemptJob?.cancel()
        connectionAttemptJob = null
        
        transportScope.launch {
            cleanupSockets()
            wifiDirectManager.disconnect()
        }
    }

    private fun cleanupSockets() {
        Log.d(TAG, "Cleaning up sockets and streams")
        receiverJob?.cancel()
        receiverJob = null
        try {
            writer?.close()
            clientSocket?.close()
            serverSocket?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error during socket cleanup: ${e.message}")
        } finally {
            writer = null
            clientSocket = null
            serverSocket = null
        }
    }

    fun release() {
        Log.d(TAG, "Releasing WiFiDirectTransport resources")
        connectionMonitorJob?.cancel()
        transportScope.cancel()
        disconnect()
    }

    companion object {
        private const val TAG = "WiFiDirectTransport"
    }
}
