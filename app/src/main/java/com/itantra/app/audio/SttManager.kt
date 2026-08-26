package com.itantra.app.audio

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.*
import java.io.File
import java.io.FileOutputStream
import kotlin.system.measureTimeMillis

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
 * Manages offline STT using sherpa-onnx and Whisper Tiny English model.
 * Handles model asset extraction and inference.
 */
class SttManager(private val context: Context) {
    private var recognizer: OfflineRecognizer? = null
    
    companion object {
        private const val TAG = "SttManager"
        private const val MODEL_DIR = "whisper-tiny-en"
    }

    init {
        try {
            // Extract Whisper model files to internal storage if they don't exist or are incomplete
            val encoderFile = copyAssetToFiles(context, "$MODEL_DIR/encoder.onnx")
            val decoderFile = copyAssetToFiles(context, "$MODEL_DIR/decoder.onnx")
            val tokensFile = copyAssetToFiles(context, "$MODEL_DIR/tokens.txt")

            val whisperConfig = OfflineWhisperModelConfig(
                encoder = encoderFile.absolutePath,
                decoder = decoderFile.absolutePath,
                language = "en",
                task = "transcribe"
            )

            val modelConfig = OfflineModelConfig(
                whisper = whisperConfig,
                tokens = tokensFile.absolutePath,
                numThreads = 1,
                debug = false,
                provider = "cpu",
                modelType = "whisper"
            )

            val config = OfflineRecognizerConfig(
                modelConfig = modelConfig,
                decodingMethod = "greedy_search"
            )

            // Since we use absolute paths for model files in OfflineModelConfig, 
            // we MUST set assetManager to null here.
            recognizer = OfflineRecognizer(null, config)
            Log.i(TAG, "Offline STT Recognizer initialized successfully with Whisper Tiny (EN).")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Offline STT Recognizer", e)
        }
    }

    /**
     * Transcribes the provided 16 kHz Float PCM audio.
     * Returns SttResult or null if recognizer is not initialized.
     */
    fun transcribe(samples: FloatArray): SttResult? {
        val recognizer = recognizer ?: return null
        if (samples.isEmpty()) return null

        try {
            val audioDuration = samples.size.toDouble() / 16000.0
            var resultText = ""
            
            val processingTimeMs = measureTimeMillis {
                val stream = recognizer.createStream()
                stream.acceptWaveform(samples, 16000)
                recognizer.decode(stream)
                resultText = recognizer.getResult(stream).text.trim()
                stream.release()
            }

            val rtf = if (audioDuration > 0) (processingTimeMs / 1000.0) / audioDuration else 0.0

            Log.d(TAG, "Transcription complete. Text: '$resultText', Duration: ${"%.2f".format(audioDuration)}s, Time: ${processingTimeMs}ms, RTF: ${"%.3f".format(rtf)}")

            return SttResult(
                text = resultText,
                audioDuration = audioDuration,
                processingTimeMs = processingTimeMs,
                rtf = rtf
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error during transcription", e)
            return null
        }
    }

    /**
     * Releases native recognizer resources.
     */
    fun release() {
        recognizer?.release()
        recognizer = null
    }

    private fun copyAssetToFiles(context: Context, assetPath: String): File {
        val outFile = File(context.filesDir, assetPath)
        if (outFile.exists() && outFile.length() > 0) {
            return outFile
        }
        
        outFile.parentFile?.mkdirs()
        context.assets.open(assetPath).use { input ->
            FileOutputStream(outFile).use { output ->
                input.copyTo(output)
            }
        }
        Log.d(TAG, "Extracted asset $assetPath to ${outFile.absolutePath}")
        return outFile
    }
}
