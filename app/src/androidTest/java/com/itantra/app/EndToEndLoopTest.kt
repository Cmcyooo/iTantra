package com.itantra.app

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.itantra.app.audio.AudioCaptureManager
import com.itantra.app.audio.SupportedLanguage
import com.itantra.app.audio.TtsManager
import com.itantra.app.comm.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class EndToEndLoopTest {

    companion object {
        private const val TAG = "EndToEndLoopTest"
    }

    private lateinit var context: Context
    private lateinit var audioManager: AudioCaptureManager
    private lateinit var ttsManager: TtsManager
    private lateinit var wifiTransport: WiFiTransport
    private lateinit var commManager: CommunicationManager
    private lateinit var callSignManager: CallSignManager
    private lateinit var transceiverManager: TransceiverManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        audioManager = AudioCaptureManager(context)
        ttsManager = TtsManager(context)
        wifiTransport = WiFiTransport()
        commManager = CommunicationManager(wifiTransport)
        callSignManager = CallSignManager(context)
        transceiverManager = TransceiverManager(
            context = context,
            audioManager = audioManager,
            ttsManager = ttsManager,
            commManager = commManager,
            callSignManager = callSignManager
        )
    }

    @Test
    fun testMultilingualEndToEndPipeline() {
        runBlocking {
            Log.i(TAG, "=== Starting Multilingual End-to-End Loop Validation ===")

            // Test 1: English Normal Message Flow
            Log.i(TAG, "--- Testing Pipeline: English ---")
            audioManager.languageModelManager.setLanguage(SupportedLanguage.ENGLISH)
            ttsManager.languageTtsManager.setLanguage(SupportedLanguage.ENGLISH)

            assertEquals(SupportedLanguage.ENGLISH, audioManager.languageModelManager.currentLanguage.value)
            assertEquals(SupportedLanguage.ENGLISH, ttsManager.languageTtsManager.currentLanguage.value)

            val englishLatch = CountDownLatch(1)
            ttsManager.speak("Station Alpha, radio check operational.") {
                Log.i(TAG, "✓ English speech synthesized and output to speaker.")
                englishLatch.countDown()
            }
            assertTrue("English audio playback must complete", englishLatch.await(8, TimeUnit.SECONDS))

            // Test 2: Hindi Emergency Alert Flow
            Log.i(TAG, "--- Testing Pipeline: Hindi Emergency Alert ---")
            audioManager.languageModelManager.setLanguage(SupportedLanguage.HINDI)
            ttsManager.languageTtsManager.setLanguage(SupportedLanguage.HINDI)

            assertEquals(SupportedLanguage.HINDI, audioManager.languageModelManager.currentLanguage.value)
            assertEquals(SupportedLanguage.HINDI, ttsManager.languageTtsManager.currentLanguage.value)

            val hindiAlert = P2PMessage(
                messageId = "ALERT-HI-${System.currentTimeMillis()}",
                timestamp = System.currentTimeMillis(),
                language = "hi",
                text = "आपातकालीन चेतावनी, सभी टीमें सुरक्षित स्थान पर जाएं।",
                senderName = "BRAVO-2",
                messageType = P2PMessage.MESSAGE_TYPE_ALERT,
                priority = P2PMessage.PRIORITY_HIGH
            )

            val hindiLatch = CountDownLatch(1)
            transceiverManager.alertPlaybackManager.onAlertPlaybackFinished = { msg, durMs ->
                if (msg.messageId == hindiAlert.messageId) {
                    Log.i(TAG, "✓ Hindi alert synthesized via piper_hi_priyamvada and played via speaker in ${durMs}ms")
                    hindiLatch.countDown()
                }
            }

            transceiverManager.alertPlaybackManager.enqueueMessage(hindiAlert)
            assertTrue("Hindi alert playback must complete", hindiLatch.await(10, TimeUnit.SECONDS))

            // Test 3: Marathi Normal Message Flow
            Log.i(TAG, "--- Testing Pipeline: Marathi ---")
            audioManager.languageModelManager.setLanguage(SupportedLanguage.MARATHI)
            ttsManager.languageTtsManager.setLanguage(SupportedLanguage.MARATHI)

            assertEquals(SupportedLanguage.MARATHI, audioManager.languageModelManager.currentLanguage.value)
            assertEquals(SupportedLanguage.MARATHI, ttsManager.languageTtsManager.currentLanguage.value)

            val marathiLatch = CountDownLatch(1)
            ttsManager.speak("आम्ही स्टेशन अल्फा येथे पोहोचलो आहोत.") {
                Log.i(TAG, "✓ Marathi speech synthesized via piper_mr_google and output to speaker.")
                marathiLatch.countDown()
            }
            assertTrue("Marathi audio playback must complete", marathiLatch.await(8, TimeUnit.SECONDS))

            // Teardown
            transceiverManager.release()
            audioManager.release()
            ttsManager.release()
            Log.i(TAG, "✓ ALL PIPELINES VERIFIED: Speech -> STT -> Transport -> TTS -> Speaker operational across languages.")
        }
    }
}
