package com.itantra.app.comm

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Manages peer-to-peer communication.
 * Acts as a facade for different transport implementations.
 */
class CommunicationManager(private val transport: Transport) {
    private val _messages = MutableStateFlow<List<P2PMessage>>(emptyList())
    val messages: StateFlow<List<P2PMessage>> = _messages.asStateFlow()

    private val _lastMessage = MutableStateFlow<P2PMessage?>(null)
    val lastMessage: StateFlow<P2PMessage?> = _lastMessage.asStateFlow()

    private val _latency = MutableStateFlow<Long?>(null)
    val latency: StateFlow<Long?> = _latency.asStateFlow()

    val connectionState: StateFlow<ConnectionState> = transport.connectionState
    val lastError: StateFlow<String?> = transport.lastError

    private val managerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var externalListener: ((P2PMessage) -> Unit)? = null

    companion object {
        private const val TAG = "CommunicationManager"
    }

    init {
        transport.setOnMessageReceivedListener { message ->
            handleReceivedMessage(message)
        }
    }

    fun connect(targetAddress: String? = null) {
        transport.connect(targetAddress)
    }

    fun disconnect() {
        transport.disconnect()
    }

    fun sendText(text: String, language: String = "en") {
        val message = P2PMessage(
            messageId = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            language = language,
            text = text
        )
        transport.sendMessage(message)
        
        // Add local message to list for UI
        addMessageToList(message)
    }

    fun setOnMessageReceivedListener(listener: (P2PMessage) -> Unit) {
        this.externalListener = listener
    }

    private fun handleReceivedMessage(message: P2PMessage) {
        managerScope.launch {
            val now = System.currentTimeMillis()
            val transportLatency = now - message.timestamp
            _latency.value = transportLatency
            
            Log.d(TAG, "Received message: ${message.text}, Latency: ${transportLatency}ms")
            
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
