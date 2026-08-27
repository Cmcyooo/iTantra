package com.itantra.app.comm

import android.content.Context
import android.util.Log
import com.itantra.app.audio.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.time.Duration.Companion.milliseconds

/**
 * High-level transceiver state for the UI.
 */
enum class TransceiverState {
    IDLE,
    LISTENING,
    SPEAKING,
    TRANSCRIBING,
    FORWARDING,
    SENT,
    RECEIVING,
    PLAYING,
    ERROR
}

/**
 * Delivery status for Emergency Alerts.
 */
enum class AlertDeliveryStatus {
    NONE,
    SENDING,
    SENT,
    DELIVERED,
    FAILED
}

/**
 * COORDINATOR: Wires together Audio, VAD, STT, Transport, Alert Priority Queue, and TTS.
 * This is the primary engine for the iTantra user experience.
 */
class TransceiverManager(
    private val context: Context,
    private val audioManager: AudioCaptureManager,
    private val ttsManager: TtsManager,
    private val commManager: CommunicationManager,
    private val callSignManager: CallSignManager
) {
    val alertPlaybackManager: AlertPlaybackManager = AlertPlaybackManager(context, ttsManager)

    private val _uiState = MutableStateFlow(TransceiverState.IDLE)
    val uiState: StateFlow<TransceiverState> = _uiState.asStateFlow()

    // Mode control: NORMAL vs EMERGENCY
    private val _isEmergencyMode = MutableStateFlow(false)
    val isEmergencyMode: StateFlow<Boolean> = _isEmergencyMode.asStateFlow()

    // Outgoing alert delivery tracking
    private val _alertDeliveryStatus = MutableStateFlow(AlertDeliveryStatus.NONE)
    val alertDeliveryStatus: StateFlow<AlertDeliveryStatus> = _alertDeliveryStatus.asStateFlow()

    private val _lastAlertText = MutableStateFlow("")
    val lastAlertText: StateFlow<String> = _lastAlertText.asStateFlow()

    // Active incoming alert exposed from AlertPlaybackManager
    val activeIncomingAlert: StateFlow<P2PMessage?> = alertPlaybackManager.activeAlert

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private var lastSentText: String = ""
    private var lastReceivedText: String = ""
    private var pendingAckAlertId: String? = null

    companion object {
        private const val TAG = "TransceiverManager"
    }

    init {
        // Observe Audio/STT state to trigger sending
        scope.launch {
            audioManager.state.collect { state ->
                updateStateFromAudio(state)
            }
        }

        // Observe TTS state
        scope.launch {
            ttsManager.status.collect { status ->
                updateStateFromTts(status)
            }
        }

        // Observe Alert playback state
        scope.launch {
            alertPlaybackManager.isAlertPlaying.collect { playing ->
                if (playing) {
                    _uiState.value = TransceiverState.PLAYING
                } else if (_uiState.value == TransceiverState.PLAYING) {
                    _uiState.value = TransceiverState.IDLE
                }
            }
        }

        // Observe Incoming Messages
        commManager.setOnMessageReceivedListener { message: P2PMessage ->
            handleIncomingMessage(message)
        }

        // Observe ACK confirmations
        commManager.setOnAckReceivedListener { ackedMessageId ->
            if (pendingAckAlertId != null && pendingAckAlertId == ackedMessageId) {
                _alertDeliveryStatus.value = AlertDeliveryStatus.DELIVERED
                Log.i(TAG, "✓ Alert delivery confirmed (ACK received) for $ackedMessageId")
            }
        }
        
        // Identity exchange on connection
        scope.launch {
            commManager.connectionState.collect { state ->
                if (state == ConnectionState.CONNECTED) {
                    // Send local identity as an empty text message with senderName
                    commManager.sendText("", senderName = callSignManager.getCallSign())
                }
            }
        }
    }

    fun setEmergencyMode(enabled: Boolean) {
        _isEmergencyMode.value = enabled
        if (!enabled) {
            _alertDeliveryStatus.value = AlertDeliveryStatus.NONE
        }
        Log.i(TAG, "Transceiver mode switched to: ${if (enabled) "EMERGENCY" else "NORMAL"}")
    }

    private var pttReleaseTime: Long = 0
    private var sttStartTime: Long = 0
    private var sttEndTime: Long = 0

    private fun updateStateFromAudio(state: AudioState) {
        when {
            state.sttStatus == SttStatus.TRANSCRIBING -> {
                if (_uiState.value != TransceiverState.TRANSCRIBING) {
                    sttStartTime = android.os.SystemClock.elapsedRealtime()
                    _uiState.value = TransceiverState.TRANSCRIBING
                }
            }
            state.sttStatus == SttStatus.COMPLETE && state.recognizedText.isNotEmpty() -> {
                if (state.recognizedText != lastSentText) {
                    sttEndTime = android.os.SystemClock.elapsedRealtime()
                    lastSentText = state.recognizedText
                    if (_isEmergencyMode.value) {
                        sendRecognizedAlert(state.recognizedText)
                    } else {
                        sendRecognizedText(state.recognizedText)
                    }
                }
            }
            state.vadStatus == VadStatus.SPEAKING || state.vadStatus == VadStatus.SPEECH_DETECTED -> 
                _uiState.value = TransceiverState.SPEAKING
            state.isRecording -> _uiState.value = TransceiverState.LISTENING
            _uiState.value != TransceiverState.PLAYING && 
            _uiState.value != TransceiverState.RECEIVING &&
            _uiState.value != TransceiverState.FORWARDING &&
            _uiState.value != TransceiverState.SENT -> 
                _uiState.value = TransceiverState.IDLE
        }
    }

    private fun updateStateFromTts(status: TtsStatus) {
        when (status) {
            TtsStatus.SYNTHESIZING -> _uiState.value = TransceiverState.RECEIVING
            TtsStatus.PLAYING -> _uiState.value = TransceiverState.PLAYING
            TtsStatus.COMPLETE -> {
                scope.launch {
                    delay(1000.milliseconds)
                    if (_uiState.value == TransceiverState.PLAYING || _uiState.value == TransceiverState.RECEIVING) {
                        _uiState.value = TransceiverState.IDLE
                    }
                }
            }
            TtsStatus.ERROR -> _uiState.value = TransceiverState.ERROR
            else -> {}
        }
    }

    private fun sendRecognizedText(text: String) {
        scope.launch {
            val sendStartTime = android.os.SystemClock.elapsedRealtime()
            _uiState.value = TransceiverState.FORWARDING
            val activeLang = audioManager.languageModelManager.currentLanguage.value.code
            val sentMsg = commManager.sendText(
                text = text,
                language = activeLang,
                senderName = callSignManager.getCallSign()
            )
            val sendEndTime = android.os.SystemClock.elapsedRealtime()
            
            val pttToSttMs = if (pttReleaseTime > 0 && sttStartTime >= pttReleaseTime) "${sttStartTime - pttReleaseTime}ms" else "N/A"
            val sttDurationMs = if (sttEndTime >= sttStartTime && sttStartTime > 0) "${sttEndTime - sttStartTime}ms" else "N/A"
            val sttEndToSendStartMs = if (sttEndTime > 0) "${sendStartTime - sttEndTime}ms" else "N/A"
            val sendDurationMs = "${sendEndTime - sendStartTime}ms"

            Log.i(
                TAG,
                "[TELEMETRY-SENDER] utteranceId=${sentMsg.utteranceId} language=$activeLang STT_language=$activeLang " +
                "PTT_RELEASE->STT_START=$pttToSttMs STT_START->STT_END=$sttDurationMs " +
                "STT_END->SEND_START=$sttEndToSendStartMs SEND_START->SEND_COMPLETE=$sendDurationMs"
            )
            
            _uiState.value = TransceiverState.SENT
            delay(1500.milliseconds) // Show "SENT" for a bit
            if (_uiState.value == TransceiverState.SENT) {
                _uiState.value = TransceiverState.IDLE
            }
        }
    }

    private fun sendRecognizedAlert(text: String) {
        scope.launch {
            val sendStartTime = android.os.SystemClock.elapsedRealtime()
            _lastAlertText.value = text
            _uiState.value = TransceiverState.FORWARDING
            
            if (commManager.connectionState.value != ConnectionState.CONNECTED) {
                Log.w(TAG, "🚨 Alert cannot be delivered: Transport not connected")
                _alertDeliveryStatus.value = AlertDeliveryStatus.FAILED
                _uiState.value = TransceiverState.ERROR
                return@launch
            }

            _alertDeliveryStatus.value = AlertDeliveryStatus.SENDING
            val activeLang = audioManager.languageModelManager.currentLanguage.value.code
            try {
                val sentMsg = commManager.sendAlert(
                    text = text,
                    language = activeLang,
                    senderName = callSignManager.getCallSign()
                )
                val sendEndTime = android.os.SystemClock.elapsedRealtime()
                val pttToSttMs = if (pttReleaseTime > 0 && sttStartTime >= pttReleaseTime) "${sttStartTime - pttReleaseTime}ms" else "N/A"
                val sttDurationMs = if (sttEndTime >= sttStartTime && sttStartTime > 0) "${sttEndTime - sttStartTime}ms" else "N/A"
                val sttEndToSendStartMs = if (sttEndTime > 0) "${sendStartTime - sttEndTime}ms" else "N/A"
                val sendDurationMs = "${sendEndTime - sendStartTime}ms"

                Log.i(
                    TAG,
                    "[TELEMETRY-SENDER] utteranceId=${sentMsg.utteranceId} language=$activeLang STT_language=$activeLang " +
                    "PTT_RELEASE->STT_START=$pttToSttMs STT_START->STT_END=$sttDurationMs " +
                    "STT_END->SEND_START=$sttEndToSendStartMs SEND_START->SEND_COMPLETE=$sendDurationMs (EMERGENCY)"
                )
                
                pendingAckAlertId = sentMsg.messageId
                _alertDeliveryStatus.value = AlertDeliveryStatus.SENT
                _uiState.value = TransceiverState.SENT

                // ACK Timeout watchdog (5 seconds)
                scope.launch {
                    delay(5000.milliseconds)
                    // If still in SENDING or not confirmed, don't falsely claim delivered
                    if (_alertDeliveryStatus.value == AlertDeliveryStatus.SENDING) {
                        _alertDeliveryStatus.value = AlertDeliveryStatus.FAILED
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send alert", e)
                _alertDeliveryStatus.value = AlertDeliveryStatus.FAILED
                _uiState.value = TransceiverState.ERROR
            }
        }
    }

    private fun handleIncomingMessage(message: P2PMessage) {
        scope.launch {
            // Update peer call sign mapping
            if (!message.senderName.isNullOrEmpty()) {
                commManager.getConnectedPeerId()?.let { peerId ->
                    callSignManager.savePeerCallSign(peerId, message.senderName)
                }
            }
            
            // If it's just an identity exchange (empty text) or an ACK packet, don't enqueue for playback
            if (message.text.isEmpty() || message.messageType == P2PMessage.MESSAGE_TYPE_ACK) return@launch

            lastReceivedText = message.text
            _uiState.value = TransceiverState.RECEIVING

            Log.d(TAG, "Incoming P2P message: utteranceId=${message.utteranceId} lang=${message.language} alert=${message.isAlert}")

            // Dispatch to AlertPlaybackManager (it handles language propagation, TTS synthesis, and receiver telemetry)
            alertPlaybackManager.enqueueMessage(message)
        }
    }

    /**
     * Sends recognized alert text (exposed for testing / programmatic alert dispatch).
     */
    fun sendAlertMessage(text: String) {
        sendRecognizedAlert(text)
    }

    /**
     * Sends recognized normal text (exposed for testing / programmatic text dispatch).
     */
    fun sendNormalMessage(text: String) {
        sendRecognizedText(text)
    }

    /**
     * Start Push-to-Talk.
     */
    fun startTalk() {
        Log.i(TAG, "[PTT-DIAG] PTT button pressed -> startTalk()")
        pttReleaseTime = 0
        audioManager.startRecording()
    }

    /**
     * Release Push-to-Talk.
     */
    fun stopTalk() {
        pttReleaseTime = android.os.SystemClock.elapsedRealtime()
        Log.i(TAG, "[PTT-DIAG] PTT button released -> stopTalk()")
        audioManager.stopRecording(forceFinalize = true)
    }

    fun release() {
        alertPlaybackManager.release()
        scope.cancel()
    }
}
