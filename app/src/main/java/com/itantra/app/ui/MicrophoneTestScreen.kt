package com.itantra.app.ui

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.itantra.app.audio.*
import com.itantra.app.comm.*
import kotlinx.coroutines.launch

typealias TransportMode = com.itantra.app.comm.TransportMode



@OptIn(ExperimentalMaterial3Api::class)
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
    peerRegistry: PeerRegistry,
    emergencyManager: ZeroConfigEmergencyManager,
    onTransportModeChange: (TransportMode) -> Unit
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    
    val audioState by audioManager.state.collectAsState()
    val commState by commManager.connectionState.collectAsState()
    val commMessages by commManager.messages.collectAsState()
    val transceiverState by transceiverManager.uiState.collectAsState()
    
    val isEmergencyMode by transceiverManager.isEmergencyMode.collectAsState()
    val alertDeliveryStatus by transceiverManager.alertDeliveryStatus.collectAsState()
    val activeIncomingAlert by transceiverManager.activeIncomingAlert.collectAsState()

    val registeredPeers by peerRegistry.peers.collectAsState()
    val emergencyFlowState by emergencyManager.flowState.collectAsState()
    val emergencyStatusText by emergencyManager.statusText.collectAsState()
    val emergencyActivePeer by emergencyManager.activePeerName.collectAsState()
    val emergencyActiveTransport by emergencyManager.activeTransport.collectAsState()

    val wifiDevices by discoveryManager.discoveredDevices.collectAsState()
    val isWifiSearching by discoveryManager.isSearching.collectAsState()

    val wifiDirectPeers by wifiDirectManager.peers.collectAsState()
    val isWifiDirectSearching by wifiDirectManager.isDiscoveryActive.collectAsState()
    val isWifiDirectAvailable by wifiDirectManager.isAvailable.collectAsState()
    val p2pStatusMessage by wifiDirectManager.statusMessage.collectAsState()
    
    val bluetoothPeers by bluetoothDiscoveryManager.discoveredPeers.collectAsState()
    val isBluetoothSearching by bluetoothDiscoveryManager.isSearching.collectAsState()
    val isBluetoothEnabled by bluetoothDiscoveryManager.isBluetoothEnabled.collectAsState()
    val bluetoothStatusMessage by bluetoothDiscoveryManager.statusMessage.collectAsState()
    
    var transportMode by remember { mutableStateOf(TransportMode.WIFI) }
    var showSettings by remember { mutableStateOf(false) }
    
    var localCallSign by remember { mutableStateOf(callSignManager.getCallSign()) }
    var connectingDeviceName by remember { mutableStateOf<String?>(null) }
    
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
        if (hasWifiDirectPermission) {
            transportMode = TransportMode.WIFI_DIRECT
            onTransportModeChange(TransportMode.WIFI_DIRECT)
        }
    }

    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasBluetoothPermission = permissions.values.all { it }
        if (hasBluetoothPermission) {
            transportMode = TransportMode.BLUETOOTH
            onTransportModeChange(TransportMode.BLUETOOTH)
        }
    }

    // --- CONNECTION FEEDBACK ---
    LaunchedEffect(commState) {
        if (commState == ConnectionState.CONNECTED) {
            val deviceName = connectingDeviceName ?: "Nearby iTantra Device"
            val transportStr = when(transportMode) {
                TransportMode.WIFI -> "Wi-Fi"
                TransportMode.WIFI_DIRECT -> "Wi-Fi Direct"
                TransportMode.BLUETOOTH -> "Bluetooth"
            }
            snackbarHostState.showSnackbar(
                message = "Connected to $deviceName • $transportStr",
                duration = SnackbarDuration.Short
            )
        } else if (commState == ConnectionState.ERROR) {
            snackbarHostState.showSnackbar(
                message = "Connection failed",
                duration = SnackbarDuration.Short
            )
        } else if (commState == ConnectionState.DISCONNECTED && connectingDeviceName != null) {
            val prevDevice = connectingDeviceName
            snackbarHostState.showSnackbar(
                message = "Disconnected from $prevDevice",
                duration = SnackbarDuration.Short
            )
            connectingDeviceName = null
        }
    }

    val scrollState = rememberScrollState()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent,
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Text("iTantra", fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp) 
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                ),
                actions = {
                    IconButton(onClick = { showSettings = !showSettings }) {
                        Icon(if (showSettings) Icons.Default.Close else Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (isEmergencyMode) Color(0xFF1E0E0E) else MaterialTheme.colorScheme.background
                )
                .verticalScroll(scrollState)
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Connection Status (Simplified)
            UserFriendlyConnectionStatus(
                commState = commState,
                transportMode = transportMode,
                connectingDeviceName = connectingDeviceName,
                isSearching = isWifiSearching || isWifiDirectSearching || isBluetoothSearching,
                registeredPeers = registeredPeers
            )

            // 1.1 Incoming Alert Card (High visibility)
            if (activeIncomingAlert != null) {
                Spacer(modifier = Modifier.height(16.dp))
                IncomingAlertCard(activeIncomingAlert!!)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 2. Mode Selector (If not active in emergency flow)
            if (emergencyFlowState == EmergencyFlowState.IDLE || emergencyFlowState == EmergencyFlowState.READY || !isEmergencyMode) {
                EmergencyModeSelector(
                    isEmergency = isEmergencyMode,
                    onModeChange = { 
                        transceiverManager.setEmergencyMode(it)
                        if (it) emergencyManager.triggerEmergency()
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isEmergencyMode) {
                // --- ZERO-CONFIG EMERGENCY SECTION ---
                ZeroConfigEmergencySection(
                    emergencyFlowState = emergencyFlowState,
                    statusText = emergencyStatusText,
                    activePeer = emergencyActivePeer,
                    activeTransport = emergencyActiveTransport,
                    hasPermission = hasAudioPermission,
                    onStartSpeech = { emergencyManager.startSpeechCapture() },
                    onStopSpeech = { emergencyManager.stopSpeechCapture() },
                    onRetry = { emergencyManager.triggerEmergency() },
                    onCancel = { 
                        emergencyManager.reset()
                        transceiverManager.setEmergencyMode(false)
                    },
                    onRequestPermission = { audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                )
            } else {
                // 3. Language Selector
                LanguageSelectorSection(audioManager.languageModelManager, ttsManager, audioManager)

                Spacer(modifier = Modifier.height(32.dp))

                // 4. Waveform (Real-time)
                VoiceWaveform(
                    rms = audioState.rms,
                    isRecording = audioState.isRecording
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 5. PRIMARY ACTION: PTT
                PTTSection(
                    state = transceiverState,
                    isEmergencyMode = isEmergencyMode,
                    alertDeliveryStatus = alertDeliveryStatus,
                    hasPermission = hasAudioPermission,
                    onStartTalk = { transceiverManager.startTalk() },
                    onStopTalk = { transceiverManager.stopTalk() },
                    onRequestPermission = { audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 6. Live Transcription Panel
            TranscriptSection(
                audioState = audioState,
                lastMessage = commMessages.lastOrNull(),
                isEmergencyMode = isEmergencyMode,
                alertDeliveryStatus = alertDeliveryStatus
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Technical Performance (Small & Subtle)
            PerformanceSection(audioState, ttsManager, commManager)

            Spacer(modifier = Modifier.height(32.dp))

            // 7. Secondary Controls (Collapsed by default)
            AnimatedVisibility(visible = showSettings) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .padding(16.dp)
                ) {
                    Text("Connection & Identity", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    CallSignSection(
                        callSign = localCallSign,
                        onCallSignChange = { localCallSign = it },
                        onSave = { callSignManager.saveCallSign(localCallSign) }
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    TransportSelector(
                        currentMode = transportMode,
                        onModeChange = { mode ->
                            connectingDeviceName = null
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
                            title = "Nearby Wi-Fi Devices",
                            commState = commState,
                            connectingDeviceName = connectingDeviceName,
                            discoveredDevices = wifiDevices.map { 
                                val savedCallSign = callSignManager.getPeerCallSign(it.ip)
                                (savedCallSign ?: it.name) to it.ip 
                            },
                            isSearching = isWifiSearching,
                            onConnect = { name, ip -> 
                                connectingDeviceName = name
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
                            title = "Nearby P2P Devices",
                            statusMessage = if (!hasWifiDirectPermission) "Permission required"
                                            else if (!isWifiDirectAvailable) "Unavailable"
                                            else p2pStatusMessage,
                            commState = commState,
                            connectingDeviceName = connectingDeviceName,
                            discoveredDevices = wifiDirectPeers.map { 
                                val savedCallSign = callSignManager.getPeerCallSign(it.deviceAddress)
                                val baseName = if (it.deviceName.isNullOrEmpty() || it.deviceName == "null") "Direct Device" else it.deviceName
                                (savedCallSign ?: baseName) to it.deviceAddress 
                            },
                            isSearching = isWifiDirectSearching,
                            emptyMessage = if (!hasWifiDirectPermission) "Permission required"
                                           else if (!isWifiDirectAvailable) "Unavailable"
                                           else if (isWifiDirectSearching) "Searching..."
                                           else "No devices found.",
                            onConnect = { name, address -> 
                                connectingDeviceName = name
                                commManager.disconnect()
                                commManager.connect(address) 
                            },
                            onDisconnect = { 
                                commManager.disconnect()
                                wifiDirectManager.disconnect()
                            },
                            onRescan = {
                                wifiDirectManager.stopDiscovery()
                                wifiDirectManager.startDiscovery()
                            }
                        )
                    } else {
                        if (!isBluetoothEnabled) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Bluetooth is off", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                                    }
                                    Button(
                                        onClick = {
                                            try {
                                                val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE).apply {
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                                context.startActivity(intent)
                                            } catch (e: Exception) {}
                                        }
                                    ) {
                                        Text("ENABLE")
                                    }
                                }
                            }
                        }

                        DiscoverySection(
                            title = "Nearby Bluetooth Devices",
                            statusMessage = if (!hasBluetoothPermission) "Permissions required"
                                            else if (!isBluetoothEnabled) "Bluetooth off"
                                            else bluetoothStatusMessage,
                            commState = commState,
                            connectingDeviceName = connectingDeviceName,
                            discoveredDevices = bluetoothPeers.map { peer ->
                                peer.displayName to peer.address 
                            },
                            isSearching = isBluetoothSearching,
                            emptyMessage = if (!isBluetoothEnabled) "Bluetooth off"
                                           else if (isBluetoothSearching) "Searching..." 
                                           else "No devices found",
                            onConnect = { name, address -> 
                                connectingDeviceName = name
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
}

@Composable
fun UserFriendlyConnectionStatus(
    commState: ConnectionState,
    transportMode: TransportMode,
    connectingDeviceName: String?,
    isSearching: Boolean,
    registeredPeers: List<PeerEntry> = emptyList()
) {
    val transportName = when(transportMode) {
        TransportMode.WIFI -> "Wi-Fi"
        TransportMode.WIFI_DIRECT -> "P2P"
        TransportMode.BLUETOOTH -> "Bluetooth"
    }

    val reachablePeer = registeredPeers.firstOrNull()

    val (color, text, subText) = when (commState) {
        ConnectionState.CONNECTED -> Triple(
            Color(0xFF4CAF50), 
            "Connected", 
            "${connectingDeviceName ?: "Device"} • $transportName"
        )
        ConnectionState.CONNECTING -> Triple(
            Color(0xFFFFC107), 
            "Connecting...", 
            "${connectingDeviceName ?: "Remote Station"} • $transportName"
        )
        ConnectionState.ERROR -> Triple(
            Color(0xFFF44336), 
            "Connection Failed", 
            "Tap Rescan below or try again"
        )
        ConnectionState.DISCONNECTED -> {
            if (isSearching) Triple(Color(0xFFFFC107), "Searching...", "Finding nearby iTantra stations")
            else if (reachablePeer != null) Triple(Color(0xFF4CAF50), "Ready", "Found ${reachablePeer.displayName} • Tap to connect")
            else Triple(Color(0xFF9E9E9E), "Disconnected", "Select a device in Settings to begin")
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = color.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = color)
                Text(subText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun VoiceWaveform(
    rms: Double,
    isRecording: Boolean,
    modifier: Modifier = Modifier
) {
    val normalizedRms = (rms / 32768.0).coerceIn(0.0, 1.0).toFloat()
    val animatedRms by animateFloatAsState(
        targetValue = if (isRecording) normalizedRms else 0.05f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "waveform"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(15) { index ->
            val distanceToCenter = kotlin.math.abs(index - 7)
            val scale = (1f - (distanceToCenter / 10f)).coerceAtLeast(0.3f)
            val barHeight = 4.dp + (32.dp * animatedRms * scale)
            
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(barHeight)
                    .padding(horizontal = 1.dp)
                    .clip(CircleShape)
                    .background(
                        if (isRecording) MaterialTheme.colorScheme.primary 
                        else MaterialTheme.colorScheme.outlineVariant
                    )
            )
        }
    }
}

@Composable
fun ZeroConfigEmergencySection(
    emergencyFlowState: EmergencyFlowState,
    statusText: String,
    activePeer: String?,
    activeTransport: TransportMode?,
    hasPermission: Boolean,
    onStartSpeech: () -> Unit,
    onStopSpeech: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onRequestPermission: () -> Unit
) {
    val isPressed = emergencyFlowState == EmergencyFlowState.LISTENING

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A0F0F)),
        border = BorderStroke(2.dp, Color(0xFFD32F2F))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🚨 EMERGENCY MODE",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFF5252)
                )
                TextButton(onClick = onCancel) {
                    Text("EXIT", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // State Badge
            Surface(
                shape = CircleShape,
                color = when (emergencyFlowState) {
                    EmergencyFlowState.DELIVERED -> Color(0xFF4CAF50).copy(alpha = 0.2f)
                    EmergencyFlowState.READY -> Color(0xFF4CAF50).copy(alpha = 0.2f)
                    EmergencyFlowState.FAILED -> Color(0xFFF44336).copy(alpha = 0.2f)
                    else -> Color(0xFFFFC107).copy(alpha = 0.2f)
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                when (emergencyFlowState) {
                                    EmergencyFlowState.DELIVERED -> Color(0xFF4CAF50)
                                    EmergencyFlowState.READY -> Color(0xFF4CAF50)
                                    EmergencyFlowState.FAILED -> Color(0xFFF44336)
                                    else -> Color(0xFFFFC107)
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when (emergencyFlowState) {
                            EmergencyFlowState.IDLE -> "IDLE"
                            EmergencyFlowState.SEARCHING -> "FINDING NEAREST DEVICE..."
                            EmergencyFlowState.SELECTING_PEER -> "SELECTING PEER..."
                            EmergencyFlowState.CONNECTING -> "CONNECTING..."
                            EmergencyFlowState.READY -> "READY"
                            EmergencyFlowState.LISTENING -> "RECORDING SPEECH..."
                            EmergencyFlowState.PROCESSING -> "PROCESSING SPEECH..."
                            EmergencyFlowState.SENDING -> "TRANSMITTING ALERT..."
                            EmergencyFlowState.WAITING_FOR_ACK -> "WAITING FOR ACK..."
                            EmergencyFlowState.DELIVERED -> "ALERT DELIVERED ✓"
                            EmergencyFlowState.FAILED -> "ALERT NOT DELIVERED"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Peer & Transport Metadata
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Station", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Text(
                        text = activePeer ?: "Searching...",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Transport", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Text(
                        text = when (activeTransport) {
                            TransportMode.WIFI -> "Local Wi-Fi"
                            TransportMode.WIFI_DIRECT -> "Wi-Fi Direct"
                            TransportMode.BLUETOOTH -> "Bluetooth"
                            null -> "Auto-detect"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action section according to state
            when (emergencyFlowState) {
                EmergencyFlowState.SEARCHING, EmergencyFlowState.SELECTING_PEER, EmergencyFlowState.CONNECTING -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = Color(0xFFFF5252),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(statusText, color = Color.LightGray, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                EmergencyFlowState.READY, EmergencyFlowState.LISTENING -> {
                    // Big Push-to-Talk Button
                    Box(
                        modifier = Modifier
                            .size(150.dp)
                            .clip(CircleShape)
                            .background(if (isPressed) Color(0xFF880E4F) else Color(0xFFD32F2F))
                            .pointerInput(hasPermission) {
                                if (!hasPermission) return@pointerInput
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        if (event.type == PointerEventType.Press) {
                                            onStartSpeech()
                                        } else if (event.type == PointerEventType.Release) {
                                            onStopSpeech()
                                        }
                                    }
                                }
                            }
                            .then(if (!hasPermission) Modifier.clickable { onRequestPermission() } else Modifier),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Emergency Mic",
                                tint = Color.White,
                                modifier = Modifier.size(56.dp)
                            )
                            Text(
                                text = if (isPressed) "RECORDING" else "HOLD TO SPEAK",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isPressed) "Keep holding while speaking emergency message..." else "Release to transmit high-priority alert",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.LightGray
                    )
                }
                EmergencyFlowState.PROCESSING, EmergencyFlowState.SENDING, EmergencyFlowState.WAITING_FOR_ACK -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = Color(0xFFFFC107),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(statusText, color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Waiting for receiver acknowledgement...", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                    }
                }
                EmergencyFlowState.DELIVERED -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "✅",
                            fontSize = 48.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "ALERT DELIVERED ✓",
                            color = Color(0xFF4CAF50),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "${activePeer ?: "Receiver"} acknowledged receipt",
                            color = Color.LightGray,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onRetry,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                        ) {
                            Text("SEND ANOTHER ALERT")
                        }
                    }
                }
                EmergencyFlowState.FAILED -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "⚠️",
                            fontSize = 48.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "ALERT NOT DELIVERED",
                            color = Color(0xFFF44336),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = statusText,
                            color = Color.LightGray,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = onRetry,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                            ) {
                                Text("RETRY")
                            }
                            OutlinedButton(onClick = onCancel) {
                                Text("RETURN TO NORMAL", color = Color.White)
                            }
                        }
                    }
                }
                else -> {
                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                        modifier = Modifier.fillMaxWidth(0.8f)
                    ) {
                        Text("🚨 START EMERGENCY CONNECTION", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun EmergencyModeSelector(
    isEmergency: Boolean,
    onModeChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Mode:",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(end = 8.dp)
        )
        FilterChip(
            selected = !isEmergency,
            onClick = { onModeChange(false) },
            label = { Text("NORMAL") }
        )
        Spacer(modifier = Modifier.width(8.dp))
        FilterChip(
            selected = isEmergency,
            onClick = { onModeChange(true) },
            label = { Text("🚨 EMERGENCY") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.error,
                selectedLabelColor = MaterialTheme.colorScheme.onError
            )
        )
    }
}

@Composable
fun IncomingAlertCard(alert: P2PMessage) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.error)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "🚨 EMERGENCY ALERT",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "From: ${alert.senderName ?: "Unknown Station"}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "\"${alert.text}\"",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Playing alert...",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun PTTSection(
    state: TransceiverState,
    isEmergencyMode: Boolean = false,
    alertDeliveryStatus: AlertDeliveryStatus = AlertDeliveryStatus.NONE,
    hasPermission: Boolean,
    onStartTalk: () -> Unit,
    onStopTalk: () -> Unit,
    onRequestPermission: () -> Unit
) {
    val isPressed = state == TransceiverState.LISTENING || state == TransceiverState.SPEAKING
    
    val buttonColor by animateColorAsState(
        targetValue = when {
            isEmergencyMode && isPressed -> Color(0xFFB71C1C)
            isEmergencyMode -> Color(0xFFD32F2F)
            isPressed -> MaterialTheme.colorScheme.error
            state == TransceiverState.TRANSCRIBING || state == TransceiverState.FORWARDING -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.primary
        },
        label = "pttColor"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(180.dp)
                .clip(CircleShape)
                .background(buttonColor.copy(alpha = 0.15f))
                .padding(12.dp)
                .clip(CircleShape)
                .background(buttonColor)
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
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = when(state) {
                        TransceiverState.TRANSCRIBING, TransceiverState.FORWARDING -> Icons.Default.Autorenew
                        TransceiverState.SENT -> Icons.Default.Check
                        TransceiverState.RECEIVING, TransceiverState.PLAYING -> Icons.AutoMirrored.Filled.VolumeUp
                        else -> Icons.Default.Mic
                    },
                    contentDescription = "Talk",
                    modifier = Modifier.size(56.dp),
                    tint = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = when (state) {
                        TransceiverState.IDLE -> "TALK"
                        TransceiverState.LISTENING, TransceiverState.SPEAKING -> "RELEASE"
                        TransceiverState.TRANSCRIBING, TransceiverState.FORWARDING -> "WAIT"
                        TransceiverState.SENT -> "SENT"
                        TransceiverState.RECEIVING -> "RECV"
                        TransceiverState.PLAYING -> "VOICE"
                        TransceiverState.ERROR -> "RETRY"
                    },
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = when {
                !hasPermission -> "Tap to grant microphone access"
                isPressed -> "RELEASE TO TRANSMIT"
                state == TransceiverState.TRANSCRIBING || state == TransceiverState.FORWARDING -> "PROCESSING AUDIO..."
                state == TransceiverState.SENT -> "MESSAGE DELIVERED ✓"
                state == TransceiverState.ERROR -> "TRANSMISSION FAILED"
                else -> "HOLD TO SPEAK"
            },
            style = MaterialTheme.typography.labelLarge,
            color = buttonColor,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
    }
}

@Composable
fun TranscriptSection(
    audioState: AudioState,
    lastMessage: P2PMessage?,
    isEmergencyMode: Boolean = false,
    alertDeliveryStatus: AlertDeliveryStatus = AlertDeliveryStatus.NONE
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "LIVE TRANSCRIPTION",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(8.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
            )
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // MY SPEECH
                Row(verticalAlignment = Alignment.Top) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("ME", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = when {
                            audioState.sttStatus == SttStatus.TRANSCRIBING -> "Transcribing..."
                            audioState.recognizedText.isNotEmpty() -> "\"${audioState.recognizedText}\""
                            audioState.isRecording -> "Listening..."
                            else -> "Your speech will appear here"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (audioState.recognizedText.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (audioState.recognizedText.isNotEmpty()) FontWeight.Medium else FontWeight.Normal,
                        fontStyle = if (audioState.recognizedText.isEmpty()) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal
                    )
                }

                if (lastMessage != null) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    
                    // RECEIVED
                    Row(verticalAlignment = Alignment.Top) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f),
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("IN", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = lastMessage.senderName ?: "Remote Station",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = "\"${lastMessage.text}\"",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
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
            val sttText = audioState.lastSttResult?.processingTimeMs?.takeIf { it > 0 }?.let { "${it}ms" } ?: "N/A"
            val netText = commLatency?.takeIf { it > 0 }?.let { "${it}ms" } ?: "N/A"
            val ttsText = ttsResult?.synthesisTimeMs?.takeIf { it > 0 }?.let { "${it}ms" } ?: "N/A"
            PerformanceItem("STT", sttText)
            PerformanceItem("Net RTT", netText)
            PerformanceItem("TTS", ttsText)
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
    statusMessage: String? = null,
    commState: ConnectionState,
    connectingDeviceName: String? = null,
    discoveredDevices: List<Pair<String, String>>,
    isSearching: Boolean,
    emptyMessage: String? = null,
    onConnect: (String, String) -> Unit,
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
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
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
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onRescan() }
                )
            }
        }

        if (!statusMessage.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = statusMessage,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        if (discoveredDevices.isEmpty()) {
            Text(
                text = emptyMessage ?: if (isSearching) "Searching for nearby devices..." else "No nearby iTantra devices found.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            discoveredDevices.forEach { (name, address) ->
                val isConnectingToThis = (connectingDeviceName == name)
                DeviceItem(
                    name = name,
                    address = address,
                    commState = commState,
                    isConnectingToThis = isConnectingToThis,
                    onConnect = { onConnect(name, address) }
                )
            }
        }
        
        if (commState == ConnectionState.CONNECTED) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onDisconnect,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("DISCONNECT", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun DeviceItem(
    name: String,
    address: String,
    commState: ConnectionState,
    isConnectingToThis: Boolean,
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
            Column(modifier = Modifier.weight(1f)) {
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
            
            val canConnect = (commState == ConnectionState.DISCONNECTED || commState == ConnectionState.ERROR)
            Button(
                onClick = onConnect,
                enabled = canConnect,
                colors = when {
                    isConnectingToThis && commState == ConnectionState.CONNECTED -> 
                        ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                    isConnectingToThis && commState == ConnectionState.ERROR ->
                        ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    else -> ButtonDefaults.buttonColors()
                }
            ) {
                Text(
                    text = when {
                        isConnectingToThis && commState == ConnectionState.CONNECTING -> "CONNECTING..."
                        isConnectingToThis && commState == ConnectionState.CONNECTED -> "CONNECTED ✓"
                        isConnectingToThis && commState == ConnectionState.ERROR -> "CONNECTION FAILED"
                        else -> "CONNECT"
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSelectorSection(
    languageModelManager: LanguageModelManager,
    ttsManager: com.itantra.app.audio.TtsManager,
    audioManager: com.itantra.app.audio.AudioCaptureManager? = null
) {
    val context = LocalContext.current
    val currentLang by languageModelManager.currentLanguage.collectAsState()
    val lifecycleState by languageModelManager.lifecycleState.collectAsState()
    val ttsLifecycleState by ttsManager.languageTtsManager.lifecycleState.collectAsState()
    val languageMode by languageModelManager.languageMode.collectAsState()
    val sessionLang by languageModelManager.sessionLanguage.collectAsState()
    val sessionState by languageModelManager.sessionLanguageState.collectAsState()
    val pendingConfirmation by languageModelManager.pendingConfirmation.collectAsState()
    val lastDetectionResult by languageModelManager.lastDetectionResult.collectAsState()
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    var showManualPickerForPending by remember { mutableStateOf(false) }
    var showPackDialog by remember { mutableStateOf(false) }

    val packManager = remember { LanguagePackManager.getInstance(context) }
    val packStatuses by packManager.packStatuses.collectAsState()
    val installedPacksCount = packStatuses.values.count { it.isInstalled }

    Column {
        if (installedPacksCount <= 1) {
            // First-launch onboarding banner
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("📦 Choose Language Packs", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        Text("Install offline language models for Hindi, Gujarati, Telugu, Kannada & more.", style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { showPackDialog = true }) {
                        Text("SETUP")
                    }
                }
            }
        }

        if (showPackDialog) {
            LanguagePackManagementDialog(
                packManager = packManager,
                activeLanguage = currentLang,
                onDismiss = { showPackDialog = false }
            )
        }

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
                        text = "Speech & TTS Language",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedButton(
                        onClick = { showPackDialog = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Packs", fontSize = 11.sp)
                    }
                }

                val isLoading = lifecycleState == ModelLifecycleState.LOADING || 
                                lifecycleState == ModelLifecycleState.RELEASING ||
                                ttsLifecycleState == ModelLifecycleState.LOADING || 
                                ttsLifecycleState == ModelLifecycleState.RELEASING

                val statusText = when {
                    isLoading -> "Loading ${currentLang.displayName}..."
                    lifecycleState == ModelLifecycleState.TRANSCRIBING -> "Transcribing..."
                    lifecycleState == ModelLifecycleState.READY && ttsLifecycleState == ModelLifecycleState.READY -> "Ready"
                    lifecycleState == ModelLifecycleState.FAILED || ttsLifecycleState == ModelLifecycleState.FAILED -> "Error"
                    else -> "Idle"
                }

                val statusColor = when {
                    statusText == "Ready" -> Color(0xFF4CAF50)
                    isLoading -> Color(0xFFFF9800)
                    lifecycleState == ModelLifecycleState.TRANSCRIBING -> Color(0xFF2196F3)
                    statusText == "Error" -> MaterialTheme.colorScheme.error
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

            // Language Mode: AUTO vs MANUAL
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Mode:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                FilterChip(
                    selected = languageMode == LanguageMode.AUTO,
                    onClick = { languageModelManager.setLanguageMode(LanguageMode.AUTO) },
                    label = { Text("AUTO") }
                )
                FilterChip(
                    selected = languageMode == LanguageMode.MANUAL,
                    onClick = { languageModelManager.setLanguageMode(LanguageMode.MANUAL) },
                    label = { Text("MANUAL") }
                )
                if (languageMode == LanguageMode.AUTO) {
                    Spacer(modifier = Modifier.weight(1f))
                    OutlinedButton(
                        onClick = { languageModelManager.triggerLanguageReverification() },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Detect",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Detect", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (languageMode == LanguageMode.AUTO) {
                if (pendingConfirmation != null) {
                    // CASE B — CONFIRM-REQUIRED: Pause STT, ask operator, zero-repeat original audio
                    val pending = pendingConfirmation!!
                    val confPct = (pending.confidence * 100).toInt()
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.error)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Confirmation Required",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${pending.language.displayName} (${pending.language.nativeName}) detected",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Confidence: $confPct% • This language needs confirmation.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        scope.launch {
                                            val res = languageModelManager.confirmPendingLanguage()
                                            if (res != null) {
                                                audioManager?.handleConfirmedSttResult(res.first.utteranceId, res.second)
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Use ${pending.language.displayName}", style = MaterialTheme.typography.labelMedium)
                                }
                                OutlinedButton(
                                    onClick = { showManualPickerForPending = true },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Choose Language", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                } else if (sessionState != null) {
                    // CASE A — AUTO-ACCEPT: Confident or confirmed session lock active
                    val state = sessionState!!
                    val confPct = (state.confidence * 100).toInt()
                    val isConfirmed = state.source == com.itantra.app.audio.SessionLanguageSource.USER_CONFIRMED
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "${state.language.displayName} (${state.language.nativeName})",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = if (isConfirmed) "Confidence: $confPct% • ✓ User Confirmed"
                                               else "Confidence: $confPct% • ✓ Using automatically",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                AssistChip(
                                    onClick = { languageModelManager.triggerLanguageReverification() },
                                    label = { Text("Re-verify") }
                                )
                            }
                        }
                    }
                } else if (lastDetectionResult?.routingDecision == com.itantra.app.audio.RoutingDecision.MANUAL_FALLBACK ||
                           lastDetectionResult?.status == DetectionStatus.AMBIGUOUS) {
                    // CASE C — AMBIGUOUS: Suggest candidates, offer manual selection
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "⚠ Language unclear",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            val topStr = lastDetectionResult?.probabilities?.entries
                                ?.sortedByDescending { it.value }
                                ?.take(3)
                                ?.joinToString(", ") { "${it.key}: ${(it.value * 100).toInt()}%" } ?: ""
                            if (topStr.isNotEmpty()) {
                                Text(
                                    text = "Candidates: $topStr",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            TextButton(
                                onClick = { languageModelManager.setLanguageMode(LanguageMode.MANUAL) },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("Choose Language")
                            }
                        }
                    }
                } else if (lastDetectionResult?.status == DetectionStatus.ERROR) {
                    // CASE D — LID FAILURE
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Automatic detection unavailable",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            TextButton(
                                onClick = { languageModelManager.setLanguageMode(LanguageMode.MANUAL) },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("Select Language")
                            }
                        }
                    }
                } else {
                    // Initial idle state before first utterance
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "AUTO Detection Active",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Hold PTT and speak. Reliable languages route automatically; weak languages require confirmation.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Modal dialog to pick language for pending audio
                if (showManualPickerForPending) {
                    AlertDialog(
                        onDismissRequest = { showManualPickerForPending = false },
                        title = { Text("Select Utterance Language") },
                        text = {
                            Column {
                                Text(
                                    "Choose the language to transcribe your recorded utterance:",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                SupportedLanguage.values().forEach { lang ->
                                    TextButton(
                                        onClick = {
                                            scope.launch {
                                                val res = languageModelManager.rejectPendingLanguageAndSelect(lang)
                                                if (res != null) {
                                                    audioManager?.handleConfirmedSttResult(res.first.utteranceId, res.second)
                                                }
                                                showManualPickerForPending = false
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "${lang.displayName} (${lang.nativeName})",
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showManualPickerForPending = false }) {
                                Text("Cancel")
                            }
                        }
                    )
                }
            } else {
                // MANUAL Mode: ExposedDropdownMenuBox
                val currentVoiceConfig = com.itantra.app.audio.TtsVoiceConfig.getConfigFor(currentLang)
                val readinessTag = if (currentVoiceConfig.isProductionReady) "Ready" else "Conditional"

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { 
                        val isLoading = lifecycleState == ModelLifecycleState.LOADING || 
                                        lifecycleState == ModelLifecycleState.RELEASING ||
                                        ttsLifecycleState == ModelLifecycleState.LOADING || 
                                        ttsLifecycleState == ModelLifecycleState.RELEASING
                        if (!isLoading) expanded = !expanded 
                    }
                ) {
                    OutlinedTextField(
                        value = "${currentLang.displayName} (${currentLang.nativeName}) — $readinessTag",
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
                            val voiceConfig = com.itantra.app.audio.TtsVoiceConfig.getConfigFor(lang)
                            val tag = if (voiceConfig.isProductionReady) "Ready" else "Conditional"
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${lang.displayName} (${lang.nativeName})",
                                            fontWeight = if (lang == currentLang) FontWeight.Bold else FontWeight.Normal
                                        )
                                        Text(
                                            text = tag,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (voiceConfig.isProductionReady) Color(0xFF4CAF50) else Color(0xFFFF9800),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                },
                                onClick = {
                                    expanded = false
                                    if (lang != currentLang) {
                                        scope.launch {
                                            languageModelManager.setLanguage(lang)
                                            ttsManager.languageTtsManager.setLanguage(lang)
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
}
