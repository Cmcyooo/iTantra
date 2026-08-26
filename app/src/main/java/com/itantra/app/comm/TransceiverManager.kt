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
    SENDING,
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
    private val commManager: CommunicationManager
) {
    private val _uiState = MutableStateFlow(TransceiverState.IDLE)
    val uiState: StateFlow<TransceiverState> = _uiState.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private var lastSentText: String = ""
    private var lastReceivedText: String = ""

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
        
        // Observe Network state for receiving/sending indicators
        // (Simple implementation: just rely on the above for now)
    }

    private fun updateStateFromAudio(state: AudioState) {
        when {
            state.sttStatus == SttStatus.TRANSCRIBING -> _uiState.value = TransceiverState.TRANSCRIBING
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
            _uiState.value != TransceiverState.SENDING -> 
                _uiState.value = TransceiverState.IDLE
        }
    }

    private fun updateStateFromTts(status: TtsStatus) {
        when (status) {
            TtsStatus.SYNTHESIZING -> _uiState.value = TransceiverState.RECEIVING
            TtsStatus.PLAYING -> _uiState.value = TransceiverState.PLAYING
            TtsStatus.COMPLETE -> _uiState.value = TransceiverState.IDLE
            TtsStatus.ERROR -> _uiState.value = TransceiverState.ERROR
            else -> {}
        }
    }

    private fun sendRecognizedText(text: String) {
        scope.launch {
            _uiState.value = TransceiverState.SENDING
            commManager.sendText(text)
            delay(500.milliseconds) // Visual feedback for "Sending"
            if (_uiState.value == TransceiverState.SENDING) {
                _uiState.value = TransceiverState.IDLE
            }
        }
    }

    private fun handleIncomingMessage(message: P2PMessage) {
        scope.launch {
            Log.d(TAG, "Incoming P2P message: ${message.text}")
            lastReceivedText = message.text
            _uiState.value = TransceiverState.RECEIVING
            ttsManager.speak(message.text)
        }
    }

    /**
     * Start Push-to-Talk.
     */
    fun startTalk() {
        audioManager.startRecording()
    }

    /**
     * Release Push-to-Talk.
     */
    fun stopTalk() {
        audioManager.stopRecording()
    }

    fun release() {
        scope.cancel()
    }
}
