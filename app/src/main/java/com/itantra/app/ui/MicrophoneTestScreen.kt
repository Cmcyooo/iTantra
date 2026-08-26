package com.itantra.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.itantra.app.audio.*
import com.itantra.app.comm.*
import kotlinx.coroutines.delay

@Composable
fun MicrophoneTestScreen(
    audioManager: AudioCaptureManager,
    ttsManager: TtsManager,
    commManager: CommunicationManager,
    discoveryManager: DiscoveryManager,
    transceiverManager: TransceiverManager
) {
    val context = LocalContext.current
    val audioState by audioManager.state.collectAsState()
    val commState by commManager.connectionState.collectAsState()
    val commMessages by commManager.messages.collectAsState()
    val transceiverState by transceiverManager.uiState.collectAsState()
    val discoveredDevices by discoveryManager.discoveredDevices.collectAsState()
    val isSearching by discoveryManager.isSearching.collectAsState()
    
    var showSettings by remember { mutableStateOf(false) }
    
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- HEADER ---
        HeaderSection(commState)

        Spacer(modifier = Modifier.height(24.dp))

        // --- PTT CONTROL ---
        PTTSection(
            state = transceiverState,
            hasPermission = hasPermission,
            onStartTalk = { transceiverManager.startTalk() },
            onStopTalk = { transceiverManager.stopTalk() },
            onRequestPermission = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
        )

        Spacer(modifier = Modifier.height(32.dp))

        // --- TRANSCRIPT SECTION ---
        TranscriptSection(audioState, commMessages.lastOrNull())

        Spacer(modifier = Modifier.height(24.dp))

        // --- PERFORMANCE CARD ---
        PerformanceSection(audioState, ttsManager, commManager)

        Spacer(modifier = Modifier.height(24.dp))

        // --- SETTINGS TOGGLE ---
        OutlinedButton(
            onClick = { showSettings = !showSettings },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Settings, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (showSettings) "HIDE SETTINGS" else "CONNECTION SETTINGS")
        }

        AnimatedVisibility(visible = showSettings) {
            DiscoverySection(
                commState = commState,
                discoveredDevices = discoveredDevices,
                isSearching = isSearching,
                onConnect = { ip -> 
                    commManager.disconnect() // Ensure fresh state
                    commManager.connect(ip) 
                },
                onDisconnect = { commManager.disconnect() },
                onRescan = {
                    discoveryManager.stopDiscovery()
                    discoveryManager.startDiscovery()
                }
            )
        }
    }
}

@Composable
fun HeaderSection(commState: ConnectionState) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "iTantra",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Offline Voice Transceiver",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Row(
            modifier = Modifier.padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Badge(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                Text("Local Wi-Fi", modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
            }
            Badge(containerColor = MaterialTheme.colorScheme.tertiaryContainer) {
                Text("No Internet Required", modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Surface(
            shape = CircleShape,
            color = when (commState) {
                ConnectionState.CONNECTED -> Color(0xFF4CAF50).copy(alpha = 0.1f)
                ConnectionState.CONNECTING -> Color(0xFFFFC107).copy(alpha = 0.1f)
                else -> Color(0xFFF44336).copy(alpha = 0.1f)
            },
            modifier = Modifier.padding(top = 4.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            when (commState) {
                                ConnectionState.CONNECTED -> Color(0xFF4CAF50)
                                ConnectionState.CONNECTING -> Color(0xFFFFC107)
                                else -> Color(0xFFF44336)
                            }
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = commState.name,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun PTTSection(
    state: TransceiverState,
    hasPermission: Boolean,
    onStartTalk: () -> Unit,
    onStopTalk: () -> Unit,
    onRequestPermission: () -> Unit
) {
    val isPressed = state == TransceiverState.LISTENING || state == TransceiverState.SPEAKING
    
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = when (state) {
                TransceiverState.IDLE -> "READY"
                TransceiverState.LISTENING -> "LISTENING..."
                TransceiverState.SPEAKING -> "SPEAKING..."
                TransceiverState.TRANSCRIBING -> "TRANSCRIBING..."
                TransceiverState.FORWARDING -> "FORWARDING..."
                TransceiverState.SENT -> "SENT ✅"
                TransceiverState.RECEIVING -> "RECEIVING..."
                TransceiverState.PLAYING -> "🔊 PLAYING"
                TransceiverState.ERROR -> "ERROR"
            },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = when(state) {
                TransceiverState.LISTENING, TransceiverState.SPEAKING -> MaterialTheme.colorScheme.error
                TransceiverState.TRANSCRIBING, TransceiverState.FORWARDING -> MaterialTheme.colorScheme.tertiary
                TransceiverState.SENT -> Color(0xFF4CAF50)
                else -> MaterialTheme.colorScheme.primary
            }
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Box(
            modifier = Modifier
                .size(160.dp)
                .clip(CircleShape)
                .background(
                    if (isPressed) MaterialTheme.colorScheme.errorContainer 
                    else MaterialTheme.colorScheme.primaryContainer
                )
                .pointerInput(hasPermission) {
                    if (!hasPermission) return@pointerInput
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Press) {
                                onStartTalk()
                            } else if (event.type == PointerEventType.Release) {
                                onStopTalk()
                            }
                        }
                    }
                }
                .then(if (!hasPermission) Modifier.clickable { onRequestPermission() } else Modifier),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "Talk",
                modifier = Modifier.size(64.dp),
                tint = if (isPressed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = if (!hasPermission) "Tap to grant permission" else "HOLD TO TALK",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun TranscriptSection(audioState: AudioState, lastMessage: P2PMessage?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("You said:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(
                text = audioState.recognizedText.ifEmpty { "..." },
                style = MaterialTheme.typography.bodyLarge,
                minLines = 2
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            
            Text("Incoming:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
            Text(
                text = lastMessage?.text ?: "...",
                style = MaterialTheme.typography.bodyLarge,
                minLines = 2
            )
        }
    }
}

@Composable
fun PerformanceSection(audioState: AudioState, ttsManager: TtsManager, commManager: CommunicationManager) {
    val ttsResult by ttsManager.lastResult.collectAsState()
    val commLatency by commManager.latency.collectAsState()
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            PerformanceItem("STT", "${audioState.lastSttResult?.processingTimeMs ?: 0}ms")
            PerformanceItem("Net", "${commLatency ?: 0}ms")
            PerformanceItem("TTS", "${ttsResult?.synthesisTimeMs ?: 0}ms")
        }
    }
}

@Composable
fun PerformanceItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun DiscoverySection(
    commState: ConnectionState,
    discoveredDevices: List<DiscoveryManager.DiscoveredDevice>,
    isSearching: Boolean,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
    onRescan: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Nearby iTantra Devices", style = MaterialTheme.typography.titleSmall)
            if (isSearching) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Text(
                    "Rescan",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onRescan() }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        if (discoveredDevices.isEmpty()) {
            Text(
                "Searching for nearby devices...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            discoveredDevices.forEach { device ->
                DeviceItem(
                    device = device,
                    isConnected = commState == ConnectionState.CONNECTED, // Simplified check
                    onConnect = { onConnect(device.ip) }
                )
            }
        }
        
        if (commState != ConnectionState.DISCONNECTED) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onDisconnect,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("DISCONNECT")
            }
        }
    }
}

@Composable
fun DeviceItem(
    device: DiscoveryManager.DiscoveredDevice,
    isConnected: Boolean,
    onConnect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "📱 ${device.name}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Available • ${device.ip}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Button(
                onClick = onConnect,
                enabled = !isConnected
            ) {
                Text("Connect")
            }
        }
    }
}
