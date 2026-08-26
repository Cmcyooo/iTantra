package com.itantra.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log
import com.itantra.app.comm.P2PMessage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Collections
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Coordinates priority-based speech playback for Emergency Alerts and Normal P2P messages.
 *
 * Responsibilities:
 * 1. Android AudioFocus management (AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE / USAGE_ALARM).
 * 2. Dedicated alert playback path that preempts normal TTS.
 * 3. Prevents normal messages from interrupting active alerts.
 * 4. FIFO queuing of multiple incoming alerts.
 * 5. Deduplication using messageId.
 * 6. Thread-safe state exposure for UI notifications.
 */
class AlertPlaybackManager(
    private val context: Context,
    private val ttsManager: TtsManager
) {
    companion object {
        private const val TAG = "AlertPlaybackManager"
        private const val MAX_DEDUP_CACHE_SIZE = 200
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var alertFocusRequest: AudioFocusRequest? = null

    // Queues
    private val alertQueue = ConcurrentLinkedQueue<P2PMessage>()
    private val normalQueue = ConcurrentLinkedQueue<P2PMessage>()

    // Deduplication cache for played alerts
    private val playedAlertIds = Collections.synchronizedSet(LinkedHashSet<String>())

    // State flows
    private val _activeAlert = MutableStateFlow<P2PMessage?>(null)
    val activeAlert: StateFlow<P2PMessage?> = _activeAlert.asStateFlow()

    private val _isAlertPlaying = MutableStateFlow(false)
    val isAlertPlaying: StateFlow<Boolean> = _isAlertPlaying.asStateFlow()

    private val _isNormalPlaying = MutableStateFlow(false)
    val isNormalPlaying: StateFlow<Boolean> = _isNormalPlaying.asStateFlow()

    private val _focusError = MutableStateFlow<String?>(null)
    val focusError: StateFlow<String?> = _focusError.asStateFlow()

    // Latency metrics callback / logging
    var onAlertPlaybackStarted: ((P2PMessage, Long) -> Unit)? = null
    var onAlertPlaybackFinished: ((P2PMessage, Long) -> Unit)? = null

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /**
     * Checks if an alert was already played or queued (deduplication check).
     */
    fun isAlertDuplicate(messageId: String): Boolean {
        synchronized(playedAlertIds) {
            return playedAlertIds.contains(messageId)
        }
    }

    /**
     * Enqueues an incoming P2P message for audio playback according to priority.
     */
    fun enqueueMessage(message: P2PMessage) {
        if (message.text.isBlank()) return

        if (message.isAlert) {
            // Step 1: Deduplication
            synchronized(playedAlertIds) {
                if (playedAlertIds.contains(message.messageId)) {
                    Log.i(TAG, "Duplicate alert packet ignored: ${message.messageId}")
                    return
                }
                playedAlertIds.add(message.messageId)
                if (playedAlertIds.size > MAX_DEDUP_CACHE_SIZE) {
                    val it = playedAlertIds.iterator()
                    if (it.hasNext()) {
                        it.next()
                        it.remove()
                    }
                }
            }

            Log.i(TAG, "Queuing HIGH PRIORITY alert: '${message.text}' (id: ${message.messageId})")

            // Preempt normal playback if currently active
            if (_isNormalPlaying.value) {
                Log.i(TAG, "Preempting active normal TTS for incoming emergency alert")
                ttsManager.stop()
                _isNormalPlaying.value = false
            }

            alertQueue.add(message)
            processNext()
        } else {
            // Normal priority message
            Log.d(TAG, "Queuing normal message: '${message.text}'")
            normalQueue.add(message)
            processNext()
        }
    }

    @Synchronized
    private fun processNext() {
        // If an alert is actively playing, nothing can preempt it
        if (_isAlertPlaying.value) {
            Log.d(TAG, "Alert is actively playing; remaining items wait in queue.")
            return
        }

        // Priority 1: High priority alert queue
        val nextAlert = alertQueue.poll()
        if (nextAlert != null) {
            _isAlertPlaying.value = true
            _activeAlert.value = nextAlert
            playAlertInternal(nextAlert)
            return
        }

        // Priority 2: Normal message queue (only if no alert is playing)
        if (!_isNormalPlaying.value) {
            val nextNormal = normalQueue.poll()
            if (nextNormal != null) {
                _isNormalPlaying.value = true
                playNormalInternal(nextNormal)
            }
        }
    }

    private fun playAlertInternal(alert: P2PMessage) {
        scope.launch {
            val alertStartTime = System.currentTimeMillis()
            onAlertPlaybackStarted?.invoke(alert, alertStartTime)

            Log.i(TAG, "🚨 Commencing Alert Playback: '${alert.text}' from ${alert.senderName ?: "Unknown"}")

            // Request AudioFocus with Exclusive Priority
            val focusGranted = requestAlertFocus()
            if (!focusGranted) {
                _focusError.value = "Audio focus unavailable for emergency alert"
                Log.w(TAG, "Audio focus not granted; continuing playback anyway under platform limits.")
            } else {
                _focusError.value = null
            }

            // Synthesize using Piper TTS
            val generatedAudio = ttsManager.generateSpeech(alert.text)
            if (generatedAudio == null || generatedAudio.samples.isEmpty()) {
                Log.e(TAG, "Failed to synthesize speech for alert '${alert.text}'")
                finishAlert(alert, alertStartTime)
                return@launch
            }

            // Play via AudioTrack with USAGE_ALARM / SPEECH
            val alertAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            ttsManager.playAudioWithAttributes(
                samples = generatedAudio.samples,
                sampleRate = generatedAudio.sampleRate,
                attributes = alertAttributes
            ) {
                finishAlert(alert, alertStartTime)
            }
        }
    }

    private fun finishAlert(alert: P2PMessage, startTime: Long) {
        scope.launch {
            val finishTime = System.currentTimeMillis()
            Log.i(TAG, "✓ Emergency alert playback completed in ${finishTime - startTime}ms")
            onAlertPlaybackFinished?.invoke(alert, finishTime)

            // Abandon alert focus and restore audio state
            abandonAlertFocus()
            _isAlertPlaying.value = false
            _activeAlert.value = null

            // Process next queued alert or queued normal message
            processNext()
        }
    }

    private fun playNormalInternal(message: P2PMessage) {
        scope.launch {
            _isNormalPlaying.value = true
            Log.d(TAG, "Playing normal message: '${message.text}'")

            ttsManager.speak(message.text) {
                scope.launch {
                    _isNormalPlaying.value = false
                    // Continue queue processing
                    processNext()
                }
            }
        }
    }

    private fun requestAlertFocus(): Boolean {
        val am = audioManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(attributes)
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener { focusChange ->
                    Log.d(TAG, "Alert AudioFocus changed: $focusChange")
                }
                .build()
            alertFocusRequest = req
            am.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(
                { /* listener */ },
                AudioManager.STREAM_ALARM,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAlertFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            alertFocusRequest?.let { am.abandonAudioFocusRequest(it) }
            alertFocusRequest = null
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(null)
        }
    }

    /**
     * Resets queues and active alerts (used during teardown/tests).
     */
    fun reset() {
        alertQueue.clear()
        normalQueue.clear()
        abandonAlertFocus()
        _activeAlert.value = null
        _isAlertPlaying.value = false
        _isNormalPlaying.value = false
        _focusError.value = null
    }

    fun release() {
        reset()
        scope.cancel()
    }
}
