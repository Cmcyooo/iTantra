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

            // Configure Silero VAD with slightly more aggressive endpointing
            val sileroConfig = SileroVadModelConfig(
                modelFile.absolutePath,
                0.5f,  // threshold
                0.5f,  // minSpeechDuration
                0.3f,  // minSilenceDuration (Reduced from 0.5s to 0.3s for faster turn-around)
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
     * Processes a chunk of normalized Float PCM samples.
     * Returns the updated VadStatus.
     */
    fun process(floatData: FloatArray): VadStatus {
        val vad = vad ?: return VadStatus.SILENCE
        
        // Push audio to detector
        vad.acceptWaveform(floatData)

        val isSpeech = vad.isSpeechDetected()
        
        // Check for state changes
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

        return currentStatus
    }

    /**
     * Resets the VAD detector state.
     */
    fun reset() {
        // Recreate or reset if supported
    }

    /**
     * Releases the VAD detector resources.
     */
    fun release() {
        vad?.release()
        vad = null
    }
}
