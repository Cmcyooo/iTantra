import os
import sys
import onnx
import sherpa_onnx

if sys.platform == "win32":
    sys.stdout.reconfigure(encoding="utf-8")

BASE_DIR = os.path.dirname(__file__)
MODELS_DIR = os.path.join(BASE_DIR, "models")
ESPEAK_DIR = os.path.abspath(os.path.join(BASE_DIR, "..", "..", "app", "src", "main", "assets", "tts-en-amy", "espeak-ng-data"))

MODELS = [
    ("English", "vits-piper-en_US-amy-low", os.path.join(BASE_DIR, "..", "..", "app", "src", "main", "assets", "tts-en-amy")),
    ("Hindi", "piper_hi_priyamvada", os.path.join(MODELS_DIR, "piper_hi_priyamvada")),
    ("Marathi", "piper_mr_google", os.path.join(MODELS_DIR, "piper_mr_google")),
    ("Bengali", "piper_bn_google", os.path.join(MODELS_DIR, "piper_bn_google")),
    ("Telugu", "piper_te_maya", os.path.join(MODELS_DIR, "piper_te_maya")),
    ("Malayalam", "piper_ml_meera", os.path.join(MODELS_DIR, "piper_ml_meera")),
    ("Tamil", "piper_ta_rasa_female", os.path.join(MODELS_DIR, "piper_ta_rasa_female")),
    ("Gujarati (MMS)", "mms_guj", os.path.join(MODELS_DIR, "mms_guj")),
    ("Kannada (MMS)", "mms_kan", os.path.join(MODELS_DIR, "mms_kan")),
    ("Odia (MMS)", "mms_ory", os.path.join(MODELS_DIR, "mms_ory")),
]

TEST_PHRASES = {
    "English": "Emergency alert, need immediate assistance at sector four.",
    "Hindi": "आपातकालीन चेतावनी, सेक्टर चार में तुरंत सहायता की आवश्यकता है।",
    "Marathi": "तातडीची सूचना, सेक्टर चारमध्ये त्वरित मदतीची आवश्यकता आहे.",
    "Bengali": "জরুরি সতর্কতা, সেক্টর চারে অবিলম্বে সহায়তার প্রয়োজন।",
    "Telugu": "అత్యవసర హెచ్చరిక, సెక్టార్ నాలుగులో తక్షణ సహాయం అవసరం.",
    "Malayalam": "അടിയന്തിര മുന്നറിയിപ്പ്, സെക്ടർ നാലിൽ അടിയന്തിര സഹായം ആവശ്യമാണ്.",
    "Tamil": "அவசர எச்சரிக்கை, பிரிவு நான்கில் உடனடி உதவி தேவைப்படுகிறது.",
    "Gujarati (MMS)": "કટોકટી ચેતવણી, સેક્ટર ચારમાં તાત્કાલિક સહાયની જરૂર છે.",
    "Kannada (MMS)": "ತುರ್ತು ಎಚ್ಚರಿಕೆ, ಸೆಕ್ಟರ್ ನಾಲ್ಕರಲ್ಲಿ ತಕ್ಷಣದ ಸಹಾಯದ ಅಗತ್ಯವಿದೆ.",
    "Odia (MMS)": "ଜରୁରୀକାଳୀନ ଚେତାବନୀ, ସେକ୍ଟର ଚାରିରେ ତୁରନ୍ତ ସହାୟତା ଆବଶ୍ୟକ।"
}

print("=== AUDITING ONNX METADATA & CONFIGURATIONS ===")
for lang, name, path in MODELS:
    onnx_file = os.path.join(path, "model.onnx") if os.path.exists(os.path.join(path, "model.onnx")) else os.path.join(path, "en_US-amy-low.onnx")
    if not os.path.exists(onnx_file):
        print(f"[{lang}] Model not found: {onnx_file}")
        continue
    
    model = onnx.load(onnx_file, load_external_data=False)
    props = {p.key: p.value for p in model.metadata_props}
    print(f"\n--- {lang} ({name}) ---")
    print(f"  ONNX File: {os.path.basename(onnx_file)} ({os.path.getsize(onnx_file)/(1024*1024):.2f} MB)")
    print(f"  Metadata Props: {props}")
    
    # Check tokens.txt
    tokens_file = os.path.join(path, "tokens.txt")
    if os.path.exists(tokens_file):
        with open(tokens_file, "r", encoding="utf-8") as f:
            tokens_count = len(f.readlines())
        print(f"  Tokens: {tokens_count} tokens in tokens.txt")
    else:
        print(f"  Tokens: NO tokens.txt found!")

    # Test loading in sherpa-onnx
    is_piper = "mms" not in name
    vits_cfg = sherpa_onnx.OfflineTtsVitsModelConfig(
        model=onnx_file,
        tokens=tokens_file if os.path.exists(tokens_file) else "",
        data_dir=ESPEAK_DIR if is_piper else "",
        noise_scale=0.667,
        noise_scale_w=0.8,
        length_scale=1.0
    )
    tts_cfg = sherpa_onnx.OfflineTtsConfig(model=sherpa_onnx.OfflineTtsModelConfig(vits=vits_cfg, num_threads=2))
    try:
        tts = sherpa_onnx.OfflineTts(tts_cfg)
        text = TEST_PHRASES[lang]
        audio = tts.generate(text)
        dur = len(audio.samples) / audio.sample_rate
        print(f"  Generation: SUCCESS ({dur:.2f}s audio at {audio.sample_rate}Hz, samples={len(audio.samples)})")
    except Exception as e:
        print(f"  Generation: FAILED: {e}")
