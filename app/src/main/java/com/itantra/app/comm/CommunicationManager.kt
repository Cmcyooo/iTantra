package com.itantra.app.comm

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Manages peer-to-peer communication.
 * Acts as a facade for different transport implementations.
 */
class CommunicationManager(initialTransport: Transport) {
    private var _transport = initialTransport
    
    private val _selectedTransport = MutableStateFlow(TransportMode.WIFI)
    val selectedTransport: StateFlow<TransportMode> = _selectedTransport.asStateFlow()

    private val _messages = MutableStateFlow<List<P2PMessage>>(emptyList())
    val messages: StateFlow<List<P2PMessage>> = _messages.asStateFlow()

    private val _lastMessage = MutableStateFlow<P2PMessage?>(null)
    val lastMessage: StateFlow<P2PMessage?> = _lastMessage.asStateFlow()

    private val _latency = MutableStateFlow<Long?>(null)
    val latency: StateFlow<Long?> = _latency.asStateFlow()

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val managerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var externalListener: ((P2PMessage) -> Unit)? = null
    
    private var transportJob: Job? = null

    companion object {
        private const val TAG = "CommunicationManager"
    }

    init {
        observeTransport(_transport)
    }

    /**
     * Explicitly switches the active transport mode and implementation.
     * Decouples transport selection from connection state, always resetting
     * connection state to DISCONNECTED so outbound CONNECT is immediately enabled.
     */
    fun selectTransport(mode: TransportMode, newTransport: Transport) {
        val prevTransport = _selectedTransport.value
        val prevConn = _connectionState.value
        Log.i(TAG, "[WIFI-STATE] selectedTransport=$mode previousTransport=$prevTransport connectionState=DISCONNECTED previousConnectionState=$prevConn reason=TRANSPORT_SELECTED")
        
        _selectedTransport.value = mode
        
        // Safely disconnect previous transport
        try {
            _transport.disconnect()
        } catch (e: Exception) {
            Log.w(TAG, "Error disconnecting old transport", e)
        }
        transportJob?.cancel()
        
        _transport = newTransport
        _connectionState.value = ConnectionState.DISCONNECTED
        _lastError.value = null
        
        observeTransport(_transport)
        Log.i(TAG, "Selected transport: $mode (${newTransport::class.java.simpleName})")
    }

    fun setTransport(newTransport: Transport) {
        val guessedMode = when (newTransport::class.java.simpleName) {
            "WiFiTransport" -> TransportMode.WIFI
            "WiFiDirectTransport" -> TransportMode.WIFI_DIRECT
            "BluetoothTransport" -> TransportMode.BLUETOOTH
            else -> _selectedTransport.value
        }
        selectTransport(guessedMode, newTransport)
    }

    private fun observeTransport(transport: Transport) {
        transportJob?.cancel()
        transportJob = managerScope.launch {
            launch {
                transport.connectionState.collect { state ->
                    val prev = _connectionState.value
                    _connectionState.value = state
                    if (prev != state) {
                        Log.i(TAG, "[WIFI-STATE] selectedTransport=${_selectedTransport.value} connectionState=$state previousState=$prev newState=$state reason=TRANSPORT_STATE_CHANGED")
                    }
                }
            }
            launch {
                transport.lastError.collect { error ->
                    _lastError.value = error
                }
            }
            transport.setOnMessageReceivedListener { message ->
                handleReceivedMessage(message)
            }
        }
    }

    fun connect(targetAddress: String? = null) {
        val isClientConnect = !targetAddress.isNullOrBlank()
        if (isClientConnect) {
            val prev = _connectionState.value
            _connectionState.value = ConnectionState.CONNECTING
            Log.i(TAG, "[WIFI-STATE] selectedTransport=${_selectedTransport.value} connectionState=CONNECTING previousState=$prev newState=CONNECTING reason=CONNECT_REQUESTED")
        } else {
            Log.i(TAG, "[WIFI-STATE] selectedTransport=${_selectedTransport.value} connectionState=${_connectionState.value} reason=PASSIVE_HOST_REQUESTED")
        }
        _transport.connect(targetAddress)
    }

    fun disconnect() {
        val prev = _connectionState.value
        try {
            _transport.disconnect()
        } catch (e: Exception) {
            Log.w(TAG, "Error during transport disconnect", e)
        }
        _connectionState.value = ConnectionState.DISCONNECTED
        Log.i(TAG, "[WIFI-STATE] selectedTransport=${_selectedTransport.value} connectionState=DISCONNECTED previousState=$prev newState=DISCONNECTED reason=USER_DISCONNECTED")
    }

    private val pendingAcks = java.util.concurrent.ConcurrentHashMap<String, Long>()

    fun getConnectedPeerId(): String? = _transport.getConnectedPeerId()

