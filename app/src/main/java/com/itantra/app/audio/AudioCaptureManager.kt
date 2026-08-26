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
        
        // Initialize VAD if not already done
        if (vadManager == null) {
            vadManager = VadManager(context)
        } else {
            vadManager?.reset()
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
                MediaRecorder.AudioSource.MIC,
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
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                Log.e(TAG, "AudioRecord failed to start recording.")
                record.release()
                _state.value = _state.value.copy(
                    errorMessage = "Audio recorder could not start."
                )
                return
            }

            audioRecord = record
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
        var latestRms = 0.0

        try {
            while (currentCoroutineContext().isActive && _state.value.isRecording) {
                val readCount = record.read(buffer, 0, buffer.size)

                if (readCount > 0) {
                    totalSamples += readCount
                    latestRms = calculateRms(buffer, readCount)
                    
                    if (totalSamples < 5000) {
                        Log.d(TAG, "First samples: ${buffer.take(10).joinToString()}, RMS: $latestRms")
                    }
                    
                    // Process VAD
                    val currentVadStatus = vadManager?.process(
                        if (readCount == buffer.size) buffer else buffer.copyOfRange(0, readCount)
                    ) ?: VadStatus.SILENCE

                    if (currentVadStatus != _state.value.vadStatus) {
                        Log.d(TAG, "VAD Status Changed: $currentVadStatus")
                    }

                    // Deliver PCM chunk to consumer (e.g., future VAD) without copying if possible
                    onAudioChunkCaptured?.invoke(buffer, readCount)

                    // Throttle UI state updates to ~10 Hz to prevent excessive recompositions
                    val now = SystemClock.uptimeMillis()
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
     */
    @Synchronized
    fun stopRecording() {
        if (!_state.value.isRecording && audioRecord == null) return

        _state.value = _state.value.copy(isRecording = false)
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
     * Releases all resources when the manager is destroyed.
     */
    fun release() {
        stopRecording()
        vadManager?.release()
        vadManager = null
        scope.cancel()
    }
}
