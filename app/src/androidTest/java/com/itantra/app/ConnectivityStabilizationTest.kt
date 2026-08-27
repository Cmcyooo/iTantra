package com.itantra.app

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.itantra.app.audio.AudioCaptureManager
import com.itantra.app.comm.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConnectivityStabilizationTest {

    companion object {
        private const val TAG = "ConnectivityTest"
    }

    private lateinit var context: Context
    private lateinit var callSignManager: CallSignManager
    private lateinit var wifiDirectManager: WiFiDirectManager
    private lateinit var wifiDirectTransport: WiFiDirectTransport
    private lateinit var bluetoothDiscoveryManager: BluetoothDiscoveryManager
    private lateinit var bluetoothTransport: BluetoothTransport
    private lateinit var audioCaptureManager: AudioCaptureManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        callSignManager = CallSignManager(context)
        wifiDirectManager = WiFiDirectManager(context)
        wifiDirectTransport = WiFiDirectTransport(wifiDirectManager)
        bluetoothDiscoveryManager = BluetoothDiscoveryManager(context, callSignManager)
        bluetoothTransport = BluetoothTransport()
        audioCaptureManager = AudioCaptureManager(context)
    }

    @Test
    fun test01_WiFiDirectManagerSafetyAndStateMachine() {
        runBlocking {
            Log.i(TAG, "=== TEST 01: Wi-Fi Direct Manager Safety & 9-State Machine ===")

            // 1. Verify Initial State
            assertEquals(P2PState.IDLE, wifiDirectManager.p2pState.value)
            assertNotNull("Status message must not be null", wifiDirectManager.statusMessage.value)
            Log.i(TAG, "  Initial P2P State: ${wifiDirectManager.p2pState.value}, Status: '${wifiDirectManager.statusMessage.value}'")

            // 2. Safe Discovery Initiation (Must never crash regardless of hardware state)
            wifiDirectManager.startDiscovery()
            delay(1000)

            val activeState = wifiDirectManager.p2pState.value
            Log.i(TAG, "  Post-Discovery P2P State: $activeState, Status: '${wifiDirectManager.statusMessage.value}'")
            assertTrue(
                "P2P State must be either DISCOVERING, PEER_FOUND, or gracefully FAILED",
                activeState == P2PState.DISCOVERING || activeState == P2PState.PEER_FOUND || activeState == P2PState.FAILED
            )

            // 3. Stop Discovery safely
            wifiDirectManager.stopDiscovery()
            assertFalse(wifiDirectManager.isDiscoveryActive.value)

            // 4. Safe Disconnect & Cleanup (Must never crash on disconnected/unformed group)
            wifiDirectManager.disconnect()
            wifiDirectTransport.disconnect()
            wifiDirectManager.cleanup()

            Log.i(TAG, "✓ TEST 01 PASSED: Wi-Fi Direct state machine and channel lifecycle crash-free.")
        }
    }

    @Test
    fun test02_BluetoothDiscoveryLifecycleAndDeduplication() {
        runBlocking {
            Log.i(TAG, "=== TEST 02: Bluetooth Discovery Lifecycle & Deduplication ===")

            // 1. Initial State
            val isEnabled = bluetoothDiscoveryManager.isBluetoothEnabled.value
            Log.i(TAG, "  Bluetooth enabled: $isEnabled, Status: '${bluetoothDiscoveryManager.statusMessage.value}'")

            if (isEnabled && bluetoothDiscoveryManager.hasRequiredPermissions()) {
                // 2. Start First Discovery Scan
                bluetoothDiscoveryManager.startDiscovery()
                delay(1500)

                val countFirst = bluetoothDiscoveryManager.discoveredPeers.value.size
                Log.i(TAG, "  First scan discovered $countFirst peers")

                // 3. Rescan Simulation (Stop -> Clear -> Start fresh)
                bluetoothDiscoveryManager.startDiscovery()
                delay(1500)

                val peersAfterRescan = bluetoothDiscoveryManager.discoveredPeers.value
                Log.i(TAG, "  Rescan discovered ${peersAfterRescan.size} peers")

                // Verify deduplication: no two peers have the same MAC address
                val addresses = peersAfterRescan.map { it.address }
                assertEquals("Discovered peers must have unique MAC addresses", addresses.size, addresses.toSet().size)

                // Verify friendly display names: none should be empty, null, or raw MAC address
                for (peer in peersAfterRescan) {
                    assertFalse("Display name must not be blank", peer.displayName.isBlank())
                    assertNotEquals("Display name must not be 'null'", "null", peer.displayName)
                    assertNotEquals("Display name must not be 'Unknown Device'", "Unknown Device", peer.displayName)
                    Log.i(TAG, "    Peer: ${peer.displayName} (${peer.address}) bonded=${peer.isBonded}")
                }

                bluetoothDiscoveryManager.stopDiscovery()
            } else {
                Log.i(TAG, "  Bluetooth disabled or permissions not granted in test environment. Verifying non-crashing fallback.")
                bluetoothDiscoveryManager.startDiscovery()
                assertFalse("Discovery must not be searching if disabled/unpermitted", bluetoothDiscoveryManager.isSearching.value)
            }

            bluetoothDiscoveryManager.cleanup()
            bluetoothTransport.disconnect()
            Log.i(TAG, "✓ TEST 02 PASSED: Bluetooth discovery lifecycle and deduplication verified.")
        }
    }

    @Test
    fun test03_HoldToSpeakPttResponsiveness() {
        runBlocking {
            Log.i(TAG, "=== TEST 03: Hold-to-Speak PTT Immediate Audio Buffering ===")

            // 1. Cold Press TALK
            val t0 = System.currentTimeMillis()
            audioCaptureManager.startRecording()
            val coldStartMs = System.currentTimeMillis() - t0

            Log.i(TAG, "  Cold Recording start latency: ${coldStartMs}ms")
            assertTrue("Cold recording start must be under 1000ms", coldStartMs < 1000)

            // Let audio record for 500ms to test rolling PTT buffer
            delay(500)

            // Release TALK with forceFinalize
            val t1 = System.currentTimeMillis()
            audioCaptureManager.stopRecording(forceFinalize = true)
            val stopMs = System.currentTimeMillis() - t1
            Log.i(TAG, "  Recording stop latency: ${stopMs}ms")
            assertTrue("Recording stop must be under 500ms", stopMs < 500)

            // 2. Warm Press TALK
            val t2 = System.currentTimeMillis()
            audioCaptureManager.startRecording()
            val warmStartMs = System.currentTimeMillis() - t2
            Log.i(TAG, "  Warm Recording start latency: ${warmStartMs}ms")
            assertTrue("Warm recording start must be under 300ms", warmStartMs < 300)

            delay(300)
            audioCaptureManager.stopRecording(forceFinalize = false)

            audioCaptureManager.release()
            Log.i(TAG, "✓ TEST 03 PASSED: Hold-to-Speak PTT latency and immediate capture verified.")
        }
    }
}
