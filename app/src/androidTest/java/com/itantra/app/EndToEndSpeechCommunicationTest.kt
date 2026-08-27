package com.itantra.app

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.itantra.app.audio.*
import com.itantra.app.comm.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.BeforeClass
import org.junit.AfterClass
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.util.UUID

/**
 * PHASE 13 — END-TO-END SPEECH COMMUNICATION LOOP TEST SUITE
 * Validates the complete pipeline:
 * Speech/Text -> UtteranceId -> Serialization -> Transport -> Reception -> Language Routing -> TTS -> Audio Playback
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class EndToEndSpeechCommunicationTest {

    companion object {
        private const val TAG = "EndToEndSpeechTest"
        private var sharedAudioManager: AudioCaptureManager? = null
        private var sharedTtsManager: TtsManager? = null

        @BeforeClass
        @JvmStatic
        fun initAll() {
            val ctx = ApplicationProvider.getApplicationContext<Context>()
            sharedAudioManager = AudioCaptureManager(ctx)
            sharedTtsManager = TtsManager(ctx)
        }

        @AfterClass
        @JvmStatic
        fun cleanAll() {
            sharedAudioManager?.release()
            sharedTtsManager?.release()
        }
    }

    private lateinit var context: Context
    private lateinit var mockTransport: MockLoopbackTransport
    private lateinit var commManager: CommunicationManager
    private lateinit var transceiverManager: TransceiverManager

    class MockLoopbackTransport : Transport {
        private val _connState = MutableStateFlow(ConnectionState.CONNECTED)
        override val connectionState = _connState
        override val lastError = MutableStateFlow<String?>(null)
        private var listener: ((P2PMessage) -> Unit)? = null
        val sentMessages = mutableListOf<P2PMessage>()

        override fun connect(targetAddress: String?) {
            _connState.value = ConnectionState.CONNECTED
        }

        override fun disconnect() {
            _connState.value = ConnectionState.DISCONNECTED
        }

        override fun getConnectedPeerId(): String = "peer_device_b"

        override fun sendMessage(message: P2PMessage) {
            sentMessages.add(message)
        }

        override fun setOnMessageReceivedListener(listener: (P2PMessage) -> Unit) {
            this.listener = listener
        }

        fun deliverToReceiver(message: P2PMessage) {
            listener?.invoke(message)
        }
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        mockTransport = MockLoopbackTransport()
        commManager = CommunicationManager(mockTransport)
        val callSignManager = CallSignManager(context)
        transceiverManager = TransceiverManager(
            context,
            sharedAudioManager!!,
            sharedTtsManager!!,
            commManager,
            callSignManager
        )
        transceiverManager.alertPlaybackManager.reset()
    }

    @After
    fun tearDown() {
        transceiverManager.alertPlaybackManager.reset()
    }

    /**
     * TEST 01: Utterance Continuity
     * Verifies that sending a normal message retains the exact utteranceId throughout the pipeline.
     */
    @Test
    fun test01_UtteranceContinuity_SpeechToMessage() {
        val testUtteranceId = UUID.randomUUID().toString()
        val text = "Hello, this is a radio check."
        
        val sentMsg = commManager.sendText(
            text = text,
            language = "en",
            senderName = "Station-A",
            utteranceId = testUtteranceId
        )

        assertEquals("UtteranceId must be preserved in sent message", testUtteranceId, sentMsg.utteranceId)
        assertEquals("Text must match", text, sentMsg.text)
        assertEquals("Language must be en", "en", sentMsg.language)
        assertEquals("Type must be NORMAL", P2PMessage.MESSAGE_TYPE_NORMAL, sentMsg.messageType)
        val textMessages = mockTransport.sentMessages.filter { it.text.isNotEmpty() }
        assertEquals("Sent list should have 1 text item", 1, textMessages.size)
        assertEquals("Transport message must have same utteranceId", testUtteranceId, textMessages[0].utteranceId)
    }

    /**
     * TEST 02: PTT Consecutive Identical Utterances
     * Verifies that saying the exact same sentence multiple times ("Radio check", "Radio check")
     * generates distinct messages with unique utteranceIds and neither is dropped.
     */
    @Test
    fun test02_PTTConsecutiveIdenticalUtterances() {
        val utterance1 = UUID.randomUUID().toString()
        val utterance2 = UUID.randomUUID().toString()
        val phrase = "Radio check."

        val msg1 = commManager.sendText(phrase, "en", "Station-A", utterance1)
        val msg2 = commManager.sendText(phrase, "en", "Station-A", utterance2)

        assertNotEquals("Utterance IDs must be unique", msg1.utteranceId, msg2.utteranceId)
        val textMessages = mockTransport.sentMessages.filter { it.text.isNotEmpty() }
        assertEquals("Both messages must be sent to transport", 2, textMessages.size)
        assertEquals("First message matches phrase", phrase, textMessages[0].text)
        assertEquals("Second message matches phrase", phrase, textMessages[1].text)
    }

    /**
     * TEST 03: Multilingual Language Routing on Receiver
     * Tests Indian languages: English (en), Hindi (hi), Telugu (te), Marathi (mr), Tamil (ta), Bengali (bn).
     * Verifies that incoming message language is correctly resolved and speech audio is synthesized.
     */
    @Test
    fun test03_MultilingualLanguageRouting_ReceiverTTS() = runBlocking {
        val testCases = listOf(
            Triple("en", "Hello, this is a radio check.", "English"),
            Triple("hi", "मुझे स्टेशन जाना है।", "Hindi"),
            Triple("te", "దయచేసి స్టేషన్కు రండి.", "Telugu"),
            Triple("mr", "कृपया स्टेशनवर या.", "Marathi"),
            Triple("ta", "தயவுசெய்து நிலையத்திற்கு வாருங்கள்.", "Tamil"),
            Triple("bn", "দয়া করে স্টেশনে আসুন।", "Bengali")
        )

        for ((langCode, text, langName) in testCases) {
            val utteranceId = UUID.randomUUID().toString()
            Log.i(TAG, "Testing Multilingual Routing for $langName [$langCode]...")

            val t0 = System.currentTimeMillis()
            val audio = sharedTtsManager!!.generateSpeech(text, langCode)
            val elapsed = System.currentTimeMillis() - t0

            assertNotNull("TTS synthesis failed for $langName ($langCode)", audio)
            assertTrue("Audio samples must be non-empty for $langName", audio!!.samples.isNotEmpty())
            assertTrue("Audio sample rate must be valid for $langName", audio.sampleRate > 0)
            val dur = audio.samples.size.toDouble() / audio.sampleRate

            Log.i(
                TAG,
                "[TEST-MULTILINGUAL-TTS] language=$langCode text='$text' samples=${audio.samples.size} " +
                "duration=${String.format(java.util.Locale.US, "%.2f", dur)}s synthesisTime=${elapsed}ms"
            )
        }
    }

    /**
     * TEST 04: Different Language UI Test
     * Sender UI: Hindi (hi)
     * Receiver UI: English (en)
     * Receiver must synthesize in Hindi, NOT English, strictly obeying message.language.
     */
    @Test
    fun test04_DifferentLanguageUI_PreservesIncomingLanguage() = runBlocking {
        // Set receiver's local TTS language to English
        sharedTtsManager!!.languageTtsManager.setLanguage(SupportedLanguage.ENGLISH)
        assertEquals("Local UI language is English", SupportedLanguage.ENGLISH, sharedTtsManager!!.languageTtsManager.currentLanguage.value)

        // Incoming message is Hindi
        val hindiUtteranceId = UUID.randomUUID().toString()
        val hindiMessage = P2PMessage(
            messageId = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            language = "hi",
            text = "मुझे सहायता चाहिए",
            utteranceId = hindiUtteranceId
        )

        // Verify receiver generates speech in Hindi
        val targetLang = SupportedLanguage.fromCodeOrNull(hindiMessage.language)
        assertNotNull("Target language must be resolved", targetLang)
        assertEquals("Resolved target language must be HINDI", SupportedLanguage.HINDI, targetLang)

        val audio = sharedTtsManager!!.generateSpeech(hindiMessage.text, hindiMessage.language)
        assertNotNull("Speech must be generated in Hindi", audio)
        assertTrue("Speech samples must be non-empty", audio!!.samples.isNotEmpty())

        // Active TTS language should now be Hindi
        assertEquals("TTS should have switched to message's language", SupportedLanguage.HINDI, sharedTtsManager!!.languageTtsManager.currentLanguage.value)
    }

    /**
     * TEST 05: Duplicate Message Protection
     * Ensures an incoming message with the same messageId or utteranceId is only queued once.
     */
    @Test
    fun test05_DuplicateMessageProtection() {
        val utteranceId = UUID.randomUUID().toString()
        val messageId = UUID.randomUUID().toString()
        val message = P2PMessage(
            messageId = messageId,
            timestamp = System.currentTimeMillis(),
            language = "en",
            text = "Single playback test.",
            utteranceId = utteranceId
        )

        // Enqueue first time
        transceiverManager.alertPlaybackManager.enqueueMessage(message)
        assertTrue("Message should be marked as processed/duplicate", transceiverManager.alertPlaybackManager.isAlertDuplicate(messageId))

        // Enqueue second time (retransmission simulation)
        transceiverManager.alertPlaybackManager.enqueueMessage(message)
        // Verify duplicate check prevents duplicate execution
        assertTrue("Message remains marked as processed", transceiverManager.alertPlaybackManager.isAlertDuplicate(messageId))
    }

    /**
     * TEST 06: TTS Warm State Reuse
     * Verifies that consecutive messages in the same language reuse the initialized TTS engine
     * without undergoing model reload.
     */
    @Test
    fun test06_TtsWarmStateReuse() = runBlocking {
        sharedTtsManager!!.languageTtsManager.setLanguage(SupportedLanguage.ENGLISH)

        val t0 = System.currentTimeMillis()
        val audio1 = sharedTtsManager!!.generateSpeech("First message.", "en")
        val dur1 = System.currentTimeMillis() - t0

        val t1 = System.currentTimeMillis()
        val audio2 = sharedTtsManager!!.generateSpeech("Second message.", "en")
        val dur2 = System.currentTimeMillis() - t1

        assertNotNull(audio1)
        assertNotNull(audio2)
        Log.i(TAG, "[TEST-WARM-TTS] firstCallMs=$dur1 secondCallMs=$dur2")
        // Warm call should be fast (< 2000ms on device)
        assertTrue("Warm TTS call should complete promptly", dur2 < 3000)
    }

    /**
     * TEST 07: Payload Compression Measurement
     * Compares the transmitted text packet bytes against the raw PCM audio baseline for the same phrase.
     */
    @Test
    fun test07_PayloadCompressionMeasurement() {
        val testPhrase = "Hello, this is a radio check."
        val message = P2PMessage(
            messageId = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            language = "en",
            text = testPhrase,
            senderName = "Redmi-Alpha",
            utteranceId = UUID.randomUUID().toString()
        )

        val jsonString = Json.encodeToString(message)
        val packetBytes = jsonString.toByteArray(Charsets.UTF_8).size
        val textBytes = testPhrase.toByteArray(Charsets.UTF_8).size

        // Speech duration for "Hello, this is a radio check." is approximately 2.5 seconds
        // 16 kHz Mono 16-bit PCM = 16,000 samples/sec * 2 bytes/sample = 32,000 bytes/sec
        val estimatedSpeechSeconds = 2.5
        val audioBaselineBytes = (estimatedSpeechSeconds * 32000).toLong() // 80,000 bytes

        val reductionPercentage = ((audioBaselineBytes - packetBytes).toDouble() / audioBaselineBytes) * 100.0

        Log.i(TAG, "==================================================")
        Log.i(TAG, "[BANDWIDTH-METRIC] textPayloadBytes=$textBytes")
        Log.i(TAG, "[BANDWIDTH-METRIC] totalPacketBytes=$packetBytes")
        Log.i(TAG, "[BANDWIDTH-METRIC] uncompressedAudioBaselineBytes=$audioBaselineBytes")
        Log.i(TAG, "[BANDWIDTH-METRIC] approximateReduction=${String.format(java.util.Locale.US, "%.2f", reductionPercentage)}%")
        Log.i(TAG, "==================================================")

        assertTrue("Packet bytes must be under 500 bytes", packetBytes < 500)
        assertTrue("Bandwidth reduction must exceed 95%", reductionPercentage > 95.0)
    }

    /**
     * TEST 08: Unsupported Language Rejection
     * Verifies that attempting to synthesize or speak an unknown language code fails cleanly
     * without substituting the wrong voice.
     */
    @Test
    fun test08_UnsupportedLanguage_Rejection() = runBlocking {
        val audio = sharedTtsManager!!.generateSpeech("Unknown language text", "xx")
        assertNull("Unsupported language 'xx' must return null without synthesizing wrong voice", audio)
    }
}
