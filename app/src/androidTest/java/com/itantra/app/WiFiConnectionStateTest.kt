package com.itantra.app

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.itantra.app.comm.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class WiFiConnectionStateTest {

    private lateinit var wifiTransport: WiFiTransport
    private lateinit var commManager: CommunicationManager
    private val testScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    companion object {
        private const val TAG = "WiFiConnStateTest"
    }

    @Before
    fun setUp() {
        wifiTransport = WiFiTransport()
        commManager = CommunicationManager(wifiTransport)
    }

    @After
    fun tearDown() {
        commManager.disconnect()
        testScope.cancel()
        Thread.sleep(200)
    }

    /**
     * Requirement 1: Selecting Wi-Fi immediately enables CONNECT when disconnected.
     * Transport selection and connection state are separate.
     */
    @Test
    fun test01_SelectWifiInitialState_IsDisconnectedAndConnectEnabled() {
        Log.i(TAG, "Running test01_SelectWifiInitialState_IsDisconnectedAndConnectEnabled")
        commManager.selectTransport(TransportMode.WIFI, wifiTransport)

        val selectedTransport = commManager.selectedTransport.value
        val connectionState = commManager.connectionState.value

        assertEquals("Selected transport must be WIFI", TransportMode.WIFI, selectedTransport)
        assertEquals("Connection state must be DISCONNECTED", ConnectionState.DISCONNECTED, connectionState)

        // Verify UI derived rule: CONNECT is enabled when DISCONNECTED
        val canConnect = (connectionState == ConnectionState.DISCONNECTED || connectionState == ConnectionState.ERROR)
        assertTrue("CONNECT button must be enabled immediately on Wi-Fi selection", canConnect)
    }

    /**
     * Requirement 2 & 6: Transport switching does not retain stale state.
     * Switch: Wi-Fi -> Bluetooth -> Wi-Fi Direct -> Wi-Fi.
     * CONNECT must remain enabled without requiring DISCONNECT first.
     */
    @Test
    fun test02_TransportSwitching_DoesNotRetainStaleState() {
        Log.i(TAG, "Running test02_TransportSwitching_DoesNotRetainStaleState")
        val dummyBt = BluetoothTransport()
        val dummyP2p = WiFiTransport() // Mocked peer transport for state check

        // 1. Switch to Bluetooth
        commManager.selectTransport(TransportMode.BLUETOOTH, dummyBt)
        assertEquals(TransportMode.BLUETOOTH, commManager.selectedTransport.value)
        assertEquals(ConnectionState.DISCONNECTED, commManager.connectionState.value)

        // 2. Switch to Wi-Fi Direct
        commManager.selectTransport(TransportMode.WIFI_DIRECT, dummyP2p)
        assertEquals(TransportMode.WIFI_DIRECT, commManager.selectedTransport.value)
        assertEquals(ConnectionState.DISCONNECTED, commManager.connectionState.value)

        // 3. Switch back to Wi-Fi
        commManager.selectTransport(TransportMode.WIFI, wifiTransport)
        assertEquals(TransportMode.WIFI, commManager.selectedTransport.value)
        assertEquals(ConnectionState.DISCONNECTED, commManager.connectionState.value)

        val canConnect = (commManager.connectionState.value == ConnectionState.DISCONNECTED)
        assertTrue("CONNECT must be immediately clickable without requiring DISCONNECT", canConnect)
    }

    /**
     * Requirement 3: CONNECTING state is clear and disables CONNECT.
     */
    @Test
    fun test03_ConnectingState_DisablesConnectButton() {
        Log.i(TAG, "Running test03_ConnectingState_DisablesConnectButton")
        commManager.selectTransport(TransportMode.WIFI, wifiTransport)

        // Initiate connection to dummy local IP
        commManager.connect("127.0.0.1")

        val state = commManager.connectionState.value
        assertEquals("Outbound connection request must transition to CONNECTING", ConnectionState.CONNECTING, state)

        val canConnect = (state == ConnectionState.DISCONNECTED || state == ConnectionState.ERROR)
        assertFalse("CONNECT button must be disabled while in CONNECTING state", canConnect)

        commManager.disconnect()
    }

    /**
     * Requirement 4: CONNECTED appears after socket connection; DISCONNECT enabled.
     */
    @Test
    fun test04_SocketConnection_TransitionsToConnected() = runBlocking {
        Log.i(TAG, "Running test04_SocketConnection_TransitionsToConnected")
        
        // Start a local test server
        val mockServer = ServerSocket().apply {
            reuseAddress = true
            bind(java.net.InetSocketAddress(8888))
        }
        var serverAcceptedSocket: Socket? = null

        val serverJob = launch(Dispatchers.IO) {
            serverAcceptedSocket = mockServer.accept()
        }

        try {
            commManager.selectTransport(TransportMode.WIFI, wifiTransport)
            commManager.connect("127.0.0.1")

            // Wait for client to connect to local mock server
            var waited = 0
            while (commManager.connectionState.value != ConnectionState.CONNECTED && waited < 3000) {
                delay(100)
                waited += 100
            }

            assertEquals("ConnectionState must be CONNECTED after socket connects", ConnectionState.CONNECTED, commManager.connectionState.value)
            
            // In CONNECTED state, DISCONNECT button is shown and CONNECT is disabled
            val isDisconnectVisible = (commManager.connectionState.value == ConnectionState.CONNECTED)
            val canConnect = (commManager.connectionState.value == ConnectionState.DISCONNECTED || commManager.connectionState.value == ConnectionState.ERROR)

            assertTrue("DISCONNECT must be enabled when CONNECTED", isDisconnectVisible)
            assertFalse("CONNECT must be disabled when CONNECTED", canConnect)
        } finally {
            commManager.disconnect()
            serverAcceptedSocket?.close()
            mockServer.close()
            serverJob.cancel()
        }
    }

    /**
     * Requirement 8: Tap DISCONNECT. Verify CONNECT is immediately clickable again. Repeat 10 times.
     */
    @Test
    fun test05_DisconnectCycle_Repeated10Times() = runBlocking {
        Log.i(TAG, "Running test05_DisconnectCycle_Repeated10Times (10 cycles)")
        
        for (i in 1..10) {
            val mockServer = ServerSocket().apply {
                reuseAddress = true
                bind(java.net.InetSocketAddress(8888))
            }
            var serverAcceptedSocket: Socket? = null
            val serverJob = launch(Dispatchers.IO) {
                try {
                    serverAcceptedSocket = mockServer.accept()
                } catch (ignored: Exception) {}
            }

            try {
                commManager.selectTransport(TransportMode.WIFI, wifiTransport)
                commManager.connect("127.0.0.1")

                // Wait for connected
                var waited = 0
                while (commManager.connectionState.value != ConnectionState.CONNECTED && waited < 2000) {
                    delay(50)
                    waited += 50
                }
                assertEquals("Cycle $i: Must reach CONNECTED", ConnectionState.CONNECTED, commManager.connectionState.value)

                // Tap DISCONNECT
                commManager.disconnect()
                assertEquals("Cycle $i: Disconnect must immediately reset to DISCONNECTED", ConnectionState.DISCONNECTED, commManager.connectionState.value)

                val canConnect = (commManager.connectionState.value == ConnectionState.DISCONNECTED)
                assertTrue("Cycle $i: CONNECT must be clickable immediately after disconnect", canConnect)
            } finally {
                serverAcceptedSocket?.close()
                mockServer.close()
                serverJob.cancel()
                delay(50)
            }
        }
    }

    /**
     * Requirement 5: Connection failure / timeout recovers cleanly to DISCONNECTED.
     */
    @Test
    fun test06_ConnectionFailure_RecoversToDisconnected() = runBlocking {
        Log.i(TAG, "Running test06_ConnectionFailure_RecoversToDisconnected")
        commManager.selectTransport(TransportMode.WIFI, wifiTransport)

        // Connect to an IP where port 8888 is not listening (immediate connection refused)
        commManager.connect("127.0.0.1")

        // Wait for connection attempt to fail and recover
        var waited = 0
        while (commManager.connectionState.value == ConnectionState.CONNECTING && waited < 4000) {
            delay(100)
            waited += 100
        }

        // Must recover to DISCONNECTED, not stuck in CONNECTING or ERROR
        assertEquals("Failed connection must recover to DISCONNECTED", ConnectionState.DISCONNECTED, commManager.connectionState.value)

        val canConnect = (commManager.connectionState.value == ConnectionState.DISCONNECTED)
        assertTrue("CONNECT button must become available again after failure", canConnect)
    }

    /**
     * Passive Host Mode: Hosting Wi-Fi service waiting for incoming clients
     * must NOT cause outbound CONNECT to become disabled.
     */
    @Test
    fun test07_PassiveHostMode_LeavesConnectButtonEnabled() {
        Log.i(TAG, "Running test07_PassiveHostMode_LeavesConnectButtonEnabled")
        commManager.selectTransport(TransportMode.WIFI, wifiTransport)

        // Start passive hosting
        commManager.connect(null)

        assertEquals("Passive host mode must remain DISCONNECTED", ConnectionState.DISCONNECTED, commManager.connectionState.value)
        
        val canConnect = (commManager.connectionState.value == ConnectionState.DISCONNECTED)
        assertTrue("CONNECT button must remain enabled while hosting", canConnect)

        commManager.disconnect()
    }

    /**
     * Actual Communication: Live TCP socket message exchange over Wi-Fi transport.
     * Verify that text message is sent, received, and delivered.
     */
    @Test
    fun test08_ActualCommunication_SendAndReceiveTextMessage() = runBlocking {
        Log.i(TAG, "Running test08_ActualCommunication_SendAndReceiveTextMessage")

        val serverComm = commManager
        val serverTransport = wifiTransport

        val clientTransport = WiFiTransport()
        val clientComm = CommunicationManager(clientTransport)

        val messageReceivedLatch = CountDownLatch(1)
        var receivedText: String? = null

        serverComm.setOnMessageReceivedListener { message ->
            Log.i(TAG, "Server received message: ${message.text}")
            receivedText = message.text
            messageReceivedLatch.countDown()
        }

        try {
            // 1. Server starts listening on port 8888
            serverTransport.connect(null)
            delay(300)

            // 2. Client connects to server on 127.0.0.1
            clientComm.connect("127.0.0.1")

            var waited = 0
            while (clientComm.connectionState.value != ConnectionState.CONNECTED && waited < 4000) {
                delay(100)
                waited += 100
            }

            assertEquals("Client must be CONNECTED", ConnectionState.CONNECTED, clientComm.connectionState.value)

            // 3. Client sends message
            val testPayload = "Hello iTantra Wi-Fi P2P Check"
            clientComm.sendText(testPayload, language = "en", senderName = "S24_Tester")

            val success = messageReceivedLatch.await(5, TimeUnit.SECONDS)
            assertTrue("Message should be received by server within 5s", success)
            assertEquals("Received text must match sent payload", testPayload, receivedText)
        } finally {
            clientComm.disconnect()
            serverComm.disconnect()
            delay(200)
        }
    }
}
