import os
import sys
import time
import wave
import numpy as np
import sherpa_onnx

if sys.platform == "win32":
    sys.stdout.reconfigure(encoding="utf-8")

BASE_DIR = os.path.dirname(__file__)
MODELS_DIR = os.path.join(BASE_DIR, "models")
ESPEAK_DIR = os.path.abspath(os.path.join(BASE_DIR, "..", "..", "app", "src", "main", "assets", "tts-en-amy", "espeak-ng-data"))
DEBUG_AUDIO_DIR = os.path.join(BASE_DIR, "audio_debug")
os.makedirs(DEBUG_AUDIO_DIR, exist_ok=True)

# Test phrases for 6 tactical categories per language
TEST_SUITE = {
    "en": {
        "lang_name": "English",
        "wav_name": "en_native_test.wav",
        "model_dir": os.path.join(BASE_DIR, "..", "..", "app", "src", "main", "assets", "tts-en-amy"),
        "model_file": "model.onnx",
        "is_piper": True,
        "voice": "en-us",
        "phrases": [
            ("greeting", "Hello, station base, this is squad alpha."),
            ("normal", "All patrol checkpoints are reported clear and secure."),
            ("numbers", "Coordinates latitude twelve point five, longitude seventy seven point six, altitude three hundred."),
            ("location", "We are at the northern perimeter checkpoint near building four."),
            ("emergency", "Emergency alert, critical medical assistance needed immediately at sector nine."),
            ("radio", "Roger that base, moving to checkpoint delta, over.")
        ]
    },
    "hi": {
        "lang_name": "Hindi",
        "wav_name": "hi_native_test.wav",
        "model_dir": os.path.join(MODELS_DIR, "piper_hi_priyamvada"),
        "model_file": "model.onnx",
        "is_piper": True,
        "voice": "hi",
        "phrases": [
            ("greeting", "नमस्ते स्टेशन बेस, यह दस्ता अल्फ़ा है।"),
            ("normal", "सभी गश्ती चौकियों की स्थिति सुरक्षित और स्पष्ट है।"),
            ("numbers", "अक्षांश बारह दशमलव पाँच, देशांतर सतहत्तर दशमलव छह, ऊँचाई तीन सौ मीटर।"),
            ("location", "हम इमारत चार के पास उत्तरी परिधि चौकी पर हैं।"),
            ("emergency", "आपातकालीन चेतावनी, सेक्टर नौ में तुरंत चिकित्सा सहायता की आवश्यकता है।"),
            ("radio", "रोज़र बेस, चेकपॉइंट डेल्टा की ओर बढ़ रहे हैं, ओवर।")
        ]
    },
    "mr": {
        "lang_name": "Marathi",
        "wav_name": "mr_native_test.wav",
        "model_dir": os.path.join(MODELS_DIR, "piper_mr_google"),
        "model_file": "model.onnx",
        "is_piper": True,
        "voice": "mr",
        "phrases": [
            ("greeting", "नमस्कार स्टेशन बेस, ही तुकडी अल्फा आहे."),
            ("normal", "सर्व गस्त चौक्या सुरक्षित आणि स्पष्ट असल्याची नोंद आहे."),
            ("numbers", "अक्षांश बारा दशांश पाच, रेखांश सत्याहत्तर दशांश सहा, उंची तीनशे मीटर."),
            ("location", "आम्ही इमारत चार जवळील उत्तर सुरक्षा चौकीवर आहोत."),
            ("emergency", "तातडीची सूचना, सेक्टर नऊमध्ये त्वरित वैद्यकीय मदतीची आवश्यकता आहे."),
            ("radio", "समजले बेस, आम्ही चेकपॉईंट डेल्टाकडे जात आहोत, ओव्हर.")
        ]
    },
    "bn": {
        "lang_name": "Bengali",
        "wav_name": "bn_native_test.wav",
        "model_dir": os.path.join(MODELS_DIR, "piper_bn_google"),
        "model_file": "model.onnx",
        "is_piper": True,
        "voice": "bn",
        "phrases": [
            ("greeting", "নমস্কার স্টেশন বেস, এটি স্কোয়াড আলফা।"),
            ("normal", "সমস্ত টহল চৌকি নিরাপদ এবং স্পষ্ট রিপোর্ট করা হয়েছে।"),
            ("numbers", "অক্ষাংশ বারো দশমিক পাঁচ, দ্রাঘিমাংশ সাতাাত্তর দশমিক ছয়, উচ্চতা তিনশত মিটার।"),
            ("location", "আমরা চার নম্বর ভবনের কাছে উত্তর সীমানা চৌকিতে আছি।"),
            ("emergency", "জরুরি সতর্কতা, সেক্টর নয়ে অবিলম্বে চিকিৎসা সহায়তার প্রয়োজন।"),
            ("radio", "রজার বেস, আমরা চেকপয়েন্ট ডেল্টার দিকে যাচ্ছি, ওভার।")
        ]
    },
    "te": {
        "lang_name": "Telugu",
        "wav_name": "te_native_test.wav",
        "model_dir": os.path.join(MODELS_DIR, "piper_te_maya"),
        "model_file": "model.onnx",
        "is_piper": True,
        "voice": "te",
        "phrases": [
            ("greeting", "నమస్కారం స్టేషన్ బేస్, ఇది స్క్వాడ్ ఆల్ఫా."),
            ("normal", "అన్ని పెట్రోలింగ్ చెక్‌పోస్టులు సురక్షితంగా ఉన్నాయని నివేదించబడింది."),
            ("numbers", "అక్షాంశం పన్నెండు పాయింట్ ఐదు, రేఖాంశం డెబ్బై ఏడు పాయింట్ ఆరు, ఎత్తు మూడు వందల మీటర్లు."),
            ("location", "మేము భవనం నాలుగు సమీపంలోని ఉత్తర సరిహద్దు చెక్‌పోస్ట్ వద్ద ఉన్నాము."),
            ("emergency", "అత్యవసర హెచ్చరిక, సెక్టార్ తొమ్మిదిలో తక్షణ వైద్య సహాయం అవసరం."),
            ("radio", "రోజర్ బేస్, చెక్‌పాయింట్ డెల్టా వైపు వెళ్తున్నాము, ఓవర్.")
        ]
    },
    "ml": {
        "lang_name": "Malayalam",
        "wav_name": "ml_native_test.wav",
        "model_dir": os.path.join(MODELS_DIR, "piper_ml_meera"),
        "model_file": "model.onnx",
        "is_piper": True,
        "voice": "ml",
        "phrases": [
            ("greeting", "നമസ്കാരം സ്റ്റേഷൻ ബേസ്, ഇത് സ്ക്വാഡ് ആൽഫയാണ്."),
            ("normal", "എല്ലാ പട്രോളിംഗ് ചെക്ക്പോസ്റ്റുകളും സുരക്ഷിതമാണെന്ന് റിപ്പോർട്ട് ചെയ്തിരിക്കുന്നു."),
            ("numbers", "അക്ഷാംശം പന്ത്രണ്ട് പോയിന്റ് അഞ്ച്, രേഖാംശം എഴുപത്തിയേഴ് പോയിന്റ് ആറ്, ഉയരം മുന്നൂറ് മീറ്റർ."),
            ("location", "നാലാം നമ്പർ കെട്ടിടത്തിന് സമീപമുള്ള വടക്കൻ അതിർത്തി ചെക്ക്പോസ്റ്റിലാണ് ഞങ്ങൾ."),
            ("emergency", "അടിയന്തിര മുന്നറിയിപ്പ്, സെക്ടർ ഒൻപതിൽ അടിയന്തിര വൈദ്യസഹായം ആവശ്യമാണ്."),
            ("radio", "റോജർ ബേസ്, ചെക്ക്പോയിന്റ് ഡെൽറ്റയിലേക്ക് നീങ്ങുന്നു, ഓവർ.")
        ]
    },
    "ta": {
        "lang_name": "Tamil",
        "wav_name": "ta_native_test.wav",
        "model_dir": os.path.join(MODELS_DIR, "piper_ta_rasa_female"),
        "model_file": "model.onnx",
        "is_piper": True,
        "voice": "ta",
        "phrases": [
            ("greeting", "வணக்கம் ஸ்டேஷன் பேஸ், இது படைப்பிரிவு ஆல்பா."),
            ("normal", "அனைத்து ரோந்து சோதனைச் சாவடிகளும் பாதுகாப்பாக உள்ளன."),
            ("numbers", "அட்சரேகை பன்னிரண்டு புள்ளி ஐந்து, தீர்க்கரேகை எழுபத்தேழு புள்ளி ஆறு, உயரம் முந்நூறு மீட்டர்."),
            ("location", "நாங்கள் கட்டிடம் நான்கு அருகிலுள்ள வடக்கு எல்லை சோதனைச் சாவடியில் இருக்கிறோம்."),
            ("emergency", "அவசர எச்சரிக்கை, பிரிவு ஒன்பதில் உடனடி மருத்துவ உதவி தேவைப்படுகிறது."),
            ("radio", "ரோஜர் பேஸ், சோதனைச் சாவடி டெல்டாவை நோக்கி நகர்கிறோம், ஓவர்.")
        ]
    },
    "gu": {
        "lang_name": "Gujarati (MMS)",
        "wav_name": "gu_native_test.wav",
        "model_dir": os.path.join(MODELS_DIR, "mms_guj"),
        "model_file": "model.onnx",
        "is_piper": False,
        "voice": "guj",
        "phrases": [
            ("greeting", "નમસ્તે સ્ટેશન બેઝ, આ ટુકડી આલ્ફા છે."),
            ("normal", "તમામ પેટ્રોલિંગ ચોકીઓ સુરક્ષિત અને સ્પષ્ટ હોવાનો અહેવાલ છે."),
            ("numbers", "અક્ષાંશ બાર પોઇન્ટ પાંચ, રેખાંશ સિત્તેર પોઇન્ટ છ, ઊંચાઈ ત્રણસો મીટર."),
            ("location", "અમે ઇમારત ચાર પાસે ઉત્તરીય સુરક્ષા ચોકી પર છીએ."),
            ("emergency", "કટોકટી ચેતવણી, સેક્ટર નવમાં તાત્કાલિક તબીબી સહાયની જરૂર છે."),
            ("radio", "રોજર બેઝ, ચેકપોઇન્ટ ડેલ્ટા તરફ આગળ વધી રહ્યા છીએ, ઓવર.")
        ]
    },
    "kn": {
        "lang_name": "Kannada (MMS)",
        "wav_name": "kn_native_test.wav",
        "model_dir": os.path.join(MODELS_DIR, "mms_kan"),
        "model_file": "model.onnx",
        "is_piper": False,
        "voice": "kan",
        "phrases": [
            ("greeting", "ನಮಸ್ಕಾರ ಸ್ಟೇಷನ್ ಬೇಸ್, ಇದು ಸ್ಕ್ವಾಡ್ ಆಲ್ಫಾ."),
            ("normal", "ಎಲ್ಲಾ ಗಸ್ತು ಚೆಕ್‌ಪೋಸ್ಟ್‌ಗಳು ಸುರಕ್ಷಿತವಾಗಿವೆ ಎಂದು ವರದಿಯಾಗಿದೆ."),
            ("numbers", "ಅಕ್ಷಾಂಶ ಹನ್ನೆರಡು ಪಾಯಿಂಟ್ ಐದು, ರೇಖಾಂಶ ಎಪ್ಪತ್ತೇಳು ಪಾಯಿಂಟ್ ಆರು, ಎತ್ತರ ಮುನ್ನೂರು ಮೀಟರ್."),
            ("location", "ನಾವು ಕಟ್ಟಡ ನಾಲ್ಕರ ಸಮೀಪವಿರುವ ಉತ್ತರ ಗಡಿ ಚೆಕ್‌ಪೋಸ್ಟ್‌ನಲ್ಲಿದ್ದೇವೆ."),
            ("emergency", "ತುರ್ತು ಎಚ್ಚರಿಕೆ, ಸೆಕ್ಟರ್ ಒಂಬತ್ತರಲ್ಲಿ ತಕ್ಷಣದ ವೈದ್ಯಕೀಯ ನೆರವಿನ ಅಗತ್ಯವಿದೆ."),
            ("radio", "ರೋಜರ್ ಬೇಸ್, ಚೆಕ್‌ಪಾಯಿಂಟ್ ಡೆಲ್ಟಾ ಕಡೆಗೆ ಚಲಿಸುತ್ತಿದ್ದೇವೆ, ಓವರ್.")
        ]
    },
    "or": {
        "lang_name": "Odia (MMS)",
        "wav_name": "or_native_test.wav",
        "model_dir": os.path.join(MODELS_DIR, "mms_ory"),
        "model_file": "model.onnx",
        "is_piper": False,
        "voice": "ory",
        "phrases": [
            ("greeting", "ନମସ୍କାର ଷ୍ଟେସନ ବେସ, ଏହା ସ୍କ୍ୱାଡ ଆଲଫା।"),
            ("normal", "ସମସ୍ତ ପାଟ୍ରୋଲିଂ ଚେକପୋଷ୍ଟ ସୁରକ୍ଷିତ ଥିବା ସୂଚନା ମିଳିଛି।"),
            ("numbers", "ଅକ୍ଷାଂଶ ବାର ଦଶମିକ ପାଞ୍ଚ, ଦ୍ରାଘିମା ସତସ୍ତରୀ ଦଶମିକ ଛଅ, ଉଚ୍ଚତା ତିନିଶହ ମିଟର।"),
            ("location", "ଆମେ ବିଲଡିଂ ଚାରି ନିକଟସ୍ଥ ଉତ୍ତର ସୀମା ଚେକପୋଷ୍ଟରେ ଅଛୁ।"),
            ("emergency", "ଜରୁରୀକାଳୀନ ଚେତାବନୀ, ସେକ୍ଟର ନଅରେ ତୁରନ୍ତ ଚିକିତ୍ସା ସହାୟତା ଆବଶ୍ୟକ।"),
            ("radio", "ରଜର ବେସ, ଚେକପଏଣ୍ଟ ଡେଲଟା ଆଡକୁ ଯାଉଛୁ, ଓଭର।")
        ]
    }
}

