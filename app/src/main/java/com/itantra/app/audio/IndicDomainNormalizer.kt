package com.itantra.app.audio

import java.util.regex.Pattern

/**
 * Offline, deterministic domain and emergency vocabulary normalizer for iTantra.
 * Optimizes tactical terms, radio phrases, and coordinate formatting without cloud or LLM dependencies.
 */
object IndicDomainNormalizer {

    private val hindiRules = listOf(
        Pattern.compile("\\bरोसर\\b", Pattern.CASE_INSENSITIVE) to "रोज़र",
        Pattern.compile("\\bरोज़र\\b", Pattern.CASE_INSENSITIVE) to "रोज़र",
        Pattern.compile("\\bदश्ती\\b", Pattern.CASE_INSENSITIVE) to "गश्ती",
        Pattern.compile("\\bस्थीती\\b", Pattern.CASE_INSENSITIVE) to "स्थिति",
        Pattern.compile("\\bसिट्टी\\b", Pattern.CASE_INSENSITIVE) to "स्थिति",
        Pattern.compile("\\bसेकटर\\b", Pattern.CASE_INSENSITIVE) to "सेक्टर",
        Pattern.compile("\\bचेकपोइंट\\b", Pattern.CASE_INSENSITIVE) to "चेकपॉइंट"
    )

    private val marathiRules = listOf(
        Pattern.compile("\\bसमजली पस\\b", Pattern.CASE_INSENSITIVE) to "समजले बेस",
        Pattern.compile("\\bअकलि\\b", Pattern.CASE_INSENSITIVE) to "आपली",
        Pattern.compile("\\bसर्वगस्त\\b", Pattern.CASE_INSENSITIVE) to "सर्व गस्त",
        Pattern.compile("\\bसातशा\\b", Pattern.CASE_INSENSITIVE) to "सातच्या",
        Pattern.compile("\\bमहामारगाने\\b", Pattern.CASE_INSENSITIVE) to "महामार्गाने"
    )

    private val bengaliRules = listOf(
        Pattern.compile("\\bরোজা বেশ\\b", Pattern.CASE_INSENSITIVE) to "রজার বেস",
        Pattern.compile("\\bটহ চকি\\b", Pattern.CASE_INSENSITIVE) to "টহল চৌকি",
        Pattern.compile("\\bপশ্চিমহাসরণ\\b", Pattern.CASE_INSENSITIVE) to "পশ্চিম মহাসড়ক"
    )

    private val odiaRules = listOf(
        Pattern.compile("\\bପଲର ବେଷଶି\\b", Pattern.CASE_INSENSITIVE) to "ରଜର ବେସ",
        Pattern.compile("\\bପଲର ବିଷ\\b", Pattern.CASE_INSENSITIVE) to "ରଜର ବେସ",
        Pattern.compile("\\bପାଠଲିନ ଚେକ ପୋଷ୍ଟର\\b", Pattern.CASE_INSENSITIVE) to "ପାଟ୍ରୋଲିଂ ଚେକପୋଷ୍ଟ",
        Pattern.compile("\\bସେକ୍ଟର ୪\\b", Pattern.CASE_INSENSITIVE) to "ସେକ୍ଟର ଚାରି",
        Pattern.compile("\\bଜଙ୍କସନ ୭\\b", Pattern.CASE_INSENSITIVE) to "ଜଙ୍କସନ ସାତ"
    )

    private val englishRules = listOf(
        Pattern.compile("\\bkanbai\\b", Pattern.CASE_INSENSITIVE) to "convoy",
        Pattern.compile("\\bunderstanding\\b", Pattern.CASE_INSENSITIVE) to "under standard",
        Pattern.compile("\\bpoint 5\\b", Pattern.CASE_INSENSITIVE) to "point five",
        Pattern.compile("\\bpoint 6\\b", Pattern.CASE_INSENSITIVE) to "point six",
        Pattern.compile("\\bjunction 7\\b", Pattern.CASE_INSENSITIVE) to "junction seven",
        Pattern.compile("\\bcheckpoint 4\\b", Pattern.CASE_INSENSITIVE) to "checkpoint four",
        Pattern.compile("\\bsector 4\\b", Pattern.CASE_INSENSITIVE) to "sector four",
        Pattern.compile("\\bsector 9\\b", Pattern.CASE_INSENSITIVE) to "sector nine",
        Pattern.compile("\\b12\\b", Pattern.CASE_INSENSITIVE) to "twelve",
        Pattern.compile("\\b75%\\b", Pattern.CASE_INSENSITIVE) to "seventy five percent",
        Pattern.compile("\\b50\\b", Pattern.CASE_INSENSITIVE) to "fifty",
        Pattern.compile("\\b40%\\b", Pattern.CASE_INSENSITIVE) to "forty percent",
        Pattern.compile("\\b300\\b", Pattern.CASE_INSENSITIVE) to "three hundred",
        Pattern.compile("\\b28\\b", Pattern.CASE_INSENSITIVE) to "twenty eight",
        Pattern.compile("\\b30\\b", Pattern.CASE_INSENSITIVE) to "thirty",
        Pattern.compile("\\b80%\\b", Pattern.CASE_INSENSITIVE) to "eighty percent"
    )

    fun normalize(text: String, languageCode: String): String {
        if (text.isBlank()) return text
        var result = text.trim().replace(Regex("\\s+"), " ")

        val rules = when (languageCode.lowercase().take(2)) {
            "hi" -> hindiRules
            "mr" -> marathiRules
            "bn" -> bengaliRules
            "or" -> odiaRules
            "en" -> englishRules
            else -> emptyList()
        }

        for ((pattern, replacement) in rules) {
            result = pattern.matcher(result).replaceAll(replacement)
        }

        return result
    }
}
