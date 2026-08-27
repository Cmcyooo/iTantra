package com.itantra.app

import android.content.Context
import android.os.Debug
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.itantra.app.audio.LanguageModelManager
import com.itantra.app.audio.ModelLifecycleState
import com.itantra.app.audio.SupportedLanguage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureTimeMillis

@RunWith(AndroidJUnit4::class)
class OdiaMarathiModelUpgradeTest {

    companion object {
        private const val TAG = "ModelUpgradeTest"
    }

    private lateinit var context: Context
    private lateinit var languageModelManager: LanguageModelManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        languageModelManager = LanguageModelManager.getInstance(context)
    }

    private fun getMemoryPssMb(): Double {
        val memoryInfo = Debug.MemoryInfo()
        Debug.getMemoryInfo(memoryInfo)
        return memoryInfo.totalPss / 1024.0
    }

    @Test
    fun testMarathiUpgradedModel(): Unit = runBlocking {
        Log.i(TAG, "==================================================")
        Log.i(TAG, "TEST: MARATHI UPGRADED MODEL ON-DEVICE BENCHMARK")
        Log.i(TAG, "==================================================")

        val memBefore = getMemoryPssMb()
        Log.i(TAG, "RAM before Marathi load: ${"%.2f".format(memBefore)} MB")

        val coldLoadMs = measureTimeMillis {
            val res = languageModelManager.setLanguage(SupportedLanguage.MARATHI)
            assertTrue("Marathi model load should succeed", res.isSuccess)
        }
        assertEquals("Active language must be MARATHI", SupportedLanguage.MARATHI, languageModelManager.currentLanguage.value)
        assertEquals("Lifecycle state must be READY", ModelLifecycleState.READY, languageModelManager.lifecycleState.value)

        val memAfterLoad = getMemoryPssMb()
        Log.i(TAG, "Marathi Cold Load Time: ${coldLoadMs} ms | RAM: ${"%.2f".format(memAfterLoad)} MB (Delta: +${"%.2f".format(memAfterLoad - memBefore)} MB)")

        // 3.0s synthetic tactical 16kHz audio
        val audioSamples = FloatArray(48000) { i ->
            (0.15f * kotlin.math.sin(2.0 * Math.PI * 300.0 * i / 16000.0)).toFloat()
        }

        // Warmup inference
        val warmupRes = languageModelManager.transcribe(audioSamples)
        assertNotNull("Marathi transcription result should not be null", warmupRes)

        // 10-run performance & memory delta test
        val latencies = mutableListOf<Long>()
        val startMem10 = getMemoryPssMb()
        for (i in 1..10) {
            val res = languageModelManager.transcribe(audioSamples)
            assertNotNull(res)
            latencies.add(res!!.processingTimeMs)
        }
        val endMem10 = getMemoryPssMb()
        val avgLat = latencies.average()
        val rtf = (avgLat / 1000.0) / 3.0
        val memDelta = endMem10 - startMem10

        Log.i(TAG, "Marathi 10-Run Results -> Avg Latency: ${"%.1f".format(avgLat)} ms | RTF: ${"%.3f".format(rtf)} | 10-run RAM Delta: ${"%.2f".format(memDelta)} MB | Peak PSS: ${"%.2f".format(endMem10)} MB")
        assertTrue("RTF must be real-time on device (< 0.40)", rtf < 0.40)
        assertTrue("Memory leak must be < 20 MB over 10 runs", kotlin.math.abs(memDelta) < 20.0)
    }

    @Test
    fun testOdiaUpgradedModel(): Unit = runBlocking {
        Log.i(TAG, "==================================================")
        Log.i(TAG, "TEST: ODIA UPGRADED MODEL ON-DEVICE BENCHMARK")
        Log.i(TAG, "==================================================")

        val memBefore = getMemoryPssMb()
        Log.i(TAG, "RAM before Odia load: ${"%.2f".format(memBefore)} MB")

        val coldLoadMs = measureTimeMillis {
            val res = languageModelManager.setLanguage(SupportedLanguage.ODIA)
            assertTrue("Odia model load should succeed", res.isSuccess)
        }
        assertEquals("Active language must be ODIA", SupportedLanguage.ODIA, languageModelManager.currentLanguage.value)
        assertEquals("Lifecycle state must be READY", ModelLifecycleState.READY, languageModelManager.lifecycleState.value)

        val memAfterLoad = getMemoryPssMb()
        Log.i(TAG, "Odia Cold Load Time: ${coldLoadMs} ms | RAM: ${"%.2f".format(memAfterLoad)} MB (Delta: +${"%.2f".format(memAfterLoad - memBefore)} MB)")

        val audioSamples = FloatArray(48000) { i ->
            (0.15f * kotlin.math.sin(2.0 * Math.PI * 300.0 * i / 16000.0)).toFloat()
        }

        // Warmup inference
        val warmupRes = languageModelManager.transcribe(audioSamples)
        assertNotNull("Odia transcription result should not be null", warmupRes)

        // 10-run performance & memory delta test
        val latencies = mutableListOf<Long>()
        val startMem10 = getMemoryPssMb()
        for (i in 1..10) {
            val res = languageModelManager.transcribe(audioSamples)
            assertNotNull(res)
            latencies.add(res!!.processingTimeMs)
        }
        val endMem10 = getMemoryPssMb()
        val avgLat = latencies.average()
        val rtf = (avgLat / 1000.0) / 3.0
        val memDelta = endMem10 - startMem10

        Log.i(TAG, "Odia 10-Run Results -> Avg Latency: ${"%.1f".format(avgLat)} ms | RTF: ${"%.3f".format(rtf)} | 10-run RAM Delta: ${"%.2f".format(memDelta)} MB | Peak PSS: ${"%.2f".format(endMem10)} MB")
        assertTrue("RTF must be real-time on device (< 0.40)", rtf < 0.40)
        assertTrue("Memory leak must be < 20 MB over 10 runs", kotlin.math.abs(memDelta) < 20.0)
    }

    @Test
    fun testSequentialSwitchingStability(): Unit = runBlocking {
        Log.i(TAG, "==================================================")
        Log.i(TAG, "TEST: SEQUENTIAL LANGUAGE SWITCHING STABILITY")
        Log.i(TAG, "==================================================")

        val initialPss = getMemoryPssMb()
        val languages = listOf(
            SupportedLanguage.MARATHI,
            SupportedLanguage.ODIA,
            SupportedLanguage.HINDI,
            SupportedLanguage.ENGLISH
        )

        for (lang in languages) {
            val switchRes = languageModelManager.setLanguage(lang)
            assertTrue("Switching to ${lang.displayName} must succeed", switchRes.isSuccess)
            assertEquals(lang, languageModelManager.currentLanguage.value)
            val pss = getMemoryPssMb()
            Log.i(TAG, "Switched to ${lang.displayName} -> RAM: ${"%.2f".format(pss)} MB (Delta from initial: ${"%.2f".format(pss - initialPss)} MB)")
            assertTrue("Per-model resident delta must stay under 250 MB", kotlin.math.abs(pss - initialPss) < 250.0)
        }

        // Return to English
        val finalRes = languageModelManager.setLanguage(SupportedLanguage.ENGLISH)
        assertTrue(finalRes.isSuccess)
        val finalPss = getMemoryPssMb()
        Log.i(TAG, "Returned to English -> RAM: ${"%.2f".format(finalPss)} MB (Overall Delta: ${"%.2f".format(finalPss - initialPss)} MB)")
    }
}
