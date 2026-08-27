package com.itantra.app.audio

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.system.measureTimeMillis

/**
 * Speech recognition engine wrapping sherpa-onnx for Whisper Tiny (English).
 */
class SherpaOnnxSttEngine(
    override val language: SupportedLanguage = SupportedLanguage.ENGLISH
) : SttEngine {

    companion object {
        private const val TAG = "SherpaOnnxSttEngine"
        private const val MODEL_DIR = "whisper-tiny-en"
        init {
            try {
                System.loadLibrary("onnxruntime")
                System.loadLibrary("sherpa-onnx-jni")
            } catch (t: Throwable) {
                Log.w(TAG, "Sherpa native load: ${t.message}")
            }
        }
    }

    private var recognizer: OfflineRecognizer? = null

    override val isReady: Boolean
        get() = recognizer != null

    override suspend fun initialize(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Initializing SherpaOnnxSttEngine for ${language.displayName}...")
            val encoderFile = copyAssetToFiles(context, "$MODEL_DIR/encoder.onnx")
            val decoderFile = copyAssetToFiles(context, "$MODEL_DIR/decoder.onnx")
            val tokensFile = copyAssetToFiles(context, "$MODEL_DIR/tokens.txt")

            val whisperConfig = OfflineWhisperModelConfig(
                encoder = encoderFile.absolutePath,
                decoder = decoderFile.absolutePath,
                language = language.code,
                task = "transcribe"
            )

            val modelConfig = OfflineModelConfig(
                whisper = whisperConfig,
                tokens = tokensFile.absolutePath,
                numThreads = 2,
                debug = false,
                provider = "cpu",
                modelType = "whisper"
            )

            val config = OfflineRecognizerConfig(
                modelConfig = modelConfig,
                decodingMethod = "greedy_search"
            )

            recognizer = OfflineRecognizer(null, config)
            Log.i(TAG, "SherpaOnnxSttEngine initialized successfully.")
            Result.success(Unit)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to initialize SherpaOnnxSttEngine", t)
            recognizer = null
            try {
                File(context.filesDir, "$MODEL_DIR/decoder.onnx").delete()
                File(context.filesDir, "$MODEL_DIR/encoder.onnx").delete()
            } catch (_: Exception) {}
            Result.failure(t)
        }
    }

    override suspend fun transcribe(samples: FloatArray): SttResult? = withContext(Dispatchers.Default) {
        val rec = recognizer ?: return@withContext null
        if (samples.isEmpty()) return@withContext null

        try {
            Log.i(TAG, "[STT-INFERENCE] language=${language.code} engine=SHERPA_WHISPER_TINY model=$MODEL_DIR tokens=$MODEL_DIR/tokens.txt samples=${samples.size} sr=16000")

            val audioDuration = samples.size.toDouble() / 16000.0
            var resultText = ""

            val processingTimeMs = measureTimeMillis {
                val stream = rec.createStream()
                stream.acceptWaveform(samples, 16000)
                rec.decode(stream)
                val rawText = rec.getResult(stream).text.trim()
                resultText = IndicDomainNormalizer.normalize(rawText, language.code)
                stream.release()
            }

            val rtf = if (audioDuration > 0) (processingTimeMs / 1000.0) / audioDuration else 0.0
            Log.d(TAG, "[${language.displayName}] Audio: ${"%.2f".format(audioDuration)}s, Latency: ${processingTimeMs}ms, RTF: ${"%.3f".format(rtf)}")

            SttResult(
                text = resultText,
                audioDuration = audioDuration,
                processingTimeMs = processingTimeMs,
                rtf = rtf
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error during transcription in SherpaOnnxSttEngine", e)
            null
        }
    }

    override fun release() {
        Log.i(TAG, "Releasing SherpaOnnxSttEngine...")
        recognizer?.release()
        recognizer = null
    }

    private fun copyAssetToFiles(context: Context, assetPath: String): File {
        val outFile = File(context.filesDir, assetPath)
        val assetSize = try {
            context.assets.openFd(assetPath).length
        } catch (e: Exception) {
            -1L
        }
        if (outFile.exists() && outFile.length() > 0 && (assetSize <= 0 || outFile.length() == assetSize)) {
            return outFile
        }
        outFile.parentFile?.mkdirs()
        val tempFile = File(context.filesDir, "$assetPath.tmp")
        tempFile.parentFile?.mkdirs()
        context.assets.open(assetPath).use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        }
        if (outFile.exists()) outFile.delete()
        tempFile.renameTo(outFile)
        return outFile
    }
}
