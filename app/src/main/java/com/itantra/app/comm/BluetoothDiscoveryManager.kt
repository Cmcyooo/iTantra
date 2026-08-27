package com.itantra.app.comm

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Data representation of a discovered Bluetooth peer with resolved friendly display name.
 */
data class DiscoveredBluetoothPeer(
    val device: BluetoothDevice,
    val address: String,
    val displayName: String,
    val isBonded: Boolean
)

/**
 * Manages Bluetooth device discovery with complete deduplication,
 * multi-tier friendly name resolution, bonded device pre-population,
 * and resilient lifecycle handling across all Android versions.
 */
class BluetoothDiscoveryManager(
    private val context: Context,
    private val callSignManager: CallSignManager? = null
) {

    companion object {
        private const val TAG = "BluetoothDiscoveryManager"
    }

    private val appContext = context.applicationContext
    private val bluetoothManager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter ?: BluetoothAdapter.getDefaultAdapter()

    // Map of address -> DiscoveredBluetoothPeer to guarantee deduplication and live name updates
    private val peerMap = ConcurrentHashMap<String, DiscoveredBluetoothPeer>()

    // Cache of resolved friendly names: address -> name
    private val nameCache = ConcurrentHashMap<String, String>()

    private val _discoveredDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<BluetoothDevice>> = _discoveredDevices.asStateFlow()

    private val _discoveredPeers = MutableStateFlow<List<DiscoveredBluetoothPeer>>(emptyList())
    val discoveredPeers: StateFlow<List<DiscoveredBluetoothPeer>> = _discoveredPeers.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _isBluetoothEnabled = MutableStateFlow(bluetoothAdapter?.isEnabled == true)
    val isBluetoothEnabled: StateFlow<Boolean> = _isBluetoothEnabled.asStateFlow()

    private val _statusMessage = MutableStateFlow("Ready")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private var isReceiverRegistered = false

    private val receiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(recvContext: Context, intent: Intent) {
            val action = intent.action ?: return
            Log.d(TAG, "[BT-DIAG] Broadcast received: $action")

            when (action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }

                    val extraName = intent.getStringExtra(BluetoothDevice.EXTRA_NAME)
                    if (device != null) {
                        Log.i(TAG, "[BT-DIAG] Device found broadcast: addr=${device.address}, extraName=$extraName, deviceName=${device.name}")
                        if (!extraName.isNullOrBlank()) {
                            nameCache[device.address] = extraName
                        }
                        processDiscoveredDevice(device, extraName)
                    }
                }

                BluetoothDevice.ACTION_NAME_CHANGED -> {
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    val extraName = intent.getStringExtra(BluetoothDevice.EXTRA_NAME)
                    if (device != null) {
                        Log.i(TAG, "[BT-DIAG] Device name changed: addr=${device.address}, name=${extraName ?: device.name}")
                        val resolvedName = extraName ?: device.name
                        if (!resolvedName.isNullOrBlank()) {
                            nameCache[device.address] = resolvedName
                        }
                        processDiscoveredDevice(device, resolvedName)
                    }
                }

                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    Log.i(TAG, "[BT-DIAG] Discovery started")
                    _isSearching.value = true
                    _statusMessage.value = "Scanning for nearby Bluetooth devices..."
                }

                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    Log.i(TAG, "[BT-DIAG] Discovery finished. Total peers found: ${peerMap.size}")
                    _isSearching.value = false
                    if (peerMap.isEmpty()) {
                        _statusMessage.value = "No nearby Bluetooth iTantra devices found"
                    } else {
                        _statusMessage.value = "Scan complete. Found ${peerMap.size} device(s)."
                    }
                }

                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    val isEnabled = (state == BluetoothAdapter.STATE_ON)
                    Log.i(TAG, "[BT-DIAG] Adapter state changed: isEnabled=$isEnabled")
                    _isBluetoothEnabled.value = isEnabled
                    if (!isEnabled) {
                        _statusMessage.value = "Bluetooth is turned off"
                        stopDiscovery()
                        peerMap.clear()
                        updatePeerLists()
                    }
                }
            }
        }
    }

    init {
        Log.i(TAG, "[BT-DIAG] Initializing BluetoothDiscoveryManager. Adapter available: ${bluetoothAdapter != null}, enabled: ${bluetoothAdapter?.isEnabled}")
        _isBluetoothEnabled.value = (bluetoothAdapter?.isEnabled == true)
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            _statusMessage.value = "Bluetooth is turned off"
        }
    }

    fun hasRequiredPermissions(): Boolean {
        val hasPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val scanGranted = ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
            val connectGranted = ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            scanGranted && connectGranted
        } else {
            val locGranted = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val btGranted = ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
            val adminGranted = ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED
            locGranted && btGranted && adminGranted
        }
        Log.d(TAG, "[BT-DIAG] Permission state: $hasPerm")
        return hasPerm
    }

    @SuppressLint("MissingPermission")
    fun startDiscovery() {
        Log.i(TAG, "[BT-DIAG] startDiscovery() requested")
        
        if (bluetoothAdapter == null) {
            Log.e(TAG, "[BT-DIAG] Bluetooth not supported on this hardware")
            _statusMessage.value = "Bluetooth not supported"
            return
        }

        _isBluetoothEnabled.value = bluetoothAdapter.isEnabled
        if (!bluetoothAdapter.isEnabled) {
            Log.w(TAG, "[BT-DIAG] Bluetooth is turned off")
            _statusMessage.value = "Bluetooth is turned off"
            return
        }

        if (!hasRequiredPermissions()) {
            Log.w(TAG, "[BT-DIAG] Missing required Bluetooth permissions")
            _statusMessage.value = "Bluetooth permissions required"
            return
        }

        // 1. Cancel previous discovery safely
        stopDiscovery()

        // 2. Clear stale discovery state
        peerMap.clear()
        _statusMessage.value = "Scanning..."

        // 3. Pre-populate paired/bonded devices (ensures already-paired devices show immediately)
        try {
            val bonded = bluetoothAdapter.bondedDevices
            if (bonded != null) {
                Log.i(TAG, "[BT-DIAG] Pre-populating ${bonded.size} bonded Bluetooth device(s)")
                for (bDevice in bonded) {
                    processDiscoveredDevice(bDevice, bDevice.name, isBonded = true)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "[BT-DIAG] Error reading bonded devices: ${e.message}")
        }

        // 4. Register receiver safely
        registerReceiverSafe()

        // 5. Start inquiry scan
        try {
            if (bluetoothAdapter.isDiscovering) {
                bluetoothAdapter.cancelDiscovery()
            }
            val started = bluetoothAdapter.startDiscovery()
            Log.i(TAG, "[BT-DIAG] bluetoothAdapter.startDiscovery() returned: $started")
            if (!started) {
                _statusMessage.value = "Unable to start Bluetooth scan"
            }
        } catch (e: Exception) {
            Log.e(TAG, "[BT-DIAG] Exception invoking startDiscovery()", e)
            _statusMessage.value = "Unable to start Bluetooth scan"
        }
    }

    @SuppressLint("MissingPermission")
    fun stopDiscovery() {
        Log.d(TAG, "[BT-DIAG] stopDiscovery() requested")
        try {
            if (bluetoothAdapter?.isDiscovering == true) {
                bluetoothAdapter.cancelDiscovery()
            }
        } catch (e: Exception) {
            Log.w(TAG, "[BT-DIAG] Error cancelling discovery: ${e.message}")
        }
        unregisterReceiverSafe()
        _isSearching.value = false
    }

    @SuppressLint("MissingPermission")
    private fun processDiscoveredDevice(device: BluetoothDevice, candidateName: String? = null, isBonded: Boolean = false) {
        val address = device.address ?: return
        val rawSystemName = candidateName ?: nameCache[address] ?: device.name

        // Multi-tier name resolution priority:
        // 1. User-defined Call Sign
        // 2. Bluetooth device name / cached name
        // 3. Android device/system name
        // 4. Friendly fallback: "Nearby iTantra Device" (Never raw MAC or "Unknown Device")
        val savedCallSign = callSignManager?.getPeerCallSign(address)
        val resolvedName = when {
            !savedCallSign.isNullOrBlank() -> savedCallSign
            !rawSystemName.isNullOrBlank() && !rawSystemName.contains("null", ignoreCase = true) && !rawSystemName.equals("Unknown", ignoreCase = true) -> rawSystemName
            else -> "Nearby iTantra Device"
        }

        Log.d(TAG, "[BT-DIAG] Device resolved: addr=$address -> displayName='$resolvedName' (bonded=$isBonded)")

        peerMap[address] = DiscoveredBluetoothPeer(
            device = device,
            address = address,
            displayName = resolvedName,
            isBonded = isBonded || (device.bondState == BluetoothDevice.BOND_BONDED)
        )

        updatePeerLists()
    }

    private fun updatePeerLists() {
        val peerList = peerMap.values.toList()
        _discoveredPeers.value = peerList
        _discoveredDevices.value = peerList.map { it.device }
    }

    private fun registerReceiverSafe() {
        if (isReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothDevice.ACTION_NAME_CHANGED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                appContext.registerReceiver(receiver, filter)
            }
            isReceiverRegistered = true
            Log.d(TAG, "[BT-DIAG] BroadcastReceiver registered successfully")
        } catch (e: Exception) {
            Log.e(TAG, "[BT-DIAG] Failed to register BroadcastReceiver", e)
        }
    }

    private fun unregisterReceiverSafe() {
        if (!isReceiverRegistered) return
        try {
            appContext.unregisterReceiver(receiver)
            isReceiverRegistered = false
            Log.d(TAG, "[BT-DIAG] BroadcastReceiver unregistered successfully")
        } catch (e: Exception) {
            Log.w(TAG, "[BT-DIAG] Error unregistering BroadcastReceiver: ${e.message}")
        }
    }

    fun cleanup() {
        Log.i(TAG, "[BT-DIAG] Cleaning up BluetoothDiscoveryManager")
        stopDiscovery()
        peerMap.clear()
        nameCache.clear()
        updatePeerLists()
    }
}
