package com.itantra.app

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.itantra.app.audio.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.sqrt

@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SttAccuracyDiagnosticTest {

    private lateinit var context: Context
    private lateinit var ttsEngine: SherpaOnnxTtsEngine
    private lateinit var sttEngine: SherpaOnnxSttEngine
    private lateinit var vadManager: VadManager
    private lateinit var audioCaptureManager: AudioCaptureManager

    companion object {
        private const val TAG = "SttDiagnosticTest"
        private val reportBuilder = StringBuilder()
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        vadManager = VadManager(context)
        audioCaptureManager = AudioCaptureManager(context)

        val voiceConfig = TtsVoiceConfig(
            language = SupportedLanguage.ENGLISH,
            voiceTag = "piper_en_amy",
            displayName = "Amy (Low)",
            type = TtsType.PIPER_VITS,
            modelDirName = "tts-en-amy",
            sampleRate = 16000,
            isProductionReady = true
        )
        ttsEngine = SherpaOnnxTtsEngine(voiceConfig)
        sttEngine = SherpaOnnxSttEngine(SupportedLanguage.ENGLISH)

        runBlocking {
            val ttsRes = ttsEngine.initialize(context)
            assertTrue("TTS init failed: ${ttsRes.exceptionOrNull()?.message}", ttsRes.isSuccess)
            val sttRes = sttEngine.initialize(context)
            assertTrue("STT init failed: ${sttRes.exceptionOrNull()?.message}", sttRes.isSuccess)
        }
    }

    @After
    fun tearDown() {
        ttsEngine.release()
        sttEngine.release()
        vadManager.release()
        audioCaptureManager.release()

        // Flush report to disk
        try {
            val reportFile = File(context.filesDir, "diagnostic_report.txt")
            reportFile.writeText(reportBuilder.toString())
            Log.i(TAG, "Diagnostic report saved to ${reportFile.absolutePath} (${reportFile.length()} bytes)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save diagnostic_report.txt", e)
        }
    }

    private fun logBoth(msg: String) {
        Log.i(TAG, msg)
        reportBuilder.appendLine(msg)
    }

    private suspend fun synthesize(text: String): FloatArray {
        val audio = ttsEngine.generateSpeech(text)
        assertNotNull("TTS failed to generate audio for '$text'", audio)
        return audio!!.samples
    }

    private fun saveWavFile(file: File, samples: FloatArray) {
        val sampleRate = 16000
        val channels = 1
        val bitsPerSample = 16
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val dataSize = samples.size * 2
        val chunkSize = 36 + dataSize

        FileOutputStream(file).use { fos ->
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray())
            header.putInt(chunkSize)
            header.put("WAVE".toByteArray())
            header.put("fmt ".toByteArray())
            header.putInt(16)
            header.putShort(1.toShort())
            header.putShort(channels.toShort())
            header.putInt(sampleRate)
            header.putInt(byteRate)
            header.putShort(blockAlign.toShort())
            header.putShort(bitsPerSample.toShort())
            header.put("data".toByteArray())
            header.putInt(dataSize)
            fos.write(header.array())

            val pcmBuffer = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            for (s in samples) {
                val clamped = s.coerceIn(-1.0f, 1.0f)
                pcmBuffer.putShort((clamped * 32767.0f).toInt().toShort())
            }
            fos.write(pcmBuffer.array())
        }
    }

    private suspend fun runPipelineDiagnostic(
        testId: String,
        referenceText: String,
        rawSamples: FloatArray,
        scaleFactor: Float = 1.0f,
        addPaddingSilenceSeconds: Double = 0.5
    ) {
        logBoth("==================================================")
        logBoth("DIAGNOSTIC TEST: $testId")
        logBoth("REFERENCE PHRASE: '$referenceText' (scale=$scaleFactor)")
        logBoth("==================================================")

        // 1. Direct STT Baseline (pure raw audio, no VAD, to verify model recognition capability)
        val directResult = sttEngine.transcribe(rawSamples)
        val directText = directResult?.text ?: "<NULL>"
        logBoth("[DIRECT-STT-BASELINE] samples=${rawSamples.size} dur=${String.format(Locale.US, "%.2f", rawSamples.size / 16000.0)}s hyp='$directText'")

        // 2. Prepare scaled audio with leading/trailing silence
        val paddingSamples = (addPaddingSilenceSeconds * 16000).toInt()
        val totalTestSamples = paddingSamples + rawSamples.size + paddingSamples
        val paddedAudio = FloatArray(totalTestSamples)

        for (i in rawSamples.indices) {
            paddedAudio[paddingSamples + i] = (rawSamples[i] * scaleFactor).coerceIn(-1.0f, 1.0f)
        }

        val capturedDuration = paddedAudio.size.toDouble() / 16000.0
        logBoth("[CAPTURE-METRICS] totalSamples=${paddedAudio.size} capturedDuration=${String.format(Locale.US, "%.2f", capturedDuration)}s")

        // 3. Feed through VAD in 512-sample chunks
        vadManager.reset()
        val accumulatedSpeech = mutableListOf<FloatArray>()
        val preSpeechBuffer = java.util.ArrayDeque<FloatArray>()
        var speechStarted = false
        var speechEnded = false
        var firstSpeechChunk = -1
        var lastSpeechChunk = -1
        var totalChunks = 0

        val tPttPress = System.nanoTime()
        val tAudioStart = tPttPress
        var tVadStart = 0L
        var tVadEnd = 0L

        val chunkSize = 512
        var offset = 0
        while (offset < paddedAudio.size) {
            val count = kotlin.math.min(chunkSize, paddedAudio.size - offset)
            val chunk = FloatArray(count)
            System.arraycopy(paddedAudio, offset, chunk, 0, count)

            var chunkRmsSum = 0.0
            for (s in chunk) chunkRmsSum += s * s
            val chunkRms = sqrt(chunkRmsSum / count)
            val chunkRmsNorm = chunkRms

            val vadResult = vadManager.processWithProbability(chunk)
            val status = vadResult.status
            val prob = vadResult.probability

            logBoth("[PTT-VAD] chunkSamples=$count rms=${String.format(Locale.US, "%.2f", chunkRms * 32768.0)} rmsNorm=${String.format(Locale.US, "%.4f", chunkRmsNorm)} vadProbability=${String.format(Locale.US, "%.3f", prob)} state=$status")

            when (status) {
                VadStatus.SPEECH_DETECTED, VadStatus.SPEAKING -> {
                    if (!speechStarted) {
                        speechStarted = true
                        firstSpeechChunk = totalChunks
                        tVadStart = System.nanoTime()
                        val preCount = preSpeechBuffer.size
                        logBoth("[PTT-VAD-EVENT] SPEECH_DETECTED at chunk $totalChunks (${totalChunks * 32}ms, expected speech start at ${paddingSamples / 16}ms). Prepending $preCount pre-speech chunks.")
                        while (preSpeechBuffer.isNotEmpty()) {
                            accumulatedSpeech.add(preSpeechBuffer.removeFirst())
                        }
                    }
                    accumulatedSpeech.add(chunk.copyOf())
                    lastSpeechChunk = totalChunks
                }
                VadStatus.SPEECH_ENDED -> {
                    if (speechStarted && !speechEnded) {
                        speechEnded = true
                        lastSpeechChunk = totalChunks
                        tVadEnd = System.nanoTime()
                        logBoth("[PTT-VAD-EVENT] SPEECH_ENDED at chunk $totalChunks (${totalChunks * 32}ms).")
                    }
                }
                VadStatus.SILENCE -> {
                    if (!speechStarted) {
                        preSpeechBuffer.addLast(chunk.copyOf())
                        if (preSpeechBuffer.size > 10) preSpeechBuffer.removeFirst()
                    }
                }
            }

            offset += count
            totalChunks++
        }

        val tPttRelease = System.nanoTime()
        if (tVadEnd == 0L && speechStarted) tVadEnd = tPttRelease

        val vadSpeechIntervalMs = if (firstSpeechChunk >= 0 && lastSpeechChunk >= 0) (lastSpeechChunk - firstSpeechChunk + 1) * 32.0 else 0.0

        // 4. Flatten accumulated speech
        val finalSttBuffer: FloatArray = if (accumulatedSpeech.isNotEmpty()) {
            val totalSpeechSamples = accumulatedSpeech.sumOf { it.size }
            val flat = FloatArray(totalSpeechSamples)
            var cur = 0
            for (c in accumulatedSpeech) {
                System.arraycopy(c, 0, flat, cur, c.size)
                cur += c.size
            }
            flat
        } else {
            logBoth("[PTT-VAD-FALLBACK] VAD did not trigger speech! Using full buffer.")
            paddedAudio
        }

        // 5. Log [PTT-STT-AUDIO]
        val metrics = audioCaptureManager.analyzeSttAudio(finalSttBuffer)
        logBoth("[PTT-STT-AUDIO] sampleCount=${metrics.sampleCount} duration=${String.format(Locale.US, "%.2f", metrics.duration)}s rms=${String.format(Locale.US, "%.4f", metrics.rms)} peak=${String.format(Locale.US, "%.4f", metrics.peak)} leadingSilence=${String.format(Locale.US, "%.2f", metrics.leadingSilence)}s trailingSilence=${String.format(Locale.US, "%.2f", metrics.trailingSilence)}s")

        // 6. Save debug WAV for Test E
        if (testId.contains("Test E")) {
            val debugFile = File(context.filesDir, "debug_stt_capture.wav")
            saveWavFile(debugFile, finalSttBuffer)
            logBoth("[DEBUG-WAV-REPORT] path=${debugFile.absolutePath} size=${debugFile.length()} bytes duration=${String.format(Locale.US, "%.2f", metrics.duration)}s sampleCount=${metrics.sampleCount} RMS=${String.format(Locale.US, "%.4f", metrics.rms)} peak=${String.format(Locale.US, "%.4f", metrics.peak)}")
        }

        // 7. STT Inference
        val tSttStart = System.nanoTime()
        val sttResult = sttEngine.transcribe(finalSttBuffer)
        val tSttEnd = System.nanoTime()

        // 8. Latency Metrics
        val pttToAudioMs = (tAudioStart - tPttPress) / 1_000_000.0
        val vadSpeechDurMs = if (tVadStart > 0 && tVadEnd > 0) (tVadEnd - tVadStart) / 1_000_000.0 else 0.0
        val pttHoldMs = (tPttRelease - tPttPress) / 1_000_000.0
        val sttDurMs = (tSttEnd - tSttStart) / 1_000_000.0
        logBoth("[PTT-LATENCY] pttPress_to_audioStart_ms=${String.format(Locale.US, "%.1f", pttToAudioMs)} vadSpeechDuration_ms=${String.format(Locale.US, "%.1f", vadSpeechDurMs)} pttHold_ms=${String.format(Locale.US, "%.1f", pttHoldMs)} sttDuration_ms=${String.format(Locale.US, "%.1f", sttDurMs)}")

        // 9. Log REFERENCE and HYPOTHESIS
        val hypothesisText = sttResult?.text ?: "<NULL_RESULT>"
        logBoth("--------------------------------------------------")
        logBoth("REFERENCE: $referenceText")
        logBoth("HYPOTHESIS: $hypothesisText")
        logBoth("VAD_SPEECH_INTERVAL: ${String.format(Locale.US, "%.1f", vadSpeechIntervalMs)}ms | STT_INPUT_DURATION: ${String.format(Locale.US, "%.2f", metrics.duration)}s")
        logBoth("--------------------------------------------------")
    }

    @Test
    fun test01_DiagnosticPhraseA_This() {
        runBlocking {
            val audio = synthesize("this")
            runPipelineDiagnostic("Test A", "this", audio)
        }
    }

    @Test
    fun test02_DiagnosticPhraseB_ThisIs() {
        runBlocking {
            val audio = synthesize("this is")
            runPipelineDiagnostic("Test B", "this is", audio)
        }
    }

    @Test
    fun test03_DiagnosticPhraseC_ThisIsA() {
        runBlocking {
            val audio = synthesize("this is a")
            runPipelineDiagnostic("Test C", "this is a", audio)
        }
    }

    @Test
    fun test04_DiagnosticPhraseD_ThisIsAMicrophone() {
        runBlocking {
            val audio = synthesize("this is a microphone")
            runPipelineDiagnostic("Test D", "this is a microphone", audio)
        }
    }

    @Test
    fun test05_DiagnosticPhraseE_ThisIsAMicrophoneTest_Nominal() {
        runBlocking {
            val audio = synthesize("This is a microphone test.")
            runPipelineDiagnostic("Test E (Nominal 1.0x)", "This is a microphone test.", audio, scaleFactor = 1.0f)
        }
    }

    @Test
    fun test06_DiagnosticPhraseE_ThisIsAMicrophoneTest_PhoneMicLevel() {
        runBlocking {
            val audio = synthesize("This is a microphone test.")
            runPipelineDiagnostic("Test E (Phone Mic Level 0.20x)", "This is a microphone test.", audio, scaleFactor = 0.20f)
        }
    }
}
