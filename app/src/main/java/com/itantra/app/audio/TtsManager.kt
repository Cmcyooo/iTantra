package com.itantra.app.audio

import android.content.Context
import android.media.AudioAttributes
import com.k2fsa.sherpa.onnx.GeneratedAudio
import kotlinx.coroutines.flow.StateFlow

/**
 * Result of a TTS synthesis session.
 */
data class TtsResult(
    val audioDuration: Double,
    val synthesisTimeMs: Long,
    val firstAudioLatencyMs: Long,
    val rtf: Double
)

/**
 * Possible status of the TTS engine.
 */
enum class TtsStatus {
    IDLE,
    LOADING,
    SYNTHESIZING,
    PLAYING,
    COMPLETE,
    ERROR
}

/**
 * Unified facade managing offline TTS in iTantra.
 * Delegates to [LanguageTtsManager] for single-active model management across all 10 languages.
 * Preserves 100% backward-compatibility for TransceiverManager, AlertPlaybackManager, and UI.
 */
class TtsManager(private val context: Context) {
    val languageTtsManager: LanguageTtsManager = LanguageTtsManager.getInstance(context)

    val status: StateFlow<TtsStatus> get() = languageTtsManager.status
    val lastResult: StateFlow<TtsResult?> get() = languageTtsManager.lastResult

    fun isReady(): Boolean = languageTtsManager.isReady()

    suspend fun generateSpeech(text: String, languageCode: String? = null): GeneratedAudio? {
        return languageTtsManager.generateSpeech(text, languageCode)
    }

    fun speak(text: String, languageCode: String? = null, onComplete: (() -> Unit)? = null) {
        languageTtsManager.speak(text, languageCode, onComplete)
    }

    fun playAudioWithAttributes(
        samples: FloatArray,
        sampleRate: Int,
        attributes: AudioAttributes,
        onComplete: () -> Unit
    ) {
        languageTtsManager.playAudioWithAttributes(samples, sampleRate, attributes, onComplete)
    }

    fun stop() {
        languageTtsManager.stop()
    }

    fun release() {
        languageTtsManager.release()
    }
}
