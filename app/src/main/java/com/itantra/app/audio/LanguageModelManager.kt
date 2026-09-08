package com.itantra.app.audio

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Temporary container holding an audio utterance pending operator confirmation.
 * Allows zero-repeat confirmation: the operator does not have to speak again.
 */
data class PendingConfirmation(
    val language: SupportedLanguage,
    val confidence: Float,
    val audioSamples: FloatArray,
    val utteranceId: String? = null,
    val timestampMs: Long = System.currentTimeMillis()
)

/**
 * Central manager for multilingual Speech-to-Text models and Auto-LID in iTantra.
 *
 * Enforces the Single-Active Model Policy:
 * Only ONE STT model is resident in memory at any given time.
 * Releases previous model sessions before allocating native memory for the next language.
 *
 * Incorporates Phase 10E.1 Confidence-Aware Safety Routing:
 * speech -> LID -> confidence -> LanguageReliabilityPolicy -> RoutingDecision:
 * - AUTO_ACCEPT: High-confidence benchmark-validated language. Immediate transcription.
 * - CONFIRM_REQUIRED: Weak/ambiguous language. Retains original audio, prompts operator.
 * - MANUAL_FALLBACK: Low confidence (< 0.60), error, or silence. Safe baseline fallback.
 */
class LanguageModelManager(
    private val context: Context,
    val languageIdentifier: LanguageIdentifier = SherpaSpokenLanguageIdentifier(LanguageDetectionConfig()),
    val detectionConfig: LanguageDetectionConfig = LanguageDetectionConfig(),
    val reliabilityPolicy: LanguageReliabilityPolicy = LanguageReliabilityPolicy()
) {

    companion object {
        private const val TAG = "LanguageModelManager"

        @Volatile
        private var instance: LanguageModelManager? = null

        fun getInstance(context: Context): LanguageModelManager {
            return instance ?: synchronized(this) {
                instance ?: LanguageModelManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mutex = Mutex()

    private val _currentLanguage = MutableStateFlow(SupportedLanguage.ENGLISH)
    val currentLanguage: StateFlow<SupportedLanguage> = _currentLanguage.asStateFlow()

    private val _lifecycleState = MutableStateFlow(ModelLifecycleState.UNLOADED)
    val lifecycleState: StateFlow<ModelLifecycleState> = _lifecycleState.asStateFlow()

    // Mode control: AUTO vs MANUAL (Default AUTO per Phase 10E)
    private val _languageMode = MutableStateFlow(LanguageMode.AUTO)
    val languageMode: StateFlow<LanguageMode> = _languageMode.asStateFlow()

    // Active session language cache for AUTO mode
    private val _sessionLanguage = MutableStateFlow<SupportedLanguage?>(null)
    val sessionLanguage: StateFlow<SupportedLanguage?> = _sessionLanguage.asStateFlow()

    // Phase 10E.1: Explicit session language state with origin source
    private val _sessionLanguageState = MutableStateFlow<SessionLanguageState?>(null)
    val sessionLanguageState: StateFlow<SessionLanguageState?> = _sessionLanguageState.asStateFlow()

    // Pending confirmation state for weak/ambiguous language detections
    private val _pendingConfirmation = MutableStateFlow<PendingConfirmation?>(null)
    val pendingConfirmation: StateFlow<PendingConfirmation?> = _pendingConfirmation.asStateFlow()

    // Latest detection result for observability and UI feedback
    private val _lastDetectionResult = MutableStateFlow<LanguageDetectionResult?>(null)
    val lastDetectionResult: StateFlow<LanguageDetectionResult?> = _lastDetectionResult.asStateFlow()

    // Debouncing & re-verification tracking
    private var utteranceCountInSession: Int = 0
    private var pendingSwitchCandidate: SupportedLanguage? = null
    private var consecutiveCandidateCount: Int = 0
    private var forceReverificationOnNextUtterance: Boolean = false

    private var activeEngine: SttEngine? = null

    init {
        // Automatically initialize the default English model and preload LID in background
        scope.launch {
            setLanguage(SupportedLanguage.ENGLISH)
        }
        scope.launch {
            languageIdentifier.initialize(context)
        }
    }

    /**
     * Updates the language selection mode (AUTO or MANUAL).
     */
    fun setLanguageMode(mode: LanguageMode) {
        if (_languageMode.value != mode) {
            Log.i(TAG, "[LID-MODE] Switching language mode from ${_languageMode.value} to $mode")
            _languageMode.value = mode
            if (mode == LanguageMode.AUTO) {
                // When switching to AUTO, schedule re-verification on next utterance
                forceReverificationOnNextUtterance = true
                pendingSwitchCandidate = null
                consecutiveCandidateCount = 0
            }
        }
    }

    /**
     * Manually triggers language re-verification on the next utterance or via UI button.
     */
    fun triggerLanguageReverification() {
        Log.i(TAG, "[LID-REVERIFY] User or system requested language re-verification.")
        forceReverificationOnNextUtterance = true
    }

    /**
     * Resets the active conversation session.
     * Clears session language, confirmation state, debouncing counters, and verification counts.
     */
    fun resetSession() {
        Log.i(TAG, "[LID-RESET] Resetting session language state, debouncing counters, and clearing pending confirmations.")
        _sessionLanguageState.value = null
        _sessionLanguage.value = null
        _pendingConfirmation.value = null
        pendingSwitchCandidate = null
        consecutiveCandidateCount = 0
        utteranceCountInSession = 0
        forceReverificationOnNextUtterance = true
    }

    /**
     * Resolves the target language and safety routing decision for an utterance according to Phase 10E.1 rules.
     *
     * 1. If MANUAL: return AUTO_ACCEPT with manual language (LID skipped).
     * 2. If AUTO and session language is valid: reuse session language unless re-verification is due.
     * 3. If AUTO and no session language (or re-verification due):
     *    run LID -> evaluate LanguageReliabilityPolicy:
     *    - AUTO_ACCEPT: route automatically (with consecutive-switch debouncing for switches).
     *    - CONFIRM_REQUIRED: store pending audio utterance, pause STT, await operator decision.
     *    - MANUAL_FALLBACK: safe fallback baseline.
     */
    suspend fun resolveLanguageWithRouting(
        samples: FloatArray,
        utteranceId: String? = null
    ): Pair<RoutingDecision, SupportedLanguage> {
        if (_languageMode.value == LanguageMode.MANUAL) {
            val manual = _currentLanguage.value
            _sessionLanguageState.value = SessionLanguageState(manual, 1.0f, SessionLanguageSource.MANUAL_SELECTED)
            _sessionLanguage.value = manual
            Log.i(TAG, "[LID-MANUAL] Manual mode active. Using language=${manual.code}")
            return Pair(RoutingDecision.AUTO_ACCEPT, manual)
        }

        utteranceCountInSession++
        val cachedState = _sessionLanguageState.value
        val isFirstUtterance = (cachedState == null)
        val isIntervalDue = (utteranceCountInSession % detectionConfig.reverificationInterval == 0)
        val shouldRunLid = isFirstUtterance || isIntervalDue || forceReverificationOnNextUtterance

        // Session cache fast path: ONLY if re-verification is not due and valid session lock exists
        if (!shouldRunLid && cachedState != null) {
            val cachedLang = cachedState.language
            Log.i(
                TAG,
                "[LID-CACHED] language=${cachedLang.code} source=${cachedState.source} confidence=${String.format(java.util.Locale.US, "%.2f", cachedState.confidence)} (utterance #$utteranceCountInSession)"
            )
            if (_currentLanguage.value != cachedLang) {
                setLanguage(cachedLang)
            }
            return Pair(RoutingDecision.AUTO_ACCEPT, cachedLang)
        }

        forceReverificationOnNextUtterance = false

        // Ensure LID engine is ready
        if (!languageIdentifier.isReady) {
            languageIdentifier.initialize(context)
        }

        val detResult = languageIdentifier.identifyLanguage(samples)
        val routingDecision = reliabilityPolicy.evaluateRouting(detResult)
        val enrichedResult = detResult.copy(routingDecision = routingDecision)
        _lastDetectionResult.value = enrichedResult

        val detectedCode = enrichedResult.language ?: ""
        val detectedLang = SupportedLanguage.fromCodeOrNull(detectedCode)
        val tier = reliabilityPolicy.getTier(detectedCode)

        Log.i(
            TAG,
            "[LID-RESULT] language=$detectedCode confidence=${String.format(java.util.Locale.US, "%.2f", enrichedResult.confidence)} policy=$tier decision=$routingDecision sessionLanguage=${cachedState?.language?.code} sessionSource=${cachedState?.source}"
        )

        return when (routingDecision) {
            RoutingDecision.AUTO_ACCEPT -> {
                if (detectedLang == null) {
                    val fallback = cachedState?.language ?: _currentLanguage.value
                    Pair(RoutingDecision.MANUAL_FALLBACK, fallback)
                } else if (cachedState == null) {
                    // First utterance in session: establish session lock
                    Log.i(TAG, "[LID-AUTO-ACCEPT] Establishing session language=${detectedLang.code} confidence=${enrichedResult.confidence}")
                    _sessionLanguageState.value = SessionLanguageState(detectedLang, enrichedResult.confidence, SessionLanguageSource.AUTO_DETECTED)
                    _sessionLanguage.value = detectedLang
                    pendingSwitchCandidate = null
                    consecutiveCandidateCount = 0
                    if (_currentLanguage.value != detectedLang) {
                        setLanguage(detectedLang)
                    }
                    Pair(RoutingDecision.AUTO_ACCEPT, detectedLang)
                } else if (cachedState.language == detectedLang) {
                    // Re-confirmed existing session language
                    pendingSwitchCandidate = null
                    consecutiveCandidateCount = 0
                    Pair(RoutingDecision.AUTO_ACCEPT, detectedLang)
                } else {
                    // Consecutive switch debouncing: requires 2 consecutive high-confidence detections
                    if (pendingSwitchCandidate == detectedLang) {
                        consecutiveCandidateCount++
                    } else {
                        pendingSwitchCandidate = detectedLang
                        consecutiveCandidateCount = 1
                    }

                    if (consecutiveCandidateCount >= detectionConfig.consecutiveSwitchesRequired) {
                        Log.i(TAG, "[LID-SWITCH] from=${cachedState.language.code} to=${detectedLang.code} (confirmed after $consecutiveCandidateCount consecutive detections)")
                        _sessionLanguageState.value = SessionLanguageState(detectedLang, enrichedResult.confidence, SessionLanguageSource.AUTO_DETECTED)
                        _sessionLanguage.value = detectedLang
                        pendingSwitchCandidate = null
                        consecutiveCandidateCount = 0
                        if (_currentLanguage.value != detectedLang) {
                            setLanguage(detectedLang)
                        }
                        Pair(RoutingDecision.AUTO_ACCEPT, detectedLang)
                    } else {
                        Log.i(TAG, "[LID-DEBOUNCE] Candidate ${detectedLang.code} count $consecutiveCandidateCount/${detectionConfig.consecutiveSwitchesRequired}, preserving current session language ${cachedState.language.code}")
                        Pair(RoutingDecision.AUTO_ACCEPT, cachedState.language)
                    }
                }
            }
            RoutingDecision.CONFIRM_REQUIRED -> {
                val targetLang = detectedLang ?: (cachedState?.language ?: _currentLanguage.value)
                Log.w(
                    TAG,
                    "[LID-CONFIRM-REQUIRED] Target language ${targetLang.displayName} (${targetLang.code}) requires operator confirmation. Pausing STT and retaining ${samples.size} audio samples."
                )
                // Retain finalized PCM utterance in memory for zero-repeat confirmation!
                _pendingConfirmation.value = PendingConfirmation(
                    language = targetLang,
                    confidence = enrichedResult.confidence,
                    audioSamples = samples,
                    utteranceId = utteranceId
                )
                // Do NOT establish session lock until confirmed!
                Pair(RoutingDecision.CONFIRM_REQUIRED, cachedState?.language ?: targetLang)
            }
            RoutingDecision.MANUAL_FALLBACK -> {
                val fallback = cachedState?.language ?: _currentLanguage.value
                Log.w(TAG, "[LID-FALLBACK] Low confidence or failure (status=${enrichedResult.status}). Retaining language: ${fallback.code}")
                Pair(RoutingDecision.MANUAL_FALLBACK, fallback)
            }
        }
    }

    /**
     * Backward-compatible convenience method.
     */
    suspend fun resolveLanguage(samples: FloatArray): SupportedLanguage {
        val (_, resolved) = resolveLanguageWithRouting(samples)
        return resolved
    }

    /**
     * Confirms the pending language detection.
     * Establishes a USER_CONFIRMED session lock, switches STT, transcribes the retained audio,
     * and clears the pending confirmation.
     */
    suspend fun confirmPendingLanguage(
        confirmedLanguage: SupportedLanguage? = null
    ): Pair<PendingConfirmation, SttResult?>? {
        val pending = _pendingConfirmation.value ?: return null
        val targetLang = confirmedLanguage ?: pending.language

        Log.i(TAG, "[LID-CONFIRMED] language=${targetLang.code} source=USER_CONFIRMED")
        _sessionLanguageState.value = SessionLanguageState(
            language = targetLang,
            confidence = pending.confidence,
            source = SessionLanguageSource.USER_CONFIRMED
        )
        _sessionLanguage.value = targetLang
        pendingSwitchCandidate = null
        consecutiveCandidateCount = 0

        if (_currentLanguage.value != targetLang) {
            setLanguage(targetLang)
        }

        // Transcribe the retained original audio without making the user speak again!
        val sttResult = transcribe(pending.audioSamples)
        _pendingConfirmation.value = null

        return Pair(pending, sttResult)
    }

    /**
     * Rejects the pending language detection and applies an explicitly selected manual language.
     * Establishes a MANUAL_SELECTED session lock, switches STT, transcribes the retained audio,
     * and clears the pending confirmation.
     */
    suspend fun rejectPendingLanguageAndSelect(
        selectedLanguage: SupportedLanguage
    ): Pair<PendingConfirmation, SttResult?>? {
        val pending = _pendingConfirmation.value ?: return null

        Log.i(TAG, "[LID-REJECT-SELECT] Operator selected ${selectedLanguage.code} over detected ${pending.language.code}")
        _sessionLanguageState.value = SessionLanguageState(
            language = selectedLanguage,
            confidence = 1.0f,
            source = SessionLanguageSource.MANUAL_SELECTED
        )
        _sessionLanguage.value = selectedLanguage
        pendingSwitchCandidate = null
        consecutiveCandidateCount = 0

        if (_currentLanguage.value != selectedLanguage) {
            setLanguage(selectedLanguage)
        }

        // Transcribe the retained original audio!
        val sttResult = transcribe(pending.audioSamples)
        _pendingConfirmation.value = null

        return Pair(pending, sttResult)
    }

    /**
     * Dismisses any active confirmation request without transcription.
     */
    fun dismissPendingConfirmation() {
        _pendingConfirmation.value = null
    }

    /**
     * Switches the active language model.
     * Guaranteed to release previous native resources before allocating new ones.
     * Preserves the Single-Active Model Policy.
     */
    suspend fun setLanguage(targetLanguage: SupportedLanguage): Result<Unit> = mutex.withLock {
        if (_currentLanguage.value == targetLanguage && activeEngine?.isReady == true) {
            Log.d(TAG, "Language $targetLanguage is already active and ready.")
            return Result.success(Unit)
        }

        Log.i(TAG, "Switching language from ${_currentLanguage.value.displayName} to ${targetLanguage.displayName}...")

        // 1. Release previous engine and free native session
        _lifecycleState.value = ModelLifecycleState.RELEASING
        try {
            activeEngine?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing active STT engine", e)
        }
        activeEngine = null

        // 2. Check if Language Pack is installed
        val packManager = LanguagePackManager.getInstance(context)
        if (!packManager.isPackInstalled(targetLanguage)) {
            val errMsg = "Language pack for ${targetLanguage.displayName} (${targetLanguage.nativeName}) is not installed. Download or import it from Language Packs."
            Log.e(TAG, errMsg)
            _lifecycleState.value = ModelLifecycleState.FAILED
            return Result.failure(IllegalStateException(errMsg))
        }

        // 3. Instantiate and initialize new engine
        _lifecycleState.value = ModelLifecycleState.LOADING
        return try {
            val engine = if (targetLanguage == SupportedLanguage.ENGLISH) {
                SherpaOnnxSttEngine(targetLanguage)
            } else {
                GenericOnnxCtcSttEngine(targetLanguage)
            }

            val initResult = engine.initialize(context)
            if (initResult.isSuccess) {
                activeEngine = engine
                _currentLanguage.value = targetLanguage
                _lifecycleState.value = ModelLifecycleState.READY
                Log.i(TAG, "Successfully loaded STT model for ${targetLanguage.displayName}")
                Result.success(Unit)
            } else {
                _lifecycleState.value = ModelLifecycleState.FAILED
                Log.e(TAG, "Failed to initialize STT model for ${targetLanguage.displayName}")
                Result.failure(initResult.exceptionOrNull() ?: RuntimeException("Initialization failed"))
            }
        } catch (e: Exception) {
            _lifecycleState.value = ModelLifecycleState.FAILED
            Log.e(TAG, "Exception loading model for ${targetLanguage.displayName}", e)
            Result.failure(e)
        }
    }

    /**
     * Transcribes 16 kHz Float32 PCM samples using the currently active STT model.
     */
    suspend fun transcribe(samples: FloatArray): SttResult? {
        val engine = activeEngine
        if (engine == null || !engine.isReady) {
            Log.w(TAG, "No active STT engine ready for transcription.")
            return null
        }
        return engine.transcribe(samples)
    }

    /**
     * Releases active STT and LID resources upon app termination.
     */
    fun release() {
        scope.launch {
            mutex.withLock {
                _lifecycleState.value = ModelLifecycleState.RELEASING
                try {
                    activeEngine?.release()
                    activeEngine = null
                    languageIdentifier.release()
                    _lifecycleState.value = ModelLifecycleState.UNLOADED
                } catch (e: Exception) {
                    Log.e(TAG, "Error releasing LanguageModelManager", e)
                }
            }
        }
    }
}
