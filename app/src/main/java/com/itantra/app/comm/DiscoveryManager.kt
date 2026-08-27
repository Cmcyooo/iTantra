package com.itantra.app.comm

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages local network device discovery using Android NSD (Network Service Discovery).
 * Implements mDNS/DNS-SD to find other iTantra devices.
 */
class DiscoveryManager(private val context: Context) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    
    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    
    private val SERVICE_TYPE = "_itantra._tcp."
    private val SERVICE_NAME_PREFIX = "iTantra-"
    private var localServiceName: String? = null
    
    companion object {
        private const val TAG = "DiscoveryManager"
        private const val PORT = 8888 // Must match WiFiTransport.PORT
    }

    data class DiscoveredDevice(
        val name: String,
        val ip: String,
        val port: Int,
        val serviceInfo: NsdServiceInfo? = null
    )

    fun startAdvertising(deviceName: String = Build.MODEL) {
        stopAdvertising()
        
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "$SERVICE_NAME_PREFIX$deviceName"
            serviceType = SERVICE_TYPE
            port = PORT
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                localServiceName = info.serviceName
                Log.d(TAG, "Service registered: ${info.serviceName}")
            }

            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Registration failed: $errorCode")
            }

            override fun onServiceUnregistered(info: NsdServiceInfo) {
                Log.d(TAG, "Service unregistered: ${info.serviceName}")
            }

            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Unregistration failed: $errorCode")
            }
        }

        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
    }

    fun stopAdvertising() {
        registrationListener?.let {
            try {
                nsdManager.unregisterService(it)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to unregister service", e)
            }
        }
        registrationListener = null
    }

    fun startDiscovery() {
        if (_isSearching.value) return
        
        stopDiscovery()
        _discoveredDevices.value = emptyList()
        _isSearching.value = true

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d(TAG, "Service discovery started")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                Log.d(TAG, "Service found: ${service.serviceName}")
                if (service.serviceType != SERVICE_TYPE) {
                    Log.d(TAG, "Unknown Service Type: ${service.serviceType}")
                } else if (service.serviceName == localServiceName) {
                    Log.d(TAG, "Same machine: $localServiceName")
                } else if (service.serviceName.contains(SERVICE_NAME_PREFIX)) {
                    nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                            Log.e(TAG, "Resolve failed for ${serviceInfo.serviceName}: $errorCode")
                            // If resolution fails (e.g. timeout), we don't add the device.
                            // The system might retry discovery automatically or the user can rescan.
                        }

                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            Log.d(TAG, "Resolve Succeeded for ${serviceInfo.serviceName}. IP: ${serviceInfo.host.hostAddress}")
                            addDevice(serviceInfo)
                        }
                    })
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                Log.d(TAG, "Service lost: ${service.serviceName}")
                removeDevice(service.serviceName)
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.i(TAG, "Discovery stopped: $serviceType")
                _isSearching.value = false
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery failed: $errorCode")
                stopDiscovery()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Stop discovery failed: $errorCode")
                nsdManager.stopServiceDiscovery(this)
            }
        }

        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    fun stopDiscovery() {
        discoveryListener?.let {
            try {
                nsdManager.stopServiceDiscovery(it)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to stop discovery", e)
            }
        }
        discoveryListener = null
        _isSearching.value = false
    }

    private fun addDevice(info: NsdServiceInfo) {
        val device = DiscoveredDevice(
            name = info.serviceName.removePrefix(SERVICE_NAME_PREFIX),
            ip = info.host.hostAddress ?: "",
            port = info.port,
            serviceInfo = info
        )
        
        val currentList = _discoveredDevices.value.toMutableList()
        if (currentList.none { it.ip == device.ip }) {
            currentList.add(device)
            _discoveredDevices.value = currentList
        }
    }

    private fun removeDevice(serviceName: String) {
        val nameToMatch = serviceName.removePrefix(SERVICE_NAME_PREFIX)
        val currentList = _discoveredDevices.value.toMutableList()
        if (currentList.removeIf { it.name == nameToMatch || it.serviceInfo?.serviceName == serviceName }) {
            _discoveredDevices.value = currentList
        }
    }
}
