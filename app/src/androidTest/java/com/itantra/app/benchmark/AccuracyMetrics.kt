package com.itantra.app.benchmark

import java.util.Locale
import kotlin.math.min

/**
 * Utility for computing Word Error Rate (WER), Character Error Rate (CER),
 * sentence accuracy, and native script validity during on-device benchmarking.
 */
object AccuracyMetrics {

    /**
     * Computes Levenshtein distance between two sequences.
     */
    fun <T> levenshteinDistance(a: List<T>, b: List<T>): Int {
        val dp = Array(a.size + 1) { IntArray(b.size + 1) }

        for (i in 0..a.size) dp[i][0] = i
        for (j in 0..b.size) dp[0][j] = j

        for (i in 1..a.size) {
            for (j in 1..b.size) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = min(
                    dp[i - 1][j] + 1, // deletion
                    min(
                        dp[i][j - 1] + 1, // insertion
                        dp[i - 1][j - 1] + cost // substitution
                    )
                )
            }
        }
        return dp[a.size][b.size]
    }

    /**
     * Computes Word Error Rate (WER): Levenshtein(words_ref, words_hyp) / len(words_ref).
     */
    fun calculateWer(reference: String, hypothesis: String): Double {
        val refWords = reference.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val hypWords = hypothesis.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }

        if (refWords.isEmpty()) {
            return if (hypWords.isEmpty()) 0.0 else 1.0
        }

        val dist = levenshteinDistance(refWords, hypWords)
        return dist.toDouble() / refWords.size.toDouble()
    }

    /**
     * Computes Character Error Rate (CER): Levenshtein(chars_ref, chars_hyp) / len(chars_ref).
     * Ignores whitespace differences.
     */
    fun calculateCer(reference: String, hypothesis: String): Double {
        val refChars = reference.replace(Regex("\\s+"), "").map { it.toString() }
        val hypChars = hypothesis.replace(Regex("\\s+"), "").map { it.toString() }

        if (refChars.isEmpty()) {
            return if (hypChars.isEmpty()) 0.0 else 1.0
        }

        val dist = levenshteinDistance(refChars, hypChars)
        return dist.toDouble() / refChars.size.toDouble()
    }

    /**
     * Verifies that the hypothesis output is written in the expected native script for the language.
     */
    fun isNativeScript(text: String, languageCode: String): Boolean {
        if (text.isBlank()) return false
        val clean = text.replace(Regex("[\\s\\p{Punct}\\d]"), "")
        if (clean.isEmpty()) return true

        var matchedChars = 0
        for (ch in clean) {
            val codePoint = ch.code
            val isMatch = when (languageCode.lowercase(Locale.US)) {
                "en" -> codePoint in 0x0041..0x007A
                "hi", "mr" -> codePoint in 0x0900..0x097F
                "bn" -> codePoint in 0x0980..0x09FF
                "gu" -> codePoint in 0x0A80..0x0AFF
                "or" -> codePoint in 0x0B00..0x0B7F
                "ta" -> codePoint in 0x0B80..0x0BFF
                "te" -> codePoint in 0x0C00..0x0C7F
                "kn" -> codePoint in 0x0C80..0x0CFF
                "ml" -> codePoint in 0x0D00..0x0D7F
                else -> false
            }
            if (isMatch) matchedChars++
        }
        return (matchedChars.toDouble() / clean.length.toDouble()) >= 0.70
    }
}
