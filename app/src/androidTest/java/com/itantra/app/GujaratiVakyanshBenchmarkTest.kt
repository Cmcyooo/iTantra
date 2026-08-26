package com.itantra.app

import android.os.Debug
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class GujaratiVakyanshBenchmarkTest {

    companion object {
        private const val TAG = "GujaratiVakyanshBenchmark"
        private const val MODEL_PATH = "/data/local/tmp/vakyansh_gujarati_base.int8.onnx"
        private const val EXPECTED_SHA256 = "bc64cb802a7dc162f38f3cec4d6a09536d2820ca87c50af370ff3d18d07bd71a"

        val VOCAB = arrayOf(
            "<s>", "<pad>", "</s>", "<unk>", "|",
            "ઁ", "ં", "ઃ", "અ", "આ", "ઇ", "ઈ", "ઉ", "ઊ", "ઋ", "ઍ", "એ", "ઐ", "ઑ", "ઓ", "ઔ",
            "ક", "ખ", "ગ", "ઘ", "ઙ", "ચ", "છ", "જ", "ઝ", "ઞ", "ટ", "ઠ", "ડ", "ઢ", "ણ",
            "ત", "થ", "દ", "ધ", "ન", "પ", "ફ", "બ", "ભ", "મ", "ય", "ર", "લ", "ળ", "વ",
            "શ", "ષ", "સ", "હ", "ા", "િ", "ી", "ુ", "ૂ", "ૃ", "ૄ", "ૅ", "ે", "ૈ", "ૉ", "ો", "ૌ", "્"
        )
    }

    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192 * 1024)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun readWavFloat32(file: File): FloatArray {
        val bytes = file.readBytes()
        val dataOffset = 44
        val numSamples = (bytes.size - dataOffset) / 2
        val floatArray = FloatArray(numSamples)
        val byteBuffer = ByteBuffer.wrap(bytes, dataOffset, bytes.size - dataOffset).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until numSamples) {
            val sample = byteBuffer.short
            floatArray[i] = sample.toFloat() / 32768.0f
        }
        return floatArray
    }

    private fun decodeCtc(logits: Array<Array<FloatArray>>): String {
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
                if (tid in VOCAB.indices && tid != 1 && tid != 0 && tid != 2 && tid != 3) {
                    val token = VOCAB[tid]
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

    @Test
    fun benchmarkGujaratiVakyanshOnDevice() {
        val modelFile = File(MODEL_PATH)
        Log.i(TAG, "Starting Android Gujarati Vakyansh Benchmark on device...")
        Log.i(TAG, "Device Model: ${android.os.Build.MODEL} (${android.os.Build.DEVICE}), API ${android.os.Build.VERSION.SDK_INT}")

        val resultsJson = JSONObject()
        resultsJson.put("device_model", android.os.Build.MODEL)
        resultsJson.put("device_sdk", android.os.Build.VERSION.SDK_INT)
        resultsJson.put("cpu_abi", android.os.Build.SUPPORTED_ABIS.joinToString(","))

        var actualSha256 = "N/A"
        if (modelFile.exists()) {
            actualSha256 = computeSha256(modelFile)
            Log.i(TAG, "Model File: ${modelFile.absolutePath}, Size: ${modelFile.length()} bytes")
            Log.i(TAG, "Computed SHA-256: $actualSha256")
            Log.i(TAG, "Expected SHA-256: $EXPECTED_SHA256")
            resultsJson.put("model_size_bytes", modelFile.length())
            resultsJson.put("model_sha256", actualSha256)
            resultsJson.put("sha256_match", actualSha256.equals(EXPECTED_SHA256, ignoreCase = true))
        }

        val initialPssKb = Debug.getPss()
        Log.i(TAG, "Initial Process PSS: ${initialPssKb / 1024.0} MB")

        val ortEnv = OrtEnvironment.getEnvironment()
        val sessOpts = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(2)
        }

        val loadStart = System.nanoTime()
        val session = if (modelFile.exists()) {
            ortEnv.createSession(modelFile.absolutePath, sessOpts)
        } else null
        val loadDurationMs = (System.nanoTime() - loadStart) / 1_000_000.0

        val postLoadPssKb = Debug.getPss()
        val modelResidentPssMb = (postLoadPssKb - initialPssKb) / 1024.0
        Log.i(TAG, "Model Load Duration: ${"%.2f".format(loadDurationMs)} ms")
        Log.i(TAG, "Post-Load Process PSS: ${postLoadPssKb / 1024.0} MB (Delta: ${"%.2f".format(modelResidentPssMb)} MB)")

        resultsJson.put("model_load_ms", loadDurationMs)
        resultsJson.put("initial_pss_kb", initialPssKb)
        resultsJson.put("post_load_pss_kb", postLoadPssKb)
        resultsJson.put("model_resident_pss_mb", modelResidentPssMb)

        val samplesArray = JSONArray()

        if (session != null) {
            val testDir = File("/data/local/tmp/gujarati_test")
            val wavFiles = if (testDir.exists()) {
                testDir.listFiles { _, name -> name.endsWith(".wav") }?.sortedBy { it.name } ?: emptyList()
            } else emptyList()

            Log.i(TAG, "Found ${wavFiles.size} test WAV files in ${testDir.absolutePath}")

            val sampleAudioList = mutableListOf<FloatArray>()
            val sampleNames = mutableListOf<String>()

            for (wav in wavFiles.take(12)) {
                sampleAudioList.add(readWavFloat32(wav))
                sampleNames.add(wav.name)
            }

            var totalAudioDurSec = 0.0
            var totalInferMs = 0.0

            for (i in sampleAudioList.indices) {
                val audio = sampleAudioList[i]
                val name = sampleNames[i]
                val durSec = audio.size / 16000.0
                totalAudioDurSec += durSec

                val inputTensor = OnnxTensor.createTensor(ortEnv, FloatBuffer.wrap(audio), longArrayOf(1, audio.size.toLong()))

                val t0 = System.nanoTime()
                val result = session.run(mapOf("input_values" to inputTensor))
                val t1 = System.nanoTime()
                val inferMs = (t1 - t0) / 1_000_000.0
                totalInferMs += inferMs

                val rtf = inferMs / (durSec * 1000.0)
                val outputTensor = result.get(0)
                @Suppress("UNCHECKED_CAST")
                val logits = outputTensor.value as Array<Array<FloatArray>>
                val decodedText = decodeCtc(logits)

                Log.i(TAG, "Sample #$i ($name) [${"%.2f".format(durSec)}s] -> Infer: ${"%.1f".format(inferMs)}ms, RTF: ${"%.3f".format(rtf)}")
                Log.i(TAG, "  Hypothesis: $decodedText")

                val sampleObj = JSONObject().apply {
                    put("name", name)
                    put("duration_sec", durSec)
                    put("infer_ms", inferMs)
                    put("rtf", rtf)
                    put("text", decodedText)
                }
                samplesArray.put(sampleObj)

                inputTensor.close()
                result.close()
            }

            val avgInferMs = totalInferMs / sampleAudioList.size
            val avgAudioDurSec = totalAudioDurSec / sampleAudioList.size
            val overallRtf = totalInferMs / (totalAudioDurSec * 1000.0)
            val peakPssKb = Debug.getPss()

            Log.i(TAG, "--- ON-DEVICE GUJARATI BENCHMARK SUMMARY ---")
            Log.i(TAG, "Avg Latency: ${"%.1f".format(avgInferMs)} ms (Avg audio: ${"%.2f".format(avgAudioDurSec)}s)")
            Log.i(TAG, "Overall RTF: ${"%.3f".format(overallRtf)}")
            Log.i(TAG, "Peak Process PSS: ${peakPssKb / 1024.0} MB")

            resultsJson.put("samples", samplesArray)
            resultsJson.put("avg_infer_ms", avgInferMs)
            resultsJson.put("overall_rtf", overallRtf)
            resultsJson.put("peak_pss_kb", peakPssKb)
            resultsJson.put("peak_pss_mb", peakPssKb / 1024.0)

            // Stability: 10 repeated inferences on sample 0
            Log.i(TAG, "Running 10 Repeated Inferences for Stability / Memory Leak Test...")
            val sample0 = sampleAudioList[0]
            val stabilityLatencies = mutableListOf<Double>()
            for (rep in 1..10) {
                val inputTensor = OnnxTensor.createTensor(ortEnv, FloatBuffer.wrap(sample0), longArrayOf(1, sample0.size.toLong()))
                val t0 = System.nanoTime()
                val res = session.run(mapOf("input_values" to inputTensor))
                val t1 = System.nanoTime()
                val ms = (t1 - t0) / 1_000_000.0
                stabilityLatencies.add(ms)
                inputTensor.close()
                res.close()
            }
            val stabilityAvgMs = stabilityLatencies.average()
            val endPssKb = Debug.getPss()
            Log.i(TAG, "Stability 10-run Avg: ${"%.1f".format(stabilityAvgMs)} ms | End PSS: ${endPssKb / 1024.0} MB")
            resultsJson.put("stability_10run_avg_ms", stabilityAvgMs)
            resultsJson.put("end_pss_mb", endPssKb / 1024.0)
            resultsJson.put("memory_leak_detected", (endPssKb - peakPssKb) > 50 * 1024)

            session.close()
        }

        ortEnv.close()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val outJsonFile = File(context.filesDir, "gujarati_android_results.json")
        outJsonFile.writeText(resultsJson.toString(2))
        Log.i(TAG, "Benchmark results written to ${outJsonFile.absolutePath}")
        Log.i(TAG, "Results JSON:\n${resultsJson.toString(2)}")
    }
}
