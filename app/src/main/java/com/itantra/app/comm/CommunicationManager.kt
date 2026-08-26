package com.itantra.app.comm

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Manages peer-to-peer communication.
 * Acts as a facade for different transport implementations.
 */
class CommunicationManager(initialTransport: Transport) {
    private var _transport = initialTransport
    
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

    private val managerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var externalListener: ((P2PMessage) -> Unit)? = null
    
    private var transportJob: Job? = null

    companion object {
        private const val TAG = "CommunicationManager"
    }

    init {
        observeTransport(_transport)
    }

    fun setTransport(newTransport: Transport) {
        if (_transport == newTransport) return
        
        _transport.disconnect()
        transportJob?.cancel()
        
        _transport = newTransport
        observeTransport(_transport)
        
        Log.i(TAG, "Switched transport to: ${newTransport::class.java.simpleName}")
    }

    private fun observeTransport(transport: Transport) {
        transportJob?.cancel()
        transportJob = managerScope.launch {
            launch {
                transport.connectionState.collect { state ->
                    _connectionState.value = state
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
        _transport.connect(targetAddress)
    }

    fun disconnect() {
        _transport.disconnect()
    }

    fun getConnectedPeerId(): String? = _transport.getConnectedPeerId()

    fun sendText(text: String, language: String = "en", senderName: String? = null): P2PMessage {
        val message = P2PMessage(
            messageId = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            language = language,
            text = text,
            senderName = senderName,
            messageType = P2PMessage.MESSAGE_TYPE_NORMAL,
            priority = P2PMessage.PRIORITY_NORMAL
        )
        try {
            _transport.sendMessage(message)
        } catch (e: Exception) {
            Log.e(TAG, "Transport send failed", e)
            _lastError.value = "Send failed: ${e.message}"
        }
        
        // Add local message to list for UI
        addMessageToList(message)
        return message
    }

    /**
     * Sends a HIGH-priority Emergency Alert message.
     */
    fun sendAlert(text: String, language: String = "en", senderName: String? = null): P2PMessage {
        val message = P2PMessage(
            messageId = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            language = language,
            text = text,
            senderName = senderName,
            messageType = P2PMessage.MESSAGE_TYPE_ALERT,
            priority = P2PMessage.PRIORITY_HIGH
        )
        try {
            _transport.sendMessage(message)
        } catch (e: Exception) {
            Log.e(TAG, "Transport sendAlert failed", e)
            _lastError.value = "Alert send failed: ${e.message}"
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
            val now = System.currentTimeMillis()
            val transportLatency = now - message.timestamp
            _latency.value = transportLatency

            if (message.isAck) {
                Log.d(TAG, "Received ACK for message: ${message.text}")
                onAckReceivedListener?.invoke(message.text)
                return@launch
            }
            
            Log.d(TAG, "Received message: ${message.text} [type=${message.messageType}, priority=${message.priority}], Latency: ${transportLatency}ms")
            
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
