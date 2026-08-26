package com.itantra.app

import android.content.Context
import android.os.Debug
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.itantra.app.audio.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ProductionMultilingualSttIntegrationTest {

    companion object {
        private const val TAG = "MultilingualSttTest"
        private const val TEST_AUDIO_DIR = "/data/local/tmp/stt_test"
    }

    private lateinit var context: Context
    private lateinit var manager: LanguageModelManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        manager = LanguageModelManager.getInstance(context)
    }

    private fun loadWavSamples(filename: String): FloatArray {
        val file = File(TEST_AUDIO_DIR, filename)
        assertTrue("Test WAV file must exist: ${file.absolutePath}", file.exists())
        val bytes = file.readBytes()
        val dataOffset = 44
        val numSamples = (bytes.size - dataOffset) / 2
        val floatArray = FloatArray(numSamples)
        val byteBuffer = ByteBuffer.wrap(bytes, dataOffset, bytes.size - dataOffset).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until numSamples) {
            val sample = byteBuffer.short
            floatArray[i] = sample.toFloat() / 32768.0f
        }
        return floatArray
    }

    @Test
    fun test01_FiveLanguageTranscription() = runBlocking {
        Log.i(TAG, "=== Running test01_FiveLanguageTranscription ===")

        // 1. English
        var res = manager.setLanguage(SupportedLanguage.ENGLISH)
        assertTrue("English initialization failed", res.isSuccess)
        assertEquals(SupportedLanguage.ENGLISH, manager.currentLanguage.value)
        assertEquals(ModelLifecycleState.READY, manager.lifecycleState.value)

        val enSamples = loadWavSamples("en.wav")
        val enResult = manager.transcribe(enSamples)
        assertNotNull("English transcription returned null", enResult)
        Log.i(TAG, "English Output: '${enResult!!.text}' (RTF: ${enResult.rtf})")
        assertTrue("English text must not be empty", enResult.text.isNotBlank())

        // 2. Hindi
        res = manager.setLanguage(SupportedLanguage.HINDI)
        assertTrue("Hindi initialization failed", res.isSuccess)
        assertEquals(SupportedLanguage.HINDI, manager.currentLanguage.value)
        assertEquals(ModelLifecycleState.READY, manager.lifecycleState.value)

        val hiSamples = loadWavSamples("hi.wav")
        val hiResult = manager.transcribe(hiSamples)
        assertNotNull("Hindi transcription returned null", hiResult)
        Log.i(TAG, "Hindi Output: '${hiResult!!.text}' (RTF: ${hiResult.rtf})")
        assertTrue("Hindi text must not be empty", hiResult.text.isNotBlank())

        // 3. Gujarati
        res = manager.setLanguage(SupportedLanguage.GUJARATI)
        assertTrue("Gujarati initialization failed", res.isSuccess)
        assertEquals(SupportedLanguage.GUJARATI, manager.currentLanguage.value)
        assertEquals(ModelLifecycleState.READY, manager.lifecycleState.value)

        val guSamples = loadWavSamples("gu.wav")
        val guResult = manager.transcribe(guSamples)
        assertNotNull("Gujarati transcription returned null", guResult)
        Log.i(TAG, "Gujarati Output: '${guResult!!.text}' (RTF: ${guResult.rtf})")
        assertTrue("Gujarati text must not be empty", guResult.text.isNotBlank())

        // 4. Telugu
        res = manager.setLanguage(SupportedLanguage.TELUGU)
        assertTrue("Telugu initialization failed", res.isSuccess)
        assertEquals(SupportedLanguage.TELUGU, manager.currentLanguage.value)
        assertEquals(ModelLifecycleState.READY, manager.lifecycleState.value)

        val teSamples = loadWavSamples("te.wav")
        val teResult = manager.transcribe(teSamples)
        assertNotNull("Telugu transcription returned null", teResult)
        Log.i(TAG, "Telugu Output: '${teResult!!.text}' (RTF: ${teResult.rtf})")
        assertTrue("Telugu text must not be empty", teResult.text.isNotBlank())

        // 5. Kannada
        res = manager.setLanguage(SupportedLanguage.KANNADA)
        assertTrue("Kannada initialization failed", res.isSuccess)
        assertEquals(SupportedLanguage.KANNADA, manager.currentLanguage.value)
        assertEquals(ModelLifecycleState.READY, manager.lifecycleState.value)

        val knSamples = loadWavSamples("kn.wav")
        val knResult = manager.transcribe(knSamples)
        assertNotNull("Kannada transcription returned null", knResult)
        Log.i(TAG, "Kannada Output: '${knResult!!.text}' (RTF: ${knResult.rtf})")
        assertTrue("Kannada text must not be empty", knResult.text.isNotBlank())
    }

    @Test
    fun test02_SwitchEnglishHindiEnglish() = runBlocking {
        Log.i(TAG, "=== Running test02_SwitchEnglishHindiEnglish ===")
        val enSamples = loadWavSamples("en.wav")
        val hiSamples = loadWavSamples("hi.wav")

        // Step 1: English
        manager.setLanguage(SupportedLanguage.ENGLISH)
        val en1 = manager.transcribe(enSamples)
        assertNotNull(en1)
        Log.i(TAG, "Step 1 (EN): ${en1!!.text}")

        // Step 2: Switch to Hindi
        manager.setLanguage(SupportedLanguage.HINDI)
        val hi = manager.transcribe(hiSamples)
        assertNotNull(hi)
        Log.i(TAG, "Step 2 (HI): ${hi!!.text}")

        // Step 3: Switch back to English
        manager.setLanguage(SupportedLanguage.ENGLISH)
        val en2 = manager.transcribe(enSamples)
        assertNotNull(en2)
        Log.i(TAG, "Step 3 (EN): ${en2!!.text}")

        assertEquals(SupportedLanguage.ENGLISH, manager.currentLanguage.value)
        assertEquals(ModelLifecycleState.READY, manager.lifecycleState.value)
    }

    @Test
    fun test03_SwitchHindiGujaratiHindi() = runBlocking {
        Log.i(TAG, "=== Running test03_SwitchHindiGujaratiHindi ===")
        val hiSamples = loadWavSamples("hi.wav")
        val guSamples = loadWavSamples("gu.wav")

        // Step 1: Hindi
        manager.setLanguage(SupportedLanguage.HINDI)
        val hi1 = manager.transcribe(hiSamples)
        assertNotNull(hi1)
        Log.i(TAG, "Step 1 (HI): ${hi1!!.text}")

        // Step 2: Switch to Gujarati
        manager.setLanguage(SupportedLanguage.GUJARATI)
        val gu = manager.transcribe(guSamples)
        assertNotNull(gu)
        Log.i(TAG, "Step 2 (GU): ${gu!!.text}")

        // Step 3: Switch back to Hindi
        manager.setLanguage(SupportedLanguage.HINDI)
        val hi2 = manager.transcribe(hiSamples)
        assertNotNull(hi2)
        Log.i(TAG, "Step 3 (HI): ${hi2!!.text}")

        assertEquals(SupportedLanguage.HINDI, manager.currentLanguage.value)
        assertEquals(ModelLifecycleState.READY, manager.lifecycleState.value)
    }

    @Test
    fun test04_RepeatedLanguageSwitchingAndMemoryStability() = runBlocking {
        Log.i(TAG, "=== Running test04_RepeatedLanguageSwitchingAndMemoryStability ===")
        
        val sequence = listOf(
            SupportedLanguage.ENGLISH,
            SupportedLanguage.HINDI,
            SupportedLanguage.GUJARATI,
            SupportedLanguage.TELUGU,
            SupportedLanguage.KANNADA,
            SupportedLanguage.ENGLISH,
            SupportedLanguage.HINDI
        )

        val initialPss = Debug.getPss()
        Log.i(TAG, "Initial Process PSS: ${initialPss / 1024.0} MB")

        for ((index, lang) in sequence.withIndex()) {
            val beforeSwitchPss = Debug.getPss()
            val switchResult = manager.setLanguage(lang)
            assertTrue("Switch $index to ${lang.displayName} failed", switchResult.isSuccess)
            assertEquals(lang, manager.currentLanguage.value)
            assertEquals(ModelLifecycleState.READY, manager.lifecycleState.value)

            val currentPss = Debug.getPss()
            Log.i(TAG, "Switch $index -> ${lang.displayName}: PSS = ${currentPss / 1024.0} MB (delta: ${(currentPss - beforeSwitchPss) / 1024.0} MB)")
        }

        System.gc()
        Thread.sleep(200)
        val finalPss = Debug.getPss()
        val totalDeltaMb = (finalPss - initialPss) / 1024.0
        Log.i(TAG, "Final Process PSS: ${finalPss / 1024.0} MB | Net Delta: ${"%.2f".format(totalDeltaMb)} MB")

        // Total memory growth across 6 switches must not exceed 50 MB (proves previous native sessions are freed)
        assertTrue("Memory growth across switches must be strictly bounded (<50 MB)", totalDeltaMb < 50.0)
    }

    @Test
    fun test05_PttTransceiverFlowCompatibility() = runBlocking {
        Log.i(TAG, "=== Running test05_PttTransceiverFlowCompatibility ===")
        
        val sttManager = SttManager(context)
        
        // Ensure SttManager delegates to LanguageModelManager
        assertSame(manager, sttManager.languageModelManager)

        // Set to Telugu
        manager.setLanguage(SupportedLanguage.TELUGU)
        val teSamples = loadWavSamples("te.wav")

        // Call legacy SttManager.transcribe
        val legacyResult = sttManager.transcribe(teSamples)
        assertNotNull("Legacy SttManager.transcribe must work seamlessly", legacyResult)
        Log.i(TAG, "Legacy SttManager Transcribe result: '${legacyResult!!.text}'")
        assertTrue(legacyResult.text.isNotBlank())
    }
}
