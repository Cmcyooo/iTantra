package com.itantra.app.audio

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import com.k2fsa.sherpa.onnx.*

/**
 * Possible speech states for VAD.
 */
enum class VadStatus {
    SILENCE,
    SPEECH_DETECTED,
    SPEAKING,
    SPEECH_ENDED
}

/**
 * Manages Silero VAD via sherpa-onnx.
 * Converts PCM16 to Float and detects speech segments.
 */
class VadManager(context: Context) {
    private var vad: Vad? = null
    
    // VAD Configuration
    private val sampleRate = 16000
    private val windowSize = 512
    
    private var currentStatus = VadStatus.SILENCE
    
    companion object {
        private const val TAG = "VadManager"
    }

    init {
        try {
            // Copy model from assets to filesDir to ensure it's not compressed and has a physical path
            val modelName = "silero_vad.onnx"
            val modelFile = java.io.File(context.filesDir, modelName)
            
            context.assets.open(modelName).use { input ->
                java.io.FileOutputStream(modelFile).use { output ->
                    input.copyTo(output)
                }
            }
            
            Log.i(TAG, "Model copied to: ${modelFile.absolutePath}, size: ${modelFile.length()} bytes")

            // Configure Silero VAD with optimized speech onset and natural pause tolerance
            // Parameter order: (model, threshold, minSilenceDuration, minSpeechDuration, windowSize)
            val sileroConfig = SileroVadModelConfig(
                modelFile.absolutePath,
                0.4f,   // threshold
                0.7f,   // minSilenceDuration: 700ms prevents premature utterance cutoff during natural pauses
                0.15f,  // minSpeechDuration: 150ms ensures rapid onset capture without clipping first syllable
                windowSize
            )
            
            // Configure Ten VAD with empty model to disable it
            val tenVadConfig = TenVadModelConfig(
                "",
                0.5f,
                0.5f,
                0.25f,
                1000
            )
            
            // VadModelConfig(sileroVad, tenVad, sampleRate, numThreads)
            val modelConfig = VadModelConfig(
                sileroConfig,
                tenVadConfig,
                sampleRate,
                1
            )
            
            // Initialize Vad with null AssetManager since we provide absolute paths to model files
            vad = Vad(null, modelConfig)
            Log.i(TAG, "Silero VAD initialized successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Silero VAD", e)
            vad = null
        }
    }

    /**
     * Complete decision result of VAD processing on an audio chunk.
     */
    data class VadDecision(
        val probability: Float,
        val nativeSpeechDetected: Boolean,
        val status: VadStatus
    )



    /**
     * Processes a chunk of normalized Float PCM samples.
     * Returns the complete VadDecision containing speech probability, native detection flag, and status.
     */
    fun processDecision(floatData: FloatArray): VadDecision {
        val vad = vad ?: return VadDecision(0.0f, false, VadStatus.SILENCE)
        
        // 1. Compute speech probability on chunk if chunk size matches windowSize
        val prob = try {
            if (floatData.size == windowSize) vad.compute(floatData) else -1.0f
        } catch (e: Exception) {
            -1.0f
        }

        // 2. Push audio to detector
        vad.acceptWaveform(floatData)

        val isSpeech = vad.isSpeechDetected()
        
        // 3. Update internal state transitions
        val wasSpeaking = currentStatus == VadStatus.SPEAKING || currentStatus == VadStatus.SPEECH_DETECTED
        
        if (isSpeech) {
            currentStatus = if (!wasSpeaking) {
                Log.d(TAG, "Speech detected!")
                VadStatus.SPEECH_DETECTED
            } else {
                VadStatus.SPEAKING
            }
        } else {
            currentStatus = if (wasSpeaking) {
                Log.d(TAG, "Speech ended.")
                VadStatus.SPEECH_ENDED
            } else {
                VadStatus.SILENCE
            }
        }

        return VadDecision(
            probability = prob,
            nativeSpeechDetected = isSpeech,
            status = currentStatus
        )
    }

    /**
     * Processes a chunk and returns VadDecision (backward compatibility).
     */
    fun processWithProbability(floatData: FloatArray): VadDecision {
        return processDecision(floatData)
    }

    /**
     * Backward-compatible process method returning only VadStatus.
     */
    fun process(floatData: FloatArray): VadStatus {
        return processDecision(floatData).status
    }

    /**
     * Resets the VAD detector state.
     */
    fun reset() {
        try {
            vad?.clear()
            vad?.reset()
        } catch (e: Exception) {
            Log.w(TAG, "Error resetting VAD", e)
        }
        currentStatus = VadStatus.SILENCE
    }

    /**
     * Releases the VAD detector resources.
     */
    fun release() {
        vad?.release()
        vad = null
    }
}

/**
 * Backward-compatible typealias for VadDecision.
 */
typealias VadProcessResult = VadManager.VadDecision
