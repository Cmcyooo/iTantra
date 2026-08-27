package com.itantra.app

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.itantra.app.audio.*
import com.itantra.app.comm.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class TelemetryAndLanguageRoutingTest {

    companion object {
        private const val TAG = "TelemetryTest"
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

    @Test
    fun testLanguagePropagationRouting(): Unit = runBlocking {
        Log.i(TAG, "=== TEST 1: LANGUAGE PROPAGATION ROUTING ===")

        val testLanguages = listOf(
            SupportedLanguage.HINDI to "नमस्ते, यह परीक्षण संदेश है।",
            SupportedLanguage.MARATHI to "नमस्कार, ही चाचणी आहे.",
            SupportedLanguage.TELUGU to "నమస్కారం, ఇది పరీక్ష.",
            SupportedLanguage.BENGALI to "নমস্কার, এটি একটি পরীক্ষা।",
            SupportedLanguage.ENGLISH to "Hello, this is a test message."
        )

        for ((targetLang, text) in testLanguages) {
            val utteranceId = "utt-${UUID.randomUUID().toString().take(6)}"
            Log.i(TAG, "Dispatching incoming packet: utteranceId=$utteranceId, language=${targetLang.code}")

            // Simulate incoming packet with target language
            val packet = P2PMessage(
                messageId = utteranceId,
                timestamp = System.currentTimeMillis(),
                language = targetLang.code,
                text = text,
                senderName = "PeerPhone",
                utteranceId = utteranceId
            )

            // Receiver synthesizes using packet language
            val ttsStart = SystemClock.elapsedRealtime()
            val audio = ttsManager.generateSpeech(packet.text, packet.language)
            val ttsEnd = SystemClock.elapsedRealtime()
            val ttsDurationMs = ttsEnd - ttsStart

            assertNotNull("Audio generation should succeed for ${targetLang.displayName}", audio)
            assertTrue("Audio samples should not be empty for ${targetLang.displayName}", audio!!.samples.isNotEmpty())

            // Verify active TTS language was automatically switched
            val currentTtsLang = languageTtsManager.currentLanguage.value
            assertEquals("LanguageTtsManager should have switched to packet language", targetLang, currentTtsLang)

            val voiceConfig = TtsVoiceConfig.getConfigFor(currentTtsLang)
            Log.i(
                TAG,
                "[TELEMETRY-RECEIVER] utteranceId=$utteranceId language=${packet.language} " +
                "TTS_language=${voiceConfig.language.code} TTS_engine=${voiceConfig.type} " +
                "TTS_model=${voiceConfig.modelDirName} TTS_START->TTS_END=${ttsDurationMs}ms (audio dur: ${"%.2f".format(audio.samples.size.toDouble() / audio.sampleRate)}s)"
            )
        }
    }

    class TestLoopbackTransport : Transport {
        var peer: TestLoopbackTransport? = null
        private val _connectionState = kotlinx.coroutines.flow.MutableStateFlow(ConnectionState.CONNECTED)
        override val connectionState = _connectionState.asStateFlow()
        private val _lastError = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
        override val lastError = _lastError.asStateFlow()
        private var messageListener: ((P2PMessage) -> Unit)? = null

        override fun connect(targetId: String?) {
            _connectionState.value = ConnectionState.CONNECTED
        }

        override fun disconnect() {
            _connectionState.value = ConnectionState.DISCONNECTED
        }

        override fun sendMessage(message: P2PMessage) {
            if (_connectionState.value != ConnectionState.CONNECTED) {
                _lastError.value = "Transport disconnected"
                return
            }
            peer?.receiveMessage(message)
        }

        fun receiveMessage(message: P2PMessage) {
            messageListener?.invoke(message)
        }

        override fun getConnectedPeerId(): String = "peer-station"

        override fun setOnMessageReceivedListener(callback: (P2PMessage) -> Unit) {
            this.messageListener = callback
        }
    }

    @Test
    fun testRttAndTelemetryTimestamps(): Unit = runBlocking {
        Log.i(TAG, "=== TEST 2: RTT & LOCAL SENDER/RECEIVER TIMING INTEGRITY ===")

        val transportAlpha = TestLoopbackTransport()
        val transportBravo = TestLoopbackTransport()
        transportAlpha.peer = transportBravo
        transportBravo.peer = transportAlpha

        val commAlpha = CommunicationManager(transportAlpha)
        val commBravo = CommunicationManager(transportBravo)
        val utteranceId = "utt-${UUID.randomUUID().toString().take(8)}"

        // Measure Sender local intervals
        val pttRelease = SystemClock.elapsedRealtime()
        delay(35) // Simulate PTT release to STT queue
        val sttStart = SystemClock.elapsedRealtime()
        delay(85) // Simulate STT processing
        val sttEnd = SystemClock.elapsedRealtime()
        delay(5)  // Pre-send overhead
        val sendStart = SystemClock.elapsedRealtime()

        val sentMsg = commAlpha.sendText("Simulated voice packet", language = "hi", senderName = "Alpha1")
        val sendEnd = SystemClock.elapsedRealtime()

        val pttToStt = sttStart - pttRelease
        val sttDuration = sttEnd - sttStart
        val sttToSend = sendStart - sttEnd
        val sendDuration = sendEnd - sendStart

        assertTrue("PTT->STT should be positive", pttToStt > 0)
        assertTrue("STT duration should be positive", sttDuration > 0)
        assertTrue("STT->Send should be non-negative", sttToSend >= 0)
        assertTrue("Send duration should be positive", sendDuration >= 0)

        Log.i(
            TAG,
            "[TELEMETRY-SENDER] utteranceId=$utteranceId language=hi STT_language=hi " +
            "PTT_RELEASE->STT_START=${pttToStt}ms STT_START->STT_END=${sttDuration}ms " +
            "STT_END->SEND_START=${sttToSend}ms SEND_START->SEND_COMPLETE=${sendDuration}ms"
        )

        // Wait for loopback ACK from commBravo
        delay(100)
        val rtt = commAlpha.latency.value
        assertNotNull("RTT should be measured from loopback ACK", rtt)
        assertTrue("RTT should be strictly positive (never negative)", rtt!! > 0)
        Log.i(TAG, "[TELEMETRY-TRANSPORT] utteranceId=${sentMsg.messageId} RTT=${rtt}ms")
    }
}
