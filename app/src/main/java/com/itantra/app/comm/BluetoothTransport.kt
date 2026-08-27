package com.itantra.app.comm

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.PrintWriter
import java.util.*

/**
 * Bluetooth implementation of the Transport interface using Classic Bluetooth Sockets.
 * Supports Host (Server) and Client modes.
 */
class BluetoothTransport : Transport {
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    override val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var serverSocket: BluetoothServerSocket? = null
    private var clientSocket: BluetoothSocket? = null
    private var writer: PrintWriter? = null
    private var receiverJob: Job? = null
    
    private var onMessageReceived: ((P2PMessage) -> Unit)? = null
    private val transportScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    companion object {
        private const val TAG = "BluetoothTransport"
        private val MY_UUID: UUID = UUID.fromString("fa812000-01d0-11ed-ad5d-0800200c9a66")
        private const val NAME = "iTantraService"
    }

    @SuppressLint("MissingPermission")
    override fun connect(targetId: String?) {
        if (bluetoothAdapter == null) {
            _lastError.value = "Bluetooth not supported"
            return
        }
        
        if (_connectionState.value != ConnectionState.DISCONNECTED) return
        
        _connectionState.value = ConnectionState.CONNECTING
        _lastError.value = null

        transportScope.launch {
            try {
                if (targetId.isNullOrBlank()) {
                    // Host mode: Start BluetoothServerSocket
                    Log.i(TAG, "[BT-DIAG] Connect request: Host mode (Listening for incoming RFCOMM)...")
                    startServer()
                } else {
                    // Client mode: Connect to BluetoothDevice address
                    Log.i(TAG, "[BT-DIAG] Connect request: Client mode connecting to $targetId...")
                    startClient(targetId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "[BT-DIAG] Connection failed: ${e.message}", e)
                _lastError.value = e.message
                _connectionState.value = ConnectionState.ERROR
                cleanup()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun startServer() = withContext(Dispatchers.IO) {
        Log.d(TAG, "[BT-DIAG] Starting RFCOMM server socket...")
        serverSocket = bluetoothAdapter?.listenUsingRfcommWithServiceRecord(NAME, MY_UUID)
        
        val socket = try {
            serverSocket?.accept()
        } catch (e: IOException) {
            Log.e(TAG, "[BT-DIAG] RFCOMM accept() failed: ${e.message}")
            null
        }
        
        if (socket != null) {
            Log.i(TAG, "[BT-DIAG] RFCOMM server accepted connection from ${socket.remoteDevice.address}")
            setupConnection(socket)
            try { serverSocket?.close() } catch (ignored: Exception) {}
            serverSocket = null
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun startClient(address: String) = withContext(Dispatchers.IO) {
        Log.i(TAG, "[BT-DIAG] Connecting to remote RFCOMM device: $address")
        val device: BluetoothDevice? = bluetoothAdapter?.getRemoteDevice(address)
        if (device == null) {
            Log.e(TAG, "[BT-DIAG] Device not found for address $address")
            throw IOException("Device not found")
        }
        
        val socket = device.createRfcommSocketToServiceRecord(MY_UUID)
        bluetoothAdapter?.cancelDiscovery() // Always cancel discovery before connecting
        
        try {
            socket.connect()
            Log.i(TAG, "[BT-DIAG] RFCOMM client successfully connected to $address")
            setupConnection(socket)
        } catch (e: IOException) {
            Log.e(TAG, "[BT-DIAG] RFCOMM socket.connect() failed: ${e.message}")
            try { socket.close() } catch (ignored: Exception) {}
            throw e
        }
    }

    private fun setupConnection(socket: BluetoothSocket) {
        clientSocket = socket
        writer = PrintWriter(socket.outputStream, true)
        _connectionState.value = ConnectionState.CONNECTED
        Log.i(TAG, "[BT-DIAG] RFCOMM Transport fully CONNECTED to ${socket.remoteDevice.name ?: socket.remoteDevice.address}")

        startReceiving(socket)
    }

    private fun startReceiving(socket: BluetoothSocket) {
        receiverJob = transportScope.launch {
            try {
                val reader = BufferedReader(InputStreamReader(socket.inputStream))
                Log.d(TAG, "[BT-DIAG] Receiver loop started")
                while (isActive) {
                    val line = reader.readLine() ?: break
                    try {
                        val message = Json.decodeFromString<P2PMessage>(line)
                        Log.d(TAG, "[BT-DIAG] Received message: ${message.messageId}")
                        onMessageReceived?.invoke(message)
                    } catch (e: Exception) {
                        Log.w(TAG, "[BT-DIAG] Failed to parse message: $line", e)
                    }
                }
            } catch (e: Exception) {
                if (isActive) {
                    Log.e(TAG, "[BT-DIAG] Receiver error: ${e.message}")
                    _lastError.value = "Connection lost: ${e.message}"
                    _connectionState.value = ConnectionState.ERROR
                }
            } finally {
                disconnect()
            }
        }
    }

    override fun getConnectedPeerId(): String? = clientSocket?.remoteDevice?.address

    override fun sendMessage(message: P2PMessage) {
        transportScope.launch {
            try {
                val json = Json.encodeToString(message)
                writer?.println(json)
                Log.d(TAG, "[BT-DIAG] Sent message: ${message.messageId}")
            } catch (e: Exception) {
                Log.e(TAG, "[BT-DIAG] Failed to send message: ${e.message}")
                _lastError.value = "Send failed: ${e.message}"
            }
        }
    }

    fun send(message: P2PMessage, onDeliveryStatus: ((DeliveryStatus) -> Unit)?) {
        transportScope.launch {
            if (_connectionState.value != ConnectionState.CONNECTED || writer == null) {
                onDeliveryStatus?.invoke(DeliveryStatus.FAILED)
                return@launch
            }
            try {
                val json = Json.encodeToString(message)
                writer?.println(json)
                if (writer?.checkError() == true) {
                    onDeliveryStatus?.invoke(DeliveryStatus.FAILED)
                } else {
                    onDeliveryStatus?.invoke(DeliveryStatus.DELIVERED)
                }
            } catch (e: Exception) {
                onDeliveryStatus?.invoke(DeliveryStatus.FAILED)
            }
        }
    }

    override fun setOnMessageReceivedListener(callback: (P2PMessage) -> Unit) {
        this.onMessageReceived = callback
    }

    override fun disconnect() {
        if (_connectionState.value == ConnectionState.DISCONNECTED && clientSocket == null) return
        
        Log.d(TAG, "Disconnecting Bluetooth...")
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
        } catch (e: Exception) {
            Log.w(TAG, "Error during cleanup", e)
        } finally {
            writer = null
            clientSocket = null
            serverSocket = null
        }
    }
}
