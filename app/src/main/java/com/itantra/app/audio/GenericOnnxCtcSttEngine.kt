package com.itantra.app.audio

import android.content.Context
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.FloatBuffer
import kotlin.system.measureTimeMillis

/**
 * Speech recognition engine wrapping generic ONNX Runtime Android
 * for non-autoregressive Wav2Vec2 CTC models (Hindi, Gujarati, Telugu, Kannada).
 */
class GenericOnnxCtcSttEngine(
    override val language: SupportedLanguage
) : SttEngine {

    companion object {
        private const val TAG = "GenericOnnxCtcSttEngine"
        init {
            try {
                System.loadLibrary("ort_runtime")
                System.loadLibrary("onnxruntime4j_jni")
            } catch (t: Throwable) {
                Log.w(TAG, "ort_runtime load: ${t.message}")
            }
        }
    }

    private var ortEnv: OrtEnvironment? = null
    private var session: OrtSession? = null
    private var vocab: Array<String> = emptyArray()
    private var padTokenId: Int = 1

    override val isReady: Boolean
        get() = session != null && vocab.isNotEmpty()

    override suspend fun initialize(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Initializing GenericOnnxCtcSttEngine for ${language.displayName} (${language.nativeName})...")

            // 1. Resolve and load vocabulary
            vocab = loadVocabulary(context, language.vocabOrTokensAssetPath)
            Log.i(TAG, "Loaded vocabulary for ${language.displayName} with ${vocab.size} tokens.")

            // 2. Resolve model file (check filesDir, check /data/local/tmp, or extract from assets)
            val modelFile = resolveModelFile(context, language.modelAssetPath)
            if (!modelFile.exists() || modelFile.length() == 0L) {
                return@withContext Result.failure(IllegalStateException("Model file not found at ${modelFile.absolutePath}"))
            }

            Log.i(TAG, "Model file resolved: ${modelFile.absolutePath} (${modelFile.length()} bytes)")

            // 3. Initialize ONNX Runtime session with CPU provider and 2 threads
            val env = OrtEnvironment.getEnvironment()
            ortEnv = env

            val sessOpts = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(2)
            }

            val loadTimeMs = measureTimeMillis {
                session = env.createSession(modelFile.absolutePath, sessOpts)
            }

            Log.i(TAG, "ONNX Session created successfully in ${loadTimeMs}ms for ${language.displayName}.")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize GenericOnnxCtcSttEngine for ${language.displayName}", e)
            release()
            Result.failure(e)
        }
    }

    override suspend fun transcribe(samples: FloatArray): SttResult? = withContext(Dispatchers.Default) {
        val env = ortEnv ?: return@withContext null
        val sess = session ?: return@withContext null
        if (samples.isEmpty()) return@withContext null

        try {
            val audioDuration = samples.size.toDouble() / 16000.0
            var decodedText = ""

            val processingTimeMs = measureTimeMillis {
                val inputTensor = OnnxTensor.createTensor(
                    env,
                    FloatBuffer.wrap(samples),
                    longArrayOf(1, samples.size.toLong())
                )

                val result = sess.run(mapOf("input_values" to inputTensor))
                val outputTensor = result.get(0)

                @Suppress("UNCHECKED_CAST")
                val logits = outputTensor.value as Array<Array<FloatArray>>
                decodedText = decodeCtc(logits)

                inputTensor.close()
                result.close()
            }

            val rtf = if (audioDuration > 0) (processingTimeMs / 1000.0) / audioDuration else 0.0
            Log.d(TAG, "[${language.displayName}] Text: '$decodedText', Audio: ${"%.2f".format(audioDuration)}s, Latency: ${processingTimeMs}ms, RTF: ${"%.3f".format(rtf)}")

            SttResult(
                text = decodedText,
                audioDuration = audioDuration,
                processingTimeMs = processingTimeMs,
                rtf = rtf
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error during transcription in GenericOnnxCtcSttEngine", e)
            null
        }
    }

    override fun release() {
        Log.i(TAG, "Releasing GenericOnnxCtcSttEngine for ${language.displayName}...")
        try {
            session?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing OrtSession", e)
        }
        try {
            ortEnv?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing OrtEnvironment", e)
        }
        session = null
        ortEnv = null
        vocab = emptyArray()
    }

    private fun decodeCtc(logits: Array<Array<FloatArray>>): String {
        if (logits.isEmpty() || logits[0].isEmpty()) return ""

        val seqLen = logits[0].size
        val predIds = IntArray(seqLen)

        for (t in 0 until seqLen) {
            val frame = logits[0][t]
            var maxIdx = 0
            var maxVal = frame[0]
            for (c in 1 until frame.size) {
                if (frame[c] > maxVal) {
                    maxVal = frame[c]
                    maxIdx = c
                }
            }
            predIds[t] = maxIdx
        }

        val sb = StringBuilder()
        var prev = -1
        for (tid in predIds) {
            if (tid != prev) {
                if (tid in vocab.indices && tid != padTokenId && tid != 0 && tid != 2 && tid != 3) {
                    val token = vocab[tid]
                    if (token == "|") {
                        sb.append(" ")
                    } else {
                        sb.append(token)
                    }
                }
                prev = tid
            }
        }
        return sb.toString().trim().replace(Regex("\\s+"), " ")
    }

    private fun loadVocabulary(context: Context, vocabAssetPath: String): Array<String> {
        val jsonStr = try {
            val fileInStorage = File(context.filesDir, vocabAssetPath)
            if (fileInStorage.exists()) {
                fileInStorage.readText(Charsets.UTF_8)
            } else {
                context.assets.open(vocabAssetPath).bufferedReader(Charsets.UTF_8).use { it.readText() }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not open $vocabAssetPath directly: ${e.message}")
            // Fallback check in /data/local/tmp/
            val tmpFile = File("/data/local/tmp/${File(vocabAssetPath).name}")
            if (tmpFile.exists()) {
                tmpFile.readText(Charsets.UTF_8)
            } else {
                throw e
            }
        }

        val json = JSONObject(jsonStr)
        var maxId = -1
        val map = mutableMapOf<Int, String>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val token = keys.next()
            val id = json.getInt(token)
            map[id] = token
            if (id > maxId) maxId = id
            if (token == "<pad>") padTokenId = id
        }

        val tokensArray = Array(maxId + 1) { "" }
        for ((id, token) in map) {
            tokensArray[id] = token
        }
        return tokensArray
    }

    private fun resolveModelFile(context: Context, assetPath: String): File {
        val fileName = File(assetPath).name

        // 1. Check local filesDir
        val localFile = File(context.filesDir, assetPath)
        if (localFile.exists() && localFile.length() > 100 * 1024 * 1024) {
            return localFile
        }

        // 2. Check /data/local/tmp/ (pushed during test/dev)
        val devTmpFile = File("/data/local/tmp/$fileName")
        if (devTmpFile.exists() && devTmpFile.length() > 100 * 1024 * 1024) {
            return devTmpFile
        }

        // 3. Extract from assets if present
        localFile.parentFile?.mkdirs()
        try {
            context.assets.open(assetPath).use { input ->
                FileOutputStream(localFile).use { output ->
                    input.copyTo(output)
                }
            }
            Log.i(TAG, "Extracted asset $assetPath to ${localFile.absolutePath} (${localFile.length()} bytes)")
            return localFile
        } catch (e: Exception) {
            Log.w(TAG, "Asset $assetPath not available in APK bundle: ${e.message}")
        }

        return localFile
    }
}
