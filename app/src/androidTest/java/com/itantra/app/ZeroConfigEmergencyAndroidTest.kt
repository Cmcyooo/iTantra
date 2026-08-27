package com.itantra.app

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.itantra.app.audio.*
import com.itantra.app.comm.*
import com.itantra.app.ui.TransportMode
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@org.junit.FixMethodOrder(org.junit.runners.MethodSorters.NAME_ASCENDING)
class ZeroConfigEmergencyAndroidTest {

    companion object {
        private const val TAG = "ZeroConfigEmergencyTest"
        private var sharedAudioManager: AudioCaptureManager? = null
        private var sharedTtsManager: TtsManager? = null

        @org.junit.BeforeClass
        @JvmStatic
        fun initAll() {
            val ctx = ApplicationProvider.getApplicationContext<Context>()
            sharedAudioManager = AudioCaptureManager(ctx)
            sharedTtsManager = TtsManager(ctx)
        }

        @org.junit.AfterClass
        @JvmStatic
        fun cleanAll() {
            sharedAudioManager?.release()
            sharedTtsManager?.release()
        }
    }

    private lateinit var context: Context
    private lateinit var peerRegistry: PeerRegistry
    private lateinit var mockTransport: MockTransport
    private lateinit var commManager: CommunicationManager
    private lateinit var transceiverManager: TransceiverManager
    private lateinit var emergencyManager: ZeroConfigEmergencyManager

    class MockTransport : Transport {
        private val _connState = kotlinx.coroutines.flow.MutableStateFlow(ConnectionState.DISCONNECTED)
        override val connectionState = _connState
        override val lastError = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
        private var listener: ((P2PMessage) -> Unit)? = null
        var lastSentMessage: P2PMessage? = null

        override fun connect(targetAddress: String?) {
            _connState.value = ConnectionState.CONNECTED
        }

        override fun disconnect() {
            _connState.value = ConnectionState.DISCONNECTED
        }

        override fun getConnectedPeerId(): String? = "mock_peer"

        override fun sendMessage(message: P2PMessage) {
            lastSentMessage = message
        }

        override fun setOnMessageReceivedListener(listener: (P2PMessage) -> Unit) {
            this.listener = listener
        }

        fun simulateReceive(message: P2PMessage) {
            listener?.invoke(message)
        }
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        peerRegistry = PeerRegistry()
        mockTransport = MockTransport()
        commManager = CommunicationManager(mockTransport)
        val callSignManager = CallSignManager(context)
        transceiverManager = TransceiverManager(context, sharedAudioManager!!, sharedTtsManager!!, commManager, callSignManager)

        emergencyManager = ZeroConfigEmergencyManager(
            peerRegistry = peerRegistry,
            commManager = commManager,
            transceiverManager = transceiverManager,
            audioManager = sharedAudioManager!!,
            onSwitchTransport = { mode, address ->
                mockTransport.connect(address)
            }
        )
    }

    @After
    fun tearDown() {
        emergencyManager.reset()
    }

    @Test
    fun test01_PeerRegistryAggregationAndBestCandidate() {
        Log.i(TAG, "=== TEST 01: PeerRegistry Aggregation ===")
        // 1. Add Wi-Fi peer
        val wifiDev = DiscoveryManager.DiscoveredDevice(
            name = "iTantra-Station-Alpha",
            ip = "192.168.1.100",
            port = 8888
        )
        peerRegistry.updateFromWifi(listOf(wifiDev))

        val best = peerRegistry.getBestCandidate()
        assertNotNull("Reachable Wi-Fi peer should be best candidate", best)
        assertEquals("Station-Alpha", best!!.first.displayName)
        assertEquals(TransportMode.WIFI, best.second)

        // 2. Mark as connected
        peerRegistry.updateConnectionStatus(best.first.peerId, TransportMode.WIFI, isConnected = true)
        val connectedBest = peerRegistry.getBestCandidate()
        assertTrue("Best peer should be marked connected", connectedBest!!.first.isConnected)
        Log.i(TAG, "✓ TEST 01 PASSED: PeerRegistry successfully aggregated and ranked peers.")
    }

    @Test
    fun test02_ZeroConfigEmergencyWorkflowWhenConnected() {
        runBlocking {
            Log.i(TAG, "=== TEST 02: Zero-Config Emergency Workflow When Connected ===")
            // Connect mock transport
            mockTransport.connect(null)
            delay(100)
            peerRegistry.updateConnectionStatus("peer_station_bravo", TransportMode.WIFI, isConnected = true)

            assertEquals(EmergencyFlowState.IDLE, emergencyManager.flowState.value)

            // Trigger emergency
            emergencyManager.triggerEmergency()
            // When already connected, it should immediately be READY
            assertEquals(EmergencyFlowState.READY, emergencyManager.flowState.value)
            Log.i(TAG, "Emergency Manager transitioned immediately to READY because peer is connected.")

            // Send simulated alert
            emergencyManager.sendEmergencyAlert("Mayday test alert")
            assertEquals(EmergencyFlowState.WAITING_FOR_ACK, emergencyManager.flowState.value)
            assertNotNull("Alert message should have been transmitted", mockTransport.lastSentMessage)
            val alert = mockTransport.lastSentMessage!!
            assertEquals(P2PMessage.MESSAGE_TYPE_ALERT, alert.messageType)
            assertEquals(P2PMessage.PRIORITY_HIGH, alert.priority)
            assertEquals("Mayday test alert", alert.text)

            // Simulate remote ACK arrival
            val ack = P2PMessage(
                messageId = "ack_1",
                timestamp = System.currentTimeMillis(),
                text = alert.messageId,
                messageType = P2PMessage.MESSAGE_TYPE_ACK,
                priority = P2PMessage.PRIORITY_HIGH
            )
            mockTransport.simulateReceive(ack)

            delay(300)
            assertEquals("Upon receiving ACK, state must be DELIVERED", EmergencyFlowState.DELIVERED, emergencyManager.flowState.value)
            Log.i(TAG, "✓ TEST 02 PASSED: Alert delivered and confirmed via ACK.")
        }
    }

    @Test
    fun test03_ZeroConfigEmergencyNoPeerFailureHandling() {
        runBlocking {
            Log.i(TAG, "=== TEST 03: No Peer Discovered Failure Handling ===")
            peerRegistry.clear()
            mockTransport.disconnect()

            emergencyManager.searchTimeoutMs = 800L
            emergencyManager.triggerEmergency()
            assertEquals(EmergencyFlowState.SEARCHING, emergencyManager.flowState.value)

            // Wait for search timeout (800ms + 400ms margin)
            Log.i(TAG, "Waiting for search timeout to verify honest failure handling...")
            delay(1200)

            assertEquals("When no peer reachable, state must be FAILED", EmergencyFlowState.FAILED, emergencyManager.flowState.value)
            assertTrue("Status text must indicate no device found", emergencyManager.statusText.value.contains("No reachable iTantra device"))

            // Reset
            emergencyManager.reset()
            assertEquals(EmergencyFlowState.IDLE, emergencyManager.flowState.value)
            Log.i(TAG, "✓ TEST 03 PASSED: System honestly reported failure without false delivery claims.")
        }
    }
}
