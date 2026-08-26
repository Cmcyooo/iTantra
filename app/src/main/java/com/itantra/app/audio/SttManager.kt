package com.itantra.app.audio

import android.content.Context
import android.util.Log
import kotlinx.coroutines.runBlocking

/**
 * Result of an STT inference session.
 */
data class SttResult(
    val text: String,
    val audioDuration: Double,
    val processingTimeMs: Long,
    val rtf: Double
)

/**
 * Possible status of the STT engine.
 */
enum class SttStatus {
    IDLE,
    LISTENING,
    SPEECH_DETECTED,
    TRANSCRIBING,
    COMPLETE,
    ERROR
}

/**
 * Manages STT inference in iTantra, delegating to the unified [LanguageModelManager].
 * Preserves full backward-compatibility for TransceiverManager and AudioCaptureManager.
 */
class SttManager(private val context: Context) {
    val languageModelManager: LanguageModelManager = LanguageModelManager.getInstance(context)

    companion object {
        private const val TAG = "SttManager"
    }

    /**
     * Transcribes the provided 16 kHz Float PCM audio using the active language model.
     * Returns SttResult or null if transcription fails.
     */
    fun transcribe(samples: FloatArray): SttResult? {
        if (samples.isEmpty()) return null
        return try {
            runBlocking {
                languageModelManager.transcribe(samples)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during transcription delegation", e)
            null
        }
    }

    /**
     * Releases native recognizer resources.
     */
    fun release() {
        languageModelManager.release()
    }
}
