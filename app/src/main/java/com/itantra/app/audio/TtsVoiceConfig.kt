package com.itantra.app.audio

/**
 * Model type for offline speech synthesis.
 */
enum class TtsType {
    PIPER_VITS,
    META_MMS
}

/**
 * Configuration describing an offline TTS voice.
 */
data class TtsVoiceConfig(
    val language: SupportedLanguage,
    val voiceTag: String,
    val displayName: String,
    val type: TtsType,
    val modelDirName: String,
    val sampleRate: Int,
    val isProductionReady: Boolean,
    val speakerId: Int = 0,
    val noiseScale: Float = 0.667f,
    val noiseScaleW: Float = 0.8f,
    val lengthScale: Float = 1.0f
) {
    val isPiper: Boolean get() = type == TtsType.PIPER_VITS

    companion object {
        private val VOICE_MAP = mapOf(
            SupportedLanguage.ENGLISH to TtsVoiceConfig(
                language = SupportedLanguage.ENGLISH,
                voiceTag = "piper_en_amy",
                displayName = "Amy (Low)",
                type = TtsType.PIPER_VITS,
                modelDirName = "tts-en-amy",
                sampleRate = 16000,
                isProductionReady = true
            ),
            SupportedLanguage.HINDI to TtsVoiceConfig(
                language = SupportedLanguage.HINDI,
                voiceTag = "piper_hi_priyamvada",
                displayName = "Priyamvada (Medium)",
                type = TtsType.PIPER_VITS,
                modelDirName = "piper_hi_priyamvada",
                sampleRate = 22050,
                isProductionReady = true
            ),
            SupportedLanguage.GUJARATI to TtsVoiceConfig(
                language = SupportedLanguage.GUJARATI,
                voiceTag = "mms_guj",
                displayName = "MMS Gujarati",
                type = TtsType.META_MMS,
                modelDirName = "mms_guj",
                sampleRate = 16000,
                isProductionReady = false
            ),
            SupportedLanguage.MARATHI to TtsVoiceConfig(
                language = SupportedLanguage.MARATHI,
                voiceTag = "piper_mr_google",
                displayName = "Google Marathi (Medium)",
                type = TtsType.PIPER_VITS,
                modelDirName = "piper_mr_google",
                sampleRate = 22050,
                isProductionReady = true
            ),
            SupportedLanguage.KANNADA to TtsVoiceConfig(
                language = SupportedLanguage.KANNADA,
                voiceTag = "mms_kan",
                displayName = "MMS Kannada",
                type = TtsType.META_MMS,
                modelDirName = "mms_kan",
                sampleRate = 16000,
                isProductionReady = false
            ),
            SupportedLanguage.MALAYALAM to TtsVoiceConfig(
                language = SupportedLanguage.MALAYALAM,
                voiceTag = "piper_ml_meera",
                displayName = "Meera (Medium)",
                type = TtsType.PIPER_VITS,
                modelDirName = "piper_ml_meera",
                sampleRate = 22050,
                isProductionReady = true
            ),
            SupportedLanguage.TAMIL to TtsVoiceConfig(
                language = SupportedLanguage.TAMIL,
                voiceTag = "piper_ta_rasa_female",
                displayName = "Rasa Female (Medium)",
                type = TtsType.PIPER_VITS,
                modelDirName = "piper_ta_rasa_female",
                sampleRate = 22050,
                isProductionReady = true
            ),
            SupportedLanguage.TELUGU to TtsVoiceConfig(
                language = SupportedLanguage.TELUGU,
                voiceTag = "piper_te_maya",
                displayName = "Maya (Medium)",
                type = TtsType.PIPER_VITS,
                modelDirName = "piper_te_maya",
                sampleRate = 22050,
                isProductionReady = true
            ),
            SupportedLanguage.ODIA to TtsVoiceConfig(
                language = SupportedLanguage.ODIA,
                voiceTag = "mms_ory",
                displayName = "MMS Odia",
                type = TtsType.META_MMS,
                modelDirName = "mms_ory",
                sampleRate = 16000,
                isProductionReady = false
            ),
            SupportedLanguage.BENGALI to TtsVoiceConfig(
                language = SupportedLanguage.BENGALI,
                voiceTag = "piper_bn_google",
                displayName = "Google Bengali (Medium)",
                type = TtsType.PIPER_VITS,
                modelDirName = "piper_bn_google",
                sampleRate = 22050,
                isProductionReady = true
            )
        )

        fun getConfigFor(language: SupportedLanguage): TtsVoiceConfig {
            return VOICE_MAP[language] ?: VOICE_MAP[SupportedLanguage.ENGLISH]!!
        }

        fun getAllConfigs(): Collection<TtsVoiceConfig> = VOICE_MAP.values
    }
}
