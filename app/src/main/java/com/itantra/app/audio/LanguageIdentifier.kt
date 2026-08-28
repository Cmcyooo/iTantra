package com.itantra.app.audio

import android.content.Context

/**
 * Status of speech language detection.
 */
enum class DetectionStatus {
    CONFIDENT,
    AMBIGUOUS,
    NO_SPEECH,
    ERROR
}

/**
 * Operating mode for transceiver language selection.
 */
enum class LanguageMode {
    AUTO,
    MANUAL
}

/**
 * Comprehensive result of a language identification inference.
 */
data class LanguageDetectionResult(
    val language: String?,
    val confidence: Float,
    val probabilities: Map<String, Float>,
    val latencyMs: Long,
    val status: DetectionStatus,
    val routingDecision: RoutingDecision? = null
)

/**
 * Central configuration for Automatic Language Identification (Auto-LID).
 * Configurable thresholds and intervals live in this single configuration object.
 */
data class LanguageDetectionConfig(
    val confidentThreshold: Float = 0.80f,
    val ambiguousThreshold: Float = 0.60f,
    val reverificationInterval: Int = 5,
    val consecutiveSwitchesRequired: Int = 2,
    val minSpeechDurationSeconds: Float = 0.4f,
    val maxLidAudioDurationSeconds: Float = 3.0f,
    val sampleRate: Int = 16000
)

/**
 * Clean abstraction for offline spoken language identification.
 */
interface LanguageIdentifier {
    val isReady: Boolean

    /**
     * Initializes native language identification sessions and weights.
     */
    suspend fun initialize(context: Context): Result<Unit>

    /**
     * Identifies spoken language from 16 kHz Float32 PCM audio samples.
     * All model inference MUST run off the main thread.
     */
    suspend fun identifyLanguage(samples: FloatArray): LanguageDetectionResult

    /**
     * Releases native resources and frees memory.
     */
    fun release()
}
