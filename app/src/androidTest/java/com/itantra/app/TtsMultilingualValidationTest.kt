package com.itantra.app

import android.content.Context
import android.os.Debug
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.k2fsa.sherpa.onnx.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File
import kotlin.system.measureTimeMillis

@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class TtsMultilingualValidationTest {

    companion object {
        private const val TAG = "TtsValidationTest"
        private const val BENCHMARK_DIR = "/data/local/tmp/tts_benchmarks"

        val HINDI_PHRASES = listOf(
            Pair("emergency_1", "आपातकालीन चेतावनी, सेक्टर चार में तुरंत सहायता की आवश्यकता है।"),
            Pair("emergency_2", "रेड अलर्ट, सभी टीमें सुरक्षित स्थान पर पीछे हटें।"),
            Pair("location", "हम स्टेशन अल्फा पर हैं, मुख्य द्वार से पचास मीटर उत्तर की ओर।"),
            Pair("numbers", "टीम में कुल बारह सदस्य हैं, बैटरी स्तर पचहत्तर प्रतिशत है।"),
            Pair("short", "रेडियो चेक, क्या आपको मेरी आवाज़ आ रही है?")
        )

        val GUJARATI_PHRASES = listOf(
            Pair("emergency_1", "કટોકટી ચેતવણી, સેક્ટર ચારમાં તાત્કાલિક સહાયની જરૂર છે."),
            Pair("emergency_2", "રેડ એલર્ટ, બધા સભ્યો તાત્કાલિક સુરક્ષિત સ્થળે પહોંચો."),
            Pair("location", "અમે સ્ટેશન આલ્ફા પર છીએ, મુખ્ય ગેટથી પચાસ મીટર ઉત્તરમાં."),
            Pair("numbers", "ટીમમાં બાર સભ્યો છે, બેટરી પંચોતેર ટકા છે."),
            Pair("short", "રેડિયો ચેક, શું તમને મારો અવાજ સંભળાય છે?")
        )

        val TELUGU_PHRASES = listOf(
            Pair("emergency_1", "అత్యవసర హెచ్చరిక, సెక్టార్ నాలుగులో తక్షణ సహాయం అవసరం."),
            Pair("emergency_2", "రెడ్ అలర్ట్, బృందం సభ్యులందరూ వెంటనే సురక్షిత ప్రాంతానికి చేరుకోండి."),
            Pair("location", "మేము స్టేషన్ ఆల్ఫా వద్ద ఉన్నాము, ప్రధాన ద్వారానికి ఉత్తరంగా యాభై మీటర్ల దూరంలో."),
            Pair("numbers", "బృందంలో పన్నెండు మంది సభ్యులు ఉన్నారు, బ్యాటరీ డెబ్బై ఐదు శాతం ఉంది."),
            Pair("short", "రేడియో తనిఖీ, నా స్వరం మీకు స్పష్టంగా వినిపిస్తుందా?")
        )

        val KANNADA_PHRASES = listOf(
            Pair("emergency_1", "ತುರ್ತು ಎಚ್ಚರಿಕೆ, ಸೆಕ್ಟರ್ ನಾಲ್ಕರಲ್ಲಿ ತಕ್ಷಣದ ಸಹಾಯದ ಅಗತ್ಯವಿದೆ."),
            Pair("emergency_2", "ರೆಡ್ ಅಲರ್ಟ್, ಎಲ್ಲಾ ತಂಡದ ಸದಸ್ಯರು ತಕ್ಷಣ ಸುರಕ್ಷಿತ ಸ್ಥಳಕ್ಕೆ ತೆರಳಿ."),
            Pair("location", "ನಾವು ಸ್ಟೇಷನ್ ಆಲ್ಫಾದಲ್ಲಿದ್ದೇವೆ, ಮುಖ್ಯ ದ್ವಾರದಿಂದ ಐವತ್ತು ಮೀಟರ್ ಉತ್ತರಕ್ಕೆ."),
            Pair("numbers", "ತಂಡದಲ್ಲಿ ಒಟ್ಟು ಹನ್ನೆರಡು ಸದಸ್ಯರಿದ್ದಾರೆ, ಬ್ಯಾಟರಿ ಮಟ್ಟ ಎಪ್ಪತ್ತೈದು ಪ್ರತಿಶತ ಇದೆ."),
            Pair("short", "ರೇಡಿಯೋ ಪರಿಶೀಲನೆ, ನನ್ನ ಧ್ವನಿ ನಿಮಗೆ ಕೇಳಿಸುತ್ತಿದೆಯೇ?")
        )

        val MALAYALAM_PHRASES = listOf(
            Pair("emergency_1", "അടിയന്തര മുന്നറിയിപ്പ്, സെക്ടർ നാലിൽ അടിയന്തര സഹായം ആവശ്യമാണ്."),
            Pair("emergency_2", "റെഡ് അലേർട്ട്, എല്ലാ ടീം അംഗങ്ങളും ഉടൻ സുരക്ഷിത സ്ഥാനത്തേക്ക് മാറുക."),
            Pair("location", "ഞങ്ങൾ സ്റ്റേഷൻ ആൽഫയിലാണ്, പ്രധാന കവാടത്തിൽ നിന്ന് അമ്പത് മീറ്റർ വടക്കോട്ട്."),
            Pair("numbers", "ടീമിൽ പന്ത്രണ്ട് അംഗങ്ങളുണ്ട്, ബാറ്ററി നില എഴുപത്തിയഞ്ച് ശതമാനമാണ്."),
            Pair("short", "റേഡിയോ പരിശോധന, എന്റെ ശബ്ദം വ്യക്തമായി കേൾക്കുന്നുണ്ടോ?")
        )

        val TAMIL_PHRASES = listOf(
            Pair("emergency_1", "அவசர எச்சரிக்கை, பிரிவு நான்கில் உடனடி உதவி தேவைப்படுகிறது."),
            Pair("emergency_2", "ரெட் அலர்ட், அனைத்து குழு உறுப்பினர்களும் உடனடியாக பாதுகாப்பான இடத்திற்கு செல்லவும்."),
            Pair("location", "நாங்கள் ஸ்டேஷன் ஆல்பாவில் இருக்கிறோம், பிரதான வாயிலில் இருந்து ஐம்பது மீட்டர் வடக்கே."),
            Pair("numbers", "குழுவில் பன்னிரண்டு உறுப்பினர்கள் உள்ளனர், பேட்டரி எழுபத்தைந்து சதவீதம் உள்ளது."),
            Pair("short", "ரேடியோ சோதனை, என் குரல் உங்களுக்கு தெளிவாக கேட்கிறதா?")
        )

        val BENGALI_PHRASES = listOf(
            Pair("emergency_1", "জরুরী সতর্কতা, চার নম্বর সেক্টরে অবিলম্বে সহায়তা প্রয়োজন।"),
            Pair("emergency_2", "রেড অ্যালার্ট, দলের সকল সদস্য অবিলম্বে নিরাপদ স্থানে সরে যান।"),
            Pair("location", "আমরা স্টেশন আলফাতে আছি, প্রধান ফটক থেকে পঞ্চাশ মিটার উত্তরে।"),
            Pair("numbers", "দলে মোট বারো জন সদস্য আছেন, ব্যাটারি স্তর পঁচাত্তর শতাংশ।"),
            Pair("short", "রেডিও চেক, আমার কথা কি পরিষ্কার শোনা যাচ্ছে?")
        )

        val MARATHI_PHRASES = listOf(
            Pair("emergency_1", "तातडीचा इशारा, सेक्टर चारमध्ये तातडीने मदतीची गरज आहे."),
            Pair("emergency_2", "रेड अलर्ट, सर्व संघ सदस्यांनी त्वरित सुरक्षित स्थळी जावे."),
            Pair("location", "आम्ही स्टेशन अल्फा येथे आहोत, मुख्य गेटपासून पन्नास मीटर उत्तरेकडे."),
            Pair("numbers", "संघामध्ये एकूण बारा सदस्य आहेत, बॅटरी पातळी पंच्याहत्तर टक्के आहे."),
            Pair("short", "रेडिओ चेक, माझा आवाज तुम्हाला स्पष्ट येत आहे का?")
        )

        val ODIA_PHRASES = listOf(
            Pair("emergency_1", "ଜରୁରୀ ସତର୍କତା, ଚାରି ନମ୍ବର ସେକ୍ଟରରେ ତୁରନ୍ତ ସାହାଯ୍ୟ ଆବଶ୍ୟକ।"),
            Pair("emergency_2", "ରେଡ୍ ଆଲର୍ଟ, ସମସ୍ତ ଦଳ ସଦସ୍ୟ ତୁରନ୍ତ ସୁରକ୍ଷିତ ସ୍ଥାନକୁ ଯାଆନ୍ତୁ।"),
            Pair("location", "ଆମେ ଷ୍ଟେସନ ଆଲଫାରେ ଅଛୁ, ମୁଖ୍ୟ ଫାଟକରୁ ପଚାଶ ମିଟର ଉତ୍ତରକୁ।"),
            Pair("numbers", "ଦଳରେ ମୋଟ ବାର ଜଣ ସଦସ୍ୟ ଅଛନ୍ତି, ବ୍ୟାଟେରୀ ସ୍ତର ପଞ୍ଚସ୍ତରୀ ପ୍ରତିଶତ।"),
            Pair("short", "ରେଡିଓ ଯାଞ୍ଚ, ମୋର ସ୍ୱର ଆପଣଙ୍କୁ ସ୍ପଷ୍ଟ ଶୁଣାଯାଉଛି କି?")
        )
    }

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        Runtime.getRuntime().gc()
    }

    private fun getProcessPssMb(): Double {
        val memInfo = Debug.MemoryInfo()
        Debug.getMemoryInfo(memInfo)
        return memInfo.totalPss / 1024.0
    }

    private fun runModelBenchmark(
        modelTag: String,
        modelDirName: String,
        isPiper: Boolean,
        phrases: List<Pair<String, String>>
    ) {
        Log.i(TAG, "==================================================")
        Log.i(TAG, "BENCHMARKING TTS MODEL: $modelTag ($modelDirName)")
        Log.i(TAG, "==================================================")

        val sourceDir = File(BENCHMARK_DIR, modelDirName)
        assertTrue("Model source directory must exist: ${sourceDir.absolutePath}", sourceDir.exists())

        val modelFile = File(sourceDir, "model.onnx")
        val tokensFile = File(sourceDir, "tokens.txt")
        assertTrue("model.onnx must exist: ${modelFile.absolutePath}", modelFile.exists())
        assertTrue("tokens.txt must exist: ${tokensFile.absolutePath}", tokensFile.exists())

        val espeakDir = File(BENCHMARK_DIR, "espeak-ng-data")
        val dataDirPath = if (isPiper) {
            assertTrue("espeak-ng-data must exist for Piper", espeakDir.exists())
            espeakDir.absolutePath
        } else {
            ""
        }

        Runtime.getRuntime().gc()
        val initialPss = getProcessPssMb()

        // 1. Measure Model Load Time
        var ttsEngine: OfflineTts? = null
        val loadTimeMs = measureTimeMillis {
            val vitsConfig = OfflineTtsVitsModelConfig(
                model = modelFile.absolutePath,
                tokens = tokensFile.absolutePath,
                dataDir = dataDirPath,
                noiseScale = 0.667f,
                noiseScaleW = 0.8f,
                lengthScale = 1.0f
            )
            val modelConfig = OfflineTtsModelConfig(
                vits = vitsConfig,
                numThreads = 2,
                debug = false,
                provider = "cpu"
            )
            val config = OfflineTtsConfig(model = modelConfig)
            ttsEngine = OfflineTts(null, config)
        }

        val postLoadPss = getProcessPssMb()
        val modelPssDelta = postLoadPss - initialPss

        assertNotNull("TTS engine failed to initialize", ttsEngine)
        val engine = ttsEngine!!
        Log.i(TAG, "RESULTS_HEADER: model,sample_rate,load_time_ms,model_pss_mb")
        Log.i(TAG, "RESULTS_LOAD: $modelTag,${engine.sampleRate()},$loadTimeMs,${"%.2f".format(modelPssDelta)}")

        // 2. Synthesize Phrases & Measure Metrics
        Log.i(TAG, "RESULTS_SYNTH_HEADER: model,tag,synth_ms,audio_duration_s,rtf,process_pss_mb")

        var totalSynthMs = 0L
        var totalAudioDuration = 0.0

        for ((tag, text) in phrases) {
            val startTime = System.currentTimeMillis()
            val generatedAudio = engine.generate(text)
            val synthMs = System.currentTimeMillis() - startTime

            assertNotNull("Generated audio is null for $tag", generatedAudio)
            val samples = generatedAudio.samples
            assertTrue("Generated audio samples are empty for $tag", samples.isNotEmpty())

            val audioDurationS = samples.size.toDouble() / engine.sampleRate()
            val rtf = if (audioDurationS > 0) (synthMs / 1000.0) / audioDurationS else 0.0
            val currentPss = getProcessPssMb()

            totalSynthMs += synthMs
            totalAudioDuration += audioDurationS

            Log.i(
                TAG,
                "RESULTS_SYNTH: $modelTag,$tag,$synthMs,${"%.2f".format(audioDurationS)},${"%.3f".format(rtf)},${"%.2f".format(currentPss)}"
            )
            Log.d(TAG, "[$tag] '$text' -> ${samples.size} samples, ${"%.2f".format(audioDurationS)}s, ${synthMs}ms, RTF: ${"%.3f".format(rtf)}")
        }

        val avgRtf = if (totalAudioDuration > 0) (totalSynthMs / 1000.0) / totalAudioDuration else 0.0
        val peakPss = getProcessPssMb()
        Log.i(TAG, "RESULTS_SUMMARY: $modelTag,avg_rtf=${"%.3f".format(avgRtf)},peak_pss_mb=${"%.2f".format(peakPss)},net_pss_delta=${"%.2f".format(peakPss - initialPss)}")

        // 3. 10 Repeated Inferences Stability & Leak Assessment
        Log.i(TAG, "Running 10 repeated inferences for memory leak assessment...")
        val preStressPss = getProcessPssMb()
        for (i in 1..10) {
            val audio = engine.generate(phrases[0].second)
            assertTrue("Stress inference $i produced empty samples", audio.samples.isNotEmpty())
        }
        Runtime.getRuntime().gc()
        val postStressPss = getProcessPssMb()
        val stressDelta = postStressPss - preStressPss
        Log.i(TAG, "STRESS_RESULTS: $modelTag,pre_pss=${"%.2f".format(preStressPss)},post_pss=${"%.2f".format(postStressPss)},delta=${"%.2f".format(stressDelta)}")
        assertTrue("Memory leak detected: stressDelta must be < 200 MB", stressDelta < 200.0)

        // Release
        ttsEngine = null
        Runtime.getRuntime().gc()
        val postReleasePss = getProcessPssMb()
        Log.i(TAG, "RELEASE_RESULTS: $modelTag,post_release_pss=${"%.2f".format(postReleasePss)},retained_delta=${"%.2f".format(postReleasePss - initialPss)}")
        Log.i(TAG, "✓ Benchmark completed successfully for $modelTag")
    }

    @Test
    fun test01_BenchmarkPiperHindiPriyamvada() {
        runModelBenchmark(
            modelTag = "piper_hi_priyamvada",
            modelDirName = "piper_hi_priyamvada",
            isPiper = true,
            phrases = HINDI_PHRASES
        )
    }

    @Test
    fun test02_BenchmarkPiperHindiRohan() {
        runModelBenchmark(
            modelTag = "piper_hi_rohan",
            modelDirName = "piper_hi_rohan",
            isPiper = true,
            phrases = HINDI_PHRASES
        )
    }

    @Test
    fun test03_BenchmarkMetaMmsHindi() {
        runModelBenchmark(
            modelTag = "mms_hin",
            modelDirName = "mms_hin",
            isPiper = false,
            phrases = HINDI_PHRASES
        )
    }

    @Test
    fun test04_BenchmarkMetaMmsGujarati() {
        runModelBenchmark(
            modelTag = "mms_guj",
            modelDirName = "mms_guj",
            isPiper = false,
            phrases = GUJARATI_PHRASES
        )
    }

    @Test
    fun test05_BenchmarkPiperTeluguMaya() {
        runModelBenchmark(
            modelTag = "piper_te_maya",
            modelDirName = "piper_te_maya",
            isPiper = true,
            phrases = TELUGU_PHRASES
        )
    }

    @Test
    fun test06_BenchmarkPiperTeluguVenkatesh() {
        runModelBenchmark(
            modelTag = "piper_te_venkatesh",
            modelDirName = "piper_te_venkatesh",
            isPiper = true,
            phrases = TELUGU_PHRASES
        )
    }

    @Test
    fun test07_BenchmarkMetaMmsTelugu() {
        runModelBenchmark(
            modelTag = "mms_tel",
            modelDirName = "mms_tel",
            isPiper = false,
            phrases = TELUGU_PHRASES
        )
    }

    @Test
    fun test08_BenchmarkMetaMmsKannada() {
        runModelBenchmark(
            modelTag = "mms_kan",
            modelDirName = "mms_kan",
            isPiper = false,
            phrases = KANNADA_PHRASES
        )
    }

    @Test
    fun test09_BenchmarkPiperMalayalamMeera() {
        runModelBenchmark(
            modelTag = "piper_ml_meera",
            modelDirName = "piper_ml_meera",
            isPiper = true,
            phrases = MALAYALAM_PHRASES
        )
    }

    @Test
    fun test10_BenchmarkPiperMalayalamArjun() {
        runModelBenchmark(
            modelTag = "piper_ml_arjun",
            modelDirName = "piper_ml_arjun",
            isPiper = true,
            phrases = MALAYALAM_PHRASES
        )
    }

    @Test
    fun test11_BenchmarkPiperTamilRasaFemale() {
        runModelBenchmark(
            modelTag = "piper_ta_rasa_female",
            modelDirName = "piper_ta_rasa_female",
            isPiper = true,
            phrases = TAMIL_PHRASES
        )
    }

    @Test
    fun test12_BenchmarkPiperTamilRasaMale() {
        runModelBenchmark(
            modelTag = "piper_ta_rasa_male",
            modelDirName = "piper_ta_rasa_male",
            isPiper = true,
            phrases = TAMIL_PHRASES
        )
    }

    @Test
    fun test13_BenchmarkMetaMmsMalayalam() {
        runModelBenchmark(
            modelTag = "mms_mal",
            modelDirName = "mms_mal",
            isPiper = false,
            phrases = MALAYALAM_PHRASES
        )
    }

    @Test
    fun test14_BenchmarkMetaMmsTamil() {
        runModelBenchmark(
            modelTag = "mms_tam",
            modelDirName = "mms_tam",
            isPiper = false,
            phrases = TAMIL_PHRASES
        )
    }

    @Test
    fun test15_BenchmarkPiperBengaliGoogle() {
        runModelBenchmark(
            modelTag = "piper_bn_google",
            modelDirName = "piper_bn_google",
            isPiper = true,
            phrases = BENGALI_PHRASES
        )
    }

    @Test
    fun test16_BenchmarkPiperMarathiGoogle() {
        runModelBenchmark(
            modelTag = "piper_mr_google",
            modelDirName = "piper_mr_google",
            isPiper = true,
            phrases = MARATHI_PHRASES
        )
    }

    @Test
    fun test17_BenchmarkMetaMmsBengali() {
        runModelBenchmark(
            modelTag = "mms_ben",
            modelDirName = "mms_ben",
            isPiper = false,
            phrases = BENGALI_PHRASES
        )
    }

    @Test
    fun test18_BenchmarkMetaMmsMarathi() {
        runModelBenchmark(
            modelTag = "mms_mar",
            modelDirName = "mms_mar",
            isPiper = false,
            phrases = MARATHI_PHRASES
        )
    }

    @Test
    fun test19_BenchmarkMetaMmsOdia() {
        runModelBenchmark(
            modelTag = "mms_ory",
            modelDirName = "mms_ory",
            isPiper = false,
            phrases = ODIA_PHRASES
        )
    }
}
