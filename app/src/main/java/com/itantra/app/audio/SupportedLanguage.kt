package com.itantra.app.audio

/**
 * Supported speech recognition and synthesis languages in iTantra.
 * Complete 10-language project set: English, Hindi, Gujarati, Marathi, Kannada, Malayalam, Tamil, Telugu, Odia, Bengali.
 */
enum class SupportedLanguage(
    val code: String,
    val displayName: String,
    val nativeName: String,
    val engineType: SttEngineType,
    val modelAssetPath: String,
    val vocabOrTokensAssetPath: String,
    val expectedSha256: String
) {
    ENGLISH(
        code = "en",
        displayName = "English",
        nativeName = "English",
        engineType = SttEngineType.SHERPA_ONNX_WHISPER,
        modelAssetPath = "whisper-tiny-en",
        vocabOrTokensAssetPath = "whisper-tiny-en/tokens.txt",
        expectedSha256 = "" // Bundle verified
    ),
    HINDI(
        code = "hi",
        displayName = "Hindi",
        nativeName = "हिन्दी",
        engineType = SttEngineType.GENERIC_ONNX_CTC,
        modelAssetPath = "indic_stt/vakyansh_hindi_base.int8.onnx",
        vocabOrTokensAssetPath = "indic_stt/hindi_vocab.json",
        expectedSha256 = "8e24e70119b8559d6299f68ae935be9999b93c1ea63f9d5c2191f902419aa516"
    ),
    GUJARATI(
        code = "gu",
        displayName = "Gujarati",
        nativeName = "ગુજરાતી",
        engineType = SttEngineType.GENERIC_ONNX_CTC,
        modelAssetPath = "indic_stt/vakyansh_gujarati_base.int8.onnx",
        vocabOrTokensAssetPath = "indic_stt/gujarati_vocab.json",
        expectedSha256 = "bc64cb802a7dc162f38f3cec4d6a09536d2820ca87c50af370ff3d18d07bd71a"
    ),
    MARATHI(
        code = "mr",
        displayName = "Marathi",
        nativeName = "मराठी",
        engineType = SttEngineType.GENERIC_ONNX_CTC,
        modelAssetPath = "indic_stt/vakyansh_marathi_base.int8.onnx",
        vocabOrTokensAssetPath = "indic_stt/marathi_vocab.json",
        expectedSha256 = ""
    ),
    KANNADA(
        code = "kn",
        displayName = "Kannada",
        nativeName = "ಕನ್ನಡ",
        engineType = SttEngineType.GENERIC_ONNX_CTC,
        modelAssetPath = "indic_stt/vakyansh_kannada_base.int8.onnx",
        vocabOrTokensAssetPath = "indic_stt/kannada_vocab.json",
        expectedSha256 = "9769b09b6c24d67acebc50f4436d1756f4a4b3ee3edec9c34099faa904f9f5a8"
    ),
    MALAYALAM(
        code = "ml",
        displayName = "Malayalam",
        nativeName = "മലയാളം",
        engineType = SttEngineType.GENERIC_ONNX_CTC,
        modelAssetPath = "indic_stt/vakyansh_malayalam_base.int8.onnx",
        vocabOrTokensAssetPath = "indic_stt/malayalam_vocab.json",
        expectedSha256 = ""
    ),
    TAMIL(
        code = "ta",
        displayName = "Tamil",
        nativeName = "தமிழ்",
        engineType = SttEngineType.GENERIC_ONNX_CTC,
        modelAssetPath = "indic_stt/vakyansh_tamil_base.int8.onnx",
        vocabOrTokensAssetPath = "indic_stt/tamil_vocab.json",
        expectedSha256 = ""
    ),
    TELUGU(
        code = "te",
        displayName = "Telugu",
        nativeName = "తెలుగు",
        engineType = SttEngineType.GENERIC_ONNX_CTC,
        modelAssetPath = "indic_stt/vakyansh_telugu_base.int8.onnx",
        vocabOrTokensAssetPath = "indic_stt/telugu_vocab.json",
        expectedSha256 = "c64bab6c69e7965d512c3b6d70fcf5f8e4f2f6e52e6c06307612c489bfd964bf"
    ),
    ODIA(
        code = "or",
        displayName = "Odia",
        nativeName = "ଓଡ଼ିଆ",
        engineType = SttEngineType.GENERIC_ONNX_CTC,
        modelAssetPath = "indic_stt/vakyansh_odia_base.int8.onnx",
        vocabOrTokensAssetPath = "indic_stt/odia_vocab.json",
        expectedSha256 = ""
    ),
    BENGALI(
        code = "bn",
        displayName = "Bengali",
        nativeName = "বাংলা",
        engineType = SttEngineType.GENERIC_ONNX_CTC,
        modelAssetPath = "indic_stt/vakyansh_bengali_base.int8.onnx",
        vocabOrTokensAssetPath = "indic_stt/bengali_vocab.json",
        expectedSha256 = ""
    );

    override fun toString(): String = "$displayName ($nativeName)"

    companion object {
        fun fromCode(code: String): SupportedLanguage {
            return fromCodeOrNull(code) ?: ENGLISH
        }

        fun fromCodeOrNull(code: String): SupportedLanguage? {
            return entries.find { it.code.equals(code.trim(), ignoreCase = true) }
        }
    }
}
