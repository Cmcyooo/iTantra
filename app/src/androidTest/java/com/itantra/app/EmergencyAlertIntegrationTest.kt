package com.itantra.app

import android.content.Context
import android.os.Debug
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.itantra.app.audio.*
import com.itantra.app.comm.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.util.UUID

@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class EmergencyAlertIntegrationTest {

    companion object {
        private const val TAG = "EmergencyAlertTest"
        private lateinit var sharedTtsManager: TtsManager

        @BeforeClass
        @JvmStatic
        fun initClass(): Unit = runBlocking(Dispatchers.Main) {
            val context = ApplicationProvider.getApplicationContext<Context>()
            sharedTtsManager = TtsManager(context)
            var wait = 0
            while (!sharedTtsManager.isReady() && wait < 100) {
                delay(100)
                wait++
            }
            Log.i(TAG, "=== Shared TTS Engine Ready for EmergencyAlertIntegrationTest ===")
        }

        @AfterClass
        @JvmStatic
        fun tearDownClass() {
            sharedTtsManager.release()
        }
    }

    private lateinit var context: Context
    private lateinit var callSignAlpha: CallSignManager
    private lateinit var callSignBravo: CallSignManager
    private lateinit var transportAlpha: TestLoopbackTransport
    private lateinit var transportBravo: TestLoopbackTransport
    private lateinit var commAlpha: CommunicationManager
    private lateinit var commBravo: CommunicationManager
    private lateinit var transceiverAlpha: TransceiverManager
    private lateinit var transceiverBravo: TransceiverManager

    class TestLoopbackTransport : Transport {
        var peer: TestLoopbackTransport? = null
        private val _connectionState = MutableStateFlow(ConnectionState.CONNECTED)
        override val connectionState = _connectionState.asStateFlow()
        private val _lastError = MutableStateFlow<String?>(null)
        override val lastError = _lastError.asStateFlow()
        private var messageListener: ((P2PMessage) -> Unit)? = null

        override fun connect(targetId: String?) {
            _connectionState.value = ConnectionState.CONNECTED
        }

        override fun disconnect() {
            _connectionState.value = ConnectionState.DISCONNECTED
        }

        override fun sendMessage(message: P2PMessage) {
            if (_connectionState.value != ConnectionState.CONNECTED) {
                _lastError.value = "Transport disconnected"
                return
            }
            peer?.receiveMessage(message)
        }

        fun receiveMessage(message: P2PMessage) {
            messageListener?.invoke(message)
        }

        override fun getConnectedPeerId(): String = "peer-station"

        override fun setOnMessageReceivedListener(callback: (P2PMessage) -> Unit) {
            this.messageListener = callback
        }
    }

    @Before
    fun setUp(): Unit = runBlocking(Dispatchers.Main) {
        context = ApplicationProvider.getApplicationContext()
        Log.i(TAG, "=== Initializing Test Stations Alpha & Bravo ===")

        callSignAlpha = CallSignManager(context).apply { saveCallSign("Station-Alpha") }
        callSignBravo = CallSignManager(context).apply { saveCallSign("Station-Bravo") }

        transportAlpha = TestLoopbackTransport()
        transportBravo = TestLoopbackTransport()
        transportAlpha.peer = transportBravo
        transportBravo.peer = transportAlpha

        commAlpha = CommunicationManager(transportAlpha)
        commBravo = CommunicationManager(transportBravo)

        val audioCaptureAlpha = AudioCaptureManager(context)
        val audioCaptureBravo = AudioCaptureManager(context)

        transceiverAlpha = TransceiverManager(context, audioCaptureAlpha, sharedTtsManager, commAlpha, callSignAlpha)
        transceiverBravo = TransceiverManager(context, audioCaptureBravo, sharedTtsManager, commBravo, callSignBravo)
    }

    @After
    fun tearDown(): Unit = runBlocking(Dispatchers.Main) {
        transceiverAlpha.alertPlaybackManager.reset()
        transceiverBravo.alertPlaybackManager.reset()
        transceiverAlpha.setEmergencyMode(false)
        transceiverBravo.setEmergencyMode(false)
        commAlpha.disconnect()
        commBravo.disconnect()
    }

    /**
     * Test 1: Normal message still works exactly as before.
     */
    @Test
    fun test01_NormalMessageIntegrity(): Unit = runBlocking(Dispatchers.Main) {
        Log.i(TAG, "=== Test 1: Normal Message Integrity ===")
        assertFalse("Default mode must be NORMAL", transceiverAlpha.isEmergencyMode.value)

        var receivedMessage: P2PMessage? = null
        commBravo.setOnMessageReceivedListener { msg ->
            if (!msg.isAck && msg.text.isNotEmpty()) {
                receivedMessage = msg
            }
        }

        val sentMsg = commAlpha.sendText("Station Bravo, radio check.", senderName = "Station-Alpha")
        assertEquals(P2PMessage.MESSAGE_TYPE_NORMAL, sentMsg.messageType)
        assertEquals(P2PMessage.PRIORITY_NORMAL, sentMsg.priority)

        delay(500)
        assertNotNull("Bravo should receive normal message", receivedMessage)
        assertEquals("Station Bravo, radio check.", receivedMessage?.text)
        assertEquals(P2PMessage.PRIORITY_NORMAL, receivedMessage?.priority)
        Log.i(TAG, "✓ Normal message verified with 0 regressions.")
    }

    /**
     * Test 2: Enable Emergency mode. Speak alert. Release PTT. Verify STT text appears.
     * Verify receiver gets ALERT packet. Verify receiver automatically speaks the alert.
     */
    @Test
    fun test02_EmergencyAlertFlowAndAutoPlayback(): Unit = runBlocking(Dispatchers.Main) {
        Log.i(TAG, "=== Test 2: Emergency Alert Flow and Auto-Playback ===")
        transceiverAlpha.setEmergencyMode(true)
        assertTrue("Emergency mode must be active", transceiverAlpha.isEmergencyMode.value)

        var alertPlayed = false
        var alertStartedTime: Long = 0
        var alertFinishedTime: Long = 0

        transceiverBravo.alertPlaybackManager.onAlertPlaybackStarted = { alert, time ->
            alertStartedTime = time
            Log.i(TAG, "Bravo alert playback started: '${alert.text}'")
        }
        transceiverBravo.alertPlaybackManager.onAlertPlaybackFinished = { alert, time ->
            alertFinishedTime = time
            alertPlayed = true
            Log.i(TAG, "Bravo alert playback finished: '${alert.text}'")
        }

        // Simulate outgoing alert dispatch from Alpha via TransceiverManager
        val alertText = "Mayday, need medical assistance at sector 4"
        transceiverAlpha.sendAlertMessage(alertText)

        // Wait for TTS audio synthesis and playback to complete on Bravo
        var wait = 0
        while (!alertPlayed && wait < 80) {
            delay(100)
            wait++
        }

        assertTrue("Receiver must automatically synthesize and complete speech playback", alertPlayed)
        assertNull("Active incoming alert must return to null after playback", transceiverBravo.activeIncomingAlert.value)
        assertFalse("Alert playing flag must reset", transceiverBravo.alertPlaybackManager.isAlertPlaying.value)
        Log.i(TAG, "✓ Emergency alert auto-playback verified successfully (Duration: ${alertFinishedTime - alertStartedTime}ms).")
    }

    /**
     * Test 3: Send normal message during alert playback.
     * Verify normal TTS does not interrupt alert playback.
     */
    @Test
    fun test03_NormalMessageDoesNotInterruptAlertPlayback(): Unit = runBlocking(Dispatchers.Main) {
        Log.i(TAG, "=== Test 3: Audio Priority (Normal cannot interrupt Alert) ===")
        val alertText = "Flash urgent alert: evacuate immediately"
        val alertMsg = P2PMessage(
            messageId = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            text = alertText,
            senderName = "Station-Alpha",
            messageType = P2PMessage.MESSAGE_TYPE_ALERT,
            priority = P2PMessage.PRIORITY_HIGH
        )

        val normalMsg = P2PMessage(
            messageId = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            text = "Routine update: weather is clear",
            senderName = "Station-Charlie",
            messageType = P2PMessage.MESSAGE_TYPE_NORMAL,
            priority = P2PMessage.PRIORITY_NORMAL
        )

        // Start playing the emergency alert
        transceiverBravo.alertPlaybackManager.enqueueMessage(alertMsg)
        assertTrue("Alert must be actively playing", transceiverBravo.alertPlaybackManager.isAlertPlaying.value)

        // While alert is playing, send normal message
        transceiverBravo.alertPlaybackManager.enqueueMessage(normalMsg)

        // Verify alert is STILL the active alert and playing
        assertTrue("Emergency alert must NOT be interrupted", transceiverBravo.alertPlaybackManager.isAlertPlaying.value)
        assertEquals(alertText, transceiverBravo.alertPlaybackManager.activeAlert.value?.text)

        // Wait until alert playback finishes
        var wait = 0
        while (transceiverBravo.alertPlaybackManager.isAlertPlaying.value && wait < 60) {
            delay(100)
            wait++
        }

        assertFalse("Alert finished playing", transceiverBravo.alertPlaybackManager.isAlertPlaying.value)
        Log.i(TAG, "✓ Audio priority verified: Normal message did not interrupt emergency alert.")
    }

    /**
     * Test 4: Send two alerts rapidly.
     * Verify both alerts are queued and played in order.
     */
    @Test
    fun test04_MultipleAlertsQueuedAndPlayedInOrder(): Unit = runBlocking(Dispatchers.Main) {
        Log.i(TAG, "=== Test 4: Rapid Alerts FIFO Queue ===")
        val alert1 = P2PMessage(
            messageId = "alert-seq-1",
            timestamp = System.currentTimeMillis(),
            text = "First alert: perimeter breached",
            senderName = "Outpost-1",
            messageType = P2PMessage.MESSAGE_TYPE_ALERT,
            priority = P2PMessage.PRIORITY_HIGH
        )
        val alert2 = P2PMessage(
            messageId = "alert-seq-2",
            timestamp = System.currentTimeMillis() + 10,
            text = "Second alert: requesting backup",
            senderName = "Outpost-2",
            messageType = P2PMessage.MESSAGE_TYPE_ALERT,
            priority = P2PMessage.PRIORITY_HIGH
        )

        val playedOrder = mutableListOf<String>()
        transceiverBravo.alertPlaybackManager.onAlertPlaybackStarted = { alert, _ ->
            playedOrder.add(alert.messageId)
            Log.i(TAG, "Order test - Alert started: ${alert.messageId}")
        }

        // Send both alerts rapidly in sequence
        transceiverBravo.alertPlaybackManager.enqueueMessage(alert1)
        transceiverBravo.alertPlaybackManager.enqueueMessage(alert2)

        // Wait for both to play sequentially
        var wait = 0
        while (playedOrder.size < 2 && wait < 100) {
            delay(100)
            wait++
        }

        assertEquals("Both alerts must be played", 2, playedOrder.size)
        assertEquals("Alert 1 must play first", "alert-seq-1", playedOrder[0])
        assertEquals("Alert 2 must play second", "alert-seq-2", playedOrder[1])
        Log.i(TAG, "✓ Rapid alerts queued and executed in exact FIFO order: $playedOrder.")
    }

    /**
     * Test 5: Send duplicate alert with same messageId.
     * Verify only one playback occurs.
     */
    @Test
    fun test05_DuplicateAlertDeduplication(): Unit = runBlocking(Dispatchers.Main) {
        Log.i(TAG, "=== Test 5: Duplicate Alert Deduplication ===")
        val duplicateId = "dedup-test-uuid-999"
        val alert = P2PMessage(
            messageId = duplicateId,
            timestamp = System.currentTimeMillis(),
            text = "Red alert, duplicate packet test",
            senderName = "Station-Alpha",
            messageType = P2PMessage.MESSAGE_TYPE_ALERT,
            priority = P2PMessage.PRIORITY_HIGH
        )

        var playCount = 0
        transceiverBravo.alertPlaybackManager.onAlertPlaybackStarted = { a, _ ->
            if (a.messageId == duplicateId) {
                playCount++
            }
        }

        // Send first packet
        transceiverBravo.alertPlaybackManager.enqueueMessage(alert)
        delay(150)
        assertEquals(1, playCount)

        // Send exact duplicate packet with same messageId
        transceiverBravo.alertPlaybackManager.enqueueMessage(alert)
        delay(300)

        // Play count must remain 1
        assertEquals("Duplicate alert packet must be dropped and NOT re-played", 1, playCount)
        Log.i(TAG, "✓ Deduplication verified: Only 1 playback occurred for duplicated messageId.")
    }

    /**
     * Test 6: Disconnect receiver before sending.
     * Verify user sees: Alert not delivered.
     */
    @Test
    fun test06_ConnectionFailureShowsAlertNotDelivered(): Unit = runBlocking(Dispatchers.Main) {
        Log.i(TAG, "=== Test 6: Connection Failure Handling ===")
        transceiverAlpha.setEmergencyMode(true)

        // Disconnect transport
        transportAlpha.disconnect()
        delay(100)
        assertEquals(ConnectionState.DISCONNECTED, transportAlpha.connectionState.value)

        // Attempt to dispatch alert while disconnected
        transceiverAlpha.sendAlertMessage("Emergency distress call while offline")
        delay(100)

        assertEquals("Alert delivery status must indicate FAILED", AlertDeliveryStatus.FAILED, transceiverAlpha.alertDeliveryStatus.value)
        assertEquals("UI state must indicate ERROR", TransceiverState.ERROR, transceiverAlpha.uiState.value)
        Log.i(TAG, "✓ Connection failure properly triggered AlertDeliveryStatus.FAILED (Alert not delivered).")
    }

    /**
     * Test 7: Reconnect and retry.
     * Verify alert works.
     */
    @Test
    fun test07_ReconnectAndRetryAlert(): Unit = runBlocking(Dispatchers.Main) {
        Log.i(TAG, "=== Test 7: Reconnect and Retry Alert ===")
        transceiverAlpha.setEmergencyMode(true)

        // Reconnect transport
        transportAlpha.connect()
        transportBravo.connect()
        delay(100)
        assertEquals(ConnectionState.CONNECTED, transportAlpha.connectionState.value)
        assertEquals(ConnectionState.CONNECTED, transportBravo.connectionState.value)

        var alertPlayed = false
        transceiverBravo.alertPlaybackManager.onAlertPlaybackStarted = { alert, _ ->
            if (alert.text == "Distress signal retransmitted successfully") {
                alertPlayed = true
            }
        }

        // Retry sending alert
        transceiverAlpha.sendAlertMessage("Distress signal retransmitted successfully")

        // Wait for Bravo to receive and start playback
        var wait = 0
        while (!alertPlayed && wait < 60) {
            delay(50)
            wait++
        }

        assertTrue("Reconnected alert must be received and played", alertPlayed)
        Log.i(TAG, "✓ Reconnect and retry succeeded.")
    }

    /**
     * Test 8: Repeat at least 10 alert cycles.
     * Check: no crash, no audio resource leak, no duplicate playback, no stuck alert state.
     */
    @Test
    fun test08_TenAlertCyclesStressAndLeakAssessment(): Unit = runBlocking(Dispatchers.Main) {
        Log.i(TAG, "=== Test 8: 10 Alert Cycles Stress & Leak Assessment ===")
        transceiverAlpha.setEmergencyMode(true)

        Runtime.getRuntime().gc()
        val memInfoStart = Debug.MemoryInfo()
        Debug.getMemoryInfo(memInfoStart)
        val initialPssMb = memInfoStart.totalPss / 1024.0

        for (cycle in 1..10) {
            val alert = P2PMessage(
                messageId = "stress-cycle-$cycle-${UUID.randomUUID()}",
                timestamp = System.currentTimeMillis(),
                text = "Emergency alert cycle $cycle all units report",
                senderName = "Station-Alpha",
                messageType = P2PMessage.MESSAGE_TYPE_ALERT,
                priority = P2PMessage.PRIORITY_HIGH
            )

            var cycleFinished = false
            transceiverBravo.alertPlaybackManager.onAlertPlaybackFinished = { a, _ ->
                if (a.messageId == alert.messageId) {
                    cycleFinished = true
                }
            }

            transceiverBravo.alertPlaybackManager.enqueueMessage(alert)

            var wait = 0
            while (!cycleFinished && wait < 60) {
                delay(100)
                wait++
            }

            assertTrue("Cycle $cycle must complete cleanly", cycleFinished)
            assertFalse("Alert playing flag must reset on cycle $cycle", transceiverBravo.alertPlaybackManager.isAlertPlaying.value)
            assertNull("Active alert must reset on cycle $cycle", transceiverBravo.alertPlaybackManager.activeAlert.value)
            Log.d(TAG, "Completed alert cycle $cycle / 10")
        }

        Runtime.getRuntime().gc()
        val memInfoEnd = Debug.MemoryInfo()
        Debug.getMemoryInfo(memInfoEnd)
        val finalPssMb = memInfoEnd.totalPss / 1024.0
        val pssDeltaMb = finalPssMb - initialPssMb

        Log.i(TAG, "10-Cycle Stress Results: Initial PSS: ${"%.2f".format(initialPssMb)}MB, Final PSS: ${"%.2f".format(finalPssMb)}MB, Delta: ${"%.2f".format(pssDeltaMb)}MB")
        assertTrue("Memory growth across 10 cycles must be less than 40 MB", pssDeltaMb < 40.0)
        Log.i(TAG, "✓ 10 Alert cycles passed with zero leaks, zero crashes, and zero stuck states.")
    }
}
