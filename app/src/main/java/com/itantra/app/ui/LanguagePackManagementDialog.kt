package com.itantra.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itantra.app.audio.LanguagePackManager
import com.itantra.app.audio.LanguagePackStatus
import com.itantra.app.audio.SupportedLanguage
import kotlinx.coroutines.launch

/**
 * Polished, production-ready dialog for managing modular iTantra offline Language Packs.
 *
 * Allows users to download or import language-specific STT and TTS offline models on demand,
 * maintaining a lightweight Base APK distribution (~95 MB) while enabling 100% offline
 * multilingual functionality once a pack is installed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguagePackManagementDialog(
    packManager: LanguagePackManager,
    activeLanguage: SupportedLanguage,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val packStatuses by packManager.packStatuses.collectAsState()

    var selectedLangForImport by remember { mutableStateOf<SupportedLanguage?>(null) }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }

    val zipPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        val lang = selectedLangForImport
        if (uri != null && lang != null) {
            scope.launch {
                val res = packManager.importPackFromUri(lang, uri)
                if (res.isSuccess) {
                    snackbarMessage = "Successfully imported ${lang.displayName} language pack!"
                } else {
                    snackbarMessage = "Import failed: ${res.exceptionOrNull()?.message}"
                }
            }
        }
        selectedLangForImport = null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Language Packs", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                        Text("100% Offline Speech Models", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Download or import complete STT & TTS offline model packs for each required language. Once installed, models run locally on device with zero internet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (snackbarMessage != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        Text(
                            text = snackbarMessage!!,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(SupportedLanguage.entries.toList()) { lang ->
                        val status = packStatuses[lang] ?: LanguagePackStatus(language = lang)
                        val isActive = (lang == activeLanguage)

                        LanguagePackItemRow(
                            status = status,
                            isActive = isActive,
                            onDownload = {
                                scope.launch {
                                    val res = packManager.downloadAndInstallPack(lang)
                                    if (res.isFailure) {
                                        snackbarMessage = "Download failed: ${res.exceptionOrNull()?.message}"
                                    } else {
                                        snackbarMessage = "Installed ${lang.displayName}!"
                                    }
                                }
                            },
                            onImportZip = {
                                selectedLangForImport = lang
                                zipPickerLauncher.launch("application/zip")
                            },
                            onRemove = {
                                scope.launch {
                                    val res = packManager.removePack(lang, activeLanguage)
                                    if (res.isFailure) {
                                        snackbarMessage = res.exceptionOrNull()?.message
                                    } else {
                                        snackbarMessage = "Removed ${lang.displayName} pack"
                                    }
                                }
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("DONE", fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun LanguagePackItemRow(
    status: LanguagePackStatus,
    isActive: Boolean,
    onDownload: () -> Unit,
    onImportZip: () -> Unit,
    onRemove: () -> Unit
) {
    val lang = status.language
    val downloadMb = (status.estimatedDownloadSizeBytes / (1024 * 1024)).toInt()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(
            1.dp,
            if (isActive) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${lang.displayName} (${lang.nativeName})",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (isActive) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                Text("ACTIVE", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (status.isInstalled) {
                            "Installed ✓ (STT + TTS) • ${(status.installedSizeBytes / (1024 * 1024))} MB"
                        } else {
                            "Not Installed • ~$downloadMb MB pack"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (status.isInstalled) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Action Buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (status.isDownloading) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${status.downloadProgressPercent}%",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    } else if (status.isInstalled) {
                        OutlinedButton(
                            onClick = onRemove,
                            enabled = !isActive,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("Remove", fontSize = 11.sp)
                        }
                    } else {
                        IconButton(
                            onClick = onImportZip,
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = "Import Zip",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Button(
                            onClick = onDownload,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("Install", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (status.isDownloading) {
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { status.downloadProgressPercent / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (!status.errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = status.errorMessage,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
