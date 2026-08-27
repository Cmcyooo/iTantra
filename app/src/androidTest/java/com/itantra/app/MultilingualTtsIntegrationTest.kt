package com.itantra.app

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.itantra.app.audio.*
import com.itantra.app.comm.P2PMessage
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class MultilingualTtsIntegrationTest {

    companion object {
        private const val TAG = "MultilingualTtsTest"

        private val TACTICAL_PHRASES = mapOf(
            SupportedLanguage.ENGLISH to "Emergency alert, immediate assistance needed in sector four.",
            SupportedLanguage.HINDI to "आपातकालीन चेतावनी, सेक्टर चार में तुरंत सहायता की आवश्यकता है।",
            SupportedLanguage.GUJARATI to "કટોકટી ચેતવણી, સેક્ટર ચારમાં તાત્કાલિક સહાયની જરૂર છે.",
            SupportedLanguage.MARATHI to "तातडीचा इशारा, सेक्टर चारमध्ये तातडीने मदतीची गरज आहे.",
            SupportedLanguage.KANNADA to "ತುರ್ತು ಎಚ್ಚರಿಕೆ, ಸೆಕ್ಟರ್ ನಾಲ್ಕರಲ್ಲಿ ತಕ್ಷಣದ ನೆರವು ಅಗತ್ಯವಿದೆ.",
            SupportedLanguage.MALAYALAM to "അടിയന്തര മുന്നറിയിപ്പ്, സെക്ടർ നാലിൽ ഉടനടി സഹായം ആവശ്യമാണ്.",
            SupportedLanguage.TAMIL to "அவசர எச்சரிக்கை, பிரிவு நான்கில் உடனடி உதவி தேவைப்படுகிறது.",
            SupportedLanguage.TELUGU to "అత్యవసర హెచ్చరిక, సెక్టార్ నాలుగులో తక్షణ సహాయం అవసరం.",
            SupportedLanguage.ODIA to "ଜରୁରୀ ସତର୍କତା, ଚାରି ନମ୍ବର ସେକ୍ଟରରେ ତୁରନ୍ତ ସାହାଯ୍ୟ ଆବଶ୍ୟକ।",
            SupportedLanguage.BENGALI to "জরুরী সতর্কতা, চার নম্বর সেক্টরে অবিলম্বে সহায়তা প্রয়োজন।"
        )
    }

    private lateinit var context: Context
    private lateinit var languageTtsManager: LanguageTtsManager
    private lateinit var ttsManager: TtsManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        languageTtsManager = LanguageTtsManager.getInstance(context)
        ttsManager = TtsManager(context)
    }

    @After
    fun tearDown() {
        Runtime.getRuntime().gc()
    }

    private fun getProcessPssMb(): Double {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val pid = android.os.Process.myPid()
        val memInfo = am.getProcessMemoryInfo(intArrayOf(pid))
        return if (memInfo.isNotEmpty()) {
            memInfo[0].totalPss.toDouble() / 1024.0
        } else {
            (Debug.getPss().toDouble() / 1024.0)
        }
    }

    @Test
    fun test01_VerifyAll10LanguageConfigurations() {
        Log.i(TAG, "=== TEST 01: Verifying All 10 Language TTS Configurations ===")
        val allLanguages = SupportedLanguage.values()
        assertEquals("Should contain exactly 10 supported languages", 10, allLanguages.size)

        for (lang in allLanguages) {
            val config = TtsVoiceConfig.getConfigFor(lang)
            assertNotNull("Config for ${lang.displayName} must not be null", config)
            assertEquals("Config language must match", lang, config.language)
            assertTrue("Sample rate must be valid (16k or 22.05k)", config.sampleRate in listOf(16000, 22050))
            Log.i(TAG, "  ✓ [${lang.code}] ${lang.displayName} -> ${config.voiceTag} (${if (config.isProductionReady) "PRODUCTION READY" else "CONDITIONAL"})")
        }

        // Verify production ready vs conditional sets
        val productionReadyLangs = listOf(
            SupportedLanguage.ENGLISH,
            SupportedLanguage.HINDI,
            SupportedLanguage.TELUGU,
            SupportedLanguage.MALAYALAM,
            SupportedLanguage.TAMIL,
            SupportedLanguage.BENGALI,
            SupportedLanguage.MARATHI
        )
        for (lang in productionReadyLangs) {
            assertTrue("${lang.displayName} must be marked PRODUCTION READY", TtsVoiceConfig.getConfigFor(lang).isProductionReady)
        }

        val conditionalLangs = listOf(
            SupportedLanguage.GUJARATI,
            SupportedLanguage.KANNADA,
            SupportedLanguage.ODIA
        )
        for (lang in conditionalLangs) {
            assertFalse("${lang.displayName} must be marked CONDITIONAL", TtsVoiceConfig.getConfigFor(lang).isProductionReady)
        }
        Log.i(TAG, "✓ TEST 01 PASSED: All 10 language configs validated successfully.")
    }

    @Test
    fun test02_LanguageSwitchingSingleActiveLifecycle() {
        runBlocking {
            Log.i(TAG, "=== TEST 02: Single-Active Language Switching Lifecycle ===")
            val languagesToTest = listOf(
                SupportedLanguage.ENGLISH,
                SupportedLanguage.HINDI,
                SupportedLanguage.MARATHI,
                SupportedLanguage.BENGALI,
                SupportedLanguage.TELUGU,
                SupportedLanguage.MALAYALAM,
                SupportedLanguage.TAMIL,
                SupportedLanguage.GUJARATI,
                SupportedLanguage.KANNADA,
                SupportedLanguage.ODIA
            )

            val initialPss = getProcessPssMb()
            Log.i(TAG, "Initial Process PSS: ${"%.2f".format(initialPss)} MB")

            for (lang in languagesToTest) {
                val t0 = System.currentTimeMillis()
                val result = languageTtsManager.setLanguage(lang)
                val loadTimeMs = System.currentTimeMillis() - t0
                val pssMb = getProcessPssMb()

                assertTrue("Language switch to ${lang.displayName} must succeed", result.isSuccess)
                assertEquals("Current language must be updated", lang, languageTtsManager.currentLanguage.value)
                assertEquals("Lifecycle state must be READY", ModelLifecycleState.READY, languageTtsManager.lifecycleState.value)
                assertTrue("Process PSS (${"%.1f".format(pssMb)} MB) must be within 450 MB limit", pssMb < 450.0)

                Log.i(TAG, "  ✓ Switched to ${lang.displayName}: load=${loadTimeMs}ms, PSS=${"%.2f".format(pssMb)} MB")
            }

            // Return to English baseline
            languageTtsManager.setLanguage(SupportedLanguage.ENGLISH)
            assertEquals(SupportedLanguage.ENGLISH, languageTtsManager.currentLanguage.value)
            Log.i(TAG, "✓ TEST 02 PASSED: Successfully switched across all 10 languages within memory limits.")
        }
    }

    @Test
    fun test03_EndToEndSynthesisAndPlayback() {
        runBlocking {
            Log.i(TAG, "=== TEST 03: End-to-End Speech Synthesis Across Languages ===")

            val languages = listOf(
                SupportedLanguage.ENGLISH,
                SupportedLanguage.HINDI,
                SupportedLanguage.MARATHI,
                SupportedLanguage.BENGALI,
                SupportedLanguage.TELUGU,
                SupportedLanguage.MALAYALAM,
                SupportedLanguage.TAMIL,
                SupportedLanguage.ODIA
            )

            for (lang in languages) {
                languageTtsManager.setLanguage(lang)
                val phrase = TACTICAL_PHRASES[lang]!!
                
                val t0 = System.currentTimeMillis()
                val audio = languageTtsManager.generateSpeech(phrase)
                val synthMs = System.currentTimeMillis() - t0

                assertNotNull("GeneratedAudio for ${lang.displayName} must not be null", audio)
                assertTrue("Audio samples for ${lang.displayName} must not be empty", audio!!.samples.isNotEmpty())

                val durS = audio.samples.size.toDouble() / audio.sampleRate
                val rtf = if (durS > 0) (synthMs / 1000.0) / durS else 0.0

                Log.i(TAG, "  ✓ [${lang.code}] '${phrase.take(25)}...' -> ${"%.2f".format(durS)}s, Synth: ${synthMs}ms, RTF: ${"%.3f".format(rtf)}")
            }

            // Verify audio playback on English voice with latch
            languageTtsManager.setLanguage(SupportedLanguage.ENGLISH)
            val latch = CountDownLatch(1)
            var playbackFinished = false

            ttsManager.speak("iTantra emergency alert test.") {
                playbackFinished = true
                latch.countDown()
            }

            val completedInTime = latch.await(10, TimeUnit.SECONDS)
            assertTrue("Speech playback must complete within 10 seconds", completedInTime)
            assertTrue("Playback callback must be invoked", playbackFinished)

            Log.i(TAG, "✓ TEST 03 PASSED: Speech synthesis and AudioTrack playback verified.")
        }
    }

    @Test
    fun test04_EmergencyAlertPlaybackIntegration() {
        runBlocking {
            Log.i(TAG, "=== TEST 04: AlertPlaybackManager Integration with Multilingual TTS ===")
            val alertPlaybackManager = AlertPlaybackManager(context, ttsManager)

            val alertMessage = P2PMessage(
                messageId = "TEST-ALERT-${System.currentTimeMillis()}",
                timestamp = System.currentTimeMillis(),
                language = "en",
                text = "Red Alert, evacuation in progress.",
                senderName = "ALPHA-1",
                messageType = P2PMessage.MESSAGE_TYPE_ALERT,
                priority = P2PMessage.PRIORITY_HIGH
            )

            val startLatch = CountDownLatch(1)
            val finishLatch = CountDownLatch(1)

            alertPlaybackManager.onAlertPlaybackStarted = { msg, _ ->
                Log.i(TAG, "  Alert playback started: ${msg.messageId}")
                startLatch.countDown()
            }

            alertPlaybackManager.onAlertPlaybackFinished = { msg, durMs ->
                Log.i(TAG, "  Alert playback finished: ${msg.messageId} in ${durMs}ms")
                finishLatch.countDown()
            }

            alertPlaybackManager.enqueueMessage(alertMessage)

            val started = startLatch.await(8, TimeUnit.SECONDS)
            val finished = finishLatch.await(12, TimeUnit.SECONDS)

            assertTrue("Emergency alert must start playback", started)
            assertTrue("Emergency alert must finish playback", finished)

            alertPlaybackManager.release()
            Log.i(TAG, "✓ TEST 04 PASSED: Emergency Alert playback pipeline operational.")
        }
    }
}
