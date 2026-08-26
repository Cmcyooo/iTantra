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
 * COORDINATOR: Wires together Audio, VAD, STT, Transport, and TTS.
 * This is the primary engine for the iTantra user experience.
 */
class TransceiverManager(
    private val context: Context,
    private val audioManager: AudioCaptureManager,
    private val ttsManager: TtsManager,
    private val commManager: CommunicationManager,
    private val callSignManager: CallSignManager
) {
    private val _uiState = MutableStateFlow(TransceiverState.IDLE)
    val uiState: StateFlow<TransceiverState> = _uiState.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private var lastSentText: String = ""
    private var lastReceivedText: String = ""
    
    private var pttReleaseTime: Long = 0

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

        // Observe Incoming Messages
        commManager.setOnMessageReceivedListener { message: P2PMessage ->
            handleIncomingMessage(message)
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

    private fun updateStateFromAudio(state: AudioState) {
        when {
            state.sttStatus == SttStatus.TRANSCRIBING -> {
                if (_uiState.value != TransceiverState.TRANSCRIBING) {
                    val sttStartTime = System.currentTimeMillis()
                    if (pttReleaseTime > 0) {
                        Log.d(TAG, "Latency: PTT Release -> STT Start: ${sttStartTime - pttReleaseTime}ms")
                    }
                    _uiState.value = TransceiverState.TRANSCRIBING
                }
            }
            state.sttStatus == SttStatus.COMPLETE && state.recognizedText.isNotEmpty() -> {
                if (state.recognizedText != lastSentText) {
                    lastSentText = state.recognizedText
                    sendRecognizedText(state.recognizedText)
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
            val sendStartTime = System.currentTimeMillis()
            _uiState.value = TransceiverState.FORWARDING
            commManager.sendText(text, senderName = callSignManager.getCallSign())
            val sendEndTime = System.currentTimeMillis()
            Log.d(TAG, "Latency: STT End -> Network Send: ${sendEndTime - sendStartTime}ms")
            
            _uiState.value = TransceiverState.SENT
            delay(1500.milliseconds) // Show "SENT" for a bit
            if (_uiState.value == TransceiverState.SENT) {
                _uiState.value = TransceiverState.IDLE
            }
        }
    }

    private fun handleIncomingMessage(message: P2PMessage) {
        scope.launch {
            val receiveTime = System.currentTimeMillis()
            val transportLatency = receiveTime - message.timestamp
            Log.d(TAG, "Incoming P2P message: ${message.text}. Sender: ${message.senderName}. Network Latency: ${transportLatency}ms")
            
            // Update peer call sign mapping
            if (!message.senderName.isNullOrEmpty()) {
                commManager.getConnectedPeerId()?.let { peerId ->
                    callSignManager.savePeerCallSign(peerId, message.senderName)
                }
            }
            
            // If it's just an identity exchange (empty text), don't process further
            if (message.text.isEmpty()) return@launch

            lastReceivedText = message.text
            _uiState.value = TransceiverState.RECEIVING
            ttsManager.speak(message.text)
        }
    }

    /**
     * Start Push-to-Talk.
     */
    fun startTalk() {
        pttReleaseTime = 0
        audioManager.startRecording()
    }

    /**
     * Release Push-to-Talk.
     */
    fun stopTalk() {
        pttReleaseTime = System.currentTimeMillis()
        audioManager.stopRecording(forceFinalize = true)
    }

    fun release() {
        scope.cancel()
    }
}
