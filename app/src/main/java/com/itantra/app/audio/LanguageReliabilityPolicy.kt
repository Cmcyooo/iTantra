package com.itantra.app.audio

/**
 * Reliability classification tier for language identification based on empirical benchmarks.
 */
enum class ReliabilityTier {
    /**
     * Highly reliable benchmarked languages (Top-1 accuracy >= 70% in physical device testing).
     * Model can be authorized to automatically switch STT model without operator confirmation.
     */
    AUTO_ACCEPT,

    /**
     * Weak or phonetically ambiguous languages (Top-1 accuracy < 70% in physical device testing).
     * Model predictions are advisory; explicit operator confirmation is mandatory before STT load.
     */
    CONFIRM_REQUIRED
}

/**
 * The routing decision resulting from LID confidence and language reliability policy.
 */
enum class RoutingDecision {
    /** High confidence and benchmark-validated reliable language. Route automatically. */
    AUTO_ACCEPT,

    /** Ambiguous detection or weak/unvalidated language. Operator confirmation required. */
    CONFIRM_REQUIRED,

    /** Low confidence (< 0.60), no speech, or detection failure. Safe manual fallback. */
    MANUAL_FALLBACK
}

/**
 * Origin source of the current session language for diagnostic tracking.
 */
enum class SessionLanguageSource {
    /** Automatically detected with high confidence and approved by AUTO_ACCEPT policy. */
    AUTO_DETECTED,

    /** Weak or ambiguous language explicitly confirmed by the operator. */
    USER_CONFIRMED,

    /** Explicitly chosen by the operator via manual language selector. */
    MANUAL_SELECTED
}

/**
 * Full state descriptor of the active session language.
 */
data class SessionLanguageState(
    val language: SupportedLanguage,
    val confidence: Float,
    val source: SessionLanguageSource
)

/**
 * Centralized, configurable policy governing language routing safety.
 *
 * Grounded in empirical Phase 10E hardware benchmarks:
 * - AUTO_ACCEPT: en (100%), hi (100%), ta (80%), bn (80%), te (70%)
 * - CONFIRM_REQUIRED: ml (40%), mr (30%), gu (30%), kn (20%), or (0%)
 *
 * Configurable so future acoustic model improvements can promote languages to AUTO_ACCEPT.
 */
class LanguageReliabilityPolicy(
    initialPolicies: Map<String, ReliabilityTier>? = null
) {
    private val policyMap = mutableMapOf<String, ReliabilityTier>()

    init {
        // Default classifications grounded in Phase 10E real-device benchmark measurements
        val defaults = mapOf(
            "en" to ReliabilityTier.AUTO_ACCEPT,
            "hi" to ReliabilityTier.AUTO_ACCEPT,
            "ta" to ReliabilityTier.AUTO_ACCEPT,
            "bn" to ReliabilityTier.AUTO_ACCEPT,
            "te" to ReliabilityTier.AUTO_ACCEPT,
            "ml" to ReliabilityTier.CONFIRM_REQUIRED,
            "mr" to ReliabilityTier.CONFIRM_REQUIRED,
            "gu" to ReliabilityTier.CONFIRM_REQUIRED,
            "kn" to ReliabilityTier.CONFIRM_REQUIRED,
            "or" to ReliabilityTier.CONFIRM_REQUIRED
        )
        policyMap.putAll(defaults)
        if (initialPolicies != null) {
            policyMap.putAll(initialPolicies)
        }
    }

    /**
     * Returns the reliability tier for a given language code.
     */
    @Synchronized
    fun getTier(langCode: String): ReliabilityTier {
        return policyMap[langCode.lowercase()] ?: ReliabilityTier.CONFIRM_REQUIRED
    }

    /**
     * Promotes or demotes a language dynamically (e.g. following updated benchmark results).
     */
    @Synchronized
    fun setTier(langCode: String, tier: ReliabilityTier) {
        policyMap[langCode.lowercase()] = tier
    }

    /**
     * Evaluates the routing decision based on detection status, confidence, and reliability tier.
     *
     * Core Rule:
     * High confidence (>= 0.80) does NOT automatically authorize a CONFIRM_REQUIRED language.
     * Wrong silent routing must be 0%.
     */
    fun evaluateRouting(result: LanguageDetectionResult): RoutingDecision {
        if (result.status == DetectionStatus.NO_SPEECH ||
            result.status == DetectionStatus.ERROR ||
            result.language.isNullOrBlank()
        ) {
            return RoutingDecision.MANUAL_FALLBACK
        }

        val lang = result.language
        val conf = result.confidence

        return when {
            conf < 0.60f -> RoutingDecision.MANUAL_FALLBACK
            conf < 0.80f -> RoutingDecision.CONFIRM_REQUIRED
            else -> {
                // High confidence (>= 0.80)
                val tier = getTier(lang)
                if (tier == ReliabilityTier.AUTO_ACCEPT) {
                    RoutingDecision.AUTO_ACCEPT
                } else {
                    RoutingDecision.CONFIRM_REQUIRED
                }
            }
        }
    }
}
