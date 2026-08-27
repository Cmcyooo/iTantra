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
 * Central manager for multilingual Speech-to-Text models in iTantra.
 *
 * Enforces the Single-Active Model Policy:
 * Only ONE STT model is resident in memory at any given time.
 * Releases previous model sessions before allocating native memory for the next language.
 */
class LanguageModelManager(private val context: Context) {

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

    private var activeEngine: SttEngine? = null

    init {
        // Automatically initialize the default English model
        scope.launch {
            setLanguage(SupportedLanguage.ENGLISH)
        }
    }

    /**
     * Switches the active language model.
     * Guaranteed to release previous native resources before allocating new ones.
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
        System.gc() // Hint garbage collection to reclaim memory

        // 2. Prepare new engine instance
        _lifecycleState.value = ModelLifecycleState.LOADING
        val newEngine = when (targetLanguage.engineType) {
            SttEngineType.SHERPA_ONNX_WHISPER -> SherpaOnnxSttEngine(targetLanguage)
            SttEngineType.GENERIC_ONNX_CTC -> GenericOnnxCtcSttEngine(targetLanguage)
        }

        // 3. Initialize new engine
        val initResult = try {
            newEngine.initialize(context)
        } catch (t: Throwable) {
            Log.e(TAG, "Uncaught error during ${targetLanguage.displayName} initialize", t)
            Result.failure(t)
        }
        if (initResult.isSuccess) {
            activeEngine = newEngine
            _currentLanguage.value = targetLanguage
            _lifecycleState.value = ModelLifecycleState.READY
            Log.i(TAG, "Successfully activated ${targetLanguage.displayName} (${targetLanguage.nativeName}).")
            Result.success(Unit)
        } else {
            Log.e(TAG, "Failed to activate ${targetLanguage.displayName}: ${initResult.exceptionOrNull()?.message}")
            _lifecycleState.value = ModelLifecycleState.FAILED
            // Fallback to English baseline if non-English model failed
            if (targetLanguage != SupportedLanguage.ENGLISH) {
                Log.w(TAG, "Attempting fallback to English baseline...")
                val fallbackEngine = SherpaOnnxSttEngine(SupportedLanguage.ENGLISH)
                if (fallbackEngine.initialize(context).isSuccess) {
                    activeEngine = fallbackEngine
                    _currentLanguage.value = SupportedLanguage.ENGLISH
                    _lifecycleState.value = ModelLifecycleState.READY
                }
            }
            Result.failure(initResult.exceptionOrNull() ?: RuntimeException("Unknown error loading model"))
        }
    }

    /**
     * Transcribes 16 kHz Float32 audio samples using the currently active language model.
     */
    suspend fun transcribe(samples: FloatArray): SttResult? {
        if (samples.isEmpty()) return null

        // Lazy initialize if unloaded
        if (activeEngine == null || !activeEngine!!.isReady) {
            val initRes = setLanguage(_currentLanguage.value)
            if (initRes.isFailure) return null
        }

        val engine = activeEngine ?: return null
        _lifecycleState.value = ModelLifecycleState.TRANSCRIBING
        return try {
            val result = engine.transcribe(samples)
            _lifecycleState.value = ModelLifecycleState.READY
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error during transcription with ${engine.language.displayName}", e)
            _lifecycleState.value = ModelLifecycleState.FAILED
            null
        }
    }

    /**
     * Releases all model resources and closes native sessions.
     */
    fun release() {
        Log.i(TAG, "Releasing LanguageModelManager...")
        try {
            activeEngine?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing active engine", e)
        }
        activeEngine = null
        _lifecycleState.value = ModelLifecycleState.UNLOADED
    }
}
