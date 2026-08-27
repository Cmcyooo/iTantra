package com.itantra.app.comm

import android.os.SystemClock
import android.util.Log
import com.itantra.app.audio.AudioCaptureManager
import com.itantra.app.audio.SttStatus
import com.itantra.app.ui.TransportMode
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * 11-State workflow for zero-configuration emergency communication.
 */
enum class EmergencyFlowState {
    IDLE,
    SEARCHING,
    SELECTING_PEER,
    CONNECTING,
    READY,
    LISTENING,
    PROCESSING,
    SENDING,
    WAITING_FOR_ACK,
    DELIVERED,
    FAILED
}

/**
 * Coordinates end-to-end zero-configuration emergency communication:
 * Auto-discovery -> Best Peer/Transport Selection -> Auto-connect -> Ready ->
 * Speech Capture -> VAD -> STT -> High-Priority Alert -> Remote ACK Confirmation.
 */
class ZeroConfigEmergencyManager(
    private val peerRegistry: PeerRegistry,
    private val commManager: CommunicationManager,
    private val transceiverManager: TransceiverManager,
    private val audioManager: AudioCaptureManager,
    private val onSwitchTransport: (TransportMode, String?) -> Unit
) {

    companion object {
        private const val TAG = "ZeroConfigEmergency"
    }

    var searchTimeoutMs: Long = 6000L
    var connectTimeoutMs: Long = 7000L
    var ackTimeoutMs: Long = 5000L

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _flowState = MutableStateFlow(EmergencyFlowState.IDLE)
    val flowState: StateFlow<EmergencyFlowState> = _flowState.asStateFlow()

    private val _statusText = MutableStateFlow("Ready")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    private val _activePeerName = MutableStateFlow<String?>(null)
    val activePeerName: StateFlow<String?> = _activePeerName.asStateFlow()

    private val _activeTransport = MutableStateFlow<TransportMode?>(null)
    val activeTransport: StateFlow<TransportMode?> = _activeTransport.asStateFlow()

    private var emergencyJob: Job? = null
    private var ackWatchdogJob: Job? = null
    private var pendingAlertId: String? = null
    private var retryCount = 0

    init {
        // Observe ACK confirmations from CommunicationManager
        commManager.setOnAckReceivedListener { ackedId ->
            if (pendingAlertId != null && pendingAlertId == ackedId) {
                Log.i(TAG, "[EMERGENCY-DIAG] Received ACK for alert $ackedId -> DELIVERED")
                ackWatchdogJob?.cancel()
                _flowState.value = EmergencyFlowState.DELIVERED
                _statusText.value = "Alert delivered and confirmed by ${_activePeerName.value ?: "peer"}"
            }
        }

        // Observe connection state transitions
        scope.launch {
            commManager.connectionState.collect { connState ->
                if (connState == ConnectionState.CONNECTED && _flowState.value == EmergencyFlowState.CONNECTING) {
                    Log.i(TAG, "[EMERGENCY-DIAG] Connection established -> READY")
                    _flowState.value = EmergencyFlowState.READY
                    _statusText.value = "Connected to ${_activePeerName.value ?: "peer"}"
                } else if (connState == ConnectionState.DISCONNECTED && _flowState.value == EmergencyFlowState.READY) {
                    Log.w(TAG, "[EMERGENCY-DIAG] Peer disconnected while in READY state")
                    _statusText.value = "Disconnected"
                }
            }
        }
    }

    /**
     * Triggered when the user taps 🚨 HELP.
     * Begins zero-configuration automated discovery, transport selection, and connection.
     */
    fun triggerEmergency() {
        Log.i(TAG, "[EMERGENCY-DIAG] triggerEmergency() invoked")
        emergencyJob?.cancel()
        transceiverManager.setEmergencyMode(true)
        retryCount = 0

        // If already connected, immediately transition to READY
        if (commManager.connectionState.value == ConnectionState.CONNECTED) {
            val candidate = peerRegistry.getBestCandidate()
            _activePeerName.value = candidate?.first?.displayName ?: "Connected Station"
            _activeTransport.value = candidate?.second ?: TransportMode.WIFI
            _flowState.value = EmergencyFlowState.READY
            _statusText.value = "Ready • Connected to ${_activePeerName.value}"
            Log.i(TAG, "[EMERGENCY-DIAG] Already connected to ${_activePeerName.value}. READY.")
            return
        }

        _flowState.value = EmergencyFlowState.SEARCHING
        _statusText.value = "Finding nearest available iTantra device..."

        emergencyJob = scope.launch {
            Log.i(TAG, "[EMERGENCY-DIAG] Searching for reachable peers...")

            // Check if a candidate is already in the registry
            var candidate = peerRegistry.getBestCandidate()
            val startSearch = SystemClock.elapsedRealtime()

            while (candidate == null && (SystemClock.elapsedRealtime() - startSearch) < searchTimeoutMs) {
                delay(300)
                candidate = peerRegistry.getBestCandidate()
            }

            if (candidate == null) {
                Log.w(TAG, "[EMERGENCY-DIAG] No reachable peer found after ${searchTimeoutMs}ms")
                _flowState.value = EmergencyFlowState.FAILED
                _statusText.value = "No reachable iTantra device found"
                return@launch
            }

            val (peer, transport) = candidate
            _flowState.value = EmergencyFlowState.SELECTING_PEER
            _activePeerName.value = peer.displayName
            _activeTransport.value = transport
            Log.i(TAG, "[EMERGENCY-DIAG] Selected peer: ${peer.displayName} via $transport")

            _flowState.value = EmergencyFlowState.CONNECTING
            _statusText.value = "Connecting to ${peer.displayName}..."

            val targetAddress = peer.availableTransports[transport]
            onSwitchTransport(transport, targetAddress)

            // Wait for connection with timeout
            val connectStart = SystemClock.elapsedRealtime()
            while (commManager.connectionState.value != ConnectionState.CONNECTED &&
                (SystemClock.elapsedRealtime() - connectStart) < connectTimeoutMs) {
                delay(200)
            }

            if (commManager.connectionState.value == ConnectionState.CONNECTED) {
                _flowState.value = EmergencyFlowState.READY
                _statusText.value = "Connected to ${peer.displayName}"
                Log.i(TAG, "[EMERGENCY-DIAG] Auto-connect succeeded. READY for speech.")
            } else {
                Log.w(TAG, "[EMERGENCY-DIAG] Connection attempt to ${peer.displayName} timed out")
                // Try fallback transport if available
                val alternateTransport = peer.availableTransports.keys.firstOrNull { it != transport }
                if (alternateTransport != null) {
                    Log.i(TAG, "[EMERGENCY-DIAG] Attempting fallback to $alternateTransport")
                    _activeTransport.value = alternateTransport
                    val fallbackAddress = peer.availableTransports[alternateTransport]
                    onSwitchTransport(alternateTransport, fallbackAddress)
                    delay(3000)
                    if (commManager.connectionState.value == ConnectionState.CONNECTED) {
                        _flowState.value = EmergencyFlowState.READY
                        _statusText.value = "Connected to ${peer.displayName} • $alternateTransport"
                        return@launch
                    }
                }

                _flowState.value = EmergencyFlowState.FAILED
                _statusText.value = "Failed to connect to ${peer.displayName}"
            }
        }
    }

    /**
     * Start speech capture when user presses/holds emergency talk button.
     */
    fun startSpeechCapture() {
        if (_flowState.value != EmergencyFlowState.READY) {
            Log.w(TAG, "[EMERGENCY-DIAG] Cannot start speech capture in state ${_flowState.value}")
            return
        }
        _flowState.value = EmergencyFlowState.LISTENING
        _statusText.value = "Recording emergency speech..."
        Log.i(TAG, "[EMERGENCY-DIAG] Speech capture started")
        transceiverManager.startTalk()
    }

    /**
     * Stop speech capture when user releases talk button.
     * Finalizes audio, runs STT, and prepares alert.
     */
    fun stopSpeechCapture() {
        if (_flowState.value != EmergencyFlowState.LISTENING) return
        _flowState.value = EmergencyFlowState.PROCESSING
        _statusText.value = "Processing emergency audio..."
        Log.i(TAG, "[EMERGENCY-DIAG] Speech capture ended -> Finalizing STT")
        transceiverManager.stopTalk()

        scope.launch {
            // Wait for STT to produce recognized text
            val sttStart = SystemClock.elapsedRealtime()
            var recognized = audioManager.state.value.recognizedText
            while (recognized.isEmpty() && (SystemClock.elapsedRealtime() - sttStart) < 4000L) {
                delay(100)
                recognized = audioManager.state.value.recognizedText
            }

            if (recognized.isNotEmpty()) {
                sendEmergencyAlert(recognized)
            } else {
                Log.w(TAG, "[EMERGENCY-DIAG] STT produced empty text")
                _flowState.value = EmergencyFlowState.FAILED
                _statusText.value = "No speech detected"
            }
        }
    }

    /**
     * Transmits high-priority alert packet and waits for remote ACK.
     */
    fun sendEmergencyAlert(alertText: String) {
        _flowState.value = EmergencyFlowState.SENDING
        _statusText.value = "Sending alert..."
        Log.i(TAG, "[EMERGENCY-DIAG] Dispatching alert message")

        val activeLang = audioManager.languageModelManager.currentLanguage.value.code
        val sentMsg = commManager.sendAlert(
            text = alertText,
            language = activeLang,
            senderName = _activePeerName.value
        )
        pendingAlertId = sentMsg.messageId

        _flowState.value = EmergencyFlowState.WAITING_FOR_ACK
        _statusText.value = "Waiting for confirmation..."
        Log.i(TAG, "[EMERGENCY-DIAG] Waiting for ACK on alert ${sentMsg.messageId}")

        armAckWatchdog(alertText)
    }

    private fun armAckWatchdog(alertText: String) {
        ackWatchdogJob?.cancel()
        ackWatchdogJob = scope.launch {
            delay(ackTimeoutMs)
            if (_flowState.value == EmergencyFlowState.WAITING_FOR_ACK) {
                if (retryCount < 1) {
                    retryCount++
                    Log.w(TAG, "[EMERGENCY-DIAG] ACK timed out. Retrying alert send (attempt $retryCount)...")
                    _statusText.value = "Retrying alert delivery..."
                    sendEmergencyAlert(alertText)
                } else {
                    Log.e(TAG, "[EMERGENCY-DIAG] Alert not delivered (ACK timeout after retries)")
                    _flowState.value = EmergencyFlowState.FAILED
                    _statusText.value = "Alert not delivered (no response from peer)"
                }
            }
        }
    }

    /**
     * Resets emergency flow back to IDLE.
     */
    fun reset() {
        Log.i(TAG, "[EMERGENCY-DIAG] reset() invoked")
        emergencyJob?.cancel()
        ackWatchdogJob?.cancel()
        pendingAlertId = null
        retryCount = 0
        _flowState.value = EmergencyFlowState.IDLE
        _statusText.value = "Ready"
        transceiverManager.setEmergencyMode(false)
    }

    fun release() {
        emergencyJob?.cancel()
        ackWatchdogJob?.cancel()
        scope.cancel()
    }
}
