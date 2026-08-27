package com.itantra.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.itantra.app.audio.AudioCaptureManager
import com.itantra.app.ui.MicrophoneTestScreen
import com.itantra.app.ui.TransportMode
import com.itantra.app.ui.theme.ITantraTheme

class MainActivity : ComponentActivity() {
    
    // Instantiate managers at the activity level
    private val audioManager by lazy { AudioCaptureManager(this) }
    private val ttsManager by lazy { com.itantra.app.audio.TtsManager(this) }
    
    // Wi-Fi Components
    private val wifiTransport by lazy { com.itantra.app.comm.WiFiTransport() }
    private val wifiDiscoveryManager by lazy { com.itantra.app.comm.DiscoveryManager(this) }

    // Wi-Fi Direct Components
    private val wifiDirectManager by lazy { com.itantra.app.comm.WiFiDirectManager(this) }
    private val wifiDirectTransport by lazy { com.itantra.app.comm.WiFiDirectTransport(wifiDirectManager) }
    
    private val callSignManager by lazy { com.itantra.app.comm.CallSignManager(this) }
    // Bluetooth Components
    private val bluetoothTransport by lazy { com.itantra.app.comm.BluetoothTransport() }
    private val bluetoothDiscoveryManager by lazy { com.itantra.app.comm.BluetoothDiscoveryManager(this, callSignManager) }

    private val commManager by lazy { com.itantra.app.comm.CommunicationManager(wifiTransport) }
    
    private val transceiverManager by lazy { 
        com.itantra.app.comm.TransceiverManager(this, audioManager, ttsManager, commManager, callSignManager) 
    }

    private val peerRegistry by lazy { com.itantra.app.comm.PeerRegistry() }

    private val zeroConfigEmergencyManager by lazy {
        com.itantra.app.comm.ZeroConfigEmergencyManager(
            peerRegistry = peerRegistry,
            commManager = commManager,
            transceiverManager = transceiverManager,
            audioManager = audioManager,
            onSwitchTransport = { mode, address ->
                handleTransportModeChange(mode)
                if (address != null) {
                    commManager.connect(address)
                }
            }
        )
    }

    private var currentTransportMode = TransportMode.WIFI

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Start with Wi-Fi by default
        wifiDiscoveryManager.startAdvertising()
        wifiDiscoveryManager.startDiscovery()
        
        // Auto-start as Host for Wi-Fi
        commManager.connect(null)

        // Safe background availability monitoring feeding PeerRegistry
        lifecycleScope.launch {
            wifiDiscoveryManager.discoveredDevices.collect { devices ->
                peerRegistry.updateFromWifi(devices)
            }
        }
        lifecycleScope.launch {
            wifiDirectManager.peers.collect { p2pDevices ->
                peerRegistry.updateFromWifiDirect(p2pDevices)
            }
        }
        lifecycleScope.launch {
            bluetoothDiscoveryManager.discoveredPeers.collect { btPeers ->
                peerRegistry.updateFromBluetooth(btPeers)
            }
        }
        lifecycleScope.launch {
            commManager.connectionState.collect { state ->
                val peerId = commManager.getConnectedPeerId() ?: "station"
                peerRegistry.updateConnectionStatus(
                    peerId = peerId,
                    transport = currentTransportMode,
                    isConnected = state == com.itantra.app.comm.ConnectionState.CONNECTED
                )
            }
        }

        setContent {
            com.itantra.app.ui.theme.ITantraTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        MicrophoneTestScreen(
                            audioManager = audioManager,
                            ttsManager = ttsManager,
                            commManager = commManager,
                            discoveryManager = wifiDiscoveryManager,
                            wifiDirectManager = wifiDirectManager,
                            bluetoothDiscoveryManager = bluetoothDiscoveryManager,
                            callSignManager = callSignManager,
                            transceiverManager = transceiverManager,
                            peerRegistry = peerRegistry,
                            emergencyManager = zeroConfigEmergencyManager,
                            onTransportModeChange = { mode ->
                                currentTransportMode = mode
                                handleTransportModeChange(mode)
                            }
                        )
                    }
                }
            }
        }
    }

    private fun handleTransportModeChange(mode: TransportMode) {
        commManager.disconnect()
        
        // Stop all discovery/advertising
        wifiDiscoveryManager.stopAdvertising()
        wifiDiscoveryManager.stopDiscovery()
        wifiDirectManager.stopDiscovery()
        bluetoothDiscoveryManager.stopDiscovery()

        when (mode) {
            TransportMode.WIFI -> {
                commManager.setTransport(wifiTransport)
                wifiDiscoveryManager.startAdvertising()
                wifiDiscoveryManager.startDiscovery()
                // Auto-start as Host for Wi-Fi
                commManager.connect(null)
            }
            TransportMode.WIFI_DIRECT -> {
                commManager.setTransport(wifiDirectTransport)
                wifiDirectManager.startDiscovery()
                // For WiFi Direct, connect(null) means start discovery/advertising
                commManager.connect(null)
            }
            TransportMode.BLUETOOTH -> {
                commManager.setTransport(bluetoothTransport)
                bluetoothDiscoveryManager.startDiscovery()
                // Auto-start as Host for Bluetooth (listening)
                commManager.connect(null)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Ensure resources are released when the activity is destroyed
        wifiDiscoveryManager.stopAdvertising()
        wifiDiscoveryManager.stopDiscovery()
        wifiDirectManager.cleanup()
        bluetoothDiscoveryManager.stopDiscovery()
        
        audioManager.release()
        ttsManager.release()
        commManager.disconnect()
        wifiDirectTransport.release()
        transceiverManager.release()
        zeroConfigEmergencyManager.release()
    }
}
