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
 * Adheres strictly to the 15-second timeout, socket retry loop, and diagnostic logging.
 */
class WiFiDirectTransport(
    private val wifiDirectManager: WiFiDirectManager
) : Transport {

    companion object {
        private const val TAG = "WiFiDirectTransport"
        private const val PORT = 8888
        private const val CONNECTION_TIMEOUT_MS = 15000L
    }

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

    private var connectionMonitorJob: Job? = null

    init {
        Log.i(TAG, "[P2P-DIAG] Initializing WiFiDirectTransport")
        connectionMonitorJob = transportScope.launch {
            wifiDirectManager.connectionInfo.collect { info ->
                Log.d(TAG, "[P2P-DIAG] Observed connection info change: groupFormed=${info?.groupFormed}")
                handleConnectionInfo(info)
            }
        }
    }

    private fun handleConnectionInfo(info: WifiP2pInfo?) {
        if (info == null) {
            if (_connectionState.value != ConnectionState.DISCONNECTED) {
                Log.d(TAG, "[P2P-DIAG] Connection info cleared, disconnecting transport")
                disconnect()
            }
            return
        }

        if (info.groupFormed) {
            if (_connectionState.value == ConnectionState.CONNECTED) {
                Log.d(TAG, "[P2P-DIAG] Already connected, ignoring redundant group formed info")
                return
            }

            Log.i(TAG, "[P2P-DIAG] Group Formed! IsOwner=${info.isGroupOwner}, OwnerAddr=${info.groupOwnerAddress?.hostAddress}")
            _connectionState.value = ConnectionState.CONNECTING
            
            connectionAttemptJob?.cancel()
            connectionAttemptJob = transportScope.launch {
                try {
                    withTimeout(CONNECTION_TIMEOUT_MS) {
                        if (info.isGroupOwner) {
                            startServer()
                        } else {
                            startClientWithRetry()
                        }
                    }
                } catch (e: TimeoutCancellationException) {
                    Log.e(TAG, "[P2P-DIAG] Socket connection attempt TIMED OUT after ${CONNECTION_TIMEOUT_MS}ms")
                    _lastError.value = "Connection timed out (15s limit)"
                    _connectionState.value = ConnectionState.ERROR
                    wifiDirectManager.updateSocketState(false)
                } catch (e: Exception) {
                    Log.e(TAG, "[P2P-DIAG] Socket setup failed: ${e.message}", e)
                    _lastError.value = e.message ?: "Socket connection failed"
                    _connectionState.value = ConnectionState.ERROR
                    wifiDirectManager.updateSocketState(false)
                }
            }
        }
    }

    override fun connect(targetId: String?) {
        Log.i(TAG, "[P2P-DIAG] Connect requested. Target: ${targetId ?: "LISTEN"}")
        _connectionState.value = ConnectionState.CONNECTING
        _lastError.value = null
        
        if (targetId != null) {
            val peer = wifiDirectManager.peers.value.find { it.deviceAddress == targetId }
            if (peer != null) {
                wifiDirectManager.connect(peer)
            } else {
                Log.e(TAG, "[P2P-DIAG] Peer with address $targetId not found in discovered list")
                _lastError.value = "Peer not found"
                _connectionState.value = ConnectionState.ERROR
            }
        } else {
            Log.d(TAG, "[P2P-DIAG] No target ID, starting discovery to wait for incoming connections")
            wifiDirectManager.startDiscovery()
        }
    }

    private suspend fun startServer() = withContext(Dispatchers.IO) {
        try {
            cleanupSockets()
            Log.i(TAG, "[P2P-DIAG] Socket attempt: Starting ServerSocket on port $PORT...")
            serverSocket = ServerSocket(PORT).apply {
                reuseAddress = true
                soTimeout = 14000 // Just under the 15s overall timeout
            }
            
            Log.i(TAG, "[P2P-DIAG] ServerSocket listening, awaiting client connection...")
            val socket = serverSocket?.accept()
            if (socket != null && isActive) {
                Log.i(TAG, "[P2P-DIAG] Socket success: Accepted client connection from ${socket.inetAddress.hostAddress}")
                setupConnection(socket)
            }
        } catch (e: Exception) {
            if (isActive) {
                Log.e(TAG, "[P2P-DIAG] Socket failure: ServerSocket error", e)
                throw e
            }
        }
    }

    private suspend fun startClientWithRetry() = withContext(Dispatchers.IO) {
        cleanupSockets()
        var socket: Socket? = null
        var lastEx: Exception? = null
        
        Log.i(TAG, "[P2P-DIAG] Socket attempt: Client initiating connection to group owner...")

        // Retry loop up to 12 attempts (~12 seconds)
        for (attempt in 1..12) {
            if (!isActive) break
            
            val currentInfo = wifiDirectManager.connectionInfo.value
            val host = currentInfo?.groupOwnerAddress?.hostAddress

            if (host == null || host == "0.0.0.0") {
                Log.d(TAG, "[P2P-DIAG] Group owner address not yet resolved (attempt $attempt/12), waiting...")
                delay(1000L)
                continue
            }

            try {
                Log.d(TAG, "[P2P-DIAG] Socket attempt $attempt/12 to $host:$PORT...")
                socket = Socket()
                socket.connect(InetSocketAddress(host, PORT), 2000)
                Log.i(TAG, "[P2P-DIAG] Socket success: Connected to group owner at $host:$PORT on attempt $attempt")
                break
            } catch (e: Exception) {
                lastEx = e
                Log.w(TAG, "[P2P-DIAG] Socket attempt $attempt failed: ${e.message}")
                try { socket?.close() } catch (ignored: Exception) {}
                socket = null
                delay(1000L)
            }
        }
        
        if (socket != null && isActive) {
            setupConnection(socket)
        } else if (isActive) {
            Log.e(TAG, "[P2P-DIAG] Socket failure: Could not connect to group owner")
            throw lastEx ?: Exception("Failed to connect to group owner")
        }
    }

    private fun setupConnection(socket: Socket) {
        socket.tcpNoDelay = true
        socket.keepAlive = true
        clientSocket = socket
        writer = PrintWriter(socket.getOutputStream(), true)
        
        Log.i(TAG, "[P2P-DIAG] Transport fully connected. Remote peer: ${socket.inetAddress.hostAddress}")
        _connectionState.value = ConnectionState.CONNECTED
        _lastError.value = null
        wifiDirectManager.updateSocketState(true)
        
        startReceiving(socket)
    }

    private fun startReceiving(socket: Socket) {
        receiverJob?.cancel()
        receiverJob = transportScope.launch {
            try {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                Log.d(TAG, "[P2P-DIAG] Receiver thread started")
                while (isActive) {
                    val line = reader.readLine() ?: break
                    try {
                        val message = Json.decodeFromString<P2PMessage>(line)
                        onMessageReceived?.invoke(message)
                    } catch (e: Exception) {
                        Log.w(TAG, "[P2P-DIAG] Failed to parse incoming message: ${e.message}")
                    }
                }
                Log.i(TAG, "[P2P-DIAG] Receiver thread: Connection closed by peer")
            } catch (e: Exception) {
                if (isActive) {
                    Log.w(TAG, "[P2P-DIAG] Socket read error: ${e.message}")
                }
            } finally {
                disconnect()
            }
        }
    }

    override fun disconnect() {
        Log.i(TAG, "[P2P-DIAG] Disconnect invoked on WiFiDirectTransport")
        connectionAttemptJob?.cancel()
        connectionAttemptJob = null
        
        cleanupSockets()
        _connectionState.value = ConnectionState.DISCONNECTED
        wifiDirectManager.updateSocketState(false)
    }

    private fun cleanupSockets() {
        receiverJob?.cancel()
        receiverJob = null
        
        try { writer?.close() } catch (ignored: Exception) {}
        writer = null
        
        try { clientSocket?.close() } catch (ignored: Exception) {}
        clientSocket = null
        
        try { serverSocket?.close() } catch (ignored: Exception) {}
        serverSocket = null
    }

    fun release() {
        disconnect()
    }

    override fun sendMessage(message: P2PMessage) {
        send(message, null)
    }

    override fun getConnectedPeerId(): String? {
        return clientSocket?.inetAddress?.hostAddress
    }

    override fun setOnMessageReceivedListener(callback: (P2PMessage) -> Unit) {
        this.onMessageReceived = callback
    }

    fun send(message: P2PMessage, onDeliveryStatus: ((DeliveryStatus) -> Unit)?) {
        transportScope.launch {
            if (_connectionState.value != ConnectionState.CONNECTED || writer == null) {
                Log.e(TAG, "[P2P-DIAG] Cannot send: Transport not connected")
                onDeliveryStatus?.invoke(DeliveryStatus.FAILED)
                return@launch
            }

            try {
                val json = Json.encodeToString(message)
                writer?.println(json)
                if (writer?.checkError() == true) {
                    Log.e(TAG, "[P2P-DIAG] PrintWriter error occurred during send")
                    onDeliveryStatus?.invoke(DeliveryStatus.FAILED)
                } else {
                    Log.d(TAG, "[P2P-DIAG] Sent message successfully: id=${message.messageId}")
                    onDeliveryStatus?.invoke(DeliveryStatus.DELIVERED)
                }
            } catch (e: Exception) {
                Log.e(TAG, "[P2P-DIAG] Failed to send message: ${e.message}", e)
                onDeliveryStatus?.invoke(DeliveryStatus.FAILED)
            }
        }
    }

    fun setMessageListener(listener: (P2PMessage) -> Unit) {
        this.onMessageReceived = listener
    }
}
