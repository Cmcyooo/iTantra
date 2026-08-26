package com.itantra.app.comm

import kotlinx.serialization.Serializable

/**
 * Lightweight message model for peer-to-peer transport.
 * Designed for low-bitrate communication with Emergency/Alert prioritization support.
 */
@Serializable
data class P2PMessage(
    val messageId: String,
    val timestamp: Long,
    val language: String = "en",
    val text: String,
    val senderName: String? = null,
    val messageType: String = MESSAGE_TYPE_NORMAL,
    val priority: String = PRIORITY_NORMAL
) {
    companion object {
        const val MESSAGE_TYPE_NORMAL = "NORMAL"
        const val MESSAGE_TYPE_ALERT = "ALERT"
        const val MESSAGE_TYPE_ACK = "ACK"

        const val PRIORITY_NORMAL = "NORMAL"
        const val PRIORITY_HIGH = "HIGH"
    }

    val isAlert: Boolean
        get() = messageType == MESSAGE_TYPE_ALERT || priority == PRIORITY_HIGH

    val isAck: Boolean
        get() = messageType == MESSAGE_TYPE_ACK
}
