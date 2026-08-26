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
    
    // Instantiate AudioCaptureManager at the activity level
    // This allows it to persist through configuration changes if needed,
    // though for this simple test we'll just release it in onDestroy.
    private val audioManager by lazy { AudioCaptureManager(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            ITantraTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        MicrophoneTestScreen(audioManager = audioManager)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Ensure resources are released when the activity is destroyed
        audioManager.release()
    }
}
