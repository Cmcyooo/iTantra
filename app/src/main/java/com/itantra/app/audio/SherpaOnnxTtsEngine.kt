package com.itantra.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.k2fsa.sherpa.onnx.*
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import kotlin.system.measureTimeMillis

/**
 * Sherpa-onnx implementation of [TtsEngine] for Piper VITS and Meta MMS models.
 * Strictly manages model lifecycle, memory allocation, and AudioTrack output.
 */
class SherpaOnnxTtsEngine(
    override val voiceConfig: TtsVoiceConfig
) : TtsEngine {

    companion object {
        private const val TAG = "SherpaOnnxTtsEngine"
        private const val BASE_ASSET_DIR = "tts-en-amy"
    }

    private var tts: OfflineTts? = null
    private var audioTrack: AudioTrack? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override val isReady: Boolean
        get() = tts != null

    override suspend fun initialize(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Initializing TTS Engine for ${voiceConfig.language.displayName} (${voiceConfig.voiceTag})...")

            // 1. Resolve shared espeak-ng-data (extracted from base assets)
            val espeakDataDir = resolveEspeakDataDir(context)

            // 2. Resolve model.onnx and tokens.txt
            val (modelFile, tokensFile) = resolveVoiceFiles(context, voiceConfig.modelDirName)
            if (!modelFile.exists() || modelFile.length() == 0L) {
                return@withContext Result.failure(IllegalStateException("Model file not found: ${modelFile.absolutePath}"))
            }
            if (!tokensFile.exists() || tokensFile.length() == 0L) {
                return@withContext Result.failure(IllegalStateException("Tokens file not found: ${tokensFile.absolutePath}"))
            }

            Log.d(TAG, "Model: ${modelFile.absolutePath} (${modelFile.length() / (1024 * 1024)} MB)")
            Log.d(TAG, "Tokens: ${tokensFile.absolutePath}")

            // 3. Build VITS configuration
            val vitsConfig = OfflineTtsVitsModelConfig(
                model = modelFile.absolutePath,
                tokens = tokensFile.absolutePath,
                dataDir = if (voiceConfig.isPiper) espeakDataDir.absolutePath else "",
                noiseScale = voiceConfig.noiseScale,
                noiseScaleW = voiceConfig.noiseScaleW,
                lengthScale = voiceConfig.lengthScale
            )

            val modelConfig = OfflineTtsModelConfig(
                vits = vitsConfig,
                numThreads = 2,
                debug = false,
                provider = "cpu"
            )

            val config = OfflineTtsConfig(model = modelConfig)

            // 4. Instantiate native engine
            tts = OfflineTts(null, config)
            Log.i(TAG, "✓ Offline TTS initialized for ${voiceConfig.language.displayName} (sample rate: ${tts?.sampleRate()})")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize TTS for ${voiceConfig.language.displayName}", e)
            Result.failure(e)
        }
    }

    override suspend fun generateSpeech(text: String): GeneratedAudio? = withContext(Dispatchers.IO) {
        val engine = tts ?: run {
            Log.w(TAG, "Cannot generate speech: engine is null.")
            return@withContext null
        }
        if (text.isBlank()) return@withContext null

        try {
            var audio: GeneratedAudio? = null
            val timeMs = measureTimeMillis {
                audio = engine.generate(text, sid = voiceConfig.speakerId)
            }
            if (audio != null && audio!!.samples.isNotEmpty()) {
                val dur = audio!!.samples.size.toDouble() / audio!!.sampleRate
                val rtf = if (dur > 0) (timeMs / 1000.0) / dur else 0.0
                Log.d(TAG, "Generated speech for ${voiceConfig.voiceTag}: ${timeMs}ms, dur: ${"%.2f".format(dur)}s, RTF: ${"%.3f".format(rtf)}")
            }
            audio
        } catch (e: Exception) {
            Log.e(TAG, "Error synthesizing speech for text: '$text'", e)
            null
        }
    }

    override fun speak(text: String, onComplete: (() -> Unit)?) {
        if (text.isBlank()) {
            onComplete?.invoke()
            return
        }

        scope.launch {
            stopPlaybackInternal()
            val audio = generateSpeech(text)
            if (audio == null || audio.samples.isEmpty()) {
                Log.w(TAG, "Speech generation produced no audio.")
                onComplete?.invoke()
                return@launch
            }

            val speechAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            playAudioWithAttributes(
                samples = audio.samples,
                sampleRate = audio.sampleRate,
                attributes = speechAttributes,
                onComplete = { onComplete?.invoke() }
            )
        }
    }

    override fun playAudioWithAttributes(
        samples: FloatArray,
        sampleRate: Int,
        attributes: AudioAttributes,
        onComplete: () -> Unit
    ) {
        stopPlaybackInternal()

        try {
            val track = AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(samples.size * 4)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            audioTrack = track

            track.apply {
                write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
                setNotificationMarkerPosition(samples.size)
                setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                    override fun onMarkerReached(track: AudioTrack?) {
                        Log.d(TAG, "AudioTrack finished playback marker.")
                        stopPlaybackInternal()
                        onComplete()
                    }
                    override fun onPeriodicNotification(track: AudioTrack?) {}
                })
                play()
            }
        } catch (e: Exception) {
            Log.e(TAG, "AudioTrack playback error", e)
            stopPlaybackInternal()
            onComplete()
        }
    }

    override fun stop() {
        stopPlaybackInternal()
    }

    override fun release() {
        Log.i(TAG, "Releasing TTS Engine for ${voiceConfig.language.displayName} (${voiceConfig.voiceTag})...")
        stopPlaybackInternal()
        try {
            tts?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error during native TTS release", e)
        }
        tts = null
        scope.cancel()
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
            Log.e(TAG, "Error stopping audio track", e)
        } finally {
            audioTrack = null
        }
    }

    private fun resolveEspeakDataDir(context: Context): File {
        val targetDir = File(context.filesDir, "$BASE_ASSET_DIR/espeak-ng-data")
        if (targetDir.exists() && targetDir.isDirectory && (targetDir.list()?.isNotEmpty() == true)) {
            return targetDir
        }
        // Also check if extracted in /data/local/tmp/tts_benchmarks/espeak-ng-data
        val devTmpDir = File("/data/local/tmp/tts_benchmarks/espeak-ng-data")
        if (devTmpDir.exists() && devTmpDir.isDirectory) {
            return devTmpDir
        }

        // Extract from APK assets
        targetDir.mkdirs()
        copyDir(context, "$BASE_ASSET_DIR/espeak-ng-data", targetDir)
        return targetDir
    }

    private fun resolveVoiceFiles(context: Context, modelDirName: String): Pair<File, File> {
        // 1. Check /data/local/tmp/tts_benchmarks/<modelDirName> (Physical device test storage)
        val devTmpDir = File("/data/local/tmp/tts_benchmarks/$modelDirName")
        val devModel = File(devTmpDir, "model.onnx")
        val devTokens = File(devTmpDir, "tokens.txt")
        if (devModel.exists() && devTokens.exists() && devModel.length() > 10 * 1024 * 1024) {
            return Pair(devModel, devTokens)
        }

        // 2. Check internal filesDir
        val localDir = File(context.filesDir, modelDirName)
        val localModel = File(localDir, "model.onnx")
        val localTokens = File(localDir, "tokens.txt")
        if (localModel.exists() && localTokens.exists() && localModel.length() > 10 * 1024 * 1024) {
            return Pair(localModel, localTokens)
        }

        // 3. Extract from APK assets if present
        localDir.mkdirs()
        try {
            copyAssetToFiles(context, "$modelDirName/model.onnx", localModel)
            copyAssetToFiles(context, "$modelDirName/tokens.txt", localTokens)
            return Pair(localModel, localTokens)
        } catch (e: Exception) {
            Log.d(TAG, "Model $modelDirName not in APK assets: ${e.message}")
        }

        return Pair(devModel, devTokens)
    }

    private fun copyAssetToFiles(context: Context, assetPath: String, outFile: File): File {
        if (outFile.exists() && outFile.length() > 0) return outFile
        outFile.parentFile?.mkdirs()
        context.assets.open(assetPath).use { input ->
            FileOutputStream(outFile).use { output ->
                input.copyTo(output)
            }
        }
        return outFile
    }

    private fun copyDir(context: Context, assetPath: String, targetDir: File) {
        val assets = context.assets.list(assetPath) ?: return
        if (assets.isEmpty()) {
            copyAssetToFiles(context, assetPath, targetDir)
        } else {
            for (asset in assets) {
                val nextAssetPath = if (assetPath.isEmpty()) asset else "$assetPath/$asset"
                val nextTargetDir = File(targetDir, asset)
                if (isAssetDir(context, nextAssetPath)) {
                    nextTargetDir.mkdirs()
                    copyDir(context, nextAssetPath, nextTargetDir)
                } else {
                    copyAssetToFiles(context, nextAssetPath, nextTargetDir)
                }
            }
        }
    }

    private fun isAssetDir(context: Context, assetPath: String): Boolean {
        return context.assets.list(assetPath)?.isNotEmpty() ?: false
    }
}
