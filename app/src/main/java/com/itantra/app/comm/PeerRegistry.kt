package com.itantra.app.comm

import android.net.wifi.p2p.WifiP2pDevice
import android.os.SystemClock
import android.util.Log
import com.itantra.app.ui.TransportMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Represents a discovered or connected iTantra peer.
 */
data class PeerEntry(
    val peerId: String,
    val displayName: String,
    val availableTransports: Map<TransportMode, String>,
    val lastSeenTimestamp: Long,
    val isConnected: Boolean = false,
    val lastSuccessfulConnection: Long = 0,
    val latencyMs: Long? = null
)

/**
 * Lightweight, thread-safe in-memory directory tracking reachable iTantra peers
 * across all local offline transports (Wi-Fi, Wi-Fi Direct, Bluetooth).
 * Ensures friendly identities are used and never exposes raw MAC addresses as primary names.
 */
class PeerRegistry {

    companion object {
        private const val TAG = "PeerRegistry"
        private const val STALE_THRESHOLD_MS = 60_000L // 60s without update is considered stale
    }

    private val peerMap = ConcurrentHashMap<String, PeerEntry>()
    private val _peers = MutableStateFlow<List<PeerEntry>>(emptyList())
    val peers: StateFlow<List<PeerEntry>> = _peers.asStateFlow()

    private fun getElapsedRealtime(): Long {
        return try {
            SystemClock.elapsedRealtime()
        } catch (e: Throwable) {
            System.currentTimeMillis()
        }
    }

    private fun publish() {
        _peers.value = peerMap.values.toList().sortedWith(
            compareByDescending<PeerEntry> { it.isConnected }
                .thenByDescending { it.lastSuccessfulConnection }
                .thenByDescending { it.lastSeenTimestamp }
        )
    }

    /**
     * Updates peer directory from Wi-Fi NSD discovered devices.
     */
    fun updateFromWifi(devices: List<DiscoveryManager.DiscoveredDevice>) {
        val now = getElapsedRealtime()
        for (dev in devices) {
            val key = "wifi_${dev.ip}:${dev.port}"
            val existing = peerMap[key]
            val transports = existing?.availableTransports?.toMutableMap() ?: mutableMapOf()
            transports[TransportMode.WIFI] = "${dev.ip}:${dev.port}"

            val friendlyName = if (dev.name.startsWith("iTantra-")) {
                dev.name.removePrefix("iTantra-")
            } else {
                dev.name
            }

            peerMap[key] = PeerEntry(
                peerId = key,
                displayName = friendlyName,
                availableTransports = transports,
                lastSeenTimestamp = now,
                isConnected = existing?.isConnected ?: false,
                lastSuccessfulConnection = existing?.lastSuccessfulConnection ?: 0,
                latencyMs = existing?.latencyMs
            )
        }
        publish()
    }

    /**
     * Updates peer directory from Wi-Fi Direct P2P devices.
     */
    fun updateFromWifiDirect(devices: List<WifiP2pDevice>) {
        val now = getElapsedRealtime()
        for (dev in devices) {
            val key = "p2p_${dev.deviceAddress}"
            val existing = peerMap[key]
            val transports = existing?.availableTransports?.toMutableMap() ?: mutableMapOf()
            transports[TransportMode.WIFI_DIRECT] = dev.deviceAddress

            val friendlyName = dev.deviceName.takeIf { it.isNotBlank() } ?: "iTantra Station"

            peerMap[key] = PeerEntry(
                peerId = key,
                displayName = friendlyName,
                availableTransports = transports,
                lastSeenTimestamp = now,
                isConnected = dev.status == WifiP2pDevice.CONNECTED || (existing?.isConnected == true),
                lastSuccessfulConnection = if (dev.status == WifiP2pDevice.CONNECTED) now else (existing?.lastSuccessfulConnection ?: 0),
                latencyMs = existing?.latencyMs
            )
        }
        publish()
    }

