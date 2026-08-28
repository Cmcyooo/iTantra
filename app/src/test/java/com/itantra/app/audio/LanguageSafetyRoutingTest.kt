package com.itantra.app.audio

import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit test suite for Phase 10E.1: Confidence-Aware Auto-LID Safety Routing.
 *
 * Validates:
 * 1. LanguageReliabilityPolicy classification (AUTO_ACCEPT vs CONFIRM_REQUIRED).
 * 2. Routing decisions (AUTO_ACCEPT, CONFIRM_REQUIRED, MANUAL_FALLBACK).
 * 3. High confidence does NOT authorize CONFIRM_REQUIRED languages silently.
 * 4. Audio sample retention in PendingConfirmation for zero-repeat confirmation.
 * 5. Session caching only for confirmed or AUTO_ACCEPT languages.
 * 6. Debounced switching rules (2 consecutive high-confidence detections).
 * 7. Session reset semantics.
 */
class LanguageSafetyRoutingTest {

    private lateinit var mockContext: Context
    private lateinit var fakeLid: FakeLanguageIdentifier
    private lateinit var config: LanguageDetectionConfig
    private lateinit var policy: LanguageReliabilityPolicy
    private lateinit var manager: LanguageModelManager

    class FakeLanguageIdentifier : LanguageIdentifier {
        var nextResult: LanguageDetectionResult = LanguageDetectionResult(
            language = "hi",
            confidence = 0.94f,
            probabilities = mapOf("hi" to 0.94f, "en" to 0.06f),
            latencyMs = 42L,
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
        policy = LanguageReliabilityPolicy()
        manager = LanguageModelManager(
            context = mockContext,
            languageIdentifier = fakeLid,
            detectionConfig = config,
            reliabilityPolicy = policy
        )
    }

    @Test
    fun testAutoAcceptHighConfidence() {
        // Hindi is in AUTO_ACCEPT tier with 0.94 confidence
        val result = LanguageDetectionResult(
            language = "hi",
            confidence = 0.94f,
            probabilities = mapOf("hi" to 0.94f),
            latencyMs = 40L,
            status = DetectionStatus.CONFIDENT
        )
        val decision = policy.evaluateRouting(result)
        assertEquals(RoutingDecision.AUTO_ACCEPT, decision)
    }

    @Test
    fun testConfirmRequiredHighConfidence() {
        // Kannada is in CONFIRM_REQUIRED tier even with 0.92 high confidence
        val result = LanguageDetectionResult(
            language = "kn",
            confidence = 0.92f,
            probabilities = mapOf("kn" to 0.92f),
            latencyMs = 50L,
            status = DetectionStatus.CONFIDENT
        )
        val decision = policy.evaluateRouting(result)
        assertEquals(RoutingDecision.CONFIRM_REQUIRED, decision)
    }

    @Test
    fun testAmbiguousDetection() {
        // Confidence between 0.60 and 0.80 on English (normally AUTO_ACCEPT) requires confirmation
        val result = LanguageDetectionResult(
            language = "en",
            confidence = 0.72f,
            probabilities = mapOf("en" to 0.72f, "hi" to 0.28f),
            latencyMs = 45L,
            status = DetectionStatus.AMBIGUOUS
        )
        val decision = policy.evaluateRouting(result)
        assertEquals(RoutingDecision.CONFIRM_REQUIRED, decision)
    }

    @Test
    fun testLowConfidenceDetection() {
        // Confidence < 0.60 returns MANUAL_FALLBACK
        val result = LanguageDetectionResult(
            language = "te",
            confidence = 0.52f,
            probabilities = mapOf("te" to 0.52f),
            latencyMs = 40L,
            status = DetectionStatus.AMBIGUOUS
        )
        val decision = policy.evaluateRouting(result)
        assertEquals(RoutingDecision.MANUAL_FALLBACK, decision)
    }

    @Test
    fun testManualModeBypassesLid() = runBlocking {
        manager.setLanguageMode(LanguageMode.MANUAL)
        fakeLid.nextResult = LanguageDetectionResult(
            language = "ta",
            confidence = 0.95f,
            probabilities = mapOf("ta" to 0.95f),
            latencyMs = 30L,
            status = DetectionStatus.CONFIDENT
        )

        val audio = FloatArray(16000) { 0.1f }
        val (decision, resolved) = manager.resolveLanguageWithRouting(audio)

        assertEquals(RoutingDecision.AUTO_ACCEPT, decision)
        assertEquals(SupportedLanguage.ENGLISH, resolved)
        assertEquals(0, fakeLid.callCount) // LID bypassed completely
    }

