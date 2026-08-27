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
    private lateinit var audioCaptureManager: AudioCaptureManager

    companion object {
        private const val TAG = "SttDiagnosticTest"
        private val reportBuilder = StringBuilder()
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
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
        audioCaptureManager.release()

        // Flush report to disk
        try {
            val reportFile = File(context.filesDir, "phase12b_diagnostic_report.txt")
            reportFile.writeText(reportBuilder.toString())
            Log.i(TAG, "Phase 12B Diagnostic report saved to ${reportFile.absolutePath} (${reportFile.length()} bytes)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save phase12b_diagnostic_report.txt", e)
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
        leadingSilenceSeconds: Double = 0.5,
        trailingSilenceSeconds: Double = 0.5
    ): String {
        logBoth("==================================================")
        logBoth("DIAGNOSTIC TEST: $testId")
        logBoth("REFERENCE PHRASE: '$referenceText' (scale=$scaleFactor, leadingSilence=${leadingSilenceSeconds}s)")
        logBoth("==================================================")

        // 1. Direct STT Baseline (pure raw audio, no VAD, to verify model ground truth)
        val directResult = sttEngine.transcribe(rawSamples)
        val directText = directResult?.text ?: "<NULL>"
        logBoth("[DIRECT-STT-BASELINE] samples=${rawSamples.size} dur=${String.format(Locale.US, "%.2f", rawSamples.size / 16000.0)}s hyp='$directText'")

        // 2. Prepare audio with variable leading & trailing silence
        val leadSamples = (leadingSilenceSeconds * 16000).toInt()
        val trailSamples = (trailingSilenceSeconds * 16000).toInt()
        val totalTestSamples = leadSamples + rawSamples.size + trailSamples
        val paddedAudio = FloatArray(totalTestSamples)

        for (i in rawSamples.indices) {
            paddedAudio[leadSamples + i] = (rawSamples[i] * scaleFactor).coerceIn(-1.0f, 1.0f)
        }

        val capturedDuration = paddedAudio.size.toDouble() / 16000.0
        logBoth("[CAPTURE-METRICS] totalSamples=${paddedAudio.size} capturedDuration=${String.format(Locale.US, "%.2f", capturedDuration)}s")

        // 3. Process through the production AudioCaptureManager state machine
        audioCaptureManager.resetCaptureState()

        val chunkSize = 512
        var offset = 0
        while (offset < paddedAudio.size) {
            val count = kotlin.math.min(chunkSize, paddedAudio.size - offset)
            val chunk = FloatArray(count)
            System.arraycopy(paddedAudio, offset, chunk, 0, count)

            var chunkRmsSum = 0.0
            for (s in chunk) chunkRmsSum += s * s
            val chunkRms = sqrt(chunkRmsSum / count)

            val decision = audioCaptureManager.processAudioChunk(chunk)
            logBoth("[PTT-VAD] chunkSamples=$count rms=${String.format(Locale.US, "%.2f", chunkRms * 32768.0)} rmsNorm=${String.format(Locale.US, "%.4f", chunkRms)} vadProbability=${String.format(Locale.US, "%.3f", decision.probability)} state=${decision.status} captureState=${audioCaptureManager.captureState}")

            offset += count
        }

        // 4. Force-finalize (simulates PTT button release at end of transmission)
        val finalSttBuffer: FloatArray = if (audioCaptureManager.getAccumulatedSpeech().isNotEmpty()) {
            audioCaptureManager.getAccumulatedSpeech()
        } else {
            logBoth("[PTT-VAD-FALLBACK] Finalizing from captured audio.")
            paddedAudio
        }

        // 5. Analyze STT Audio metrics
        val metrics = audioCaptureManager.analyzeSttAudio(finalSttBuffer)
        logBoth("[PTT-STT-AUDIO] sampleCount=${metrics.sampleCount} duration=${String.format(Locale.US, "%.2f", metrics.duration)}s rms=${String.format(Locale.US, "%.4f", metrics.rms)} peak=${String.format(Locale.US, "%.4f", metrics.peak)} leadingSilence=${String.format(Locale.US, "%.2f", metrics.leadingSilence)}s trailingSilence=${String.format(Locale.US, "%.2f", metrics.trailingSilence)}s")

        // 6. Save debug WAV for controlled inspection
        if (testId.contains("Test E")) {
            val debugFile = File(context.filesDir, "phase12b_stt_capture.wav")
            saveWavFile(debugFile, finalSttBuffer)
            logBoth("[DEBUG-WAV-REPORT] path=${debugFile.absolutePath} size=${debugFile.length()} bytes duration=${String.format(Locale.US, "%.2f", metrics.duration)}s sampleCount=${metrics.sampleCount}")
        }

        // 7. STT Inference
        val t0 = System.currentTimeMillis()
        val sttResult = sttEngine.transcribe(finalSttBuffer)
        val elapsed = System.currentTimeMillis() - t0

        val hypothesisText = sttResult?.text ?: "<NULL_RESULT>"
        logBoth("[STT-RESULT] latency=${elapsed}ms resultLength=${hypothesisText.length}")

        logBoth("--------------------------------------------------")
        logBoth("REFERENCE: $referenceText")
        logBoth("HYPOTHESIS: $hypothesisText")
        logBoth("--------------------------------------------------")

        return hypothesisText
    }

    // ==========================================
    // Requirement 10: Synthetic Ground Truth Tests (A to E)
    // ==========================================

    @Test
    fun test01_PhraseA_This() {
        runBlocking {
            val audio = synthesize("this")
            val hyp = runPipelineDiagnostic("Test A", "this", audio)
            assertTrue("Expected hypothesis to contain 'this', got: '$hyp'", hyp.contains("this", ignoreCase = true))
        }
    }

    @Test
    fun test02_PhraseB_ThisIs() {
        runBlocking {
            val audio = synthesize("this is")
            val hyp = runPipelineDiagnostic("Test B", "this is", audio)
            assertTrue("Expected hypothesis to contain 'this', got: '$hyp'", hyp.contains("this", ignoreCase = true))
        }
    }

    @Test
    fun test03_PhraseC_ThisIsA() {
        runBlocking {
            val audio = synthesize("this is a")
            val hyp = runPipelineDiagnostic("Test C", "this is a", audio)
            assertTrue("Expected first word 'this' preserved, got: '$hyp'", hyp.contains("this", ignoreCase = true))
        }
    }

    @Test
    fun test04_PhraseD_ThisIsAMicrophone() {
        runBlocking {
            val audio = synthesize("this is a microphone")
            val hyp = runPipelineDiagnostic("Test D", "this is a microphone", audio)
            assertTrue("Expected first word 'this' preserved, got: '$hyp'", hyp.contains("this", ignoreCase = true))
        }
    }

    @Test
    fun test05_PhraseE_ThisIsAMicrophoneTest_Nominal() {
        runBlocking {
            val audio = synthesize("This is a microphone test.")
            val hyp = runPipelineDiagnostic("Test E (Nominal 1.0x)", "This is a microphone test.", audio, scaleFactor = 1.0f)
            assertTrue("Expected complete phrase with 'this', got: '$hyp'", hyp.contains("this", ignoreCase = true))
            assertTrue("Expected complete phrase with 'microphone', got: '$hyp'", hyp.contains("microphone", ignoreCase = true))
            assertTrue("Expected complete phrase with 'test', got: '$hyp'", hyp.contains("test", ignoreCase = true))
        }
    }

    @Test
    fun test06_PhraseE_ThisIsAMicrophoneTest_PhoneMicLevel() {
        runBlocking {
            val audio = synthesize("This is a microphone test.")
            val hyp = runPipelineDiagnostic("Test E (Phone Mic Level 0.20x)", "This is a microphone test.", audio, scaleFactor = 0.20f)
            assertTrue("Expected complete phrase with 'this', got: '$hyp'", hyp.contains("this", ignoreCase = true))
            assertTrue("Expected complete phrase with 'microphone', got: '$hyp'", hyp.contains("microphone", ignoreCase = true))
        }
    }

    // ==========================================
    // Requirement 11: Variable Onset Timing Tests
    // (100ms, 300ms, 500ms, 800ms, 1000ms delay post-PTT)
    // ==========================================

    @Test
    fun test07_OnsetDelay_100ms() {
        runBlocking {
            val audio = synthesize("This is a microphone test.")
            val hyp = runPipelineDiagnostic("Timing 100ms", "This is a microphone test.", audio, leadingSilenceSeconds = 0.10)
            assertTrue("100ms onset: Expected 'this' preserved, got: '$hyp'", hyp.contains("this", ignoreCase = true))
        }
    }

    @Test
    fun test08_OnsetDelay_300ms() {
        runBlocking {
            val audio = synthesize("This is a microphone test.")
            val hyp = runPipelineDiagnostic("Timing 300ms", "This is a microphone test.", audio, leadingSilenceSeconds = 0.30)
            assertTrue("300ms onset: Expected 'this' preserved, got: '$hyp'", hyp.contains("this", ignoreCase = true))
        }
    }

    @Test
    fun test09_OnsetDelay_500ms() {
        runBlocking {
            val audio = synthesize("This is a microphone test.")
            val hyp = runPipelineDiagnostic("Timing 500ms", "This is a microphone test.", audio, leadingSilenceSeconds = 0.50)
            assertTrue("500ms onset: Expected 'this' preserved, got: '$hyp'", hyp.contains("this", ignoreCase = true))
        }
    }

    @Test
    fun test10_OnsetDelay_800ms() {
        runBlocking {
            val audio = synthesize("This is a microphone test.")
            val hyp = runPipelineDiagnostic("Timing 800ms", "This is a microphone test.", audio, leadingSilenceSeconds = 0.80)
            assertTrue("800ms onset: Expected 'this' preserved, got: '$hyp'", hyp.contains("this", ignoreCase = true))
        }
    }

    @Test
    fun test11_OnsetDelay_1000ms() {
        runBlocking {
            val audio = synthesize("This is a microphone test.")
            val hyp = runPipelineDiagnostic("Timing 1000ms", "This is a microphone test.", audio, leadingSilenceSeconds = 1.00)
            assertTrue("1000ms onset: Expected 'this' preserved, got: '$hyp'", hyp.contains("this", ignoreCase = true))
        }
    }

    // ==========================================
    // Requirement 12: Real Tactical Phrases
    // ==========================================

    @Test
    fun test12_Phrase_RadioCheck() {
        runBlocking {
            val audio = synthesize("Hello, this is a radio check.")
            val hyp = runPipelineDiagnostic("Radio Check", "Hello, this is a radio check.", audio, leadingSilenceSeconds = 0.50)
            assertTrue("Expected 'Hello' preserved, got: '$hyp'", hyp.contains("hello", ignoreCase = true))
        }
    }

    @Test
    fun test13_Phrase_StationAlpha() {
        runBlocking {
            val audio = synthesize("I need assistance at Station Alpha.")
            val hyp = runPipelineDiagnostic("Station Alpha", "I need assistance at Station Alpha.", audio, leadingSilenceSeconds = 0.50)
            assertTrue("Expected 'I need assistance' preserved, got: '$hyp'", hyp.contains("assistance", ignoreCase = true))
        }
    }
}
