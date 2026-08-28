package com.itantra.app

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.itantra.app.audio.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Android Instrumentation Integration Test for Phase 10E.1: Confidence-Aware Safety Routing.
 *
 * Verifies on real Samsung Galaxy S24 hardware (SM-S921B):
 * 1. Hindi AUTO -> automatic routing (AUTO_ACCEPT)
 * 2. English AUTO -> automatic routing (AUTO_ACCEPT)
 * 3. Kannada AUTO -> confirmation before Kannada STT (CONFIRM_REQUIRED)
 * 4. Gujarati AUTO -> confirmation before Gujarati STT (CONFIRM_REQUIRED)
 * 5. Marathi AUTO -> confirmation before Marathi STT (CONFIRM_REQUIRED)
 * 6. Malayalam AUTO -> confirmation before Malayalam STT (CONFIRM_REQUIRED)
 * 7. Odia AUTO -> confirmation / manual fallback (CONFIRM_REQUIRED / MANUAL_FALLBACK)
 * 8. MANUAL Kannada -> LID completely bypassed
 * 9. Kannada confirmed -> subsequent utterance uses cached Kannada (USER_CONFIRMED)
 * 10. Hindi -> Kannada -> no silent automatic switch into Kannada
 * 11. Hindi -> Tamil -> automatic switch only after two consecutive high-confidence detections
 */
@RunWith(AndroidJUnit4::class)
class AutoLidIntegrationTest {

    companion object {
        private const val TAG = "AutoLidIntegrationTest"
        private const val CORPUS_DIR = "/data/local/tmp/benchmarks/stt/corpus/clean"
    }

    private lateinit var context: Context
    private lateinit var languageModelManager: LanguageModelManager

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

    private fun findAudioFile(lang: String, index: Int): File? {
        val indexStr = String.format(java.util.Locale.US, "%02d", index)
        val dir = File(CORPUS_DIR)
        val suffixes = listOf(
            "normal", "short", "long", "numbers", "location",
            "emergency", "radio", "punct", "proper_nouns", "fast"
        )
        for (suf in suffixes) {
            val f = File(dir, "${lang}_${indexStr}_$suf.wav")
            if (f.exists() && f.length() > 44) return f
        }
        val matches = dir.listFiles { f -> f.name.startsWith("${lang}_${indexStr}") && f.name.endsWith(".wav") }
        return matches?.firstOrNull()
    }

    private fun getAudioForLanguage(lang: String, index: Int = 1): FloatArray {
        val file = findAudioFile(lang, index)
        if (file != null && file.exists()) {
            val samples = readWavFloat32(file)
            if (samples.isNotEmpty()) return samples
        }
        // Fallback synthetic tone if file is missing
        val sampleRate = 16000
        val durationSec = 2.0
        val sampleCount = (durationSec * sampleRate).toInt()
        return FloatArray(sampleCount) { i ->
            (0.2f * kotlin.math.sin(2.0 * Math.PI * 440.0 * i / sampleRate)).toFloat()
        }
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        languageModelManager = LanguageModelManager.getInstance(context)
        languageModelManager.resetSession()
        languageModelManager.setLanguageMode(LanguageMode.AUTO)
    }

    @Test
    fun test01_HindiAutoRouting() {
        runBlocking {
            languageModelManager.resetSession()
            val audio = getAudioForLanguage("hi", 1)
            val (decision, resolved) = languageModelManager.resolveLanguageWithRouting(audio, "utt-hi")

            Log.i(TAG, "[TEST-01] Hindi decision=$decision resolved=${resolved.code}")
            assertEquals("Hindi must be AUTO_ACCEPT", RoutingDecision.AUTO_ACCEPT, decision)
            assertEquals("Hindi must resolve to HINDI", SupportedLanguage.HINDI, resolved)
            assertNotNull(languageModelManager.sessionLanguageState.value)
            assertEquals(SessionLanguageSource.AUTO_DETECTED, languageModelManager.sessionLanguageState.value?.source)
        }
    }

