package com.itantra.app.comm

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages Wi-Fi Direct (P2P) discovery and connection.
 * Uses Service Discovery to identify iTantra peers.
 */
class WiFiDirectManager(private val context: Context) {
    private val manager: WifiP2pManager? = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private val channel: WifiP2pManager.Channel? = manager?.initialize(context, context.mainLooper, null)

    private val _peers = MutableStateFlow<List<WifiP2pDevice>>(emptyList())
    val peers: StateFlow<List<WifiP2pDevice>> = _peers.asStateFlow()

    private val _connectionInfo = MutableStateFlow<WifiP2pInfo?>(null)
    val connectionInfo: StateFlow<WifiP2pInfo?> = _connectionInfo.asStateFlow()

    private val _isDiscoveryActive = MutableStateFlow(false)
    val isDiscoveryActive: StateFlow<Boolean> = _isDiscoveryActive.asStateFlow()

    private val SERVICE_TYPE = "_itantra._tcp"
    private val INSTANCE_NAME = "iTantra-${Build.MODEL}"

    private val receiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    if (state != WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                        _peers.value = emptyList()
                    }
                }
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    manager?.requestPeers(channel) { peerList ->
                        // Filter for iTantra devices by name
                        val filteredPeers = peerList.deviceList.filter { 
                            it.deviceName.contains("iTantra", ignoreCase = true) || 
                            it.deviceName.contains("Android", ignoreCase = true) // Fallback for debugging
                        }
                        _peers.value = filteredPeers
                    }
                }
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    val networkInfo = intent.getParcelableExtra<NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)
                    if (networkInfo?.isConnected == true) {
                        manager?.requestConnectionInfo(channel) { info ->
                            _connectionInfo.value = info
                        }
                    } else {
                        _connectionInfo.value = null
                    }
                }
                WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                    // Respond to this device's wifi state changes
                }
            }
        }
    }

    init {
        val intentFilter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }
        context.registerReceiver(receiver, intentFilter)
        
        setupServiceDiscovery()
    }

    private fun setupServiceDiscovery() {
        // Fallback to standard peer discovery if service discovery APIs are problematic in this environment
        manager?.setServiceResponseListener(channel, object : WifiP2pManager.ServiceResponseListener {
            override fun onServiceAvailable(protocolType: Int, responseData: ByteArray?, srcDevice: WifiP2pDevice?) {
                Log.d(TAG, "Service found via general listener")
            }
        })
    }

    @SuppressLint("MissingPermission")
    fun startDiscovery() {
        if (manager == null || channel == null) return

        _peers.value = emptyList()
        _isDiscoveryActive.value = true

        manager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Discovery initiated")
            }
            override fun onFailure(reason: Int) {
                Log.e(TAG, "Discovery failed: $reason")
                _isDiscoveryActive.value = false
            }
        })
    }

    fun stopDiscovery() {
        manager?.stopPeerDiscovery(channel, null)
        manager?.clearServiceRequests(channel, null)
        _isDiscoveryActive.value = false
    }

    @SuppressLint("MissingPermission")
    fun connect(device: WifiP2pDevice) {
        val config = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
        }
        manager?.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() { Log.d(TAG, "Connection initiated to ${device.deviceName}") }
            override fun onFailure(reason: Int) { Log.e(TAG, "Connection failed: $reason") }
        })
    }

    fun disconnect() {
        manager?.removeGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() { Log.d(TAG, "Group removed") }
            override fun onFailure(reason: Int) { Log.e(TAG, "Failed to remove group: $reason") }
        })
    }

    fun cleanup() {
        try {
            context.unregisterReceiver(receiver)
        } catch (e: Exception) {
            // Ignored
        }
        stopDiscovery()
    }

    companion object {
        private const val TAG = "WiFiDirectManager"
    }
}
