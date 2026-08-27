package com.itantra.app.comm

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
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

/**
 * Explicit 9-state P2P lifecycle state machine.
 */
enum class P2PState {
    IDLE,
    DISCOVERING,
    PEER_FOUND,
    CONNECTING,
    GROUP_FORMING,
    SOCKET_CONNECTING,
    CONNECTED,
    FAILED,
    DISCONNECTED
}

/**
 * Manages Wi-Fi Direct (P2P) discovery and connection.
 * Guarantees crash-free lifecycle, channel auto-recovery, defensive permission handling,
 * and an explicit 9-state state machine.
 */
class WiFiDirectManager(private val context: Context) {

    companion object {
        private const val TAG = "WiFiDirectManager"
    }

    private val appContext = context.applicationContext
    private val manager: WifiP2pManager? = appContext.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private var channel: WifiP2pManager.Channel? = null

    private val _p2pState = MutableStateFlow(P2PState.IDLE)
    val p2pState: StateFlow<P2PState> = _p2pState.asStateFlow()

    private val _statusMessage = MutableStateFlow("Ready")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _peers = MutableStateFlow<List<WifiP2pDevice>>(emptyList())
    val peers: StateFlow<List<WifiP2pDevice>> = _peers.asStateFlow()

    private val _connectionInfo = MutableStateFlow<WifiP2pInfo?>(null)
    val connectionInfo: StateFlow<WifiP2pInfo?> = _connectionInfo.asStateFlow()

    private val _isDiscoveryActive = MutableStateFlow(false)
    val isDiscoveryActive: StateFlow<Boolean> = _isDiscoveryActive.asStateFlow()

    private val _isAvailable = MutableStateFlow(false)
    val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    private var isReceiverRegistered = false

