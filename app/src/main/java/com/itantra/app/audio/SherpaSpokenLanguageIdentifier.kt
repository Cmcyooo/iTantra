package com.itantra.app.audio

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.SpokenLanguageIdentification
import com.k2fsa.sherpa.onnx.SpokenLanguageIdentificationConfig
import com.k2fsa.sherpa.onnx.SpokenLanguageIdentificationWhisperConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

/**
 * Sherpa-ONNX implementation of Spoken Language Identification (SLI).
 *
 * Uses the offline INT8 quantized Whisper-Tiny multilingual encoder and decoder models
 * packaged directly in app assets (assets/lid/tiny-encoder.int8.onnx and tiny-decoder.int8.onnx).
 *
 * Runs off the main thread and enforces the Single-Active Model Policy:
 * LID runs during speech endpointing to identify the target language, allowing
 * LanguageModelManager to load the matching language-specific STT model.
 */
class SherpaSpokenLanguageIdentifier(
    val config: LanguageDetectionConfig = LanguageDetectionConfig()
) : LanguageIdentifier {

    companion object {
        private const val TAG = "SherpaSpokenLID"
        private const val ASSET_ENCODER = "lid/tiny-encoder.int8.onnx"
        private const val ASSET_DECODER = "lid/tiny-decoder.int8.onnx"
        private const val SILENCE_RMS_THRESHOLD = 0.005f
    }

    private val mutex = Mutex()
    private var nativeSlid: SpokenLanguageIdentification? = null

    @Volatile
    private var _isReady: Boolean = false
    override val isReady: Boolean get() = _isReady

    override suspend fun initialize(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (_isReady && nativeSlid != null) {
                return@withContext Result.success(Unit)
            }

            try {
                Log.i(TAG, "[LID-INIT] Loading offline LID model from assets (encoder=$ASSET_ENCODER, decoder=$ASSET_DECODER)...")
                val whisperConfig = SpokenLanguageIdentificationWhisperConfig(
                    encoder = ASSET_ENCODER,
                    decoder = ASSET_DECODER,
                    tailPaddings = 0
                )

                val slidConfig = SpokenLanguageIdentificationConfig(
                    whisper = whisperConfig,
                    numThreads = 2,
                    debug = false,
                    provider = "cpu"
                )

                nativeSlid = SpokenLanguageIdentification(
                    assetManager = context.assets,
                    config = slidConfig
                )

                _isReady = true
                Log.i(TAG, "[LID-INIT] Successfully initialized SherpaSpokenLanguageIdentifier.")
                Result.success(Unit)
            } catch (t: Throwable) {
                Log.e(TAG, "[LID-INIT] Failed to initialize SpokenLanguageIdentification: ${t.message}", t)
                _isReady = false
                nativeSlid = null
                Result.failure(t)
            }
        }
    }

    override suspend fun identifyLanguage(samples: FloatArray): LanguageDetectionResult = withContext(Dispatchers.Default) {
        val t0 = System.currentTimeMillis()

        if (samples.isEmpty()) {
            return@withContext LanguageDetectionResult(
                language = null,
                confidence = 0.0f,
                probabilities = emptyMap(),
                latencyMs = System.currentTimeMillis() - t0,
                status = DetectionStatus.NO_SPEECH
            )
        }

        // 1. RMS Energy Check for silence / low-energy noise
        val rms = calculateRms(samples)
        if (rms < SILENCE_RMS_THRESHOLD) {
            Log.d(TAG, "[LID-SILENCE] Audio RMS ($rms) below threshold ($SILENCE_RMS_THRESHOLD)")
            return@withContext LanguageDetectionResult(
                language = null,
                confidence = 0.0f,
                probabilities = emptyMap(),
                latencyMs = System.currentTimeMillis() - t0,
                status = DetectionStatus.NO_SPEECH
            )
        }

        val slid = nativeSlid
        if (!_isReady || slid == null) {
            Log.e(TAG, "[LID-ERROR] Engine not initialized or ready")
            return@withContext LanguageDetectionResult(
                language = null,
                confidence = 0.0f,
                probabilities = emptyMap(),
                latencyMs = System.currentTimeMillis() - t0,
                status = DetectionStatus.ERROR
            )
        }

        Log.i(TAG, "[LID-START] sampleCount=${samples.size} rms=${String.format(java.util.Locale.US, "%.4f", rms)}")

        try {
            // 2. Bound speech window to representative speech segment
            val maxSamples = (config.maxLidAudioDurationSeconds * config.sampleRate).toInt()
            val boundedSamples = if (samples.size > maxSamples) {
                samples.copyOfRange(0, maxSamples)
            } else {
                samples
            }

            // 3. Multi-window temporal consensus evaluation
            val sampleCount = boundedSamples.size
            val windows = mutableListOf<FloatArray>()
            windows.add(boundedSamples)

            // If audio is at least 1.0s, add sub-windows for temporal consensus
            if (sampleCount >= config.sampleRate * 1.0f) {
                val subLen = (sampleCount * 0.70f).toInt()
                windows.add(boundedSamples.copyOfRange(0, subLen))
                windows.add(boundedSamples.copyOfRange(sampleCount - subLen, sampleCount))
            }

            val votes = mutableMapOf<String, Int>()
            for (window in windows) {
                val stream = slid.createStream()
                try {
                    stream.acceptWaveform(window, config.sampleRate)
                    val rawLang = slid.compute(stream).trim().lowercase()
                    val normalizedLang = normalizeLanguageCode(rawLang)
                    if (normalizedLang != null) {
                        votes[normalizedLang] = (votes[normalizedLang] ?: 0) + 1
                    }
                } finally {
                    stream.release()
                }
            }

            val totalVotes = windows.size
            val probabilities = mutableMapOf<String, Float>()
            for ((lang, count) in votes) {
                probabilities[lang] = count.toFloat() / totalVotes
            }

            // Sort candidates by probability descending
            val sortedCandidates = probabilities.toList().sortedByDescending { it.second }
            val topCandidate = sortedCandidates.firstOrNull()
            val topLang = topCandidate?.first
            val topConfidence = topCandidate?.second ?: 0.0f

            val latencyMs = System.currentTimeMillis() - t0

            val status = when {
                topCandidate == null -> DetectionStatus.NO_SPEECH
                topConfidence >= config.confidentThreshold -> DetectionStatus.CONFIDENT
                topConfidence >= config.ambiguousThreshold -> DetectionStatus.AMBIGUOUS
                else -> DetectionStatus.AMBIGUOUS
            }

            val result = LanguageDetectionResult(
                language = topLang,
                confidence = topConfidence,
                probabilities = probabilities,
                latencyMs = latencyMs,
                status = status
            )

            Log.i(TAG, "[LID-RESULT] language=$topLang confidence=${String.format(java.util.Locale.US, "%.2f", topConfidence)} latency=${latencyMs}ms status=$status topCandidates=$probabilities")

            if (status == DetectionStatus.AMBIGUOUS) {
                Log.w(TAG, "[LID-AMBIGUOUS] language=$topLang confidence=$topConfidence candidates=$probabilities")
            }

            result
        } catch (t: Throwable) {
            val latencyMs = System.currentTimeMillis() - t0
            Log.e(TAG, "[LID-FALLBACK] Error during spoken language identification: ${t.message}", t)
            LanguageDetectionResult(
                language = null,
                confidence = 0.0f,
                probabilities = emptyMap(),
                latencyMs = latencyMs,
                status = DetectionStatus.ERROR
            )
        }
    }

    override fun release() {
        Log.i(TAG, "Releasing SherpaSpokenLanguageIdentifier...")
        _isReady = false
        try {
            nativeSlid?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing SpokenLanguageIdentification native handle", e)
        }
        nativeSlid = null
    }

    private fun calculateRms(samples: FloatArray): Float {
        if (samples.isEmpty()) return 0.0f
        var sumSquares = 0.0
        for (s in samples) {
            sumSquares += s * s
        }
        return sqrt(sumSquares / samples.size).toFloat()
    }

    /**
     * Maps raw language tags produced by Whisper to target project languages.
     * Linguistic fallbacks:
     * - Urdu ('ur') is linguistically mutual intelligible with Hindi ('hi')
     * - Assamese ('as') is closely related to Bengali ('bn')
     */
    private fun normalizeLanguageCode(rawCode: String): String? {
        return when (rawCode) {
            "en" -> "en"
            "hi", "ur" -> "hi"
            "gu" -> "gu"
            "mr" -> "mr"
            "kn" -> "kn"
            "ml" -> "ml"
            "ta" -> "ta"
            "te" -> "te"
            "bn", "as" -> "bn"
            "or" -> "or"
            else -> rawCode // Keep raw if in 99 languages
        }
    }
}
