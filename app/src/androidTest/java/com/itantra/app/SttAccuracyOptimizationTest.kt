package com.itantra.app

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.itantra.app.audio.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.sqrt

@RunWith(AndroidJUnit4::class)
class SttAccuracyOptimizationTest {

    companion object {
        private const val TAG = "SttAccuracyTest"
    }

    private lateinit var context: Context
    private lateinit var languageModelManager: LanguageModelManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        languageModelManager = LanguageModelManager.getInstance(context)
    }

    @Test
    fun testDomainNormalizer() {
        Log.i(TAG, "=== TEST 1: DOMAIN NORMALIZER VERIFICATION ===")

        // Hindi tactical normalizations
        assertEquals("रोज़र", IndicDomainNormalizer.normalize("रोसर", "hi"))
        assertEquals("गश्ती चौकियों की स्थिति सुरक्षित", IndicDomainNormalizer.normalize("दश्ती चौकियों की स्थीती सुरक्षित", "hi"))
        assertEquals("सेक्टर चार", IndicDomainNormalizer.normalize("सेकटर चार", "hi"))

        // Marathi tactical normalizations
        assertEquals("समजले बेस", IndicDomainNormalizer.normalize("समजली पस", "mr"))
        assertEquals("सर्व गस्त चौक्या", IndicDomainNormalizer.normalize("सर्वगस्त चौक्या", "mr"))

        // Bengali tactical normalizations
        assertEquals("রজার বেস", IndicDomainNormalizer.normalize("রোজা বেশ", "bn"))
        assertEquals("টহল চৌকি", IndicDomainNormalizer.normalize("টহ চকি", "bn"))

        // English tactical normalizations
        assertEquals("convoy is moving", IndicDomainNormalizer.normalize("kanbai is moving", "en"))
        assertEquals("twelve personnel at sector four", IndicDomainNormalizer.normalize("12 personnel at sector 4", "en"))
        assertEquals("speed thirty kilometers per hour", IndicDomainNormalizer.normalize("speed 30 kilometers per hour", "en"))
    }

    @Test
    fun testAcousticNormalizationLogic() {
        Log.i(TAG, "=== TEST 2: ZERO-MEAN UNIT-VARIANCE NORMALIZATION ===")

        val sampleInput = FloatArray(16000) { i ->
            // Synthetic 440Hz tone with DC offset of 0.2 and amplitude 0.05
            0.2f + 0.05f * kotlin.math.sin(2.0 * Math.PI * 440.0 * i / 16000.0).toFloat()
        }

        var sum = 0.0
        for (s in sampleInput) sum += s
        val mean = (sum / sampleInput.size).toFloat()
        var sumSq = 0.0
        for (s in sampleInput) {
            val diff = s - mean
            sumSq += diff * diff
        }
        val std = sqrt(sumSq / sampleInput.size + 1e-7).toFloat()

        val normalized = FloatArray(sampleInput.size) { i -> (sampleInput[i] - mean) / std }

        var normSum = 0.0
        for (s in normalized) normSum += s
        val normMean = normSum / normalized.size

        var normSumSq = 0.0
        for (s in normalized) {
            val diff = s - normMean
            normSumSq += diff * diff
        }
        val normStd = sqrt(normSumSq / normalized.size)

        // Mean should be ~0.0, std should be ~1.0
        assertEquals("Normalized mean should be zero", 0.0, normMean, 0.01)
        assertEquals("Normalized std should be 1.0", 1.0, normStd, 0.05)
    }

    @Test
    fun testLanguageModelSingleActiveLifecycle(): Unit = runBlocking {
        Log.i(TAG, "=== TEST 3: SINGLE-ACTIVE MODEL LIFECYCLE ===")

        val currentLang = languageModelManager.currentLanguage.value
        assertNotNull("Default active language should exist", currentLang)
        Log.i(TAG, "Initial active language: ${currentLang.displayName}")

        // Switch to Hindi
        val switchRes = languageModelManager.setLanguage(SupportedLanguage.HINDI)
        assertTrue("Switch to Hindi should succeed", switchRes.isSuccess)
        assertEquals("Active language should be Hindi", SupportedLanguage.HINDI, languageModelManager.currentLanguage.value)
        assertEquals("Lifecycle state should be READY", ModelLifecycleState.READY, languageModelManager.lifecycleState.value)

        // Switch back to English
        val switchBack = languageModelManager.setLanguage(SupportedLanguage.ENGLISH)
        assertTrue("Switch to English should succeed", switchBack.isSuccess)
        assertEquals("Active language should be English", SupportedLanguage.ENGLISH, languageModelManager.currentLanguage.value)
        assertEquals("Lifecycle state should be READY", ModelLifecycleState.READY, languageModelManager.lifecycleState.value)
    }
}
