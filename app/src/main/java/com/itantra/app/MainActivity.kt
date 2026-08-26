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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            com.itantra.app.ui.theme.ITantraTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        MicrophoneTestScreen(
                            audioManager = audioManager,
                            ttsManager = ttsManager
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Ensure resources are released when the activity is destroyed
        audioManager.release()
        ttsManager.release()
    }
}
