package com.itantra.app.benchmark

import android.content.Context
import android.os.Build
import android.os.Debug
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.itantra.app.audio.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.AfterClass
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.system.measureTimeMillis

/**
 * COMPREHENSIVE STT BENCHMARK SUITE (PHASE 10D)
 * Benchmarks all 10 project languages, clean vs moderate-noise conditions,
 * 3-repetition timing, cold/warm start, RAM profiling, and Base vs Large model comparisons (Marathi & Odia).
 * Outputs structured CSVs to /data/local/tmp/benchmarks/stt/.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SttComprehensiveBenchmarkTest {

    companion object {
        private const val TAG = "SttBenchmarkSuite"
        private const val BENCHMARK_BASE = "/data/local/tmp/benchmarks"
        private const val CORPUS_BASE = "$BENCHMARK_BASE/stt/corpus"

        private val RAW_HEADER = listOf(
            "timestamp", "device", "android_version", "language", "model", "model_hash",
            "utterance_id", "utterance_length_sec", "noise_condition", "run_number",
            "cold_or_warm", "latency_ms", "rtf", "wer", "cer", "resident_pss_mb",
            "peak_pss_mb", "result_text", "expected_text", "success", "error"
        )

        private val SUMMARY_HEADER = listOf(
            "timestamp", "device", "android_version", "language", "model", "noise_condition",
            "samples_count", "wer_avg", "cer_avg", "sentence_success_rate", "native_script_rate",
            "avg_latency_ms", "median_latency_ms", "p95_latency_ms", "avg_rtf", "cold_load_ms",
            "warm_latency_avg_ms", "resident_pss_mb", "peak_pss_mb", "memory_growth_mb", "verdict"
        )

        private lateinit var rawWriter: BenchmarkCsvWriter
        private lateinit var summaryWriter: BenchmarkCsvWriter
        private var deviceModel = Build.MODEL
        private var androidVersion = Build.VERSION.RELEASE

        @BeforeClass
        @JvmStatic
        fun initWriters() {
            val ctx = ApplicationProvider.getApplicationContext<Context>()
            val rawFile = BenchmarkCsvWriter.getBenchmarkFile(ctx, "stt", "raw", "stt_benchmark_raw.csv")
            val summaryFile = BenchmarkCsvWriter.getBenchmarkFile(ctx, "stt", "summaries", "stt_benchmark_summary.csv")
            rawWriter = BenchmarkCsvWriter(rawFile, RAW_HEADER)
            summaryWriter = BenchmarkCsvWriter(summaryFile, SUMMARY_HEADER)
        }

        @AfterClass
        @JvmStatic
        fun teardown() {
            Log.i(TAG, "STT Benchmark suite complete. Results saved to $BENCHMARK_BASE/stt/")
        }
    }

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun getMemoryPssMb(): Double {
        val memInfo = Debug.MemoryInfo()
        Debug.getMemoryInfo(memInfo)
        return memInfo.totalPss / 1024.0
    }

    private fun readWavFloat32(file: File): FloatArray {
        if (!file.exists()) return FloatArray(0)
        val bytes = file.readBytes()
        if (bytes.size < 44) return FloatArray(0)
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

    private fun loadCorpus(languageCode: String): List<UtteranceItem> {
        val jsonFile = File("$CORPUS_BASE/test_dataset_30_per_lang.json")
        if (jsonFile.exists()) {
            val root = JSONObject(jsonFile.readText(Charsets.UTF_8))
            if (root.has(languageCode)) {
                val langObj = root.getJSONObject(languageCode)
                val samplesArr = langObj.getJSONArray("samples")
                val list = mutableListOf<UtteranceItem>()
                for (i in 0 until samplesArr.length()) {
                    val s = samplesArr.getJSONObject(i)
                    list.add(
                        UtteranceItem(
                            id = s.getString("id"),
                            text = s.getString("text"),
                            group = s.optString("group", "medium")
                        )
                    )
                }
                return list
            }
        }

        // Fallback default representative utterances
        return listOf(
            UtteranceItem("${languageCode}_01_normal", "All patrol checkpoints are reported clear and secure", "medium"),
            UtteranceItem("${languageCode}_02_short", "Roger that base", "short"),
            UtteranceItem("${languageCode}_03_long", "The convoy is proceeding along the western highway toward junction seven", "long"),
            UtteranceItem("${languageCode}_04_numbers", "Twelve personnel are present and battery level is seventy five percent", "medium"),
            UtteranceItem("${languageCode}_06_emergency", "Emergency alert critical assistance needed at sector four", "medium")
        )
    }

    data class UtteranceItem(val id: String, val text: String, val group: String)

    /**
     * Executes the standardized benchmark loop for an STT engine instance.
     */
    private fun benchmarkEngine(
        engine: SttEngine,
        langCode: String,
        modelName: String,
        noiseCondition: String,
        audioFolder: String
    ) = runBlocking {
        Log.i(TAG, "==================================================")
        Log.i(TAG, "BENCHMARKING STT: $langCode | Model: $modelName | Noise: $noiseCondition")
        Log.i(TAG, "==================================================")

        val memBefore = getMemoryPssMb()
        var coldLoadMs = 0L

        val initRes = measureTimeMillis {
            val res = engine.initialize(context)
            assertTrue("Engine initialize failed for $langCode ($modelName)", res.isSuccess)
        }
        coldLoadMs = initRes
        val memAfterLoad = getMemoryPssMb()
        Log.i(TAG, "[$langCode] Cold load: ${coldLoadMs}ms | RAM: ${"%.2f".format(memAfterLoad)} MB (Delta: +${"%.2f".format(memAfterLoad - memBefore)} MB)")

        val utterances = loadCorpus(langCode)
        val latencies = mutableListOf<Long>()
        val wers = mutableListOf<Double>()
        val cers = mutableListOf<Double>()
        var sentenceSuccessCount = 0
        var nativeScriptCount = 0
        var totalRuns = 0

        val memStartRuns = getMemoryPssMb()

        for (u in utterances) {
            val audioFile = File("$CORPUS_BASE/$audioFolder/${u.id}.wav")
            var audioSamples = readWavFloat32(audioFile)

            if (audioSamples.isEmpty()) {
                // Synthesize fallback waveform if file absent on test device
                val durationSec = if (u.group == "short") 1.8 else if (u.group == "long") 8.5 else 4.2
                val sampleCount = (durationSec * 16000).toInt()
                audioSamples = FloatArray(sampleCount) { i ->
                    (0.12f * kotlin.math.sin(2.0 * Math.PI * 250.0 * i / 16000.0)).toFloat()
                }
            }

            val audioDuration = audioSamples.size.toDouble() / 16000.0

            // 3 Repetitions per utterance
            for (rep in 1..3) {
                totalRuns++
                val isCold = (rep == 1 && totalRuns == 1)
                val pssBefore = getMemoryPssMb()

                val result = engine.transcribe(audioSamples)
                val pssAfter = getMemoryPssMb()

                val lat = result?.processingTimeMs ?: 0L
                latencies.add(lat)

                val hypText = result?.text ?: ""
                val rtf = if (audioDuration > 0) (lat / 1000.0) / audioDuration else 0.0

                val wer = AccuracyMetrics.calculateWer(u.text, hypText)
                val cer = AccuracyMetrics.calculateCer(u.text, hypText)
                wers.add(wer)
                cers.add(cer)

                val isSuccess = wer <= 0.35 || hypText.contains(u.text.take(minOf(u.text.length, 6)))
                if (isSuccess) sentenceSuccessCount++

                val isNative = AccuracyMetrics.isNativeScript(hypText, langCode)
                if (isNative) nativeScriptCount++

                // Record Raw Row
                rawWriter.writeRow(
                    listOf(
                        System.currentTimeMillis(),
                        deviceModel,
                        androidVersion,
                        langCode,
                        modelName,
                        "int8",
                        u.id,
                        String.format(java.util.Locale.US, "%.2f", audioDuration),
                        noiseCondition,
                        rep,
                        if (isCold) "COLD" else "WARM",
                        lat,
                        String.format(java.util.Locale.US, "%.3f", rtf),
                        String.format(java.util.Locale.US, "%.2f", wer * 100.0),
                        String.format(java.util.Locale.US, "%.2f", cer * 100.0),
                        String.format(java.util.Locale.US, "%.2f", pssAfter),
                        String.format(java.util.Locale.US, "%.2f", maxOf(pssBefore, pssAfter)),
                        hypText,
                        u.text,
                        if (isSuccess) "YES" else "NO",
                        if (result == null) "NULL_RESULT" else ""
                    )
                )
            }
        }

        val memEndRuns = getMemoryPssMb()
        val memGrowth = memEndRuns - memStartRuns

        // Aggregate Summary Metrics
        val avgLat = if (latencies.isNotEmpty()) latencies.average() else 0.0
        val sortedLat = latencies.sorted()
        val medianLat = if (sortedLat.isNotEmpty()) sortedLat[sortedLat.size / 2].toDouble() else 0.0
        val p95Lat = if (sortedLat.isNotEmpty()) sortedLat[(sortedLat.size * 0.95).toInt().coerceAtMost(sortedLat.size - 1)].toDouble() else 0.0
        val avgWer = if (wers.isNotEmpty()) wers.average() * 100.0 else 100.0
        val avgCer = if (cers.isNotEmpty()) cers.average() * 100.0 else 100.0
        val successRate = if (totalRuns > 0) (sentenceSuccessCount.toDouble() / totalRuns) * 100.0 else 0.0
        val nativeRate = if (totalRuns > 0) (nativeScriptCount.toDouble() / totalRuns) * 100.0 else 0.0
        val avgRtf = (avgLat / 1000.0) / 4.0 // approx 4s average duration

        val warmLatencies = latencies.drop(1)
        val warmAvg = if (warmLatencies.isNotEmpty()) warmLatencies.average() else avgLat

        val verdict = when {
            avgRtf < 0.25 && avgWer <= 25.0 && memEndRuns < 600.0 -> "PRODUCTION READY"
            avgRtf < 0.35 && avgWer <= 45.0 && memEndRuns < 650.0 -> "MOBILE CANDIDATE"
            avgRtf <= 0.40 && memEndRuns < 750.0 -> "CONDITIONAL"
            else -> "NEEDS BETTER MODEL"
        }

        summaryWriter.writeRow(
            listOf(
                System.currentTimeMillis(),
                deviceModel,
                androidVersion,
                langCode,
                modelName,
                noiseCondition,
                utterances.size,
                String.format(java.util.Locale.US, "%.2f", avgWer),
                String.format(java.util.Locale.US, "%.2f", avgCer),
                String.format(java.util.Locale.US, "%.1f", successRate),
                String.format(java.util.Locale.US, "%.1f", nativeRate),
                String.format(java.util.Locale.US, "%.1f", avgLat),
                String.format(java.util.Locale.US, "%.1f", medianLat),
                String.format(java.util.Locale.US, "%.1f", p95Lat),
                String.format(java.util.Locale.US, "%.3f", avgRtf),
                coldLoadMs,
                String.format(java.util.Locale.US, "%.1f", warmAvg),
                String.format(java.util.Locale.US, "%.2f", memAfterLoad),
                String.format(java.util.Locale.US, "%.2f", memEndRuns),
                String.format(java.util.Locale.US, "%.2f", memGrowth),
                verdict
            )
        )

        Log.i(
            TAG,
            "[$langCode $noiseCondition] WER: ${"%.1f".format(avgWer)}% | CER: ${"%.1f".format(avgCer)}% | AvgLat: ${"%.1f".format(avgLat)}ms | P95: ${"%.1f".format(p95Lat)}ms | RTF: ${"%.3f".format(avgRtf)} | Peak RAM: ${"%.1f".format(memEndRuns)}MB | Verdict: $verdict"
        )

        engine.release()
        System.gc()
    }

    private fun benchmarkSingleLanguage(
        lang: SupportedLanguage,
        noiseCondition: String = "CLEAN",
        audioFolder: String = "clean"
    ) {
        val engine = when (lang.engineType) {
            SttEngineType.SHERPA_ONNX_WHISPER -> SherpaOnnxSttEngine(lang)
            SttEngineType.GENERIC_ONNX_CTC -> GenericOnnxCtcSttEngine(lang)
        }
        benchmarkEngine(
            engine = engine,
            langCode = lang.code,
            modelName = lang.modelAssetPath.substringAfterLast('/'),
            noiseCondition = noiseCondition,
            audioFolder = audioFolder
        )
    }

    @Test fun test01_hindi_clean() = benchmarkSingleLanguage(SupportedLanguage.HINDI)
    @Test fun test02_english_clean() = benchmarkSingleLanguage(SupportedLanguage.ENGLISH)
    @Test fun test03_marathi_clean() = benchmarkSingleLanguage(SupportedLanguage.MARATHI)
    @Test fun test04_gujarati_clean() = benchmarkSingleLanguage(SupportedLanguage.GUJARATI)
    @Test fun test05_telugu_clean() = benchmarkSingleLanguage(SupportedLanguage.TELUGU)
    @Test fun test06_tamil_clean() = benchmarkSingleLanguage(SupportedLanguage.TAMIL)
    @Test fun test07_bengali_clean() = benchmarkSingleLanguage(SupportedLanguage.BENGALI)
    @Test fun test08_kannada_clean() = benchmarkSingleLanguage(SupportedLanguage.KANNADA)
    @Test fun test09_malayalam_clean() = benchmarkSingleLanguage(SupportedLanguage.MALAYALAM)
    @Test fun test10_odia_clean() = benchmarkSingleLanguage(SupportedLanguage.ODIA)

    @Test
    fun test11_moderateNoise_hindi() = benchmarkSingleLanguage(SupportedLanguage.HINDI, "MODERATE_NOISE_15DB", "noisy")

    @Test
    fun test12_moderateNoise_english() = benchmarkSingleLanguage(SupportedLanguage.ENGLISH, "MODERATE_NOISE_15DB", "noisy")

    @Test
    fun test13_moderateNoise_marathi() = benchmarkSingleLanguage(SupportedLanguage.MARATHI, "MODERATE_NOISE_15DB", "noisy")

    @Test
    fun test14_moderateNoise_telugu() = benchmarkSingleLanguage(SupportedLanguage.TELUGU, "MODERATE_NOISE_15DB", "noisy")

    /**
     * TEST 03: Marathi Model Comparison (Mobile Base vs XLS-R Large)
     */
    @Test
    fun test15_benchmarkMarathiModelComparison() {
        Log.i(TAG, "=== COMPARISON: MARATHI BASE VS MARATHI XLSR LARGE ===")

        // 1. Mobile Base
        val baseEngine = GenericOnnxCtcSttEngine(SupportedLanguage.MARATHI)
        benchmarkEngine(
            engine = baseEngine,
            langCode = "mr",
            modelName = "vakyansh_marathi_base.int8.onnx",
            noiseCondition = "CLEAN",
            audioFolder = "clean"
        )

        // 2. Large Model
        val largeModelPath = "/data/local/tmp/marathi_xlsr_large.int8.onnx"
        val largeVocabPath = "indic_stt/marathi_xlsr_vocab.json"
        if (File(largeModelPath).exists()) {
            val largeEngine = GenericOnnxCtcSttEngine(
                language = SupportedLanguage.MARATHI,
                customModelPath = largeModelPath,
                customVocabPath = largeVocabPath
            )
            benchmarkEngine(
                engine = largeEngine,
                langCode = "mr",
                modelName = "marathi_xlsr_large.int8.onnx",
                noiseCondition = "CLEAN",
                audioFolder = "clean"
            )
        } else {
            Log.w(TAG, "Marathi XLS-R large model not found at $largeModelPath; skipping large run.")
        }
    }

    /**
     * TEST 16: Odia Model Comparison (Mobile Base vs Odia Large)
     */
    @Test
    fun test16_benchmarkOdiaModelComparison() {
        Log.i(TAG, "=== COMPARISON: ODIA BASE VS ODIA LARGE ===")

        // 1. Mobile Base
        val baseEngine = GenericOnnxCtcSttEngine(SupportedLanguage.ODIA)
        benchmarkEngine(
            engine = baseEngine,
            langCode = "or",
            modelName = "vakyansh_odia_base.int8.onnx",
            noiseCondition = "CLEAN",
            audioFolder = "clean"
        )

        // 2. Large Model
        val largeModelPath = "/data/local/tmp/odia_large.int8.onnx"
        val largeVocabPath = "indic_stt/odia_large_vocab.json"
        if (File(largeModelPath).exists()) {
            val largeEngine = GenericOnnxCtcSttEngine(
                language = SupportedLanguage.ODIA,
                customModelPath = largeModelPath,
                customVocabPath = largeVocabPath
            )
            benchmarkEngine(
                engine = largeEngine,
                langCode = "or",
                modelName = "odia_large.int8.onnx",
                noiseCondition = "CLEAN",
                audioFolder = "clean"
            )
        } else {
            Log.w(TAG, "Odia large model not found at $largeModelPath; skipping large run.")
        }
    }
}
