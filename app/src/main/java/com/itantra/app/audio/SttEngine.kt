package com.itantra.app.audio

import android.content.Context

/**
 * Supported underlying STT engine implementations.
 */
enum class SttEngineType {
    SHERPA_ONNX_WHISPER,
    GENERIC_ONNX_CTC
}

/**
 * Model lifecycle states for language management.
 */
enum class ModelLifecycleState {
    UNLOADED,
    LOADING,
    READY,
    TRANSCRIBING,
    RELEASING,
    FAILED
}

/**
 * Unified interface for speech recognition engines.
 */
interface SttEngine {
    val language: SupportedLanguage
    val isReady: Boolean

    suspend fun initialize(context: Context): Result<Unit>
    suspend fun transcribe(samples: FloatArray): SttResult?
    fun release()
}
