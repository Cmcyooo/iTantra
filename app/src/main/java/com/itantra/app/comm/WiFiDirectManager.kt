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
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.Manifest
import android.content.pm.PackageManager

/**
 * Manages Wi-Fi Direct (P2P) discovery and connection.
 * Uses Service Discovery to identify iTantra peers.
 */
class WiFiDirectManager(private val context: Context) {
    private val manager: WifiP2pManager? = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private val channel: WifiP2pManager.Channel? = manager?.let {
        try {
            it.initialize(context, context.mainLooper, null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Wi-Fi Direct channel", e)
            null
        }
    }

    private val _peers = MutableStateFlow<List<WifiP2pDevice>>(emptyList())
    val peers: StateFlow<List<WifiP2pDevice>> = _peers.asStateFlow()

    private val _connectionInfo = MutableStateFlow<WifiP2pInfo?>(null)
    val connectionInfo: StateFlow<WifiP2pInfo?> = _connectionInfo.asStateFlow()

    private val _isDiscoveryActive = MutableStateFlow(false)
    val isDiscoveryActive: StateFlow<Boolean> = _isDiscoveryActive.asStateFlow()

    private val _isAvailable = MutableStateFlow(manager != null && channel != null)
    val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            if (!hasRequiredPermissions()) {
                Log.w(TAG, "Missing permissions in BroadcastReceiver")
                return
            }

            when (intent.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    val isEnabled = state == WifiP2pManager.WIFI_P2P_STATE_ENABLED
                    Log.d(TAG, "P2P State changed: ${if (isEnabled) "ENABLED" else "DISABLED"}")
                    if (!isEnabled) {
                        _peers.value = emptyList()
                    }
                }
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    Log.d(TAG, "Peers changed action received")
                    if (manager != null && channel != null) {
                        manager.requestPeers(channel) { peerList ->
                            // Filter for iTantra devices by name
                            val filteredPeers = peerList.deviceList.filter { 
                                it.deviceName.contains("iTantra", ignoreCase = true) || 
                                it.deviceName.contains("Android", ignoreCase = true) ||
                                it.deviceName.contains("Direct", ignoreCase = true) // Fallback for debugging
                            }
                            Log.d(TAG, "Discovered ${filteredPeers.size} potential peers")
                            _peers.value = filteredPeers
                        }
                    }
                }
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    @Suppress("DEPRECATION")
                    val networkInfo = intent.getParcelableExtra<NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)
                    Log.d(TAG, "Connection changed: isConnected=${networkInfo?.isConnected}")
                    if (networkInfo?.isConnected == true) {
                        if (manager != null && channel != null) {
                            manager.requestConnectionInfo(channel) { info ->
                                Log.i(TAG, "Connection info available: GroupFormed=${info.groupFormed}, IsGroupOwner=${info.isGroupOwner}, OwnerAddr=${info.groupOwnerAddress?.hostAddress}")
                                _connectionInfo.value = info
                            }
                        }
                    } else {
                        _connectionInfo.value = null
                    }
                }
                WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE, WifiP2pDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE)
                    }
                    Log.d(TAG, "This device changed: ${device?.deviceName} status=${device?.status}")
                }
            }
        }
    }

    init {
        Log.i(TAG, "Initializing WiFiDirectManager. Available: ${manager != null && channel != null}")
        
        val intentFilter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }
        
        // Use proper registration for system broadcasts
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, intentFilter)
        }
        
        setupServiceDiscovery()
    }

    private fun hasRequiredPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun setupServiceDiscovery() {
        if (manager == null || channel == null) return
        
        try {
            manager.setServiceResponseListener(channel, object : WifiP2pManager.ServiceResponseListener {
                override fun onServiceAvailable(protocolType: Int, responseData: ByteArray?, srcDevice: WifiP2pDevice?) {
                    Log.d(TAG, "Service found via general listener from ${srcDevice?.deviceName}")
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set service response listener", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun startDiscovery() {
        if (manager == null || channel == null) {
            Log.e(TAG, "Cannot start discovery: P2P manager or channel is null")
            return
        }

        if (!hasRequiredPermissions()) {
            Log.e(TAG, "Cannot start discovery: Missing permissions")
            return
        }

        _peers.value = emptyList()
        _isDiscoveryActive.value = true

        Log.d(TAG, "Initiating peer discovery...")
        manager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Discovery initiation success")
            }
            override fun onFailure(reason: Int) {
                val reasonStr = when(reason) {
                    WifiP2pManager.P2P_UNSUPPORTED -> "P2P Unsupported"
                    WifiP2pManager.ERROR -> "Internal Error"
                    WifiP2pManager.BUSY -> "Busy"
                    else -> "Unknown ($reason)"
                }
                Log.e(TAG, "Discovery initiation failed: $reasonStr")
                _isDiscoveryActive.value = false
            }
        })
    }

    fun stopDiscovery() {
        if (manager == null || channel == null) return
        
        Log.d(TAG, "Stopping peer discovery")
        manager.stopPeerDiscovery(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() { Log.d(TAG, "Stop discovery success") }
            override fun onFailure(reason: Int) { Log.e(TAG, "Stop discovery failed: $reason") }
        })
        _isDiscoveryActive.value = false
    }

    @SuppressLint("MissingPermission")
    fun connect(device: WifiP2pDevice) {
        if (manager == null || channel == null) return
        
        Log.i(TAG, "Connecting to ${device.deviceName} (${device.deviceAddress})")
        val config = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
        }
        
        manager.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() { Log.d(TAG, "Connection request accepted for ${device.deviceName}") }
            override fun onFailure(reason: Int) { Log.e(TAG, "Connection request failed: $reason") }
        })
    }

    fun disconnect() {
        if (manager == null || channel == null) return
        
        Log.i(TAG, "Disconnecting and removing P2P group")
        manager.removeGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() { 
                Log.d(TAG, "Group removed successfully") 
                _connectionInfo.value = null
            }
            override fun onFailure(reason: Int) { Log.e(TAG, "Failed to remove group: $reason") }
        })
    }

    fun cleanup() {
        Log.d(TAG, "Cleaning up WiFiDirectManager")
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