def save_wav(filepath, samples, sample_rate):
    with wave.open(filepath, "wb") as wf:
        wf.setnchannels(1)
        wf.setsampwidth(2)
        wf.setframerate(sample_rate)
        int_samples = (np.clip(samples, -1.0, 1.0) * 32767).astype(np.int16)
        wf.writeframes(int_samples.tobytes())

print("================================================================================")
print("PHASE 10A — TTS NATIVE QUALITY BENCHMARK ACROSS 10 LANGUAGES")
print("================================================================================")

results = []

for code, cfg in TEST_SUITE.items():
    print(f"\n>>> Auditing [{code.upper()}] {cfg['lang_name']} ({os.path.basename(cfg['model_dir'])})")
    model_path = os.path.join(cfg['model_dir'], cfg['model_file'])
    tokens_path = os.path.join(cfg['model_dir'], "tokens.txt")
    
    if not os.path.exists(model_path):
        print(f"    ERROR: Model file not found at {model_path}")
        continue
    
    t0 = time.perf_counter()
    vits_cfg = sherpa_onnx.OfflineTtsVitsModelConfig(
        model=model_path,
        tokens=tokens_path if os.path.exists(tokens_path) else "",
        data_dir=ESPEAK_DIR if cfg['is_piper'] else "",
        noise_scale=0.667,
        noise_scale_w=0.8,
        length_scale=1.0
    )
    model_cfg = sherpa_onnx.OfflineTtsModelConfig(vits=vits_cfg, num_threads=2)
    tts = sherpa_onnx.OfflineTts(sherpa_onnx.OfflineTtsConfig(model=model_cfg))
    load_time_ms = (time.perf_counter() - t0) * 1000
    print(f"    Model loaded in {load_time_ms:.1f}ms, Sample Rate: {tts.sample_rate}Hz")

    lang_samples = []
    total_synth_ms = 0.0
    total_dur_s = 0.0

    for cat, text in cfg['phrases']:
        t_synth0 = time.perf_counter()
        audio = tts.generate(text)
        synth_ms = (time.perf_counter() - t_synth0) * 1000
        dur_s = len(audio.samples) / audio.sample_rate if audio.sample_rate > 0 else 0
        rtf = (synth_ms / 1000.0) / dur_s if dur_s > 0 else 0
        
        total_synth_ms += synth_ms
        total_dur_s += dur_s
        print(f"    [{cat:9s}] {dur_s:.2f}s audio | synth: {synth_ms:.1f}ms | RTF: {rtf:.3f}")
        
        # Concatenate audio for debug wav (with 0.5s pause)
        silence = np.zeros(int(audio.sample_rate * 0.5), dtype=np.float32)
        lang_samples.extend(audio.samples)
        lang_samples.extend(silence)

    # Save single debug WAV containing all 6 category phrases
    out_wav_path = os.path.join(DEBUG_AUDIO_DIR, cfg['wav_name'])
    save_wav(out_wav_path, np.array(lang_samples, dtype=np.float32), tts.sample_rate)
    avg_rtf = (total_synth_ms / 1000.0) / total_dur_s if total_dur_s > 0 else 0
    print(f"    ✓ Debug WAV saved: {cfg['wav_name']} ({total_dur_s:.2f}s total audio, avg RTF: {avg_rtf:.3f})")

    results.append({
        "code": code,
        "name": cfg['lang_name'],
        "model": os.path.basename(cfg['model_dir']),
        "engine": "Piper VITS" if cfg['is_piper'] else "Meta MMS",
        "phonemizer": f"espeak-ng ({cfg['voice']})" if cfg['is_piper'] else "characters",
        "load_time_ms": load_time_ms,
        "total_dur_s": total_dur_s,
        "avg_rtf": avg_rtf,
        "wav": cfg['wav_name']
    })

print("\n" + "="*80)
print("SUMMARY TABLE: TTS NATIVE AUDIT")
print("="*80)
print(f"{'Code':4s} | {'Language':15s} | {'Model':22s} | {'Engine':10s} | {'Phonemizer':16s} | {'Load(ms)':8s} | {'Dur(s)':6s} | {'RTF':6s}")
print("-"*80)
for r in results:
    print(f"{r['code']:4s} | {r['name']:15s} | {r['model']:22s} | {r['engine']:10s} | {r['phonemizer']:16s} | {r['load_time_ms']:8.1f} | {r['total_dur_s']:6.2f} | {r['avg_rtf']:6.3f}")
print("="*80)
