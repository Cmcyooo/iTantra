package com.itantra.app.audio

import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy

/**
 * Unit test suite for Phase 10E: Automatic Language Identification (Auto-LID).
 * Tests confidence thresholds, session caching, debouncing state machine,
 * and graceful fallback behavior without external mocking frameworks.
 */
class LanguageIdentifierTest {

    private lateinit var mockContext: Context
    private lateinit var fakeLid: FakeLanguageIdentifier
    private lateinit var config: LanguageDetectionConfig

    class FakeLanguageIdentifier : LanguageIdentifier {
        var nextResult: LanguageDetectionResult = LanguageDetectionResult(
            language = "hi",
            confidence = 0.90f,
            probabilities = mapOf("hi" to 0.90f, "mr" to 0.10f),
            latencyMs = 45L,
            status = DetectionStatus.CONFIDENT
        )
        var callCount = 0
        override var isReady: Boolean = true

        override suspend fun initialize(context: Context): Result<Unit> {
            isReady = true
            return Result.success(Unit)
        }

        override suspend fun identifyLanguage(samples: FloatArray): LanguageDetectionResult {
            callCount++
            return nextResult
        }

        override fun release() {
            isReady = false
        }
    }

    class TestContext : android.content.ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
    }

    @Before
    fun setUp() {
        mockContext = TestContext()
        fakeLid = FakeLanguageIdentifier()
        config = LanguageDetectionConfig(
            confidentThreshold = 0.80f,
            ambiguousThreshold = 0.60f,
            reverificationInterval = 5,
            consecutiveSwitchesRequired = 2
        )
    }

    @Test
    fun testConfidenceThresholds() {
        assertEquals(0.80f, config.confidentThreshold, 0.001f)
        assertEquals(0.60f, config.ambiguousThreshold, 0.001f)
        assertEquals(5, config.reverificationInterval)
        assertEquals(2, config.consecutiveSwitchesRequired)
    }

    @Test
    fun testDetectionStatusClassification() {
        val confident = LanguageDetectionResult("hi", 0.85f, mapOf("hi" to 0.85f), 50L, DetectionStatus.CONFIDENT)
        val ambiguous = LanguageDetectionResult("mr", 0.65f, mapOf("mr" to 0.65f, "hi" to 0.35f), 50L, DetectionStatus.AMBIGUOUS)
        val noSpeech = LanguageDetectionResult(null, 0.0f, emptyMap(), 5L, DetectionStatus.NO_SPEECH)
        val error = LanguageDetectionResult(null, 0.0f, emptyMap(), 10L, DetectionStatus.ERROR)

        assertEquals(DetectionStatus.CONFIDENT, confident.status)
        assertEquals(DetectionStatus.AMBIGUOUS, ambiguous.status)
        assertEquals(DetectionStatus.NO_SPEECH, noSpeech.status)
        assertEquals(DetectionStatus.ERROR, error.status)
    }

    @Test
    fun testManualModeOverridesAutoDetection() = runBlocking {
        val manager = LanguageModelManager(mockContext, fakeLid, config)
        manager.setLanguageMode(LanguageMode.MANUAL)

        val samples = FloatArray(16000) { 0.1f }
        fakeLid.nextResult = LanguageDetectionResult("te", 0.95f, mapOf("te" to 0.95f), 40L, DetectionStatus.CONFIDENT)

        val resolved = manager.resolveLanguage(samples)
        assertEquals(SupportedLanguage.ENGLISH, resolved) // Default manual language preserved
        assertEquals(0, fakeLid.callCount) // LID must NOT be invoked in MANUAL mode
    }

    @Test
    fun testAutoModeFirstUtteranceEstablishesSessionLanguage() = runBlocking {
        val manager = LanguageModelManager(mockContext, fakeLid, config)
        manager.setLanguageMode(LanguageMode.AUTO)

        val samples = FloatArray(16000) { 0.1f }
        fakeLid.nextResult = LanguageDetectionResult("hi", 0.92f, mapOf("hi" to 0.92f), 40L, DetectionStatus.CONFIDENT)

        val resolved = manager.resolveLanguage(samples)
        assertEquals(SupportedLanguage.HINDI, resolved)
        assertEquals(1, fakeLid.callCount)
        assertEquals(SupportedLanguage.HINDI, manager.sessionLanguage.value)
    }

    @Test
    fun testAutoModeCachesSessionLanguageOnSubsequentUtterances() = runBlocking {
        val manager = LanguageModelManager(mockContext, fakeLid, config)
        manager.setLanguageMode(LanguageMode.AUTO)

        val samples = FloatArray(16000) { 0.1f }
        fakeLid.nextResult = LanguageDetectionResult("ta", 0.90f, mapOf("ta" to 0.90f), 40L, DetectionStatus.CONFIDENT)

        // Utterance 1 -> runs LID
        val r1 = manager.resolveLanguage(samples)
        assertEquals(SupportedLanguage.TAMIL, r1)
        assertEquals(1, fakeLid.callCount)

        // Utterance 2, 3, 4 -> MUST reuse cached language without running LID
        val r2 = manager.resolveLanguage(samples)
        val r3 = manager.resolveLanguage(samples)
        val r4 = manager.resolveLanguage(samples)

        assertEquals(SupportedLanguage.TAMIL, r2)
        assertEquals(SupportedLanguage.TAMIL, r3)
        assertEquals(SupportedLanguage.TAMIL, r4)
        assertEquals(1, fakeLid.callCount) // Still only 1 LID invocation! Zero overhead on subsequent utterances
    }

    @Test
    fun testAutoModeReverificationInterval() = runBlocking {
        val manager = LanguageModelManager(mockContext, fakeLid, config)
        manager.setLanguageMode(LanguageMode.AUTO)

        val samples = FloatArray(16000) { 0.1f }
        fakeLid.nextResult = LanguageDetectionResult("ta", 0.91f, mapOf("ta" to 0.91f), 40L, DetectionStatus.CONFIDENT)

        // Utterances 1 to 4
        for (i in 1..4) {
            manager.resolveLanguage(samples)
        }
        assertEquals(1, fakeLid.callCount)

        // Utterance 5 -> Re-verification interval (5) due! Must invoke LID
        val r5 = manager.resolveLanguage(samples)
        assertEquals(SupportedLanguage.TAMIL, r5)
        assertEquals(2, fakeLid.callCount)
    }

    @Test
    fun testConsecutiveDetectionLanguageSwitchProtection() = runBlocking {
        val manager = LanguageModelManager(mockContext, fakeLid, config)
        manager.setLanguageMode(LanguageMode.AUTO)

        val samples = FloatArray(16000) { 0.1f }

        // Utterance 1: Establish Hindi
        fakeLid.nextResult = LanguageDetectionResult("hi", 0.90f, mapOf("hi" to 0.90f), 40L, DetectionStatus.CONFIDENT)
        val r1 = manager.resolveLanguage(samples)
        assertEquals(SupportedLanguage.HINDI, r1)

        // Force re-verification to test language switch protection
        manager.triggerLanguageReverification()

        // Detection 1 of different language (Telugu) with high confidence
        fakeLid.nextResult = LanguageDetectionResult("te", 0.88f, mapOf("te" to 0.88f), 40L, DetectionStatus.CONFIDENT)
        val r2 = manager.resolveLanguage(samples)
        // MUST NOT switch on a single detection! Must preserve Hindi
        assertEquals(SupportedLanguage.HINDI, r2)
        assertEquals(SupportedLanguage.HINDI, manager.sessionLanguage.value)

        // Force re-verification again
        manager.triggerLanguageReverification()

        // Detection 2 of Telugu with high confidence (consecutive!)
        val r3 = manager.resolveLanguage(samples)
        // Now it MUST switch to Telugu after 2 consecutive high-confidence detections
        assertEquals(SupportedLanguage.TELUGU, r3)
        assertEquals(SupportedLanguage.TELUGU, manager.sessionLanguage.value)
    }

    @Test
    fun testAmbiguousDetectionRetainsSessionLanguage() = runBlocking {
        val manager = LanguageModelManager(mockContext, fakeLid, config)
        manager.setLanguageMode(LanguageMode.AUTO)

        val samples = FloatArray(16000) { 0.1f }

        // Utterance 1: Establish Hindi (AUTO_ACCEPT)
        fakeLid.nextResult = LanguageDetectionResult("hi", 0.95f, mapOf("hi" to 0.95f), 40L, DetectionStatus.CONFIDENT)
        manager.resolveLanguage(samples)
        assertEquals(SupportedLanguage.HINDI, manager.sessionLanguage.value)

        // Utterance 2: Ambiguous detection (confidence 0.65)
        manager.triggerLanguageReverification()
        fakeLid.nextResult = LanguageDetectionResult("gu", 0.65f, mapOf("gu" to 0.65f, "hi" to 0.35f), 40L, DetectionStatus.AMBIGUOUS)

        val r2 = manager.resolveLanguage(samples)
        assertEquals(SupportedLanguage.HINDI, r2) // Retains Hindi
        assertEquals(SupportedLanguage.HINDI, manager.sessionLanguage.value)
    }

    @Test
    fun testLidFailureFallbackSafety() = runBlocking {
        val manager = LanguageModelManager(mockContext, fakeLid, config)
        manager.setLanguageMode(LanguageMode.AUTO)

        val samples = FloatArray(16000) { 0.1f }

        // Error detection
        fakeLid.nextResult = LanguageDetectionResult(null, 0.0f, emptyMap(), 10L, DetectionStatus.ERROR)

        val resolved = manager.resolveLanguage(samples)
        assertNotNull(resolved)
        assertEquals(SupportedLanguage.ENGLISH, resolved) // Graceful fallback to default English baseline
    }

    @Test
    fun testNoSpeechDetectionSafety() = runBlocking {
        val manager = LanguageModelManager(mockContext, fakeLid, config)
        manager.setLanguageMode(LanguageMode.AUTO)

        val samples = FloatArray(16000) { 0.0001f } // Silence

        fakeLid.nextResult = LanguageDetectionResult(null, 0.0f, emptyMap(), 5L, DetectionStatus.NO_SPEECH)

        val resolved = manager.resolveLanguage(samples)
        assertNotNull(resolved)
        assertEquals(SupportedLanguage.ENGLISH, resolved)
    }
}