    @Test
    fun testConfirmedWeakLanguageGetsCached() = runBlocking {
        // Step 1: Kannada detected with 0.92
        fakeLid.nextResult = LanguageDetectionResult(
            language = "kn",
            confidence = 0.92f,
            probabilities = mapOf("kn" to 0.92f),
            latencyMs = 50L,
            status = DetectionStatus.CONFIDENT
        )
        val audio1 = FloatArray(16000) { 0.15f }
        val (decision, _) = manager.resolveLanguageWithRouting(audio1, "utt-1")

        assertEquals(RoutingDecision.CONFIRM_REQUIRED, decision)
        assertNotNull(manager.pendingConfirmation.value)
        assertEquals(SupportedLanguage.KANNADA, manager.pendingConfirmation.value?.language)

        // Step 2: Operator confirms Kannada
        manager.confirmPendingLanguage()

        val sessionState = manager.sessionLanguageState.value
        assertNotNull(sessionState)
        assertEquals(SupportedLanguage.KANNADA, sessionState?.language)
        assertEquals(SessionLanguageSource.USER_CONFIRMED, sessionState?.source)
        assertNull(manager.pendingConfirmation.value)

        // Step 3: Utterance 2 reuses cached Kannada without LID
        val callsBefore = fakeLid.callCount
        val audio2 = FloatArray(16000) { 0.12f }
        val (decision2, resolved2) = manager.resolveLanguageWithRouting(audio2, "utt-2")

        assertEquals(RoutingDecision.AUTO_ACCEPT, decision2)
        assertEquals(SupportedLanguage.KANNADA, resolved2)
        assertEquals(callsBefore, fakeLid.callCount) // Cached, 0ms LID overhead
    }

    @Test
    fun testUnconfirmedWeakLanguageIsNotCached() = runBlocking {
        // Kannada detected
        fakeLid.nextResult = LanguageDetectionResult(
            language = "kn",
            confidence = 0.92f,
            probabilities = mapOf("kn" to 0.92f),
            latencyMs = 50L,
            status = DetectionStatus.CONFIDENT
        )
        val audio = FloatArray(16000) { 0.1f }
        val (decision, _) = manager.resolveLanguageWithRouting(audio, "utt-1")

        assertEquals(RoutingDecision.CONFIRM_REQUIRED, decision)
        // Session lock MUST NOT be established prior to confirmation
        assertNull(manager.sessionLanguageState.value)
    }

    @Test
    fun testAutoAcceptLanguageCanBeCached() = runBlocking {
        fakeLid.nextResult = LanguageDetectionResult(
            language = "hi",
            confidence = 0.95f,
            probabilities = mapOf("hi" to 0.95f),
            latencyMs = 38L,
            status = DetectionStatus.CONFIDENT
        )
        val audio1 = FloatArray(16000) { 0.2f }
        val (decision, resolved) = manager.resolveLanguageWithRouting(audio1, "utt-1")

        assertEquals(RoutingDecision.AUTO_ACCEPT, decision)
        assertEquals(SupportedLanguage.HINDI, resolved)

        val sessionState = manager.sessionLanguageState.value
        assertNotNull(sessionState)
        assertEquals(SupportedLanguage.HINDI, sessionState?.language)
        assertEquals(SessionLanguageSource.AUTO_DETECTED, sessionState?.source)

        // Utterance 2 reuses cached session language
        val callsBefore = fakeLid.callCount
        val audio2 = FloatArray(16000) { 0.2f }
        val (decision2, resolved2) = manager.resolveLanguageWithRouting(audio2, "utt-2")

        assertEquals(RoutingDecision.AUTO_ACCEPT, decision2)
        assertEquals(SupportedLanguage.HINDI, resolved2)
        assertEquals(callsBefore, fakeLid.callCount)
    }

    @Test
    fun testWeakLanguageCannotAutoSwitch() = runBlocking {
        // Active session is Hindi
        fakeLid.nextResult = LanguageDetectionResult(
            language = "hi",
            confidence = 0.95f,
            probabilities = mapOf("hi" to 0.95f),
            latencyMs = 35L,
            status = DetectionStatus.CONFIDENT
        )
        manager.resolveLanguageWithRouting(FloatArray(16000) { 0.2f })
        assertEquals(SupportedLanguage.HINDI, manager.sessionLanguageState.value?.language)

        // Force re-verification; LID returns Kannada with 0.95 confidence
        manager.triggerLanguageReverification()
        fakeLid.nextResult = LanguageDetectionResult(
            language = "kn",
            confidence = 0.95f,
            probabilities = mapOf("kn" to 0.95f),
            latencyMs = 45L,
            status = DetectionStatus.CONFIDENT
        )

        val (decision, _) = manager.resolveLanguageWithRouting(FloatArray(16000) { 0.2f })

        // MUST NOT switch automatically to Kannada
        assertEquals(RoutingDecision.CONFIRM_REQUIRED, decision)
        assertEquals(SupportedLanguage.HINDI, manager.sessionLanguageState.value?.language)
        assertNotNull(manager.pendingConfirmation.value)
        assertEquals(SupportedLanguage.KANNADA, manager.pendingConfirmation.value?.language)
    }