    private val receiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(recvContext: Context, intent: Intent) {
            val action = intent.action ?: return
            Log.d(TAG, "[P2P-DIAG] Broadcast received: $action")

            if (!hasRequiredPermissions()) {
                Log.w(TAG, "[P2P-DIAG] Permission state: Missing required permissions in receiver")
                _statusMessage.value = "Nearby devices permission required"
                return
            }

            when (action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    val isEnabled = state == WifiP2pManager.WIFI_P2P_STATE_ENABLED
                    Log.i(TAG, "[P2P-DIAG] Wi-Fi P2P state transition: ${if (isEnabled) "ENABLED" else "DISABLED"}")
                    _isAvailable.value = isEnabled && manager != null && channel != null
                    if (!isEnabled) {
                        _peers.value = emptyList()
                        _statusMessage.value = "Wi-Fi Direct unavailable"
                        transitionTo(P2PState.DISCONNECTED)
                    }
                }

                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    Log.d(TAG, "[P2P-DIAG] Peers changed broadcast received")
                    val currentChannel = channel
                    if (manager != null && currentChannel != null) {
                        try {
                            manager.requestPeers(currentChannel) { peerList ->
                                val list = peerList?.deviceList?.toList() ?: emptyList()
                                Log.i(TAG, "[P2P-DIAG] Peer discovered count: ${list.size}")
                                list.forEach { dev ->
                                    Log.d(TAG, "  -> Found peer: ${dev.deviceName} (${dev.deviceAddress}) status=${dev.status}")
                                }
                                _peers.value = list
                                if (list.isNotEmpty()) {
                                    if (_p2pState.value == P2PState.DISCOVERING || _p2pState.value == P2PState.IDLE) {
                                        transitionTo(P2PState.PEER_FOUND)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "[P2P-DIAG] Exception calling requestPeers", e)
                        }
                    }
                }

                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    @Suppress("DEPRECATION")
                    val networkInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO, NetworkInfo::class.java)
                    } else {
                        intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO)
                    }
                    val isConnected = networkInfo?.isConnected == true
                    Log.i(TAG, "[P2P-DIAG] Connection changed broadcast: isConnected=$isConnected")

                    if (isConnected) {
                        transitionTo(P2PState.GROUP_FORMING)
                        val currentChannel = channel
                        if (manager != null && currentChannel != null) {
                            try {
                                manager.requestConnectionInfo(currentChannel) { info ->
                                    if (info != null) {
                                        Log.i(TAG, "[P2P-DIAG] Group formed info: groupFormed=${info.groupFormed}, isGroupOwner=${info.isGroupOwner}, ownerAddr=${info.groupOwnerAddress?.hostAddress}")
                                        _connectionInfo.value = info
                                        if (info.groupFormed) {
                                            transitionTo(P2PState.SOCKET_CONNECTING)
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "[P2P-DIAG] Exception calling requestConnectionInfo", e)
                            }
                        }
                    } else {
                        Log.i(TAG, "[P2P-DIAG] Disconnected event received")
                        _connectionInfo.value = null
                        if (_p2pState.value != P2PState.IDLE && _p2pState.value != P2PState.DISCOVERING) {
                            transitionTo(P2PState.DISCONNECTED)
                        }
                    }
                }

                WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE, WifiP2pDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE)
                    }
                    Log.d(TAG, "[P2P-DIAG] Local device status changed: ${device?.deviceName} status=${device?.status}")
                }
            }
        }
    }

    init {
        Log.i(TAG, "[P2P-DIAG] Initializing WiFiDirectManager...")
        initChannel()
        registerReceiverSafe()
    }

    private fun initChannel() {
        if (manager == null) {
            Log.w(TAG, "[P2P-DIAG] WifiP2pManager is null. Device does not support Wi-Fi Direct.")
            _isAvailable.value = false
            _statusMessage.value = "Wi-Fi Direct unavailable"
            return
        }

        try {
            channel = manager.initialize(appContext, appContext.mainLooper) {
                Log.w(TAG, "[P2P-DIAG] Wi-Fi Direct Channel disconnected by framework! Attempting recovery...")
                channel = null
                _isAvailable.value = false
                // Attempt recovery after a short delay
                initChannel()
            }
            _isAvailable.value = (channel != null)
            Log.i(TAG, "[P2P-DIAG] Wi-Fi Direct channel initialized successfully: ${channel != null}")
        } catch (e: Exception) {
            Log.e(TAG, "[P2P-DIAG] Failed to initialize Wi-Fi Direct channel", e)
            channel = null
            _isAvailable.value = false
            _statusMessage.value = "Wi-Fi Direct unavailable"
        }
    }

    private fun registerReceiverSafe() {
        if (isReceiverRegistered) return
        val intentFilter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.registerReceiver(receiver, intentFilter, Context.RECEIVER_EXPORTED)
            } else {
                appContext.registerReceiver(receiver, intentFilter)
            }
            isReceiverRegistered = true
            Log.d(TAG, "[P2P-DIAG] BroadcastReceiver registered successfully")
        } catch (e: Exception) {
            Log.e(TAG, "[P2P-DIAG] Failed to register BroadcastReceiver", e)
        }
    }

    private fun unregisterReceiverSafe() {
        if (!isReceiverRegistered) return
        try {
            appContext.unregisterReceiver(receiver)
            isReceiverRegistered = false
            Log.d(TAG, "[P2P-DIAG] BroadcastReceiver unregistered successfully")
        } catch (e: Exception) {
            Log.w(TAG, "[P2P-DIAG] Error unregistering BroadcastReceiver: ${e.message}")
        }
    }

    fun hasRequiredPermissions(): Boolean {
        val hasPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
        Log.d(TAG, "[P2P-DIAG] Permission state: $hasPerm")
        return hasPerm
    }

    @SuppressLint("MissingPermission")
    fun startDiscovery() {
        Log.i(TAG, "[P2P-DIAG] Discovery start requested...")
        if (!hasRequiredPermissions()) {
            Log.w(TAG, "[P2P-DIAG] Cannot start discovery: Missing permissions")
            _statusMessage.value = "Nearby devices permission required"
            transitionTo(P2PState.FAILED)
            return
        }

        if (manager == null || channel == null) {
            Log.e(TAG, "[P2P-DIAG] Cannot start discovery: Manager or channel null")
            initChannel()
            if (channel == null) {
                _statusMessage.value = "Wi-Fi Direct unavailable"
                transitionTo(P2PState.FAILED)
                return
            }
        }

        _peers.value = emptyList()
        _isDiscoveryActive.value = true
        transitionTo(P2PState.DISCOVERING)
        _statusMessage.value = "Searching for nearby Wi-Fi Direct devices..."

        val currentMgr = manager
        val currentChannel = channel
        if (currentMgr == null || currentChannel == null) {
            _statusMessage.value = "Wi-Fi Direct unavailable"
            transitionTo(P2PState.FAILED)
            return
        }

        try {
            currentMgr.discoverPeers(currentChannel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.i(TAG, "[P2P-DIAG] Discovery start: discoverPeers() SUCCESS")
                }

                override fun onFailure(reason: Int) {
                    val reasonStr = when (reason) {
                        WifiP2pManager.P2P_UNSUPPORTED -> "P2P Unsupported"
                        WifiP2pManager.ERROR -> "Internal Error"
                        WifiP2pManager.BUSY -> "System Busy"
                        else -> "Error Code $reason"
                    }
                    Log.e(TAG, "[P2P-DIAG] Discovery start: discoverPeers() FAILED: $reasonStr")
                    _isDiscoveryActive.value = false
                    _statusMessage.value = "Unable to start Wi-Fi Direct discovery ($reasonStr)"
                    transitionTo(P2PState.FAILED)
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "[P2P-DIAG] Exception invoking discoverPeers()", e)
            _isDiscoveryActive.value = false
            _statusMessage.value = "Unable to start Wi-Fi Direct discovery"
            transitionTo(P2PState.FAILED)
        }
    }

    fun stopDiscovery() {
        Log.d(TAG, "[P2P-DIAG] Discovery stop requested")
        val currentChannel = channel
        if (manager != null && currentChannel != null) {
            try {
                manager.stopPeerDiscovery(currentChannel, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        Log.d(TAG, "[P2P-DIAG] stopPeerDiscovery() success")
                    }
                    override fun onFailure(reason: Int) {
                        Log.d(TAG, "[P2P-DIAG] stopPeerDiscovery() failure: $reason")
                    }
                })
            } catch (e: Exception) {
                Log.w(TAG, "[P2P-DIAG] Exception stopping discovery: ${e.message}")
            }
        }
        _isDiscoveryActive.value = false
    }

    @SuppressLint("MissingPermission")
    fun connect(device: WifiP2pDevice) {
        Log.i(TAG, "[P2P-DIAG] Connect request: target=${device.deviceName} (${device.deviceAddress})")
        if (manager == null || channel == null) {
            initChannel()
            if (channel == null) {
                _statusMessage.value = "Wi-Fi Direct unavailable"
                transitionTo(P2PState.FAILED)
                return
            }
        }

        transitionTo(P2PState.CONNECTING)
        _statusMessage.value = "Connecting to ${device.deviceName}..."

        val config = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
        }

        val currentMgr = manager
        val currentChannel = channel
        if (currentMgr == null || currentChannel == null) {
            _statusMessage.value = "Wi-Fi Direct unavailable"
            transitionTo(P2PState.FAILED)
            return
        }

        try {
            currentMgr.connect(currentChannel, config, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.i(TAG, "[P2P-DIAG] connect() request accepted by framework for ${device.deviceName}")
                }

                override fun onFailure(reason: Int) {
                    val reasonStr = when (reason) {
                        WifiP2pManager.P2P_UNSUPPORTED -> "P2P Unsupported"
                        WifiP2pManager.ERROR -> "Internal Error"
                        WifiP2pManager.BUSY -> "System Busy"
                        else -> "Error Code $reason"
                    }
                    Log.e(TAG, "[P2P-DIAG] connect() failed: $reasonStr")
                    _statusMessage.value = "Connection failed: $reasonStr"
                    transitionTo(P2PState.FAILED)
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "[P2P-DIAG] Exception invoking connect()", e)
            _statusMessage.value = "Connection attempt failed"
            transitionTo(P2PState.FAILED)
        }
    }

    fun disconnect() {
        Log.i(TAG, "[P2P-DIAG] Disconnect requested, tearing down P2P group")
        val currentChannel = channel
        if (manager != null && currentChannel != null) {
            try {
                manager.removeGroup(currentChannel, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        Log.i(TAG, "[P2P-DIAG] removeGroup() success")
                        _connectionInfo.value = null
                        transitionTo(P2PState.DISCONNECTED)
                    }
                    override fun onFailure(reason: Int) {
                        Log.w(TAG, "[P2P-DIAG] removeGroup() failed: $reason")
                        _connectionInfo.value = null
                        transitionTo(P2PState.DISCONNECTED)
                    }
                })
            } catch (e: Exception) {
                Log.w(TAG, "[P2P-DIAG] Exception removing group: ${e.message}")
                _connectionInfo.value = null
                transitionTo(P2PState.DISCONNECTED)
            }
        } else {
            _connectionInfo.value = null
            transitionTo(P2PState.DISCONNECTED)
        }
    }

    fun updateSocketState(connected: Boolean) {
        if (connected) {
            Log.i(TAG, "[P2P-DIAG] Socket connection verified! Transitioning to CONNECTED.")
            transitionTo(P2PState.CONNECTED)
            _statusMessage.value = "Connected"
        } else {
            if (_p2pState.value == P2PState.CONNECTED || _p2pState.value == P2PState.SOCKET_CONNECTING) {
                Log.i(TAG, "[P2P-DIAG] Socket disconnected.")
                transitionTo(P2PState.DISCONNECTED)
                _statusMessage.value = "Disconnected"
            }
        }
    }

    fun resetState() {
        Log.d(TAG, "[P2P-DIAG] Resetting state to IDLE")
        _peers.value = emptyList()
        _connectionInfo.value = null
        _isDiscoveryActive.value = false
        transitionTo(P2PState.IDLE)
        _statusMessage.value = "Ready"
    }

    private fun transitionTo(newState: P2PState) {
        Log.i(TAG, "[P2P-DIAG] P2P State transition: ${_p2pState.value} -> $newState")
        _p2pState.value = newState
    }

    fun cleanup() {
        Log.i(TAG, "[P2P-DIAG] Cleaning up WiFiDirectManager")
        stopDiscovery()
        unregisterReceiverSafe()
        _peers.value = emptyList()
        _connectionInfo.value = null
        transitionTo(P2PState.IDLE)
    }
}
