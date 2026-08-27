package com.itantra.app.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt

/**
 * Lightweight data class representing the state of the audio capture pipeline.
 */
data class AudioState(
    val isRecording: Boolean = false,
    val sampleCount: Long = 0,
    val durationSeconds: Double = 0.0,
    val rms: Double = 0.0,
    val vadStatus: VadStatus = VadStatus.SILENCE,
    val sttStatus: SttStatus = SttStatus.IDLE,
    val recognizedText: String = "",
    val lastSttResult: SttResult? = null,
    val errorMessage: String? = null
)

/**
 * Manages 16 kHz mono PCM 16-bit audio capture using Android's native AudioRecord API.
 *
 * Optimized for low-end and mid-range devices:
 * - Minimal buffer allocations (buffers are allocated once per session and reused in loop).
 * - Background execution on Dispatchers.IO.
 * - Throttled UI state updates (~10 Hz) to avoid excessive Compose recompositions.
 * - Direct callback hook ([onAudioChunkCaptured]) for future streaming VAD / STT ingestion.
 * - Strict resource lifecycle management ensuring AudioRecord is always released.
 */
class AudioCaptureManager(private val context: Context) {

    private val _state = MutableStateFlow(AudioState())
    val state: StateFlow<AudioState> = _state.asStateFlow()

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private var vadManager: VadManager? = null
    private var sttManager: SttManager? = null

    val languageModelManager: LanguageModelManager
        get() = LanguageModelManager.getInstance(context)

    /**
     * Explicit VAD capture states for Phase 12B onset recovery.
     */
    enum class CaptureState {
        IDLE,
        LISTENING,
        LIKELY_SPEECH,
        SPEAKING,
        ENDING
    }

    /**
     * Timestamped chunk stored in bounded rolling history.
     */
    data class HistoryChunk(
        val sequence: Long,
        val samples: FloatArray,
        val probability: Float
    )

    var captureState: CaptureState = CaptureState.IDLE
        private set

    // Accumulates audio samples during active speech
    private val speechAccumulator = mutableListOf<FloatArray>()
    private var speechSamplesCount = 0

    // Bounded rolling history buffer: 40 chunks * 512 samples = 20,480 samples = 1.28 seconds
    private val rollingHistory = java.util.ArrayDeque<HistoryChunk>()
    private val MAX_HISTORY_CHUNKS = 40
    private val PRE_SPEECH_PADDING_CHUNKS = 8 // 8 * 32ms = ~256ms pre-speech padding before onset
    private val ONSET_PROB_THRESHOLD = 0.40f
    private val ONSET_CONSECUTIVE_CHUNKS = 2 // 2 * 32ms = 64ms temporal consistency debouncer
    private val SILENCE_ENDPOINT_CHUNKS = 22 // 22 * 32ms = ~704ms silence endpointing

    private var chunkSequence = 0L
    private var lastAppendedSequence = -1L
    private var consecutiveElevatedCount = 0
    private var consecutiveSilenceCount = 0

    // Monotonic timing for latency diagnostics (Phase 12A)
    var tPttPressNano: Long = 0L
    var tAudioRecordStartNano: Long = 0L
    var tVadSpeechStartNano: Long = 0L
    var tVadSpeechEndNano: Long = 0L
    var tPttReleaseNano: Long = 0L
    var tSttStartNano: Long = 0L
    var tSttEndNano: Long = 0L

    // Standard 16 kHz, Mono, 16-bit PCM configuration
    val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    /**
     * Optional callback for streaming audio chunks to future consumers (e.g. Silero VAD / STT).
     * Invoked on the background audio capture thread with a reusable buffer.
     * Parameters: (buffer: ShortArray, readCount: Int)
     */
    var onAudioChunkCaptured: ((ShortArray, Int) -> Unit)? = null

    companion object {
        private const val TAG = "AudioCaptureManager"
        private const val UI_UPDATE_INTERVAL_MS = 100L // 10 Hz UI updates
        // 512 samples = 32ms at 16kHz, ideal chunk size for low latency & future VAD processing
        private const val CHUNK_SIZE_SAMPLES = 512
    }

