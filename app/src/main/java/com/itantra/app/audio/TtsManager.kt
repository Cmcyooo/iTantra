package com.itantra.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.k2fsa.sherpa.onnx.*
import java.io.File
import java.io.FileOutputStream
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Result of a TTS synthesis session.
 */
data class TtsResult(
    val audioDuration: Double,
    val synthesisTimeMs: Long,
    val firstAudioLatencyMs: Long,
    val rtf: Double
)

/**
 * Possible status of the TTS engine.
 */
enum class TtsStatus {
    IDLE,
    LOADING,
    SYNTHESIZING,
    PLAYING,
    COMPLETE,
    ERROR
}

/**
 * Manages offline TTS using sherpa-onnx and VITS (Piper) model.
 * Handles model asset extraction, synthesis, and playback via AudioTrack.
 */
class TtsManager(private val context: Context) {
    private var tts: OfflineTts? = null
    private var audioTrack: AudioTrack? = null
    
    private val _status = MutableStateFlow(TtsStatus.IDLE)
    val status: StateFlow<TtsStatus> = _status.asStateFlow()

    private val _lastResult = MutableStateFlow<TtsResult?>(null)
    val lastResult: StateFlow<TtsResult?> = _lastResult.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val TAG = "TtsManager"
        private const val MODEL_DIR = "tts-en-amy"
    }

    init {
        scope.launch {
            _status.value = TtsStatus.LOADING
            try {
                // Extract assets to internal storage
                val modelFile = copyAssetToFiles(context, "$MODEL_DIR/model.onnx")
                val tokensFile = copyAssetToFiles(context, "$MODEL_DIR/tokens.txt")
                val dataDir = extractAssetDir(context, "$MODEL_DIR/espeak-ng-data")

                val vitsConfig = OfflineTtsVitsModelConfig(
                    model = modelFile.absolutePath,
                    tokens = tokensFile.absolutePath,
                    dataDir = dataDir.absolutePath,
                    noiseScale = 0.667f,
                    noiseScaleW = 0.8f,
                    lengthScale = 1.0f
                )

                val modelConfig = OfflineTtsModelConfig(
                    vits = vitsConfig,
                    numThreads = 2, // Increased from 1 to 2
                    debug = false,
                    provider = "cpu"
                )

                val config = OfflineTtsConfig(
                    model = modelConfig
                )

                // Initialize TTS with null assetManager because we provide absolute paths
                tts = OfflineTts(null, config)
                _status.value = TtsStatus.IDLE
                Log.i(TAG, "Offline TTS initialized successfully.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize Offline TTS", e)
                _status.value = TtsStatus.ERROR
            }
        }
    }

    /**
     * Synthesizes the provided text and plays it.
     */
    fun speak(text: String) {
        if (text.isBlank()) return
        val ttsEngine = tts ?: run {
            Log.w(TAG, "TTS engine not initialized.")
            return
        }

        scope.launch {
            // Stop current playback if any
            stopPlaybackInternal()
            
            _status.value = TtsStatus.SYNTHESIZING
            try {
                var generatedAudio: GeneratedAudio? = null
                val synthesisTimeMs = measureTimeMillis {
                    generatedAudio = ttsEngine.generate(text)
                }

                val audio = generatedAudio
                if (audio == null || audio.samples.isEmpty()) {
                    Log.e(TAG, "Synthesis failed or produced no samples.")
                    _status.value = TtsStatus.ERROR
                    return@launch
                }

                val audioDuration = audio.samples.size.toDouble() / audio.sampleRate
                val rtf = if (audioDuration > 0) (synthesisTimeMs / 1000.0) / audioDuration else 0.0

                _lastResult.value = TtsResult(
                    audioDuration = audioDuration,
                    synthesisTimeMs = synthesisTimeMs,
                    firstAudioLatencyMs = synthesisTimeMs, // In non-streaming, it's the same
                    rtf = rtf
                )

                Log.d(TAG, "Synthesis complete. Duration: ${"%.2f".format(audioDuration)}s, Time: ${synthesisTimeMs}ms, RTF: ${"%.3f".format(rtf)}")

                playAudio(audio.samples, audio.sampleRate)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error during speech synthesis", e)
                _status.value = TtsStatus.ERROR
            }
        }
    }

    /**
     * Stops current playback.
     */
    fun stop() {
        scope.launch {
            stopPlaybackInternal()
            _status.value = TtsStatus.IDLE
        }
    }

    private fun stopPlaybackInternal() {
        try {
            audioTrack?.apply {
                if (playState == AudioTrack.PLAYSTATE_PLAYING) {
                    pause()
                    flush()
                }
                stop()
                release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping playback", e)
        } finally {
            audioTrack = null
        }
    }

    private fun playAudio(samples: FloatArray, sampleRate: Int) {
        _status.value = TtsStatus.PLAYING
        
        try {
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(samples.size * 4) // Float is 4 bytes
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            audioTrack = track

            track.apply {
                write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
                setNotificationMarkerPosition(samples.size)
                setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                    override fun onMarkerReached(track: AudioTrack?) {
                        Log.d(TAG, "Playback reached marker.")
                        _status.value = TtsStatus.COMPLETE
                        scope.launch {
                            delay(1000)
                            if (_status.value == TtsStatus.COMPLETE) {
                                _status.value = TtsStatus.IDLE
                            }
                        }
                        stopPlaybackInternal()
                    }
                    override fun onPeriodicNotification(track: AudioTrack?) {}
                })
                play()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Playback error", e)
            _status.value = TtsStatus.ERROR
        }
    }

    fun release() {
        stop()
        tts?.release()
        tts = null
        scope.cancel()
    }

    private fun copyAssetToFiles(context: Context, assetPath: String): File {
        val outFile = File(context.filesDir, assetPath)
        if (outFile.exists() && outFile.length() > 0) {
            return outFile
        }
        
        outFile.parentFile?.mkdirs()
        context.assets.open(assetPath).use { input ->
            FileOutputStream(outFile).use { output ->
                input.copyTo(output)
            }
        }
        return outFile
    }

    private fun extractAssetDir(context: Context, assetDir: String): File {
        val targetDir = File(context.filesDir, assetDir)
        if (targetDir.exists()) return targetDir

        targetDir.mkdirs()
        copyDir(context, assetDir, targetDir)
        return targetDir
    }

    private fun copyDir(context: Context, assetPath: String, targetDir: File) {
        val assets = context.assets.list(assetPath) ?: return
        if (assets.isEmpty()) {
            // It's a file
            copyAssetToFiles(context, assetPath)
        } else {
            // It's a directory
            for (asset in assets) {
                val nextAssetPath = if (assetPath.isEmpty()) asset else "$assetPath/$asset"
                val nextTargetDir = File(targetDir, asset)
                if (isAssetDir(context, nextAssetPath)) {
                    nextTargetDir.mkdirs()
                    copyDir(context, nextAssetPath, nextTargetDir)
                } else {
                    copyAssetToFiles(context, nextAssetPath)
                }
            }
        }
    }

    private fun isAssetDir(context: Context, assetPath: String): Boolean {
        return context.assets.list(assetPath)?.isNotEmpty() ?: false
    }
}