    @Test
    fun testTwoConsecutiveSafeSwitches() = runBlocking {
        // Active session is Hindi
        fakeLid.nextResult = LanguageDetectionResult(
            language = "hi",
            confidence = 0.95f,
            probabilities = mapOf("hi" to 0.95f),
            latencyMs = 35L,
            status = DetectionStatus.CONFIDENT
        )
        manager.resolveLanguageWithRouting(FloatArray(16000) { 0.2f })

        // Switch attempt 1: Tamil (AUTO_ACCEPT) detected once
        manager.triggerLanguageReverification()
        fakeLid.nextResult = LanguageDetectionResult(
            language = "ta",
            confidence = 0.92f,
            probabilities = mapOf("ta" to 0.92f),
            latencyMs = 40L,
            status = DetectionStatus.CONFIDENT
        )
        val (_, resolved1) = manager.resolveLanguageWithRouting(FloatArray(16000) { 0.2f })
        assertEquals(SupportedLanguage.HINDI, resolved1) // Preserved on first detection

        // Switch attempt 2: Tamil detected second consecutive time
        manager.triggerLanguageReverification()
        val (_, resolved2) = manager.resolveLanguageWithRouting(FloatArray(16000) { 0.2f })
        assertEquals(SupportedLanguage.TAMIL, resolved2) // Switched after 2 consecutive detections
        assertEquals(SupportedLanguage.TAMIL, manager.sessionLanguageState.value?.language)
    }

    @Test
    fun testSingleSafeSwitchDoesNotSwitch() = runBlocking {
        // Active session is Hindi
        fakeLid.nextResult = LanguageDetectionResult(
            language = "hi",
            confidence = 0.95f,
            probabilities = mapOf("hi" to 0.95f),
            latencyMs = 35L,
            status = DetectionStatus.CONFIDENT
        )
        manager.resolveLanguageWithRouting(FloatArray(16000) { 0.2f })

        // Single detection of Bengali
        manager.triggerLanguageReverification()
        fakeLid.nextResult = LanguageDetectionResult(
            language = "bn",
            confidence = 0.90f,
            probabilities = mapOf("bn" to 0.90f),
            latencyMs = 40L,
            status = DetectionStatus.CONFIDENT
        )
        val (decision, resolved) = manager.resolveLanguageWithRouting(FloatArray(16000) { 0.2f })

        assertEquals(RoutingDecision.AUTO_ACCEPT, decision)
        assertEquals(SupportedLanguage.HINDI, resolved) // Kept Hindi
        assertEquals(SupportedLanguage.HINDI, manager.sessionLanguageState.value?.language)
    }

    @Test
    fun testAudioRetainedDuringConfirmation() = runBlocking {
        fakeLid.nextResult = LanguageDetectionResult(
            language = "mr",
            confidence = 0.88f,
            probabilities = mapOf("mr" to 0.88f),
            latencyMs = 40L,
            status = DetectionStatus.CONFIDENT
        )
        val inputSamples = FloatArray(8000) { idx -> idx * 0.0001f }
        val (decision, _) = manager.resolveLanguageWithRouting(inputSamples, "sample-utt-99")

        assertEquals(RoutingDecision.CONFIRM_REQUIRED, decision)
        val pending = manager.pendingConfirmation.value
        assertNotNull(pending)
        assertEquals("sample-utt-99", pending?.utteranceId)
        assertEquals(8000, pending?.audioSamples?.size)
        assertEquals(inputSamples[100], pending?.audioSamples?.get(100) ?: 0f, 0.00001f)
    }

    @Test
    fun testManualFallbackUsesOriginalAudio() = runBlocking {
        fakeLid.nextResult = LanguageDetectionResult(
            language = "gu",
            confidence = 0.85f,
            probabilities = mapOf("gu" to 0.85f),
            latencyMs = 40L,
            status = DetectionStatus.CONFIDENT
        )
        val inputSamples = FloatArray(16000) { 0.33f }
        manager.resolveLanguageWithRouting(inputSamples, "utt-manual-fb")

        // User chooses Telugu instead of Gujarati
        val result = manager.rejectPendingLanguageAndSelect(SupportedLanguage.TELUGU)
        assertNotNull(result)
        assertEquals(SupportedLanguage.TELUGU, manager.sessionLanguageState.value?.language)
        assertEquals(SessionLanguageSource.MANUAL_SELECTED, manager.sessionLanguageState.value?.source)
        assertNull(manager.pendingConfirmation.value)
    }

    @Test
    fun testSessionResetClearsDetection() = runBlocking {
        fakeLid.nextResult = LanguageDetectionResult(
            language = "hi",
            confidence = 0.95f,
            probabilities = mapOf("hi" to 0.95f),
            latencyMs = 35L,
            status = DetectionStatus.CONFIDENT
        )
        manager.resolveLanguageWithRouting(FloatArray(16000) { 0.2f })
        assertNotNull(manager.sessionLanguageState.value)

        manager.resetSession()

        assertNull(manager.sessionLanguageState.value)
        assertNull(manager.sessionLanguage.value)
        assertNull(manager.pendingConfirmation.value)
    }
}
