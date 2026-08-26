package com.itantra.app.comm

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages Bluetooth device discovery.
 * Filters for iTantra-named devices.
 */
class BluetoothDiscoveryManager(private val context: Context) {
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    
    private val _discoveredDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<BluetoothDevice>> = _discoveredDevices.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    device?.let {
                        // Add every device that might be an iTantra device.
                        // We will refine the display name in the UI layer.
                        addDevice(it)
                    }
                }
                BluetoothDevice.ACTION_NAME_CHANGED -> {
                    val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    device?.let {
                        updateDevice(it)
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    _isSearching.value = true
                    Log.d(TAG, "Bluetooth Discovery Started")
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    _isSearching.value = false
                    Log.d(TAG, "Bluetooth Discovery Finished")
                }
            }
        }
    }

    companion object {
        private const val TAG = "BluetoothDiscoveryManager"
    }

    @SuppressLint("MissingPermission")
    fun startDiscovery() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.w(TAG, "Bluetooth is disabled or not supported")
            return
        }

        _discoveredDevices.value = emptyList()
        
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothDevice.ACTION_NAME_CHANGED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        context.registerReceiver(receiver, filter)

        if (bluetoothAdapter.isDiscovering) {
            bluetoothAdapter.cancelDiscovery()
        }
        bluetoothAdapter.startDiscovery()
    }

    @SuppressLint("MissingPermission")
    fun stopDiscovery() {
        if (bluetoothAdapter?.isDiscovering == true) {
            bluetoothAdapter.cancelDiscovery()
        }
        try {
            context.unregisterReceiver(receiver)
        } catch (e: Exception) {
            // Receiver might not be registered
        }
        _isSearching.value = false
    }

    private fun addDevice(device: BluetoothDevice) {
        val currentList = _discoveredDevices.value.toMutableList()
        if (currentList.none { it.address == device.address }) {
            currentList.add(device)
            _discoveredDevices.value = currentList
        }
    }

    private fun updateDevice(device: BluetoothDevice) {
        val currentList = _discoveredDevices.value.toMutableList()
        val index = currentList.indexOfFirst { it.address == device.address }
        if (index != -1) {
            currentList[index] = device
            _discoveredDevices.value = currentList
        }
    }
}