    /**
     * Starts recording audio.
     * Assumes android.permission.RECORD_AUDIO is granted by caller.
     */
    @Synchronized
    @SuppressLint("MissingPermission")
    fun startRecording() {
        if (_state.value.isRecording) return
        
        tPttPressNano = System.nanoTime()
        tVadSpeechStartNano = 0L
        tVadSpeechEndNano = 0L
        tPttReleaseNano = 0L
        tSttStartNano = 0L
        tSttEndNano = 0L

        // Initialize VAD if not already done
        if (vadManager == null) {
            vadManager = VadManager(context)
        } else {
            vadManager?.reset()
        }

        // Initialize STT if not already done
        if (sttManager == null) {
            sttManager = SttManager(context)
        }

        val minBufferSizeInBytes = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        if (minBufferSizeInBytes <= 0) {
            Log.e(TAG, "AudioRecord.getMinBufferSize failed: $minBufferSizeInBytes")
            _state.value = _state.value.copy(
                errorMessage = "Audio hardware does not support requested audio configuration."
            )
            return
        }

        // Use at least 2x minBufferSize or 2048 bytes for hardware driver safety on low-end chipsets
        val audioRecordBufferSizeInBytes = (minBufferSizeInBytes * 2).coerceAtLeast(2048)

        try {
            val record = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                channelConfig,
                audioFormat,
                audioRecordBufferSizeInBytes
            )

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed (state != STATE_INITIALIZED).")
                record.release()
                _state.value = _state.value.copy(
                    errorMessage = "Failed to initialize audio recorder."
                )
                return
            }

