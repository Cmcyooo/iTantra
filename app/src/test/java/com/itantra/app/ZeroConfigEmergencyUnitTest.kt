package com.itantra.app

import com.itantra.app.comm.*
import com.itantra.app.ui.TransportMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ZeroConfigEmergencyUnitTest {

    private lateinit var peerRegistry: PeerRegistry

    @Before
    fun setUp() {
        peerRegistry = PeerRegistry()
    }

    @Test
    fun testPeerRegistryRankingConnectedFirst() {
        // Add a disconnected Wi-Fi peer
        val wifiDev = DiscoveryManager.DiscoveredDevice(
            name = "iTantra-Station-Alpha",
            ip = "192.168.1.10",
            port = 8888
        )
        peerRegistry.updateFromWifi(listOf(wifiDev))

        // Add a connected Bluetooth peer
        peerRegistry.updateConnectionStatus(
            peerId = "bt_02:00:00:00:00:01",
            transport = TransportMode.BLUETOOTH,
            isConnected = true
        )

        val best = peerRegistry.getBestCandidate()
        assertNotNull("Best candidate must not be null", best)
        assertEquals("Connected peer must win over disconnected Wi-Fi", "bt_02:00:00:00:00:01", best!!.first.peerId)
        assertTrue("Best peer must have isConnected = true", best.first.isConnected)
    }

    @Test
    fun testPeerRegistryTransportPriorityWhenDisconnected() {
        // Add Bluetooth peer
        peerRegistry.updateConnectionStatus(
            peerId = "bt_02:00:00:00:00:02",
            transport = TransportMode.BLUETOOTH,
            isConnected = false
        )

        // Add Wi-Fi peer
        val wifiDev = DiscoveryManager.DiscoveredDevice(
            name = "iTantra-Station-Bravo",
            ip = "192.168.1.20",
            port = 8888
        )
        peerRegistry.updateFromWifi(listOf(wifiDev))

        val best = peerRegistry.getBestCandidate()
        assertNotNull(best)
        assertEquals("Wi-Fi must take priority over Bluetooth when disconnected", TransportMode.WIFI, best!!.second)
        assertEquals("Station-Bravo", best.first.displayName)
    }

    @Test
    fun testFriendlyNameResolutionNoRawMac() {
        val wifiDev = DiscoveryManager.DiscoveredDevice(
            name = "iTantra-Command-Post",
            ip = "192.168.1.50",
            port = 8888
        )
        peerRegistry.updateFromWifi(listOf(wifiDev))

        val peers = peerRegistry.peers.value
        assertEquals(1, peers.size)
        assertEquals("Prefix must be cleanly removed for friendly display", "Command-Post", peers[0].displayName)
        assertFalse("Display name must not contain raw ip or mac", peers[0].displayName.contains("192.168.1.50"))
    }
}
