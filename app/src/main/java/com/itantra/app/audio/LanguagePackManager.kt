package com.itantra.app.audio

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * Status of a language pack.
 */
data class LanguagePackStatus(
    val language: SupportedLanguage,
    val isInstalled: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgressPercent: Int = 0,
    val installedSizeBytes: Long = 0L,
    val estimatedDownloadSizeBytes: Long = 0L,
    val errorMessage: String? = null
)

/**
 * Central manager for iTantra Language Model Packs.
 *
 * Enables modular, low-footprint Base APK distribution by downloading or importing
 * language-specific STT and TTS offline models on demand.
 */
class LanguagePackManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "LanguagePackManager"
        private const val BASE_GITHUB_RELEASE_URL =
            "https://github.com/Cmcyooo/iTantra/releases/download/v1.0.0"

        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: LanguagePackManager? = null

        fun getInstance(context: Context): LanguagePackManager {
            return instance ?: synchronized(this) {
                instance ?: LanguagePackManager(context.applicationContext).also { instance = it }
            }
        }

        // Expected download/storage estimates for 10 languages (STT + TTS)
        val PACK_METADATA: Map<SupportedLanguage, Pair<Long, Long>> = mapOf(
            SupportedLanguage.ENGLISH to Pair(120_000_000L, 175_000_000L),
            SupportedLanguage.HINDI to Pair(138_000_000L, 186_000_000L),
            SupportedLanguage.GUJARATI to Pair(185_000_000L, 236_000_000L),
            SupportedLanguage.MARATHI to Pair(151_000_000L, 199_000_000L),
            SupportedLanguage.KANNADA to Pair(185_000_000L, 236_000_000L),
            SupportedLanguage.MALAYALAM to Pair(138_000_000L, 185_000_000L),
            SupportedLanguage.TAMIL to Pair(138_000_000L, 186_000_000L),
            SupportedLanguage.TELUGU to Pair(138_000_000L, 185_000_000L),
            SupportedLanguage.ODIA to Pair(185_000_000L, 236_000_000L),
            SupportedLanguage.BENGALI to Pair(151_000_000L, 199_000_000L)
        )
    }

    private val _packStatuses = MutableStateFlow<Map<SupportedLanguage, LanguagePackStatus>>(emptyMap())
    val packStatuses: StateFlow<Map<SupportedLanguage, LanguagePackStatus>> = _packStatuses.asStateFlow()

    init {
        refreshStatuses()
    }

    /**
     * Refreshes the installation status for all 10 supported languages.
     */
    fun refreshStatuses() {
        val newMap = mutableMapOf<SupportedLanguage, LanguagePackStatus>()
        for (lang in SupportedLanguage.entries) {
            val installed = checkIsPackInstalled(lang)
            val size = getInstalledSize(lang)
            val meta = PACK_METADATA[lang] ?: Pair(138_000_000L, 185_000_000L)
            
            val currentStatus = _packStatuses.value[lang]
            newMap[lang] = LanguagePackStatus(
                language = lang,
                isInstalled = installed,
                isDownloading = currentStatus?.isDownloading ?: false,
                downloadProgressPercent = currentStatus?.downloadProgressPercent ?: 0,
                installedSizeBytes = size,
                estimatedDownloadSizeBytes = meta.first
            )
        }
        _packStatuses.value = newMap
    }

    /**
     * Checks if the complete language pack (STT + TTS models) exists in context.filesDir or assets.
     */
    fun isPackInstalled(lang: SupportedLanguage): Boolean {
        return checkIsPackInstalled(lang)
    }

    private fun checkIsPackInstalled(lang: SupportedLanguage): Boolean {
        // 1. Verify STT Model & Vocab
        val sttModelInStorage = File(context.filesDir, lang.modelAssetPath)
        val sttVocabInStorage = File(context.filesDir, lang.vocabOrTokensAssetPath)

        val hasSttInStorage = sttModelInStorage.exists() && 
                sttModelInStorage.length() > 10 * 1024 * 1024 && 
                sttVocabInStorage.exists()

        // 2. Verify TTS Model
        val ttsVoice = TtsVoiceConfig.getConfigFor(lang)
        val ttsModelInStorage = File(context.filesDir, "${ttsVoice.modelDirName}/model.onnx")
        val hasTtsInStorage = ttsModelInStorage.exists() && ttsModelInStorage.length() > 10 * 1024 * 1024

        if (hasSttInStorage && hasTtsInStorage) {
            return true
        }

        // Check if bundled in assets (e.g., if base English is bundled)
        val hasSttInAssets = isAssetFilePresent(lang.modelAssetPath)
        val hasTtsInAssets = isAssetFilePresent("${ttsVoice.modelDirName}/model.onnx")

        return (hasSttInStorage || hasSttInAssets) && (hasTtsInStorage || hasTtsInAssets)
    }

    private fun isAssetFilePresent(assetPath: String): Boolean {
        return try {
            val parent = File(assetPath).parent ?: ""
            val name = File(assetPath).name
            context.assets.list(parent)?.contains(name) == true
        } catch (_: Exception) {
            false
        }
    }

    private fun getInstalledSize(lang: SupportedLanguage): Long {
        var total = 0L

        val sttModel = File(context.filesDir, lang.modelAssetPath)
        if (sttModel.exists()) total += sttModel.length()

        val sttVocab = File(context.filesDir, lang.vocabOrTokensAssetPath)
        if (sttVocab.exists()) total += sttVocab.length()

        val ttsVoice = TtsVoiceConfig.getConfigFor(lang)
        val ttsDir = File(context.filesDir, ttsVoice.modelDirName)
        if (ttsDir.exists()) {
            ttsDir.walkTopDown().forEach { f ->
                if (f.isFile) total += f.length()
            }
        }

        return total
    }

    /**
     * Installs a language pack by importing a local zip file Uri (e.g. from File Picker or SD card).
     */
    suspend fun importPackFromUri(lang: SupportedLanguage, uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        updateStatus(lang) { it.copy(isDownloading = true, downloadProgressPercent = 10, errorMessage = null) }
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                unzipAndInstall(inputStream)
            } ?: return@withContext Result.failure(IllegalStateException("Could not open file URI"))

            refreshStatuses()
            Log.i(TAG, "Successfully imported language pack for ${lang.displayName} from Uri")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import language pack for ${lang.displayName}", e)
            updateStatus(lang) { it.copy(isDownloading = false, errorMessage = "Import failed: ${e.message}") }
            Result.failure(e)
        }
    }

    /**
     * Downloads and installs a language pack zip from GitHub Releases / URL.
     */
    suspend fun downloadAndInstallPack(
        lang: SupportedLanguage,
        customUrl: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        updateStatus(lang) { it.copy(isDownloading = true, downloadProgressPercent = 0, errorMessage = null) }
        
        val downloadUrl = customUrl ?: "$BASE_GITHUB_RELEASE_URL/langpack_${lang.code}.zip"
        val tempZipFile = File(context.cacheDir, "langpack_${lang.code}.tmp.zip")

        try {
            Log.i(TAG, "Downloading language pack for ${lang.displayName} from $downloadUrl...")
            val url = URL(downloadUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException("HTTP ${connection.responseCode}: ${connection.responseMessage}")
            }

            val fileLength = connection.contentLength
            val input = connection.inputStream
            val output = FileOutputStream(tempZipFile)

            val data = ByteArray(8192)
            var total: Long = 0
            var count: Int
            var lastReportedPercent = 0

            while (input.read(data).also { count = it } != -1) {
                total += count.toLong()
                output.write(data, 0, count)

                if (fileLength > 0) {
                    val percent = ((total * 100) / fileLength).toInt()
                    if (percent >= lastReportedPercent + 5) {
                        lastReportedPercent = percent
                        updateStatus(lang) { it.copy(downloadProgressPercent = (percent * 0.8).toInt()) }
                    }
                }
            }

            output.flush()
            output.close()
            input.close()

            updateStatus(lang) { it.copy(downloadProgressPercent = 85) }

            // Extract zip
            tempZipFile.inputStream().use { inputStream ->
                unzipAndInstall(inputStream)
            }

            tempZipFile.delete()
            refreshStatuses()
            Log.i(TAG, "Successfully downloaded and installed language pack for ${lang.displayName}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download language pack for ${lang.displayName}: ${e.message}", e)
            tempZipFile.delete()
            updateStatus(lang) { it.copy(isDownloading = false, errorMessage = "Download failed: ${e.message}") }
            Result.failure(e)
        }
    }

    /**
     * Unzips and installs model files into context.filesDir.
     */
    private fun unzipAndInstall(inputStream: InputStream) {
        val zipInput = ZipInputStream(inputStream)
        var entry = zipInput.nextEntry

        val buffer = ByteArray(8192)
        while (entry != null) {
            if (!entry.isDirectory) {
                val entryName = entry.name.removePrefix("/")
                val targetFile = File(context.filesDir, entryName)
                targetFile.parentFile?.mkdirs()

                FileOutputStream(targetFile).use { fos ->
                    var len: Int
                    while (zipInput.read(buffer).also { len = it } > 0) {
                        fos.write(buffer, 0, len)
                    }
                }
                Log.d(TAG, "Extracted pack file: ${targetFile.absolutePath} (${targetFile.length()} bytes)")
            }
            zipInput.closeEntry()
            entry = zipInput.nextEntry
        }
        zipInput.close()
    }

    /**
     * Removes an installed language pack (both STT and TTS models) from filesDir.
     * Prevents removing the currently active language pack.
     * Preserves shared phoneme data (`tts-en-amy/espeak-ng-data`).
     */
    suspend fun removePack(
        lang: SupportedLanguage,
        activeLanguage: SupportedLanguage
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (lang == activeLanguage) {
            val msg = "Cannot remove active language pack (${lang.displayName}). Switch active language first."
            Log.w(TAG, msg)
            return@withContext Result.failure(IllegalStateException(msg))
        }

        try {
            // Delete STT model & vocab
            val sttModel = File(context.filesDir, lang.modelAssetPath)
            if (sttModel.exists()) sttModel.delete()

            val sttVocab = File(context.filesDir, lang.vocabOrTokensAssetPath)
            if (sttVocab.exists()) sttVocab.delete()

            // Delete TTS model folder
            val ttsVoice = TtsVoiceConfig.getConfigFor(lang)
            val ttsDir = File(context.filesDir, ttsVoice.modelDirName)
            if (ttsDir.exists()) {
                // Delete model.onnx, tokens.txt, and model.onnx.json inside voice folder
                File(ttsDir, "model.onnx").delete()
                File(ttsDir, "tokens.txt").delete()
                File(ttsDir, "model.onnx.json").delete()
            }

            refreshStatuses()
            Log.i(TAG, "Removed language pack for ${lang.displayName}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove language pack for ${lang.displayName}", e)
            Result.failure(e)
        }
    }

    /**
     * Computes the SHA-256 hash of a file for integrity verification.
     */
    fun computeSha256(file: File): String {
        if (!file.exists()) return ""
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { fis ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        val hexChars = StringBuilder()
        for (b in digest.digest()) {
            hexChars.append(String.format("%02x", b))
        }
        return hexChars.toString()
    }

    private fun updateStatus(lang: SupportedLanguage, transform: (LanguagePackStatus) -> LanguagePackStatus) {
        val current = _packStatuses.value.toMutableMap()
        val existing = current[lang] ?: LanguagePackStatus(language = lang)
        current[lang] = transform(existing)
        _packStatuses.value = current
    }
}