    fun sendText(
        text: String,
        language: String = "en",
        senderName: String? = null,
        utteranceId: String = UUID.randomUUID().toString()
    ): P2PMessage {
        val sendStartTime = android.os.SystemClock.elapsedRealtime()
        val msgId = UUID.randomUUID().toString()
        val message = P2PMessage(
            messageId = msgId,
            timestamp = System.currentTimeMillis(),
            language = language,
            text = text,
            senderName = senderName,
            messageType = P2PMessage.MESSAGE_TYPE_NORMAL,
            priority = P2PMessage.PRIORITY_NORMAL,
            utteranceId = utteranceId
        )
        if (text.isNotEmpty()) {
            pendingAcks[message.messageId] = sendStartTime
        }
        val payloadBytes = try {
            Json.encodeToString(message).toByteArray(Charsets.UTF_8).size
        } catch (e: Exception) {
            text.toByteArray(Charsets.UTF_8).size
        }
        Log.i(TAG, "[TX-MESSAGE] utteranceId=$utteranceId messageId=$msgId language=$language payloadBytes=$payloadBytes")

        try {
            _transport.sendMessage(message)
        } catch (e: Exception) {
            Log.e(TAG, "Transport send failed for utteranceId=$utteranceId", e)
            _lastError.value = "Send failed: ${e.message}"
            throw e
        }
        
        // Add local message to list for UI
        addMessageToList(message)
        return message
    }

    /**
     * Sends a HIGH-priority Emergency Alert message.
     */
    fun sendAlert(
        text: String,
        language: String = "en",
        senderName: String? = null,
        utteranceId: String = UUID.randomUUID().toString()
    ): P2PMessage {
        val sendStartTime = android.os.SystemClock.elapsedRealtime()
        val msgId = UUID.randomUUID().toString()
        val message = P2PMessage(
            messageId = msgId,
            timestamp = System.currentTimeMillis(),
            language = language,
            text = text,
            senderName = senderName,
            messageType = P2PMessage.MESSAGE_TYPE_ALERT,
            priority = P2PMessage.PRIORITY_HIGH,
            utteranceId = utteranceId
        )
        pendingAcks[message.messageId] = sendStartTime
        val payloadBytes = try {
            Json.encodeToString(message).toByteArray(Charsets.UTF_8).size
        } catch (e: Exception) {
            text.toByteArray(Charsets.UTF_8).size
        }
        Log.i(TAG, "[TX-MESSAGE] utteranceId=$utteranceId messageId=$msgId language=$language payloadBytes=$payloadBytes (ALERT)")

        try {
            _transport.sendMessage(message)
        } catch (e: Exception) {
            Log.e(TAG, "Transport sendAlert failed for utteranceId=$utteranceId", e)
            _lastError.value = "Alert send failed: ${e.message}"
            throw e
        }
        addMessageToList(message)
        return message
    }

    /**
     * Sends an ACK for a received message.
     */
    fun sendAck(targetMessageId: String, senderName: String? = null) {
        val ackMessage = P2PMessage(
            messageId = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            text = targetMessageId,
            senderName = senderName,
            messageType = P2PMessage.MESSAGE_TYPE_ACK,
            priority = P2PMessage.PRIORITY_HIGH
        )
        try {
            _transport.sendMessage(ackMessage)
        } catch (e: Exception) {
            Log.e(TAG, "Transport sendAck failed", e)
        }
    }

    private var onAckReceivedListener: ((String) -> Unit)? = null

    fun setOnAckReceivedListener(listener: (String) -> Unit) {
        this.onAckReceivedListener = listener
    }

    fun setOnMessageReceivedListener(listener: (P2PMessage) -> Unit) {
        this.externalListener = listener
    }

    private fun handleReceivedMessage(message: P2PMessage) {
        managerScope.launch {
            if (message.isAck) {
                val targetId = message.text
                val sentTime = pendingAcks.remove(targetId)
                if (sentTime != null) {
                    val rttMs = android.os.SystemClock.elapsedRealtime() - sentTime
                    _latency.value = rttMs
                    Log.i(TAG, "[TELEMETRY-TRANSPORT] utteranceId=$targetId RTT=${rttMs}ms")
                }
                onAckReceivedListener?.invoke(message.text)
                return@launch
            }

            val payloadBytes = try {
                Json.encodeToString(message).toByteArray(Charsets.UTF_8).size
            } catch (e: Exception) {
                message.text.toByteArray(Charsets.UTF_8).size
            }
            Log.i(TAG, "[RX-MESSAGE] utteranceId=${message.utteranceId} messageId=${message.messageId} language=${message.language} payloadBytes=$payloadBytes")

            // Immediately send ACK for received message so sender can measure RTT
            if (message.text.isNotEmpty()) {
                sendAck(message.messageId)
            }
            
            Log.d(TAG, "Received message: utteranceId=${message.utteranceId} [type=${message.messageType}, priority=${message.priority}, lang=${message.language}]")
            
            _lastMessage.value = message
            addMessageToList(message)
            externalListener?.invoke(message)
        }
    }

    private fun addMessageToList(message: P2PMessage) {
        val currentList = _messages.value.toMutableList()
        // Simple duplicate check
        if (currentList.none { it.messageId == message.messageId }) {
            currentList.add(message)
            // Keep only last 50 messages
            if (currentList.size > 50) currentList.removeAt(0)
            _messages.value = currentList
        }
    }

    fun clearMessages() {
        _messages.value = emptyList()
        _lastMessage.value = null
        _latency.value = null
    }
}
