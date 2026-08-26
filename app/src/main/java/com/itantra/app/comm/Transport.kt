package com.itantra.app.comm

import kotlinx.coroutines.flow.StateFlow

/**
 * Possible connection states for any transport.
 */
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

/**
 * Abstraction for peer-to-peer communication.
 */
interface Transport {
    val connectionState: StateFlow<ConnectionState>
    val lastError: StateFlow<String?>

    /**
     * Start listening for or connect to a peer.
     * In Phase 5, this will be Wi-Fi based.
     */
    fun connect(targetId: String? = null)

    /**
     * Stop communication and release resources.
     */
    fun disconnect()

    /**
     * Send a message to the connected peer.
     */
    fun sendMessage(message: P2PMessage)

    /**
     * Set a callback for received messages.
     */
    fun setOnMessageReceivedListener(callback: (P2PMessage) -> Unit)
}