            record.startRecording()
            tAudioRecordStartNano = System.nanoTime()
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                Log.e(TAG, "AudioRecord failed to start recording.")
                record.release()
                _state.value = _state.value.copy(
                    errorMessage = "Audio recorder could not start."
                )
                return
            }

            audioRecord = record
            speechAccumulator.clear()
            speechSamplesCount = 0
            rollingHistory.clear()
            consecutiveElevatedCount = 0
            consecutiveSilenceCount = 0
            lastAppendedSequence = -1L
            chunkSequence = 0L
            captureState = CaptureState.LISTENING

            Log.d(TAG, "AudioRecord started. State: ${record.state}, RecordingState: ${record.recordingState}")
            _state.value = AudioState(
                isRecording = true,
                sampleCount = 0,
                durationSeconds = 0.0,
                rms = 0.0,
                errorMessage = null
            )

            recordingJob = scope.launch {
                readAudioLoop(record)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during startRecording", e)
            stopRecording()
            _state.value = _state.value.copy(
                errorMessage = "Recording error: ${e.localizedMessage ?: "Unknown error"}"
            )
        }
    }

    /**
     * Continuous background reading loop.
     * Reuses a single ShortArray to prevent object allocation and GC pressure.
     */
    private suspend fun readAudioLoop(record: AudioRecord) {
        val buffer = ShortArray(CHUNK_SIZE_SAMPLES)
        var totalSamples = 0L
        var lastUiUpdateTime = SystemClock.uptimeMillis()
        var lastDiagLogTime = SystemClock.uptimeMillis()
        var latestRms = 0.0
        var peakAmplitude = 0

        try {
            while (currentCoroutineContext().isActive && _state.value.isRecording) {
                val readCount = record.read(buffer, 0, buffer.size)

                if (readCount > 0) {
                    totalSamples += readCount
                    
                    // Track peak for diagnostics
                    for (i in 0 until readCount) {
                        val absVal = kotlin.math.abs(buffer[i].toInt())
                        if (absVal > peakAmplitude) peakAmplitude = absVal
                    }
                    
                    latestRms = calculateRms(buffer, readCount)
                    val latestRmsNorm = latestRms / 32768.0
                    
                    // Convert PCM16 chunk to Float for accumulation - Reuse buffer if possible
                    val floatChunk = FloatArray(readCount)
                    for (i in 0 until readCount) {
                        floatChunk[i] = buffer[i] / 32768.0f
                    }

                    // Process VAD with the float chunk we just converted
                    val vm = vadManager
                    val decision = vm?.processDecision(floatChunk) ?: VadManager.VadDecision(0.0f, false, VadStatus.SILENCE)
                    val currentVadStatus = decision.status
                    val vadProbability = decision.probability

                    // Log [PTT-VAD] diagnostic log per Phase 12A specification
                    Log.i(TAG, "[PTT-VAD] chunkSamples=$readCount rms=${String.format(java.util.Locale.US, "%.2f", latestRms)} rmsNorm=${String.format(java.util.Locale.US, "%.4f", latestRmsNorm)} vadProbability=${String.format(java.util.Locale.US, "%.3f", vadProbability)} state=$currentVadStatus")

                    if ((currentVadStatus == VadStatus.SPEECH_DETECTED || currentVadStatus == VadStatus.SPEAKING || captureState == CaptureState.SPEAKING) && tVadSpeechStartNano == 0L) {
                        tVadSpeechStartNano = System.nanoTime()
                    }
                    if (currentVadStatus == VadStatus.SPEECH_ENDED && tVadSpeechEndNano == 0L) {
                        tVadSpeechEndNano = System.nanoTime()
                    }

                    // Handle speech accumulation and STT triggering
                    handleSpeechTransitions(decision, floatChunk)

                    // Deliver PCM chunk to consumer (e.g., future VAD) without copying if possible
                    onAudioChunkCaptured?.invoke(buffer, readCount)

                    // Throttle UI state updates to ~10 Hz to prevent excessive recompositions
                    val now = SystemClock.uptimeMillis()
                    
                    // Periodically log aggregate metrics every 1 second
                    if (now - lastDiagLogTime >= 1000L) {
                        Log.i(TAG, "[PTT-AUDIO] samples=$totalSamples rms=${String.format("%.2f", latestRms)} rms_norm=${String.format("%.4f", latestRmsNorm)} peak=$peakAmplitude duration=${String.format("%.2f", totalSamples.toDouble()/sampleRate)}s")
                        lastDiagLogTime = now
                        peakAmplitude = 0 // Reset peak for next interval
                    }

                    if (now - lastUiUpdateTime >= UI_UPDATE_INTERVAL_MS) {
                        lastUiUpdateTime = now
                        _state.value = _state.value.copy(
                            sampleCount = totalSamples,
                            durationSeconds = totalSamples.toDouble() / sampleRate,
                            rms = latestRms,
                            vadStatus = currentVadStatus
                        )
                    }
                } else if (readCount < 0) {
                    Log.e(TAG, "AudioRecord.read returned error code: $readCount")
                    break
                } else {
                    // readCount == 0, wait a bit
                    delay(10)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception in audio capture loop", e)
        } finally {
            val finalRmsNorm = latestRms / 32768.0
            Log.i(TAG, "[PTT-AUDIO-FINAL] samples=$totalSamples rms=${String.format("%.2f", latestRms)} rms_norm=${String.format("%.4f", finalRmsNorm)} peak=$peakAmplitude duration=${String.format("%.2f", totalSamples.toDouble()/sampleRate)}s")
            Log.d(TAG, "Audio capture loop finished. Total samples: $totalSamples")
            // Final state sync with exact sample count and duration
            _state.value = _state.value.copy(
                isRecording = false,
                sampleCount = totalSamples,
                durationSeconds = totalSamples.toDouble() / sampleRate,
                rms = if (_state.value.isRecording) 0.0 else latestRms
            )
            cleanupAudioRecord(record)
        }
    }

    /**
     * Stops recording and releases AudioRecord resources.
     * @param forceFinalize If true, immediately triggers STT with whatever speech was accumulated.
     */
    @Synchronized
    fun stopRecording(forceFinalize: Boolean = false) {
        if (!_state.value.isRecording && audioRecord == null) return

        tPttReleaseNano = System.nanoTime()
        Log.d(TAG, "stopRecording(forceFinalize=$forceFinalize)")
        _state.value = _state.value.copy(isRecording = false)
        
        if (forceFinalize) {
            val audioToTranscribe = if (speechAccumulator.isNotEmpty() && speechSamplesCount >= sampleRate * 0.15) {
                Log.i(TAG, "[PTT-DIAG] Force-finalizing from speechAccumulator ($speechSamplesCount samples)")
                flattenAccumulator()
            } else if (rollingHistory.isNotEmpty()) {
                val historyData = flattenRollingHistory()
                if (historyData.size >= sampleRate * 0.15) {
                    Log.i(TAG, "[PTT-DIAG] Force-finalizing from rollingHistory (${historyData.size} samples)")
                    historyData
                } else {
                    null
                }
            } else {
                null
            }

            speechAccumulator.clear()
            speechSamplesCount = 0
            rollingHistory.clear()
            consecutiveElevatedCount = 0
            consecutiveSilenceCount = 0
            lastAppendedSequence = -1L
            captureState = CaptureState.IDLE

            if (audioToTranscribe != null) {
                runStt(audioToTranscribe)
            }
        } else {
            speechAccumulator.clear()
            speechSamplesCount = 0
            rollingHistory.clear()
            consecutiveElevatedCount = 0
            consecutiveSilenceCount = 0
            lastAppendedSequence = -1L
            captureState = CaptureState.IDLE
        }

        recordingJob?.cancel()
        recordingJob = null

        audioRecord?.let { record ->
            cleanupAudioRecord(record)
            audioRecord = null
        }
    }

    /**
     * Safely stops and releases an AudioRecord instance.
     */
    private fun cleanupAudioRecord(record: AudioRecord) {
        try {
            if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                record.stop()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping AudioRecord", e)
        }
        try {
            record.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing AudioRecord", e)
        }
    }

    /**
     * Lightweight RMS calculation for audio level visualization.
     * Computes the root mean square of 16-bit PCM amplitudes.
     */
    private fun calculateRms(buffer: ShortArray, count: Int): Double {
        if (count <= 0) return 0.0
        var sumSquares = 0.0
        for (i in 0 until count) {
            val sample = buffer[i].toDouble()
            sumSquares += sample * sample
        }
        return sqrt(sumSquares / count)
    }

    /**
     * Handles accumulation of audio during speech and triggers STT when speech ends.
     * Phase 12B: Probability-assisted onset recovery from bounded rolling history.
     */
    fun handleSpeechTransitions(decision: VadManager.VadDecision, floatChunk: FloatArray) {
        // 1. Add current chunk to bounded rolling history
        val historyChunk = HistoryChunk(
            sequence = chunkSequence,
            samples = floatChunk.copyOf(),
            probability = decision.probability
        )
        rollingHistory.addLast(historyChunk)
        if (rollingHistory.size > MAX_HISTORY_CHUNKS) {
            rollingHistory.removeFirst()
        }

        // 2. Update temporal consistency debouncing counters
        if (decision.probability >= ONSET_PROB_THRESHOLD) {
            consecutiveElevatedCount++
            consecutiveSilenceCount = 0
        } else {
            consecutiveElevatedCount = 0
            consecutiveSilenceCount++
        }

        // 3. State machine transitions
        when (captureState) {
            CaptureState.IDLE, CaptureState.LISTENING -> {
                val isOnset = (consecutiveElevatedCount >= ONSET_CONSECUTIVE_CHUNKS) || decision.nativeSpeechDetected
                if (isOnset) {
                    captureState = CaptureState.LIKELY_SPEECH

                    // Onset chunk is the earliest elevated chunk in this contiguous burst
                    val onsetSeq = (chunkSequence - consecutiveElevatedCount + 1).coerceAtLeast(0L)
                    val targetStartSeq = (onsetSeq - PRE_SPEECH_PADDING_CHUNKS).coerceAtLeast(0L)

                    speechAccumulator.clear()
                    speechSamplesCount = 0
                    for (hc in rollingHistory) {
                        if (hc.sequence >= targetStartSeq && hc.sequence <= chunkSequence) {
                            speechAccumulator.add(hc.samples)
                            speechSamplesCount += hc.samples.size
                            lastAppendedSequence = hc.sequence
                        }
                    }

                    // Required Phase 12B diagnostic log
                    Log.i(TAG, "[PTT-VAD-EVENT] LIKELY_SPEECH_ONSET onsetChunk=$onsetSeq onsetProbability=${String.format(java.util.Locale.US, "%.3f", decision.probability)} historySamples=$speechSamplesCount")

                    captureState = CaptureState.SPEAKING
                    if (_state.value.sttStatus != SttStatus.SPEECH_DETECTED) {
                        _state.value = _state.value.copy(sttStatus = SttStatus.SPEECH_DETECTED)
                    }
                } else {
                    if (_state.value.sttStatus != SttStatus.IDLE && _state.value.sttStatus != SttStatus.COMPLETE) {
                        _state.value = _state.value.copy(sttStatus = SttStatus.IDLE)
                    }
                }
            }
            CaptureState.LIKELY_SPEECH, CaptureState.SPEAKING -> {
                // Append incoming chunk if not already added during history recovery
                if (chunkSequence > lastAppendedSequence) {
                    if (speechSamplesCount < sampleRate * 30) {
                        speechAccumulator.add(floatChunk.copyOf())
                        speechSamplesCount += floatChunk.size
                        lastAppendedSequence = chunkSequence
                    }
                }

                // Check for speech endpoint:
                // a) Native VAD SPEECH_ENDED
                // b) Silence duration >= SILENCE_ENDPOINT_CHUNKS (700ms) with at least 150ms of speech accumulated
                val isEndpoint = (decision.status == VadStatus.SPEECH_ENDED) ||
                        (consecutiveSilenceCount >= SILENCE_ENDPOINT_CHUNKS && speechSamplesCount >= sampleRate * 0.15)

                if (isEndpoint) {
                    Log.i(TAG, "[PTT-DIAG] Speech endpoint reached (silence detected). Finalizing utterance (${speechSamplesCount} samples).")
                    captureState = CaptureState.ENDING
                    val speechData = flattenAccumulator()
                    speechAccumulator.clear()
                    speechSamplesCount = 0
                    rollingHistory.clear()
                    consecutiveElevatedCount = 0
                    consecutiveSilenceCount = 0
                    lastAppendedSequence = -1L

                    runStt(speechData)
                    captureState = CaptureState.LISTENING
                }
            }
            CaptureState.ENDING -> {
                captureState = CaptureState.LISTENING
            }
        }
        chunkSequence++
    }

    private fun flattenAccumulator(): FloatArray {
        val result = FloatArray(speechSamplesCount)
        var offset = 0
        for (chunk in speechAccumulator) {
            chunk.copyInto(result, offset)
            offset += chunk.size
        }
        return result
    }

    private fun flattenRollingHistory(): FloatArray {
        val total = rollingHistory.sumOf { it.samples.size }
        val result = FloatArray(total)
        var offset = 0
        for (chunk in rollingHistory) {
            chunk.samples.copyInto(result, offset)
            offset += chunk.samples.size
        }
        return result
    }

    /**
     * Diagnostic helper: feeds a single 512-sample float chunk through the VAD and capture state machine.
     */
    fun processAudioChunk(floatChunk: FloatArray): VadManager.VadDecision {
        val vm = vadManager ?: VadManager(context).also { vadManager = it }
        val decision = vm.processDecision(floatChunk)
        handleSpeechTransitions(decision, floatChunk)
        return decision
    }

    /**
     * Diagnostic helper: returns the currently accumulated speech buffer.
     */
    fun getAccumulatedSpeech(): FloatArray = flattenAccumulator()

    /**
     * Diagnostic helper: resets capture state machine and clears all history buffers.
     */
    fun resetCaptureState() {
        speechAccumulator.clear()
        speechSamplesCount = 0
        rollingHistory.clear()
        consecutiveElevatedCount = 0
        consecutiveSilenceCount = 0
        lastAppendedSequence = -1L
        chunkSequence = 0L
        captureState = CaptureState.LISTENING
        vadManager?.reset()
    }

    data class SttAudioMetrics(
        val sampleCount: Int,
        val duration: Double,
        val rms: Double,
        val peak: Double,
        val leadingSilence: Double,
        val trailingSilence: Double
    )

    fun analyzeSttAudio(samples: FloatArray): SttAudioMetrics {
        val sampleCount = samples.size
        val duration = sampleCount.toDouble() / sampleRate
        var sumSquares = 0.0
        var peak = 0.0f
        for (s in samples) {
            val absVal = kotlin.math.abs(s)
            if (absVal > peak) peak = absVal
            sumSquares += s * s
        }
        val rms = if (sampleCount > 0) sqrt(sumSquares / sampleCount) else 0.0

        val frameSize = 160 // 10ms at 16kHz
        val silenceThreshold = 0.01
        val numFrames = sampleCount / frameSize

        var leadingFrames = 0
        for (i in 0 until numFrames) {
            var fSum = 0.0
            for (j in 0 until frameSize) {
                val s = samples[i * frameSize + j]
                fSum += s * s
            }
            if (sqrt(fSum / frameSize) < silenceThreshold) {
                leadingFrames++
            } else {
                break
            }
        }
        val leadingSilence = leadingFrames * (frameSize.toDouble() / sampleRate)

        var trailingFrames = 0
        for (i in (numFrames - 1) downTo 0) {
            var fSum = 0.0
            for (j in 0 until frameSize) {
                val s = samples[i * frameSize + j]
                fSum += s * s
            }
            if (sqrt(fSum / frameSize) < silenceThreshold) {
                trailingFrames++
            } else {
                break
            }
        }
        val trailingSilence = trailingFrames * (frameSize.toDouble() / sampleRate)

        return SttAudioMetrics(sampleCount, duration, rms, peak.toDouble(), leadingSilence, trailingSilence)
    }

    fun saveDebugWav(context: Context, samples: FloatArray, filename: String = "debug_stt_capture.wav"): java.io.File {
        val file = java.io.File(context.filesDir, filename)
        val channels = 1
        val bitsPerSample = 16
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val dataSize = samples.size * 2
        val chunkSize = 36 + dataSize

        java.io.FileOutputStream(file).use { fos ->
            val header = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN)
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

            val pcmBuffer = java.nio.ByteBuffer.allocate(samples.size * 2).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            for (s in samples) {
                val clamped = s.coerceIn(-1.0f, 1.0f)
                pcmBuffer.putShort((clamped * 32767.0f).toInt().toShort())
            }
            fos.write(pcmBuffer.array())
        }
        Log.i(TAG, "[DEBUG-WAV] Saved exact STT buffer to ${file.absolutePath} (${file.length()} bytes)")
        return file
    }

    private fun runStt(samples: FloatArray) {
        scope.launch {
            val metrics = analyzeSttAudio(samples)
            // Log [PTT-STT-AUDIO] per Phase 12A/12B specification
            Log.i(TAG, "[PTT-STT-AUDIO] sampleCount=${metrics.sampleCount} duration=${String.format(java.util.Locale.US, "%.2f", metrics.duration)}s rms=${String.format(java.util.Locale.US, "%.4f", metrics.rms)} peak=${String.format(java.util.Locale.US, "%.4f", metrics.peak)} leadingSilence=${String.format(java.util.Locale.US, "%.2f", metrics.leadingSilence)}s trailingSilence=${String.format(java.util.Locale.US, "%.2f", metrics.trailingSilence)}s")

            // Save debug WAV for controlled debug inspection (requirement 6)
            try {
                saveDebugWav(context, samples, "debug_stt_capture.wav")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to save debug_stt_capture.wav", e)
            }

            tSttStartNano = System.nanoTime()
            Log.i(TAG, "[PTT-DIAG] STT transcription started (${samples.size} samples, RMS=${String.format(java.util.Locale.US, "%.4f", metrics.rms)})")
            val t0 = System.currentTimeMillis()
            _state.value = _state.value.copy(sttStatus = SttStatus.TRANSCRIBING)
            
            val result = sttManager?.transcribe(samples)
            tSttEndNano = System.nanoTime()
            val elapsed = System.currentTimeMillis() - t0

            val resultLength = result?.text?.length ?: 0
            // Log [STT-RESULT] per Phase 12B specification
            Log.i(TAG, "[STT-RESULT] latency=${elapsed}ms resultLength=$resultLength")

            val pttToAudioStartMs = if (tPttPressNano > 0 && tAudioRecordStartNano > 0) (tAudioRecordStartNano - tPttPressNano) / 1_000_000.0 else 0.0
            val vadSpeechDurationMs = if (tVadSpeechStartNano > 0 && tVadSpeechEndNano > 0) (tVadSpeechEndNano - tVadSpeechStartNano) / 1_000_000.0 else 0.0
            val pttHoldMs = if (tPttPressNano > 0 && tPttReleaseNano > 0) (tPttReleaseNano - tPttPressNano) / 1_000_000.0 else 0.0
            val sttDurationMs = if (tSttStartNano > 0 && tSttEndNano > 0) (tSttEndNano - tSttStartNano) / 1_000_000.0 else 0.0

            Log.i(TAG, "[PTT-LATENCY] pttPress_to_audioStart_ms=${String.format(java.util.Locale.US, "%.1f", pttToAudioStartMs)} vadSpeechDuration_ms=${String.format(java.util.Locale.US, "%.1f", vadSpeechDurationMs)} pttHold_ms=${String.format(java.util.Locale.US, "%.1f", pttHoldMs)} sttDuration_ms=${String.format(java.util.Locale.US, "%.1f", sttDurationMs)}")
            
            if (result != null) {
                Log.i(TAG, "[PTT-DIAG] STT transcription ended in ${elapsed}ms: '${result.text.take(30)}...'")
                _state.value = _state.value.copy(
                    sttStatus = SttStatus.COMPLETE,
                    recognizedText = result.text,
                    lastSttResult = result
                )
            } else {
                Log.w(TAG, "[PTT-DIAG] STT transcription returned null in ${elapsed}ms")
                _state.value = _state.value.copy(sttStatus = SttStatus.ERROR)
            }
        }
    }

    /**
     * Releases all resources when the manager is destroyed.
     */
    fun release() {
        stopRecording()
        vadManager?.release()
        sttManager?.release()
        vadManager = null
        sttManager = null
        scope.cancel()
    }
}
