package com.itantra.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.itantra.app.audio.AudioCaptureManager

/**
 * A simple test screen for microphone capture.
 * Displays real-time stats and handles permissions.
 */
@Composable
fun MicrophoneTestScreen(audioManager: AudioCaptureManager) {
    val context = LocalContext.current
    val audioState by audioManager.state.collectAsState()
    
    // Track permission state locally
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "iTantra",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Microphone Test",
            fontSize = 20.sp,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                StatusItem(
                    label = "Microphone Permission:",
                    value = if (hasPermission) "GRANTED" else "NOT GRANTED",
                    valueColor = if (hasPermission) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
                
                StatusItem(
                    label = "Recording Status:",
                    value = if (audioState.isRecording) "RECORDING" else "IDLE",
                    valueColor = if (audioState.isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                )
                
                StatusItem(
                    label = "VAD Status:",
                    value = audioState.vadStatus.name,
                    valueColor = when (audioState.vadStatus) {
                        com.itantra.app.audio.VadStatus.SILENCE -> MaterialTheme.colorScheme.secondary
                        com.itantra.app.audio.VadStatus.SPEECH_DETECTED, 
                        com.itantra.app.audio.VadStatus.SPEAKING -> MaterialTheme.colorScheme.primary
                        com.itantra.app.audio.VadStatus.SPEECH_ENDED -> MaterialTheme.colorScheme.tertiary
                    }
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                StatusItem(
                    label = "PCM Samples:", 
                    value = audioState.sampleCount.toString()
                )
                StatusItem(
                    label = "Captured Duration:", 
                    value = "%.2f seconds".format(audioState.durationSeconds)
                )
                StatusItem(
                    label = "Audio Level (RMS):", 
                    value = "%.0f".format(audioState.rms)
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                StatusItem(
                    label = "STT Status:",
                    value = audioState.sttStatus.name,
                    valueColor = when (audioState.sttStatus) {
                        com.itantra.app.audio.SttStatus.IDLE -> MaterialTheme.colorScheme.secondary
                        com.itantra.app.audio.SttStatus.SPEECH_DETECTED -> MaterialTheme.colorScheme.primary
                        com.itantra.app.audio.SttStatus.TRANSCRIBING -> MaterialTheme.colorScheme.tertiary
                        com.itantra.app.audio.SttStatus.COMPLETE -> MaterialTheme.colorScheme.primary
                        com.itantra.app.audio.SttStatus.ERROR -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )

                if (audioState.recognizedText.isNotEmpty()) {
                    Text(
                        text = "Recognized Text:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Text(
                        text = audioState.recognizedText,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                audioState.lastSttResult?.let { res ->
                    Text(
                        text = "Last Inference: Proc: ${res.processingTimeMs}ms, RTF: ${"%.3f".format(res.rtf)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        if (!hasPermission) {
            Button(
                onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("GRANT MICROPHONE PERMISSION")
            }
        } else {
            if (audioState.isRecording) {
                Button(
                    onClick = { audioManager.stopRecording() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("STOP RECORDING")
                }
            } else {
                Button(
                    onClick = { audioManager.startRecording() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("START RECORDING")
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Audio format: 16 kHz, Mono, PCM 16-bit",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun StatusItem(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontWeight = FontWeight.Medium)
        Text(text = value, color = valueColor, fontWeight = FontWeight.Bold)
    }
}
