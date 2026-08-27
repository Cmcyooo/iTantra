package com.itantra.app

import com.itantra.app.audio.SupportedLanguage
import com.itantra.app.audio.TtsVoiceConfig
import com.itantra.app.comm.ConnectionState
import com.itantra.app.comm.P2PMessage
import com.itantra.app.comm.Transport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class TelemetryAndRoutingUnitTest {

    @Test
    fun testP2PMessageUtteranceIdAndLanguagePropagation() {
        val original = P2PMessage(
            messageId = "msg-12345",
            timestamp = 1700000000000L,
            language = "hi",
            text = "नमस्ते परीक्षण",
            senderName = "SquadAlpha",
            utteranceId = "utt-98765"
        )

        val jsonString = Json.encodeToString(original)
        val deserialized = Json.decodeFromString<P2PMessage>(jsonString)

        assertEquals("msg-12345", deserialized.messageId)
        assertEquals("utt-98765", deserialized.utteranceId)
        assertEquals("hi", deserialized.language)
        assertEquals("नमस्ते परीक्षण", deserialized.text)
        assertEquals("SquadAlpha", deserialized.senderName)
    }

    @Test
    fun testSupportedLanguageFromCode() {
        assertEquals(SupportedLanguage.HINDI, SupportedLanguage.fromCode("hi"))
        assertEquals(SupportedLanguage.MARATHI, SupportedLanguage.fromCode("mr"))
        assertEquals(SupportedLanguage.BENGALI, SupportedLanguage.fromCode("bn"))
        assertEquals(SupportedLanguage.TELUGU, SupportedLanguage.fromCode("te"))
        assertEquals(SupportedLanguage.MALAYALAM, SupportedLanguage.fromCode("ml"))
        assertEquals(SupportedLanguage.TAMIL, SupportedLanguage.fromCode("ta"))
        assertEquals(SupportedLanguage.GUJARATI, SupportedLanguage.fromCode("gu"))
        assertEquals(SupportedLanguage.KANNADA, SupportedLanguage.fromCode("kn"))
        assertEquals(SupportedLanguage.ODIA, SupportedLanguage.fromCode("or"))
        assertEquals(SupportedLanguage.ENGLISH, SupportedLanguage.fromCode("en"))

        // Fallback check
        assertEquals(SupportedLanguage.ENGLISH, SupportedLanguage.fromCode("unknown_code"))
    }

    @Test
    fun testTtsVoiceConfigMappings() {
        val hindiConfig = TtsVoiceConfig.getConfigFor(SupportedLanguage.HINDI)
        assertEquals("piper_hi_priyamvada", hindiConfig.modelDirName)
        assertEquals(22050, hindiConfig.sampleRate)
        assertTrue(hindiConfig.isPiper)

        val marathiConfig = TtsVoiceConfig.getConfigFor(SupportedLanguage.MARATHI)
        assertEquals("piper_mr_google", marathiConfig.modelDirName)
        assertTrue(marathiConfig.isPiper)

        val bengaliConfig = TtsVoiceConfig.getConfigFor(SupportedLanguage.BENGALI)
        assertEquals("piper_bn_google", bengaliConfig.modelDirName)
        assertTrue(bengaliConfig.isPiper)

        val teluguConfig = TtsVoiceConfig.getConfigFor(SupportedLanguage.TELUGU)
        assertEquals("piper_te_maya", teluguConfig.modelDirName)
        assertTrue(teluguConfig.isPiper)

        val malayalamConfig = TtsVoiceConfig.getConfigFor(SupportedLanguage.MALAYALAM)
        assertEquals("piper_ml_meera", malayalamConfig.modelDirName)
        assertTrue(malayalamConfig.isPiper)

        val tamilConfig = TtsVoiceConfig.getConfigFor(SupportedLanguage.TAMIL)
        assertEquals("piper_ta_rasa_female", tamilConfig.modelDirName)
        assertTrue(tamilConfig.isPiper)
    }

    @Test
    fun testLocalIntervalIntegrity() {
        // Verify local calculation logic produces valid non-zero intervals
        val pttRelease = 1000L
        val sttStart = 1080L
        val sttEnd = 1250L
        val sendStart = 1260L
        val sendEnd = 1275L

        val pttToStt = sttStart - pttRelease // 80ms
        val sttDuration = sttEnd - sttStart // 170ms
        val sttToSend = sendStart - sttEnd // 10ms
        val sendDuration = sendEnd - sendStart // 15ms

        assertTrue("PTT->STT must be strictly positive", pttToStt > 0)
        assertTrue("STT duration must be strictly positive", sttDuration > 0)
        assertTrue("Pre-send must be positive", sttToSend > 0)
        assertTrue("Send duration must be positive", sendDuration > 0)

        // Verify formatting logic doesn't return 0ms or negative
        fun formatInterval(start: Long, end: Long): String {
            val delta = end - start
            return if (delta > 0) "${delta}ms" else "N/A"
        }

        assertEquals("80ms", formatInterval(pttRelease, sttStart))
        assertEquals("170ms", formatInterval(sttStart, sttEnd))
        assertEquals("N/A", formatInterval(0L, 0L))
        assertEquals("N/A", formatInterval(100L, 50L)) // Negative interval converted to N/A
    }
}