    /**
     * Updates peer directory from Bluetooth discovered peers.
     */
    fun updateFromBluetooth(peers: List<DiscoveredBluetoothPeer>) {
        val now = getElapsedRealtime()
        for (peer in peers) {
            val key = "bt_${peer.address}"
            val existing = peerMap[key]
            val transports = existing?.availableTransports?.toMutableMap() ?: mutableMapOf()
            transports[TransportMode.BLUETOOTH] = peer.address

            peerMap[key] = PeerEntry(
                peerId = key,
                displayName = peer.displayName,
                availableTransports = transports,
                lastSeenTimestamp = now,
                isConnected = existing?.isConnected ?: false,
                lastSuccessfulConnection = existing?.lastSuccessfulConnection ?: 0,
                latencyMs = existing?.latencyMs
            )
        }
        publish()
    }

    /**
     * Marks connection status for a peer.
     */
    fun updateConnectionStatus(peerId: String, transport: TransportMode, isConnected: Boolean, latencyMs: Long? = null) {
        val now = getElapsedRealtime()
        val existing = peerMap[peerId]
        if (existing != null) {
            peerMap[peerId] = existing.copy(
                isConnected = isConnected,
                lastSuccessfulConnection = if (isConnected) now else existing.lastSuccessfulConnection,
                latencyMs = latencyMs ?: existing.latencyMs
            )
            publish()
        } else if (isConnected) {
            // New connected peer
            val transports = mapOf(transport to peerId)
            peerMap[peerId] = PeerEntry(
                peerId = peerId,
                displayName = "Station $peerId",
                availableTransports = transports,
                lastSeenTimestamp = now,
                isConnected = true,
                lastSuccessfulConnection = now,
                latencyMs = latencyMs
            )
            publish()
        }
    }

    /**
     * Selects the best available candidate peer for zero-configuration emergency connection.
     * Ranking priority:
     * 1. Currently connected peer
     * 2. Recently validated reachable peer
     * 3. Reachable over Wi-Fi
     * 4. Reachable over Wi-Fi Direct
     * 5. Reachable over Bluetooth
     */
    fun getBestCandidate(): Pair<PeerEntry, TransportMode>? {
        val now = getElapsedRealtime()
        val activePeers = peerMap.values.filter { 
            (now - it.lastSeenTimestamp) < STALE_THRESHOLD_MS || it.isConnected 
        }
        if (activePeers.isEmpty()) return null

        // 1. Already connected peer
        val connected = activePeers.firstOrNull { it.isConnected }
        if (connected != null) {
            val preferredTransport = when {
                connected.availableTransports.containsKey(TransportMode.WIFI) -> TransportMode.WIFI
                connected.availableTransports.containsKey(TransportMode.WIFI_DIRECT) -> TransportMode.WIFI_DIRECT
                else -> TransportMode.BLUETOOTH
            }
            Log.i(TAG, "[EMERGENCY-DIAG] Best candidate (Connected): ${connected.displayName} via $preferredTransport")
            return Pair(connected, preferredTransport)
        }

        // 2. Wi-Fi reachable peer
        val wifiPeer = activePeers.firstOrNull { it.availableTransports.containsKey(TransportMode.WIFI) }
        if (wifiPeer != null) {
            Log.i(TAG, "[EMERGENCY-DIAG] Best candidate (Wi-Fi): ${wifiPeer.displayName}")
            return Pair(wifiPeer, TransportMode.WIFI)
        }

        // 3. Wi-Fi Direct reachable peer
        val p2pPeer = activePeers.firstOrNull { it.availableTransports.containsKey(TransportMode.WIFI_DIRECT) }
        if (p2pPeer != null) {
            Log.i(TAG, "[EMERGENCY-DIAG] Best candidate (Wi-Fi Direct): ${p2pPeer.displayName}")
            return Pair(p2pPeer, TransportMode.WIFI_DIRECT)
        }

        // 4. Bluetooth reachable peer
        val btPeer = activePeers.firstOrNull { it.availableTransports.containsKey(TransportMode.BLUETOOTH) }
        if (btPeer != null) {
            Log.i(TAG, "[EMERGENCY-DIAG] Best candidate (Bluetooth): ${btPeer.displayName}")
            return Pair(btPeer, TransportMode.BLUETOOTH)
        }

        return null
    }

    fun clear() {
        peerMap.clear()
        publish()
    }
}