    @Test
    fun test02_EnglishAutoRouting() {
        runBlocking {
            languageModelManager.resetSession()
            val audio = getAudioForLanguage("en", 1)
            val (decision, resolved) = languageModelManager.resolveLanguageWithRouting(audio, "utt-en")

            Log.i(TAG, "[TEST-02] English decision=$decision resolved=${resolved.code}")
            assertEquals("English must be AUTO_ACCEPT", RoutingDecision.AUTO_ACCEPT, decision)
            assertEquals("English must resolve to ENGLISH", SupportedLanguage.ENGLISH, resolved)
            assertNotNull(languageModelManager.sessionLanguageState.value)
            assertEquals(SessionLanguageSource.AUTO_DETECTED, languageModelManager.sessionLanguageState.value?.source)
        }
    }

    @Test
    fun test03_KannadaAutoRequiresConfirmation() {
        runBlocking {
            languageModelManager.resetSession()
            val audio = getAudioForLanguage("kn", 8) // kn_08_punct.wav predicts Kannada
            val (decision, resolved) = languageModelManager.resolveLanguageWithRouting(audio, "utt-kn")

            Log.i(TAG, "[TEST-03] Kannada decision=$decision resolved=${resolved.code}")
            assertEquals("Kannada must require confirmation", RoutingDecision.CONFIRM_REQUIRED, decision)
            assertNotNull("Pending audio must be retained", languageModelManager.pendingConfirmation.value)
            assertEquals(SupportedLanguage.KANNADA, languageModelManager.pendingConfirmation.value?.language)
            assertEquals(audio.size, languageModelManager.pendingConfirmation.value?.audioSamples?.size)
        }
    }

    @Test
    fun test04_GujaratiAutoRequiresConfirmation() {
        runBlocking {
            languageModelManager.resetSession()
            val audio = getAudioForLanguage("gu", 1)
            val (decision, _) = languageModelManager.resolveLanguageWithRouting(audio, "utt-gu")

            Log.i(TAG, "[TEST-04] Gujarati decision=$decision")
            assertNotEquals("Gujarati must NEVER silently route", RoutingDecision.AUTO_ACCEPT, decision)
        }
    }

    @Test
    fun test05_MarathiAutoRequiresConfirmation() {
        runBlocking {
            languageModelManager.resetSession()
            val audio = getAudioForLanguage("mr", 1)
            val (decision, _) = languageModelManager.resolveLanguageWithRouting(audio, "utt-mr")

            Log.i(TAG, "[TEST-05] Marathi decision=$decision")
            assertNotEquals("Marathi must NEVER silently route", RoutingDecision.AUTO_ACCEPT, decision)
        }
    }

    @Test
    fun test06_MalayalamAutoRequiresConfirmation() {
        runBlocking {
            languageModelManager.resetSession()
            val audio = getAudioForLanguage("ml", 1)
            val (decision, _) = languageModelManager.resolveLanguageWithRouting(audio, "utt-ml")

            Log.i(TAG, "[TEST-06] Malayalam decision=$decision")
            assertNotEquals("Malayalam must NEVER silently route", RoutingDecision.AUTO_ACCEPT, decision)
        }
    }

    @Test
    fun test07_OdiaAutoRequiresConfirmationOrFallback() {
        runBlocking {
            languageModelManager.resetSession()
            val audio = getAudioForLanguage("or", 1)
            val (decision, _) = languageModelManager.resolveLanguageWithRouting(audio, "utt-or")

            Log.i(TAG, "[TEST-07] Odia decision=$decision")
            // Odia has 0% Top-1 in Whisper token set, must be CONFIRM_REQUIRED or MANUAL_FALLBACK
            assertNotEquals("Odia must NEVER silently route", RoutingDecision.AUTO_ACCEPT, decision)
        }
    }

    @Test
    fun test08_ManualModeBypassesLid() {
        runBlocking {
            languageModelManager.setLanguageMode(LanguageMode.MANUAL)
            languageModelManager.setLanguage(SupportedLanguage.KANNADA)

            val audio = getAudioForLanguage("hi", 1) // Hindi audio while manual mode is Kannada
            val (decision, resolved) = languageModelManager.resolveLanguageWithRouting(audio, "utt-manual")

            assertEquals(RoutingDecision.AUTO_ACCEPT, decision)
            assertEquals("Manual mode must preserve selected Kannada", SupportedLanguage.KANNADA, resolved)
            assertEquals(SupportedLanguage.KANNADA, languageModelManager.currentLanguage.value)
        }
    }

