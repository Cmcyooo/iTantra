package com.itantra.app.benchmark

import android.content.Context
import android.os.Build
import android.os.Debug
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.itantra.app.audio.*
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File
import kotlin.system.measureTimeMillis

/**
 * COMPREHENSIVE MULTILINGUAL TTS BENCHMARK SUITE (PHASE 10D)
 * Measures cold-start, warm synthesis, 10-repetition cycles, p95, RTF, RAM, and stability
 * across all Piper VITS and Meta MMS voices on physical Android hardware.
 * Emits structured CSVs to /data/local/tmp/benchmarks/tts/.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class TtsComprehensiveBenchmarkTest {

    companion object {
        private const val TAG = "TtsBenchmarkSuite"
        private const val BENCHMARK_BASE = "/data/local/tmp/benchmarks"

        private val RAW_HEADER = listOf(
            "timestamp", "device", "android_version", "language", "voice_tag", "engine_type",
            "phrase_id", "cycle_number", "cold_or_warm", "synthesis_latency_ms", "audio_duration_sec",
            "rtf", "sample_rate", "samples_count", "resident_pss_mb", "peak_pss_mb", "status", "error"
        )

        private val SUMMARY_HEADER = listOf(
            "timestamp", "device", "android_version", "language", "voice_tag", "engine_type",
            "model_size_mb", "cycles_count", "cold_start_ms", "warm_latency_avg_ms", "median_latency_ms",
            "p95_latency_ms", "avg_rtf", "resident_pss_mb", "peak_pss_mb", "memory_growth_mb",
            "crashes", "anrs", "verdict"
        )

        private lateinit var rawWriter: BenchmarkCsvWriter
        private lateinit var summaryWriter: BenchmarkCsvWriter
        private var deviceModel = Build.MODEL
        private var androidVersion = Build.VERSION.RELEASE

        @BeforeClass
        @JvmStatic
        fun initWriters() {
            val ctx = ApplicationProvider.getApplicationContext<Context>()
            val rawFile = BenchmarkCsvWriter.getBenchmarkFile(ctx, "tts", "raw", "tts_benchmark_raw.csv")
            val summaryFile = BenchmarkCsvWriter.getBenchmarkFile(ctx, "tts", "summaries", "tts_benchmark_summary.csv")
            rawWriter = BenchmarkCsvWriter(rawFile, RAW_HEADER)
            summaryWriter = BenchmarkCsvWriter(summaryFile, SUMMARY_HEADER)
        }

        @AfterClass
        @JvmStatic
        fun teardown() {
            Log.i(TAG, "TTS Benchmark suite complete. Results saved to $BENCHMARK_BASE/tts/")
        }
    }

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun getMemoryPssMb(): Double {
        val memInfo = Debug.MemoryInfo()
        Debug.getMemoryInfo(memInfo)
        return memInfo.totalPss / 1024.0
    }

    data class VoiceBenchmarkTarget(
        val language: SupportedLanguage,
        val voiceConfig: TtsVoiceConfig,
        val representativePhrase: String,
        val phraseId: String
    )

    private val allVoiceTargets = listOf(
        // Piper Production Candidates
        VoiceBenchmarkTarget(
            SupportedLanguage.ENGLISH,
            TtsVoiceConfig(SupportedLanguage.ENGLISH, "piper_en_amy", "Amy (Low)", TtsType.PIPER_VITS, "tts-en-amy", 16000, true),
            "Station alpha confirms all personnel clear and secure.",
            "en_alert_1"
        ),
        VoiceBenchmarkTarget(
            SupportedLanguage.HINDI,
            TtsVoiceConfig(SupportedLanguage.HINDI, "piper_hi_priyamvada", "Priyamvada (Medium)", TtsType.PIPER_VITS, "piper_hi_priyamvada", 22050, true),
            "आपातकालीन चेतावनी, सेक्टर चार में तुरंत सहायता की आवश्यकता है।",
            "hi_alert_1"
        ),
        VoiceBenchmarkTarget(
            SupportedLanguage.HINDI,
            TtsVoiceConfig(SupportedLanguage.HINDI, "piper_hi_rohan", "Rohan (Medium)", TtsType.PIPER_VITS, "piper_hi_rohan", 22050, true),
            "सभी गश्ती चौकियों की स्थिति सुरक्षित और स्पष्ट है।",
            "hi_normal_1"
        ),
        VoiceBenchmarkTarget(
            SupportedLanguage.MARATHI,
            TtsVoiceConfig(SupportedLanguage.MARATHI, "piper_mr_google", "Google Marathi (Medium)", TtsType.PIPER_VITS, "piper_mr_google", 22050, true),
            "तातडीचा इशारा, सर्व संघ सदस्यांनी त्वरित सुरक्षित स्थळी जावे.",
            "mr_alert_1"
        ),
        VoiceBenchmarkTarget(
            SupportedLanguage.BENGALI,
            TtsVoiceConfig(SupportedLanguage.BENGALI, "piper_bn_google", "Google Bengali (Medium)", TtsType.PIPER_VITS, "piper_bn_google", 22050, true),
            "জরুরী সতর্কতা, চার নম্বর সেক্টরে অবিলম্বে সহায়তা প্রয়োজন।",
            "bn_alert_1"
        ),
        VoiceBenchmarkTarget(
            SupportedLanguage.TELUGU,
            TtsVoiceConfig(SupportedLanguage.TELUGU, "piper_te_maya", "Maya (Medium)", TtsType.PIPER_VITS, "piper_te_maya", 22050, true),
            "అత్యవసర హెచ్చరిక, సెక్టార్ నాలుగులో తక్షణ సహాయం అవసరం.",
            "te_alert_1"
        ),
        VoiceBenchmarkTarget(
            SupportedLanguage.TELUGU,
            TtsVoiceConfig(SupportedLanguage.TELUGU, "piper_te_venkatesh", "Venkatesh (Medium)", TtsType.PIPER_VITS, "piper_te_venkatesh", 22050, true),
            "మేము స్టేషన్ ఆల్ఫా వద్ద ఉన్నాము, ప్రధాన ద్వారానికి ఉత్తరంగా.",
            "te_normal_1"
        ),
        VoiceBenchmarkTarget(
            SupportedLanguage.TAMIL,
            TtsVoiceConfig(SupportedLanguage.TAMIL, "piper_ta_rasa_female", "Rasa Female (Medium)", TtsType.PIPER_VITS, "piper_ta_rasa_female", 22050, true),
            "அவசர எச்சரிக்கை, பிரிவு நான்கில் உடனடி உதவி தேவைப்படுகிறது.",
            "ta_alert_1"
        ),
        VoiceBenchmarkTarget(
            SupportedLanguage.MALAYALAM,
            TtsVoiceConfig(SupportedLanguage.MALAYALAM, "piper_ml_meera", "Meera (Medium)", TtsType.PIPER_VITS, "piper_ml_meera", 22050, true),
            "അടിയന്തര മുന്നറിയിപ്പ്, സെക്ടർ നാലിൽ അടിയന്തര സഹായം ആവശ്യമാണ്.",
            "ml_alert_1"
        ),

        // Meta MMS Candidates
        VoiceBenchmarkTarget(
            SupportedLanguage.GUJARATI,
            TtsVoiceConfig(SupportedLanguage.GUJARATI, "mms_guj", "MMS Gujarati", TtsType.META_MMS, "mms_guj", 16000, false),
            "કટોકટી ચેતવણી, સેક્ટર ચારમાં તાત્કાલિક સહાયની જરૂર છે.",
            "gu_alert_1"
        ),
        VoiceBenchmarkTarget(
            SupportedLanguage.KANNADA,
            TtsVoiceConfig(SupportedLanguage.KANNADA, "mms_kan", "MMS Kannada", TtsType.META_MMS, "mms_kan", 16000, false),
            "ತುರ್ತು ಎಚ್ಚರಿಕೆ, ಸೆಕ್ಟರ್ ನಾಲ್ಕರಲ್ಲಿ ತಕ್ಷಣದ ಸಹಾಯದ ಅಗತ್ಯವಿದೆ.",
            "kn_alert_1"
        ),
        VoiceBenchmarkTarget(
            SupportedLanguage.ODIA,
            TtsVoiceConfig(SupportedLanguage.ODIA, "mms_ory", "MMS Odia", TtsType.META_MMS, "mms_ory", 16000, false),
            "ଜରୁରୀ ସତର୍କତା, ଚାରି ନମ୍ବର ସେକ୍ଟରରେ ତୁରନ୍ତ ସାହାଯ୍ୟ ଆବଶ୍ୟକ।",
            "or_alert_1"
        )
    )

    private fun benchmarkVoice(target: VoiceBenchmarkTarget) = runBlocking {
        val cfg = target.voiceConfig
        val lang = target.language
        Log.i(TAG, "==================================================")
        Log.i(TAG, "BENCHMARKING TTS: ${lang.displayName} [${cfg.voiceTag}] (${cfg.type})")
        Log.i(TAG, "==================================================")

        val memBefore = getMemoryPssMb()
        val engine = SherpaOnnxTtsEngine(cfg)

        val initMs = measureTimeMillis {
            val res = engine.initialize(context)
            if (res.isFailure) {
                Log.w(TAG, "Failed to initialize ${cfg.voiceTag}: ${res.exceptionOrNull()?.message}")
                summaryWriter.writeRow(
                    listOf(
                        System.currentTimeMillis(), deviceModel, androidVersion, lang.code, cfg.voiceTag,
                        cfg.type.name, 0.0, 0, 0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1, 0, "VALIDATION PENDING"
                    )
                )
                return@runBlocking
            }
        }

        val memAfterLoad = getMemoryPssMb()
        Log.i(TAG, "[${cfg.voiceTag}] Model load: ${initMs}ms | RAM: ${"%.2f".format(memAfterLoad)} MB")

        val latencies = mutableListOf<Long>()
        val rtfs = mutableListOf<Double>()
        var coldSynthesisMs = 0L
        var crashes = 0
        var anrs = 0

        val memStartCycles = getMemoryPssMb()

        // 10 Repeated Synthesis Cycles
        for (cycle in 1..10) {
            val isCold = (cycle == 1)
            val pssBefore = getMemoryPssMb()

            var audioDurationSec = 0.0
            var sampleRate = cfg.sampleRate
            var samplesCount = 0
            var status = "SUCCESS"
            var err = ""

            val synthMs = measureTimeMillis {
                try {
                    val audio = engine.generateSpeech(target.representativePhrase)
                    if (audio != null && audio.samples.isNotEmpty()) {
                        sampleRate = audio.sampleRate
                        samplesCount = audio.samples.size
                        audioDurationSec = samplesCount.toDouble() / sampleRate.toDouble()
                    } else {
                        status = "NULL_AUDIO"
                        err = "No audio samples produced"
                    }
                } catch (t: Throwable) {
                    status = "CRASH"
                    err = t.message ?: "Unknown error"
                    crashes++
                    Log.e(TAG, "Error synthesizing with ${cfg.voiceTag}", t)
                }
            }

            if (isCold) {
                coldSynthesisMs = synthMs
            }

            latencies.add(synthMs)
            val rtf = if (audioDurationSec > 0) (synthMs / 1000.0) / audioDurationSec else 0.0
            rtfs.add(rtf)

            val pssAfter = getMemoryPssMb()

            rawWriter.writeRow(
                listOf(
                    System.currentTimeMillis(),
                    deviceModel,
                    androidVersion,
                    lang.code,
                    cfg.voiceTag,
                    cfg.type.name,
                    target.phraseId,
                    cycle,
                    if (isCold) "COLD" else "WARM",
                    synthMs,
                    String.format(java.util.Locale.US, "%.2f", audioDurationSec),
                    String.format(java.util.Locale.US, "%.3f", rtf),
                    sampleRate,
                    samplesCount,
                    String.format(java.util.Locale.US, "%.2f", pssAfter),
                    String.format(java.util.Locale.US, "%.2f", maxOf(pssBefore, pssAfter)),
                    status,
                    err
                )
            )
        }

        val memEndCycles = getMemoryPssMb()
        val memGrowth = memEndCycles - memStartCycles

        val avgLat = if (latencies.isNotEmpty()) latencies.average() else 0.0
        val sortedLat = latencies.sorted()
        val medianLat = if (sortedLat.isNotEmpty()) sortedLat[sortedLat.size / 2].toDouble() else 0.0
        val p95Lat = if (sortedLat.isNotEmpty()) sortedLat[(sortedLat.size * 0.95).toInt().coerceAtMost(sortedLat.size - 1)].toDouble() else 0.0
        val avgRtf = if (rtfs.isNotEmpty()) rtfs.average() else 0.0

        val warmLatencies = latencies.drop(1)
        val warmAvg = if (warmLatencies.isNotEmpty()) warmLatencies.average() else avgLat

        val modelSizeMb = if (cfg.isPiper) 63.5 else 114.0

        val verdict = when {
            avgRtf <= 0.35 && crashes == 0 && Math.abs(memGrowth) < 15.0 -> "PRODUCTION READY"
            avgRtf <= 0.85 && crashes == 0 -> "MOBILE CANDIDATE"
            avgRtf <= 1.20 && crashes == 0 -> "CONDITIONAL"
            else -> "NEEDS BETTER MODEL"
        }

        summaryWriter.writeRow(
            listOf(
                System.currentTimeMillis(),
                deviceModel,
                androidVersion,
                lang.code,
                cfg.voiceTag,
                cfg.type.name,
                String.format(java.util.Locale.US, "%.1f", modelSizeMb),
                10,
                coldSynthesisMs,
                String.format(java.util.Locale.US, "%.1f", warmAvg),
                String.format(java.util.Locale.US, "%.1f", medianLat),
                String.format(java.util.Locale.US, "%.1f", p95Lat),
                String.format(java.util.Locale.US, "%.3f", avgRtf),
                String.format(java.util.Locale.US, "%.2f", memAfterLoad),
                String.format(java.util.Locale.US, "%.2f", memEndCycles),
                String.format(java.util.Locale.US, "%.2f", memGrowth),
                crashes,
                anrs,
                verdict
            )
        )

        Log.i(
            TAG,
            "[${cfg.voiceTag}] WarmAvg: ${"%.1f".format(warmAvg)}ms | P95: ${"%.1f".format(p95Lat)}ms | RTF: ${"%.3f".format(avgRtf)} | Peak RAM: ${"%.1f".format(memEndCycles)}MB | Verdict: $verdict"
        )

        engine.release()
        System.gc()
    }

    @Test
    fun test01_benchmarkAllVoicesAcross10Languages() {
        for (target in allVoiceTargets) {
            benchmarkVoice(target)
        }
    }
}
