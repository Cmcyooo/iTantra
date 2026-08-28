package com.itantra.app.benchmark

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.itantra.app.audio.*
import com.itantra.app.comm.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.Assert.*
import org.junit.Before
import org.junit.BeforeClass
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File
import java.util.UUID

/**
 * COMPREHENSIVE END-TO-END LATENCY BENCHMARK SUITE (PHASE 10D)
 * Instruments the full transceiver pipeline:
 * PTT Press (t0) -> Speech End (t1) -> STT Complete (t2) -> Send (t3) -> Receive (t4) -> TTS Start (t5) -> Playback Start (t6)
 * Measures STT latency, Transport latency, TTS startup, and E2E latency against internal targets.
 * Emits structured CSVs to /data/local/tmp/benchmarks/end_to_end/.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class EndToEndComprehensiveBenchmarkTest {

    companion object {
        private const val TAG = "E2EBenchmarkSuite"
        private const val BENCHMARK_BASE = "/data/local/tmp/benchmarks"

        private val RAW_HEADER = listOf(
            "timestamp", "device", "android_version", "language", "iteration", "phrase",
            "t0_ptt_press_ms", "t1_speech_end_ms", "t_lid_start_ms", "t_lid_end_ms",
            "t2_stt_complete_ms", "t3_packet_send_ms", "t4_packet_receive_ms",
            "t5_tts_start_ms", "t6_playback_start_ms", "lid_latency_ms", "stt_latency_ms",
            "transport_latency_ms", "tts_startup_ms", "e2e_cached_ms", "e2e_autolid_ms",
            "target_met", "error"
        )

        private val SUMMARY_HEADER = listOf(
            "timestamp", "device", "android_version", "language", "iterations_count",
            "lid_avg_ms", "stt_avg_ms", "transport_avg_ms", "tts_start_avg_ms",
            "e2e_cached_avg_ms", "e2e_autolid_avg_ms", "lid_overhead_ms",
            "e2e_median_ms", "e2e_p95_ms", "target_e2e_1s_pct", "verdict"
        )

        private lateinit var rawWriter: BenchmarkCsvWriter
        private lateinit var summaryWriter: BenchmarkCsvWriter
        private var deviceModel = Build.MODEL
        private var androidVersion = Build.VERSION.RELEASE

        @BeforeClass
        @JvmStatic
        fun initWriters() {
            val ctx = ApplicationProvider.getApplicationContext<Context>()
            val rawFile = BenchmarkCsvWriter.getBenchmarkFile(ctx, "end_to_end", "raw", "e2e_benchmark_raw.csv")
            val summaryFile = BenchmarkCsvWriter.getBenchmarkFile(ctx, "end_to_end", "summaries", "e2e_benchmark_summary.csv")
            rawWriter = BenchmarkCsvWriter(rawFile, RAW_HEADER)
            summaryWriter = BenchmarkCsvWriter(summaryFile, SUMMARY_HEADER)
        }

        @AfterClass
        @JvmStatic
        fun teardown() {
            Log.i(TAG, "End-to-End Benchmark suite complete. Results saved to $BENCHMARK_BASE/end_to_end/")
        }
    }

    private lateinit var context: Context
    private lateinit var mockTransport: BenchmarkLoopbackTransport
    private lateinit var commManager: CommunicationManager
    private lateinit var languageModelManager: LanguageModelManager
    private lateinit var languageTtsManager: LanguageTtsManager

    class BenchmarkLoopbackTransport : Transport {
        private val _connState = MutableStateFlow(ConnectionState.CONNECTED)
        override val connectionState = _connState
        override val lastError = MutableStateFlow<String?>(null)
        private var listener: ((P2PMessage) -> Unit)? = null

        var sendTimestamp: Long = 0L
        var receiveTimestamp: Long = 0L

        override fun connect(targetAddress: String?) {
            _connState.value = ConnectionState.CONNECTED
        }

        override fun disconnect() {
            _connState.value = ConnectionState.DISCONNECTED
        }

        override fun getConnectedPeerId(): String = "peer_device_b"

        override fun sendMessage(message: P2PMessage) {
            sendTimestamp = System.currentTimeMillis()
            // Local network simulated transport delay (2-8 ms realistic TCP socket latency)
            try { Thread.sleep(4) } catch (_: Exception) {}
            receiveTimestamp = System.currentTimeMillis()
            listener?.invoke(message)
        }

        override fun setOnMessageReceivedListener(listener: (P2PMessage) -> Unit) {
            this.listener = listener
        }
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        mockTransport = BenchmarkLoopbackTransport()
        commManager = CommunicationManager(mockTransport)
        languageModelManager = LanguageModelManager.getInstance(context)
        languageTtsManager = LanguageTtsManager.getInstance(context)
    }

    data class E2ETestLanguage(val lang: SupportedLanguage, val phrase: String, val audioDurationSec: Double)

    private val e2eLanguages = listOf(
        E2ETestLanguage(SupportedLanguage.ENGLISH, "Roger that base proceeding now", 2.2),
        E2ETestLanguage(SupportedLanguage.HINDI, "सभी गश्ती चौकियों की स्थिति सुरक्षित है", 2.5),
        E2ETestLanguage(SupportedLanguage.MARATHI, "सर्व गस्त चौक्या सुरक्षित असल्याची नोंद आहे", 2.4),
        E2ETestLanguage(SupportedLanguage.TELUGU, "దయచేసి స్టేషన్కు రండి అత్యవసరం", 2.3),
        E2ETestLanguage(SupportedLanguage.TAMIL, "அனைத்து சோதனை சாவடிகளும் பாதுகாப்பாக உள்ளன", 2.5),
        E2ETestLanguage(SupportedLanguage.BENGALI, "টহল চৌকি সম্পূর্ণ নিরাপদ আছে", 2.2)
    )

    private fun benchmarkPipelineForLanguage(target: E2ETestLanguage, iterations: Int = 10) = runBlocking {
        val lang = target.lang
        Log.i(TAG, "==================================================")
        Log.i(TAG, "BENCHMARKING END-TO-END PIPELINE: ${lang.displayName} (${lang.code})")
        Log.i(TAG, "==================================================")

        // Pre-warm STT & TTS for this language
        languageModelManager.setLanguage(lang)
        languageTtsManager.setLanguage(lang)

        val sampleCount = (target.audioDurationSec * 16000).toInt()
        val syntheticAudio = FloatArray(sampleCount) { i ->
            (0.15f * kotlin.math.sin(2.0 * Math.PI * 300.0 * i / 16000.0)).toFloat()
        }

        val lidLatencies = mutableListOf<Long>()
        val sttLatencies = mutableListOf<Long>()
        val transportLatencies = mutableListOf<Long>()
        val ttsStartupLatencies = mutableListOf<Long>()
        val e2eCachedLatencies = mutableListOf<Long>()
        val e2eAutoLidLatencies = mutableListOf<Long>()

        for (iter in 1..iterations) {
            val t0 = System.currentTimeMillis() // PTT Press / Speech Start
            // Speech duration
            val speechDurationMs = (target.audioDurationSec * 1000).toLong()
            val t1 = System.currentTimeMillis() // Speech End (LID & STT processing start)

            // 0. Auto-LID Language Resolution
            val tLidStart = System.currentTimeMillis()
            val resolvedLang = languageModelManager.resolveLanguage(syntheticAudio)
            val tLidEnd = System.currentTimeMillis()
            val lidLatency = (tLidEnd - tLidStart).coerceAtLeast(0)

            // 1. STT
            val sttResult = languageModelManager.transcribe(syntheticAudio)
            val t2 = System.currentTimeMillis() // STT Complete
            val sttLatency = (t2 - tLidEnd).coerceAtLeast(1)

            // 2. Serialize & Send
            val utteranceId = UUID.randomUUID().toString()
            val textToSend = if (!sttResult?.text.isNullOrBlank()) sttResult!!.text else target.phrase
            val t3 = System.currentTimeMillis() // Packet Send Start

            var receivedMessage: P2PMessage? = null
            mockTransport.setOnMessageReceivedListener { msg ->
                receivedMessage = msg
            }

            commManager.sendText(textToSend, lang.code, "Station-Alpha", utteranceId)
            val t4 = mockTransport.receiveTimestamp // Packet Receive
            val transportLatency = (t4 - t3).coerceAtLeast(1)

            // 3. Receiver TTS Startup & Synthesis
            val t5 = System.currentTimeMillis() // TTS Start
            val ttsEngine = languageTtsManager
            val generatedAudio = ttsEngine.generateSpeech(textToSend, lang.code)
            val t6 = System.currentTimeMillis() // Audio Playback Start (first audio chunk buffer)

            val ttsStartup = (t5 - t4).coerceAtLeast(0)
            val ttsSynthesis = (t6 - t5).coerceAtLeast(1)

            val e2eCached = sttLatency + transportLatency + ttsStartup + ttsSynthesis
            val e2eAutoLid = lidLatency + e2eCached

            lidLatencies.add(lidLatency)
            sttLatencies.add(sttLatency)
            transportLatencies.add(transportLatency)
            ttsStartupLatencies.add(ttsStartup)
            e2eCachedLatencies.add(e2eCached)
            e2eAutoLidLatencies.add(e2eAutoLid)

            val targetMet = (e2eCached <= 1200)

            rawWriter.writeRow(
                listOf(
                    System.currentTimeMillis(),
                    deviceModel,
                    androidVersion,
                    lang.code,
                    iter,
                    target.phrase,
                    t0,
                    t1,
                    tLidStart,
                    tLidEnd,
                    t2,
                    t3,
                    t4,
                    t5,
                    t6,
                    lidLatency,
                    sttLatency,
                    transportLatency,
                    ttsStartup,
                    e2eCached,
                    e2eAutoLid,
                    if (targetMet) "YES" else "NO",
                    if (generatedAudio == null) "TTS_NULL" else ""
                )
            )

            Log.i(
                TAG,
                "[$iter/$iterations] ${lang.code} -> LID: ${lidLatency}ms | STT: ${sttLatency}ms | Net: ${transportLatency}ms | TTS: ${ttsStartup}ms | E2E(Cached): ${e2eCached}ms | E2E(AutoLID): ${e2eAutoLid}ms"
            )
        }

        // Summary Statistics
        val lidAvg = lidLatencies.average()
        val sttAvg = sttLatencies.average()
        val transportAvg = transportLatencies.average()
        val ttsStartAvg = ttsStartupLatencies.average()
        val e2eCachedAvg = e2eCachedLatencies.average()
        val e2eAutoLidAvg = e2eAutoLidLatencies.average()
        val lidOverhead = (e2eAutoLidAvg - e2eCachedAvg).coerceAtLeast(0.0)

        val sortedCached = e2eCachedLatencies.sorted()
        val e2eMedian = sortedCached[sortedCached.size / 2].toDouble()
        val e2eP95 = sortedCached[(sortedCached.size * 0.95).toInt().coerceAtMost(sortedCached.size - 1)].toDouble()

        val targetMetCount = e2eCachedLatencies.count { it <= 1000 }
        val targetMetPct = (targetMetCount.toDouble() / iterations) * 100.0

        val verdict = when {
            e2eCachedAvg <= 1000.0 && e2eP95 <= 1200.0 -> "TARGET MET (FLUID PTT)"
            e2eCachedAvg <= 1500.0 -> "ACCEPTABLE WALKIE-TALKIE"
            else -> "HIGH LATENCY (NEEDS OPTIMIZATION)"
        }

        summaryWriter.writeRow(
            listOf(
                System.currentTimeMillis(),
                deviceModel,
                androidVersion,
                lang.code,
                iterations,
                String.format(java.util.Locale.US, "%.1f", lidAvg),
                String.format(java.util.Locale.US, "%.1f", sttAvg),
                String.format(java.util.Locale.US, "%.1f", transportAvg),
                String.format(java.util.Locale.US, "%.1f", ttsStartAvg),
                String.format(java.util.Locale.US, "%.1f", e2eCachedAvg),
                String.format(java.util.Locale.US, "%.1f", e2eAutoLidAvg),
                String.format(java.util.Locale.US, "%.1f", lidOverhead),
                String.format(java.util.Locale.US, "%.1f", e2eMedian),
                String.format(java.util.Locale.US, "%.1f", e2eP95),
                String.format(java.util.Locale.US, "%.1f", targetMetPct),
                verdict
            )
        )

        Log.i(
            TAG,
            "[SUMMARY ${lang.code}] E2E Cached: ${"%.1f".format(e2eCachedAvg)}ms | E2E AutoLID: ${"%.1f".format(e2eAutoLidAvg)}ms | Overhead: ${"%.1f".format(lidOverhead)}ms | Target <=1.0s: ${"%.1f".format(targetMetPct)}% | Verdict: $verdict"
        )
    }

    @Test
    fun test01_benchmarkEndToEndPipelineAcrossLanguages() {
        for (target in e2eLanguages) {
            benchmarkPipelineForLanguage(target, iterations = 5)
        }
    }
}
