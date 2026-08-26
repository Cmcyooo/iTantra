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
import com.itantra.app.audio.AudioCaptureManager
import com.itantra.app.ui.MicrophoneTestScreen
import com.itantra.app.ui.theme.ITantraTheme

class MainActivity : ComponentActivity() {
    
    // Instantiate managers at the activity level
    private val audioManager by lazy { AudioCaptureManager(this) }
    private val ttsManager by lazy { com.itantra.app.audio.TtsManager(this) }
    private val discoveryManager by lazy { com.itantra.app.comm.DiscoveryManager(this) }
    private val commManager by lazy { com.itantra.app.comm.CommunicationManager(com.itantra.app.comm.WiFiTransport()) }
    private val transceiverManager by lazy { 
        com.itantra.app.comm.TransceiverManager(this, audioManager, ttsManager, commManager) 
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Start discovery and advertising automatically
        discoveryManager.startAdvertising()
        discoveryManager.startDiscovery()
        
        // Auto-start as Host so we're ready to receive
        commManager.connect(null)

        setContent {
            com.itantra.app.ui.theme.ITantraTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        MicrophoneTestScreen(
                            audioManager = audioManager,
                            ttsManager = ttsManager,
                            commManager = commManager,
                            discoveryManager = discoveryManager,
                            transceiverManager = transceiverManager
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Ensure resources are released when the activity is destroyed
        discoveryManager.stopAdvertising()
        discoveryManager.stopDiscovery()
        audioManager.release()
        ttsManager.release()
        commManager.disconnect()
        transceiverManager.release()
    }
}
