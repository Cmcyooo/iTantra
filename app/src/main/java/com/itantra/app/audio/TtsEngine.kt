package com.itantra.app.audio

import android.content.Context
import android.media.AudioAttributes
import com.k2fsa.sherpa.onnx.GeneratedAudio

/**
 * Common abstraction for offline Text-to-Speech engines in iTantra.
 * Encapsulates model lifecycle, speech synthesis, and audio output.
 */
interface TtsEngine {
    val voiceConfig: TtsVoiceConfig
    val isReady: Boolean

    suspend fun initialize(context: Context): Result<Unit>
    suspend fun generateSpeech(text: String): GeneratedAudio?
    fun speak(text: String, onComplete: (() -> Unit)?)
    fun playAudioWithAttributes(
        samples: FloatArray,
        sampleRate: Int,
        attributes: AudioAttributes,
        onComplete: () -> Unit
    )
    fun stop()
    fun release()
}
