package com.itantra.app.ui

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
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
import kotlinx.coroutines.launch

enum class TransportMode {
    WIFI,
    WIFI_DIRECT,
    BLUETOOTH
}

@Composable
fun MicrophoneTestScreen(
    audioManager: AudioCaptureManager,
    ttsManager: TtsManager,
    commManager: CommunicationManager,
    discoveryManager: DiscoveryManager,
    wifiDirectManager: WiFiDirectManager,
    bluetoothDiscoveryManager: BluetoothDiscoveryManager,
    callSignManager: CallSignManager,
    transceiverManager: TransceiverManager,
    onTransportModeChange: (TransportMode) -> Unit
) {
    val context = LocalContext.current
    val audioState by audioManager.state.collectAsState()
    val commState by commManager.connectionState.collectAsState()
    val commMessages by commManager.messages.collectAsState()
    val transceiverState by transceiverManager.uiState.collectAsState()
    
    val wifiDevices by discoveryManager.discoveredDevices.collectAsState()
    val isWifiSearching by discoveryManager.isSearching.collectAsState()

    val wifiDirectPeers by wifiDirectManager.peers.collectAsState()
    val isWifiDirectSearching by wifiDirectManager.isDiscoveryActive.collectAsState()
    
    val bluetoothDevices by bluetoothDiscoveryManager.discoveredDevices.collectAsState()
    val isBluetoothSearching by bluetoothDiscoveryManager.isSearching.collectAsState()
    
    var transportMode by remember { mutableStateOf(TransportMode.WIFI) }
    var showSettings by remember { mutableStateOf(false) }
    
    var localCallSign by remember { mutableStateOf(callSignManager.getCallSign()) }
    
    val bluetoothPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE
        )
    } else {
        listOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    val wifiDirectPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        listOf(Manifest.permission.NEARBY_WIFI_DEVICES)
    } else {
        listOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    var hasBluetoothPermission by remember {
        mutableStateOf(
            bluetoothPermissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    var hasWifiDirectPermission by remember {
        mutableStateOf(
            wifiDirectPermissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { hasAudioPermission = it }

    val wifiDirectPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasWifiDirectPermission = permissions.values.all { it }
    }

    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasBluetoothPermission = permissions.values.all { it }
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
        HeaderSection(commState, transportMode)

        Spacer(modifier = Modifier.height(16.dp))

        // --- LANGUAGE SELECTOR ---
        LanguageSelectorSection(audioManager.languageModelManager)

        Spacer(modifier = Modifier.height(16.dp))

        // --- PTT CONTROL ---
        PTTSection(
            state = transceiverState,
            hasPermission = hasAudioPermission,
            onStartTalk = { transceiverManager.startTalk() },
            onStopTalk = { transceiverManager.stopTalk() },
            onRequestPermission = { audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
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
            Column {
                CallSignSection(
                    callSign = localCallSign,
                    onCallSignChange = { localCallSign = it },
                    onSave = { callSignManager.saveCallSign(localCallSign) }
                )
                
                Spacer(modifier = Modifier.height(16.dp))

                TransportSelector(
                    currentMode = transportMode,
                    onModeChange = { mode ->
                        when (mode) {
                            TransportMode.BLUETOOTH -> {
                                if (!hasBluetoothPermission) {
                                    bluetoothPermissionLauncher.launch(bluetoothPermissions.toTypedArray())
                                } else {
                                    transportMode = mode
                                    onTransportModeChange(mode)
                                }
                            }
                            TransportMode.WIFI_DIRECT -> {
                                if (!hasWifiDirectPermission) {
                                    wifiDirectPermissionLauncher.launch(wifiDirectPermissions.toTypedArray())
                                } else {
                                    transportMode = mode
                                    onTransportModeChange(mode)
                                }
                            }
                            TransportMode.WIFI -> {
                                transportMode = mode
                                onTransportModeChange(mode)
                            }
                        }
                    }
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                if (transportMode == TransportMode.WIFI) {
                    DiscoverySection(
                        title = "Nearby Wi-Fi iTantra Devices",
                        commState = commState,
                        discoveredDevices = wifiDevices.map { it.name to it.ip },
                        isSearching = isWifiSearching,
                        onConnect = { ip -> 
                            commManager.disconnect()
                            commManager.connect(ip) 
                        },
                        onDisconnect = { commManager.disconnect() },
                        onRescan = {
                            discoveryManager.stopDiscovery()
                            discoveryManager.startDiscovery()
                        }
                    )
                } else if (transportMode == TransportMode.WIFI_DIRECT) {
                    DiscoverySection(
                        title = "Nearby Wi-Fi Direct iTantra Devices",
                        commState = commState,
                        discoveredDevices = wifiDirectPeers.map { it.deviceName to it.deviceAddress },
                        isSearching = isWifiDirectSearching,
                        onConnect = { address -> 
                            commManager.disconnect()
                            commManager.connect(address) 
                        },
                        onDisconnect = { commManager.disconnect() },
                        onRescan = {
                            wifiDirectManager.stopDiscovery()
                            wifiDirectManager.startDiscovery()
                        }
                    )
                } else {
                    DiscoverySection(
                        title = "Nearby Bluetooth iTantra Devices",
                        commState = commState,
                        discoveredDevices = bluetoothDevices.map { device ->
                            @SuppressLint("MissingPermission")
                            val systemName = device.name
                            val savedCallSign = callSignManager.getPeerCallSign(device.address)
                            
                            val displayName = when {
                                !savedCallSign.isNullOrEmpty() -> savedCallSign
                                !systemName.isNullOrEmpty() -> systemName
                                else -> "iTantra Device"
                            }
                            displayName to device.address 
                        },
                        isSearching = isBluetoothSearching,
                        onConnect = { address -> 
                            commManager.disconnect()
                            commManager.connect(address) 
                        },
                        onDisconnect = { commManager.disconnect() },
                        onRescan = {
                            bluetoothDiscoveryManager.stopDiscovery()
                            bluetoothDiscoveryManager.startDiscovery()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun HeaderSection(commState: ConnectionState, transportMode: TransportMode) {
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
                Text(
                    text = when(transportMode) {
                        TransportMode.WIFI -> "Local Wi-Fi"
                        TransportMode.WIFI_DIRECT -> "Wi-Fi Direct"
                        TransportMode.BLUETOOTH -> "Bluetooth"
                    },
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp), 
                    style = MaterialTheme.typography.labelSmall
                )
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
fun CallSignSection(
    callSign: String,
    onCallSignChange: (String) -> Unit,
    onSave: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("My iTantra Call Sign", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = callSign,
                    onValueChange = onCallSignChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = onSave) {
                    Text("SAVE")
                }
            }
        }
    }
}

@Composable
fun TransportSelector(currentMode: TransportMode, onModeChange: (TransportMode) -> Unit) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = currentMode == TransportMode.WIFI,
                onClick = { onModeChange(TransportMode.WIFI) },
                label = { Text("Wi-Fi") },
                leadingIcon = { Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = currentMode == TransportMode.WIFI_DIRECT,
                onClick = { onModeChange(TransportMode.WIFI_DIRECT) },
                label = { Text("P2P") },
                leadingIcon = { Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = currentMode == TransportMode.BLUETOOTH,
                onClick = { onModeChange(TransportMode.BLUETOOTH) },
                label = { Text("BT") },
                leadingIcon = { Icon(Icons.Default.Bluetooth, contentDescription = null, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun DiscoverySection(
    title: String,
    commState: ConnectionState,
    discoveredDevices: List<Pair<String, String>>,
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
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (isSearching) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Scanning...", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.width(8.dp))
                    CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                }
            } else {
                Text(
                    "Ready (Rescan)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onRescan() }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        if (discoveredDevices.isEmpty()) {
            Text(
                if (isSearching) "Searching for nearby devices..." else "No nearby iTantra devices found.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            discoveredDevices.forEach { (name, address) ->
                DeviceItem(
                    name = name,
                    address = address,
                    isConnected = commState == ConnectionState.CONNECTED,
                    onConnect = { onConnect(address) }
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
    name: String,
    address: String,
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
                    text = "📱 $name",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Available • $address",
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSelectorSection(languageModelManager: LanguageModelManager) {
    val currentLang by languageModelManager.currentLanguage.collectAsState()
    val lifecycleState by languageModelManager.lifecycleState.collectAsState()
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Speech Language",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val statusText = when (lifecycleState) {
                    ModelLifecycleState.UNLOADED -> "Idle"
                    ModelLifecycleState.LOADING -> "Loading ${currentLang.displayName}..."
                    ModelLifecycleState.RELEASING -> "Releasing..."
                    ModelLifecycleState.READY -> "Ready"
                    ModelLifecycleState.TRANSCRIBING -> "Transcribing..."
                    ModelLifecycleState.FAILED -> "Failed"
                }

                val statusColor = when (lifecycleState) {
                    ModelLifecycleState.READY -> Color(0xFF4CAF50)
                    ModelLifecycleState.LOADING, ModelLifecycleState.RELEASING -> Color(0xFFFF9800)
                    ModelLifecycleState.TRANSCRIBING -> Color(0xFF2196F3)
                    ModelLifecycleState.FAILED -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }

                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { if (lifecycleState != ModelLifecycleState.LOADING && lifecycleState != ModelLifecycleState.RELEASING) expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = "${currentLang.displayName} (${currentLang.nativeName})",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    enabled = lifecycleState != ModelLifecycleState.LOADING && lifecycleState != ModelLifecycleState.RELEASING
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    SupportedLanguage.values().forEach { lang ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "${lang.displayName} (${lang.nativeName})",
                                    fontWeight = if (lang == currentLang) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                expanded = false
                                if (lang != currentLang) {
                                    scope.launch {
                                        languageModelManager.setLanguage(lang)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
