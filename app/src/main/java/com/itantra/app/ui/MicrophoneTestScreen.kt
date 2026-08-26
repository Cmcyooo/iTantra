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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.core.content.ContextCompat
import com.itantra.app.audio.*

/**
 * A simple test screen for microphone capture and TTS.
 * Displays real-time stats and handles permissions.
 */
@Composable
fun MicrophoneTestScreen(
    audioManager: AudioCaptureManager,
    ttsManager: TtsManager
) {
    val context = LocalContext.current
    val audioState by audioManager.state.collectAsState()
    val ttsStatus by ttsManager.status.collectAsState()
    val ttsResult by ttsManager.lastResult.collectAsState()
    
    var ttsText by remember { mutableStateOf("Hello, this is iTantra.") }
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

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
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
                        VadStatus.SILENCE -> MaterialTheme.colorScheme.secondary
                        VadStatus.SPEECH_DETECTED, 
                        VadStatus.SPEAKING -> MaterialTheme.colorScheme.primary
                        VadStatus.SPEECH_ENDED -> MaterialTheme.colorScheme.tertiary
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

        Spacer(modifier = Modifier.height(24.dp))

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Text(
            text = "Text-to-Speech Test",
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(vertical = 12.dp)
        )

        OutlinedTextField(
            value = ttsText,
            onValueChange = { ttsText = it },
            label = { Text("Text to Speak") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3
        )

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                StatusItem(
                    label = "TTS Status:",
                    value = ttsStatus.name,
                    valueColor = when (ttsStatus) {
                        TtsStatus.IDLE -> MaterialTheme.colorScheme.secondary
                        TtsStatus.LOADING -> MaterialTheme.colorScheme.tertiary
                        TtsStatus.SYNTHESIZING -> MaterialTheme.colorScheme.primary
                        TtsStatus.PLAYING -> MaterialTheme.colorScheme.primary
                        TtsStatus.COMPLETE -> MaterialTheme.colorScheme.secondary
                        TtsStatus.ERROR -> MaterialTheme.colorScheme.error
                    }
                )

                ttsResult?.let { res ->
                    Spacer(modifier = Modifier.height(8.dp))
                    StatusItem(label = "Synthesis Time:", value = "${res.synthesisTimeMs} ms")
                    StatusItem(label = "Audio Duration:", value = "%.2f s".format(res.audioDuration))
                    StatusItem(label = "RTF:", value = "%.3f".format(res.rtf))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { ttsManager.speak(ttsText) },
                modifier = Modifier.weight(1f),
                enabled = ttsStatus != TtsStatus.LOADING && ttsStatus != TtsStatus.SYNTHESIZING
            ) {
                Text("🔊 SPEAK")
            }
            Button(
                onClick = { ttsManager.stop() },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                enabled = ttsStatus == TtsStatus.PLAYING
            ) {
                Text("■ STOP")
            }
        }
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