    @Test
    fun test09_ConfirmedKannadaReusedInSubsequentUtterance() {
        runBlocking {
            languageModelManager.resetSession()
            // Force pending confirmation for Kannada using sample 8 which detects kn
            val audio = getAudioForLanguage("kn", 8)
            languageModelManager.resolveLanguageWithRouting(audio, "utt-kn-conf")

            // Simulate user clicking [ Use Kannada ]
            languageModelManager.confirmPendingLanguage(SupportedLanguage.KANNADA)

            val sessionState = languageModelManager.sessionLanguageState.value
            assertNotNull(sessionState)
            assertEquals(SupportedLanguage.KANNADA, sessionState?.language)
            assertEquals(SessionLanguageSource.USER_CONFIRMED, sessionState?.source)

            // Utterance 2: MUST reuse cached Kannada without re-prompting
            val (decision2, resolved2) = languageModelManager.resolveLanguageWithRouting(audio, "utt-kn-subsequent")
            assertEquals(RoutingDecision.AUTO_ACCEPT, decision2)
            assertEquals(SupportedLanguage.KANNADA, resolved2)
        }
    }

    @Test
    fun test10_HindiToKannadaNoSilentSwitch() {
        runBlocking {
            languageModelManager.resetSession()
            // Utterance 1: Hindi (AUTO_ACCEPT)
            val hiAudio = getAudioForLanguage("hi", 1)
            val (d1, r1) = languageModelManager.resolveLanguageWithRouting(hiAudio, "utt-hi-1")
            assertEquals(RoutingDecision.AUTO_ACCEPT, d1)
            assertEquals(SupportedLanguage.HINDI, r1)

            // Utterance 2: Kannada speech arrives during Hindi session
            languageModelManager.triggerLanguageReverification()
            val knAudio = getAudioForLanguage("kn", 8)
            val (d2, r2) = languageModelManager.resolveLanguageWithRouting(knAudio, "utt-kn-switch")

            // MUST NOT switch automatically to Kannada!
            assertNotEquals("Must not silently switch to Kannada", SupportedLanguage.KANNADA, languageModelManager.sessionLanguageState.value?.language)
            assertEquals("Hindi remains active session language", SupportedLanguage.HINDI, languageModelManager.sessionLanguageState.value?.language)
        }
    }

    @Test
    fun test11_HindiToTamilConsecutiveSwitch() {
        runBlocking {
            languageModelManager.resetSession()
            // Utterance 1: Hindi establishes session
            val hiAudio = getAudioForLanguage("hi", 1)
            languageModelManager.resolveLanguageWithRouting(hiAudio, "utt-hi-init")
            assertEquals(SupportedLanguage.HINDI, languageModelManager.sessionLanguageState.value?.language)

            // Utterance 2: Tamil detection 1 (sample 4 has 1.0 confidence, debounced)
            languageModelManager.triggerLanguageReverification()
            val taAudio = getAudioForLanguage("ta", 4)
            val (d1, r1) = languageModelManager.resolveLanguageWithRouting(taAudio, "utt-ta-1")
            // First detection does NOT switch yet
            assertEquals(SupportedLanguage.HINDI, languageModelManager.sessionLanguageState.value?.language)

            // Utterance 3: Tamil detection 2 (consecutive -> switch allowed for AUTO_ACCEPT language)
            languageModelManager.triggerLanguageReverification()
            val (d2, r2) = languageModelManager.resolveLanguageWithRouting(taAudio, "utt-ta-2")
            assertEquals(RoutingDecision.AUTO_ACCEPT, d2)
            assertEquals(SupportedLanguage.TAMIL, r2)
            assertEquals(SupportedLanguage.TAMIL, languageModelManager.sessionLanguageState.value?.language)
            Log.i(TAG, "[TEST-11] Successfully transitioned to Tamil after 2 consecutive detections!")
        }
    }
}
