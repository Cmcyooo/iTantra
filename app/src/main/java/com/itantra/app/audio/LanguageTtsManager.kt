package com.itantra.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.util.Log
import com.k2fsa.sherpa.onnx.GeneratedAudio
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Central manager for multilingual Text-to-Speech voices in iTantra.
 *
 * Enforces the Single-Active Voice Policy:
 * Only ONE TTS voice engine is resident in memory at any given time.
 * Releases previous native sessions and flushes AudioTrack before allocating
 * memory for the next language voice.
 */
class LanguageTtsManager(private val context: Context) {

    companion object {
        private const val TAG = "LanguageTtsManager"

        @Volatile
        private var instance: LanguageTtsManager? = null

        fun getInstance(context: Context): LanguageTtsManager {
            return instance ?: synchronized(this) {
                instance ?: LanguageTtsManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mutex = Mutex()

    private val _currentLanguage = MutableStateFlow(SupportedLanguage.ENGLISH)
    val currentLanguage: StateFlow<SupportedLanguage> = _currentLanguage.asStateFlow()

    private val _lifecycleState = MutableStateFlow(ModelLifecycleState.UNLOADED)
    val lifecycleState: StateFlow<ModelLifecycleState> = _lifecycleState.asStateFlow()

    private val _status = MutableStateFlow(TtsStatus.IDLE)
    val status: StateFlow<TtsStatus> = _status.asStateFlow()

    private val _lastResult = MutableStateFlow<TtsResult?>(null)
    val lastResult: StateFlow<TtsResult?> = _lastResult.asStateFlow()

    private var activeEngine: TtsEngine? = null

    init {
        // Automatically initialize the default English voice
        scope.launch {
            setLanguage(SupportedLanguage.ENGLISH)
        }
    }

    fun isReady(): Boolean = activeEngine?.isReady == true && _status.value == TtsStatus.IDLE

    /**
     * Switches the active TTS voice engine.
     * Guaranteed to release previous native resources before allocating new ones.
     */
    suspend fun setLanguage(targetLanguage: SupportedLanguage): Result<Unit> = mutex.withLock {
        if (_currentLanguage.value == targetLanguage && activeEngine?.isReady == true) {
            Log.d(TAG, "TTS Language $targetLanguage is already active and ready.")
            return Result.success(Unit)
        }

        Log.i(TAG, "Switching TTS language from ${_currentLanguage.value.displayName} to ${targetLanguage.displayName}...")

        // 1. Stop playback and release previous engine
        _status.value = TtsStatus.LOADING
        _lifecycleState.value = ModelLifecycleState.RELEASING
        try {
            activeEngine?.stop()
            activeEngine?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing active TTS engine", e)
        }
        activeEngine = null
        System.gc() // Reclaim native/managed heap

        // 2. Resolve voice config and prepare new engine instance
        _lifecycleState.value = ModelLifecycleState.LOADING
        val voiceConfig = TtsVoiceConfig.getConfigFor(targetLanguage)
        val newEngine = SherpaOnnxTtsEngine(voiceConfig)

        // 3. Initialize new engine
        val initResult = newEngine.initialize(context)
        if (initResult.isSuccess) {
            activeEngine = newEngine
            _currentLanguage.value = targetLanguage
            _lifecycleState.value = ModelLifecycleState.READY
            _status.value = TtsStatus.IDLE
            Log.i(TAG, "Successfully activated TTS for ${targetLanguage.displayName} (${voiceConfig.voiceTag}).")
            Result.success(Unit)
        } else {
            Log.e(TAG, "Failed to activate TTS for ${targetLanguage.displayName}: ${initResult.exceptionOrNull()?.message}")
            _lifecycleState.value = ModelLifecycleState.FAILED
            _status.value = TtsStatus.ERROR

            // Fallback to English baseline if non-English voice failed
            if (targetLanguage != SupportedLanguage.ENGLISH) {
                Log.w(TAG, "Attempting fallback to English TTS baseline...")
                val englishConfig = TtsVoiceConfig.getConfigFor(SupportedLanguage.ENGLISH)
                val fallbackEngine = SherpaOnnxTtsEngine(englishConfig)
                if (fallbackEngine.initialize(context).isSuccess) {
                    activeEngine = fallbackEngine
                    _currentLanguage.value = SupportedLanguage.ENGLISH
                    _lifecycleState.value = ModelLifecycleState.READY
                    _status.value = TtsStatus.IDLE
                }
            }
            Result.failure(initResult.exceptionOrNull() ?: RuntimeException("Unknown error loading TTS model"))
        }
    }

    /**
     * Synthesizes text to GeneratedAudio directly, optionally switching to target language.
     */
    suspend fun generateSpeech(text: String, languageCode: String? = null): GeneratedAudio? {
        if (text.isBlank()) return null

        if (!languageCode.isNullOrBlank()) {
            val targetLang = SupportedLanguage.fromCodeOrNull(languageCode)
            if (targetLang == null) {
                Log.e(TAG, "generateSpeech: Unsupported language code: $languageCode")
                return null
            }
            if (_currentLanguage.value != targetLang || activeEngine == null || !activeEngine!!.isReady) {
                setLanguage(targetLang)
            }
        } else if (activeEngine == null || !activeEngine!!.isReady) {
            val initRes = setLanguage(_currentLanguage.value)
            if (initRes.isFailure) return null
        }

        val engine = activeEngine ?: return null
        _status.value = TtsStatus.SYNTHESIZING
        val startTime = System.currentTimeMillis()

        return try {
            val audio = engine.generateSpeech(text)
            val timeMs = System.currentTimeMillis() - startTime
            if (audio != null && audio.samples.isNotEmpty()) {
                val dur = audio.samples.size.toDouble() / audio.sampleRate
                val rtf = if (dur > 0) (timeMs / 1000.0) / dur else 0.0
                _lastResult.value = TtsResult(
                    audioDuration = dur,
                    synthesisTimeMs = timeMs,
                    firstAudioLatencyMs = timeMs,
                    rtf = rtf
                )
            }
            _status.value = TtsStatus.IDLE
            audio
        } catch (e: Exception) {
            Log.e(TAG, "Error in generateSpeech for ${engine.voiceConfig.voiceTag}", e)
            _status.value = TtsStatus.ERROR
            null
        }
    }

    /**
     * Synthesizes and plays the provided text with callback, optionally switching to target language.
     */
    fun speak(text: String, languageCode: String? = null, onComplete: (() -> Unit)? = null) {
        if (text.isBlank()) {
            onComplete?.invoke()
            return
        }

        scope.launch {
            if (!languageCode.isNullOrBlank()) {
                val targetLang = SupportedLanguage.fromCodeOrNull(languageCode)
                if (targetLang == null) {
                    Log.e(TAG, "speak: Unsupported language code: $languageCode")
                    onComplete?.invoke()
                    return@launch
                }
                if (_currentLanguage.value != targetLang || activeEngine == null || !activeEngine!!.isReady) {
                    setLanguage(targetLang)
                }
            } else if (activeEngine == null || !activeEngine!!.isReady) {
                setLanguage(_currentLanguage.value)
            }
            val engine = activeEngine ?: run {
                Log.w(TAG, "speak: Engine not available.")
                onComplete?.invoke()
                return@launch
            }

            _status.value = TtsStatus.SYNTHESIZING
            val startTime = System.currentTimeMillis()
            val audio = engine.generateSpeech(text)
            val synthTimeMs = System.currentTimeMillis() - startTime

            if (audio == null || audio.samples.isEmpty()) {
                Log.e(TAG, "speak: Speech generation failed.")
                _status.value = TtsStatus.ERROR
                onComplete?.invoke()
                return@launch
            }

            val audioDur = audio.samples.size.toDouble() / audio.sampleRate
            val rtf = if (audioDur > 0) (synthTimeMs / 1000.0) / audioDur else 0.0
            _lastResult.value = TtsResult(
                audioDuration = audioDur,
                synthesisTimeMs = synthTimeMs,
                firstAudioLatencyMs = synthTimeMs,
                rtf = rtf
            )

            _status.value = TtsStatus.PLAYING
            val speechAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            engine.playAudioWithAttributes(
                samples = audio.samples,
                sampleRate = audio.sampleRate,
                attributes = speechAttributes,
                onComplete = {
                    _status.value = TtsStatus.COMPLETE
                    scope.launch {
                        delay(500)
                        if (_status.value == TtsStatus.COMPLETE) {
                            _status.value = TtsStatus.IDLE
                        }
                    }
                    onComplete?.invoke()
                }
            )
        }
    }

    /**
     * Plays audio samples with custom AudioAttributes (used by AlertPlaybackManager for USAGE_ALARM).
     */
    fun playAudioWithAttributes(
        samples: FloatArray,
        sampleRate: Int,
        attributes: AudioAttributes,
        onComplete: () -> Unit
    ) {
        val engine = activeEngine
        if (engine == null) {
            Log.w(TAG, "playAudioWithAttributes: activeEngine is null.")
            onComplete()
            return
        }

        _status.value = TtsStatus.PLAYING
        engine.playAudioWithAttributes(
            samples = samples,
            sampleRate = sampleRate,
            attributes = attributes,
            onComplete = {
                _status.value = TtsStatus.COMPLETE
                scope.launch {
                    delay(500)
                    if (_status.value == TtsStatus.COMPLETE) {
                        _status.value = TtsStatus.IDLE
                    }
                }
                onComplete()
            }
        )
    }

    /**
     * Stops current playback.
     */
    fun stop() {
        activeEngine?.stop()
        _status.value = TtsStatus.IDLE
    }

    /**
     * Releases all native TTS resources.
     */
    fun release() {
        Log.i(TAG, "Releasing LanguageTtsManager...")
        stop()
        try {
            activeEngine?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing active TTS engine", e)
        }
        activeEngine = null
        _lifecycleState.value = ModelLifecycleState.UNLOADED
        scope.cancel()
    }
}
