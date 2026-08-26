package com.itantra.app.comm

import kotlinx.serialization.Serializable

/**
 * Lightweight message model for peer-to-peer transport.
 * Designed for low-bitrate communication.
 */
@Serializable
data class P2PMessage(
    val messageId: String,
    val timestamp: Long,
    val language: String = "en",
    val text: String,
    val senderName: String? = null
)
