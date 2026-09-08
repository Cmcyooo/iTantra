package com.itantra.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
 * Dialog for managing modular iTantra offline Language Packs.
 *
 * Allows users to download or import language-specific STT/TTS offline models on demand,
 * maintaining a lightweight Base APK distribution (~110 MB) while enabling 100% offline
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
            .padding(vertical = 16.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Language Packs", fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Download or import language models for offline speech recognition & synthesis. Installed packs work 100% offline with zero internet required.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (snackbarMessage != null) {
                    Text(
                        text = snackbarMessage!!,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
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
            TextButton(onClick = onDismiss) {
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
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = if (isActive) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
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
                            "Installed ✓ • ${(status.installedSizeBytes / (1024 * 1024))} MB"
                        } else {
                            "Not Installed • ~$downloadMb MB download"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (status.isInstalled) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Action Buttons
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (status.isDownloading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else if (status.isInstalled) {
                        OutlinedButton(
                            onClick = onRemove,
                            enabled = !isActive,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Remove", fontSize = 11.sp)
                        }
                    } else {
                        IconButton(
                            onClick = onImportZip,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = "Import Zip",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Button(
                            onClick = onDownload,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(32.dp)
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
