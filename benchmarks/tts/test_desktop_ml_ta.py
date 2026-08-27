import os
import sys
import time
import sherpa_onnx
import wave
import numpy as np

if sys.platform == "win32":
    sys.stdout.reconfigure(encoding="utf-8")

BASE_DIR = os.path.dirname(__file__)
MODELS_DIR = os.path.join(BASE_DIR, "models")
ESPEAK_DIR = os.path.abspath(os.path.join(BASE_DIR, "..", "..", "app", "src", "main", "assets", "tts-en-amy", "espeak-ng-data"))
OUTPUT_DIR = os.path.join(BASE_DIR, "output")
os.makedirs(OUTPUT_DIR, exist_ok=True)

MALAYALAM_PHRASES = [
    ("emergency_1", "അടിയന്തര മുന്നറിയിപ്പ്, സെക്ടർ നാലിൽ അടിയന്തര സഹായം ആവശ്യമാണ്."),
    ("emergency_2", "റെഡ് അലേർട്ട്, എല്ലാ ടീം അംഗങ്ങളും ഉടൻ സുരക്ഷിത സ്ഥാനത്തേക്ക് മാറുക."),
    ("location", "ഞങ്ങൾ സ്റ്റേഷൻ ആൽഫയിലാണ്, പ്രധാന കവാടത്തിൽ നിന്ന് അമ്പത് മീറ്റർ വടക്കോട്ട്."),
    ("numbers", "ടീമിൽ പന്ത്രണ്ട് അംഗങ്ങളുണ്ട്, ബാറ്ററി നില എഴുപത്തിയഞ്ച് ശതമാനമാണ്."),
    ("short", "റേഡിയോ പരിശോധന, എന്റെ ശബ്ദം വ്യക്തമായി കേൾക്കുന്നുണ്ടോ?")
]

TAMIL_PHRASES = [
    ("emergency_1", "அவசர எச்சரிக்கை, பிரிவு நான்கில் உடனடி உதவி தேவைப்படுகிறது."),
    ("emergency_2", "ரெட் அலர்ட், அனைத்து குழு உறுப்பினர்களும் உடனடியாக பாதுகாப்பான இடத்திற்கு செல்லவும்."),
    ("location", "நாங்கள் ஸ்டேஷன் ஆல்பாவில் இருக்கிறோம், பிரதான வாயிலில் இருந்து ஐம்பது மீட்டர் வடக்கே."),
    ("numbers", "குழுவில் பன்னிரண்டு உறுப்பினர்கள் உள்ளனர், பேட்டரி எழுபத்தைந்து சதவீதம் உள்ளது."),
    ("short", "ரேடியோ சோதனை, என் குரல் உங்களுக்கு தெளிவாக கேட்கிறதா?")
]

def save_wav(filename, samples, sample_rate):
    with wave.open(filename, "wb") as wf:
        wf.setnchannels(1)
        wf.setsampwidth(2)
        wf.setframerate(sample_rate)
        int_samples = (np.clip(samples, -1.0, 1.0) * 32767).astype(np.int16)
        wf.writeframes(int_samples.tobytes())

def test_model(model_name, is_piper, phrases):
    print(f"\n==========================================")
    print(f"Testing Model: {model_name} (Piper: {is_piper})")
    print(f"==========================================")
    model_dir = os.path.join(MODELS_DIR, model_name)
    model_path = os.path.join(model_dir, "model.onnx")
    tokens_path = os.path.join(model_dir, "tokens.txt")

    vits_config = sherpa_onnx.OfflineTtsVitsModelConfig(
        model=model_path,
        tokens=tokens_path,
        data_dir=ESPEAK_DIR if is_piper else "",
        noise_scale=0.667,
        noise_scale_w=0.8,
        length_scale=1.0
    )
    model_config = sherpa_onnx.OfflineTtsModelConfig(
        vits=vits_config,
        num_threads=2,
        debug=False,
        provider="cpu"
    )
    tts_config = sherpa_onnx.OfflineTtsConfig(model=model_config)

    t0 = time.perf_counter()
    tts = sherpa_onnx.OfflineTts(tts_config)
    load_time_ms = (time.perf_counter() - t0) * 1000
    print(f"Model load time: {load_time_ms:.2f} ms, Sample Rate: {tts.sample_rate}")

    for tag, text in phrases:
        t_start = time.perf_counter()
        audio = tts.generate(text)
        synth_ms = (time.perf_counter() - t_start) * 1000

        duration_s = len(audio.samples) / audio.sample_rate if audio.sample_rate > 0 else 0
        rtf = (synth_ms / 1000.0) / duration_s if duration_s > 0 else 0

        out_wav = os.path.join(OUTPUT_DIR, f"{model_name}_{tag}.wav")
        save_wav(out_wav, audio.samples, audio.sample_rate)

        print(f"[{tag}] '{text[:30]}...' -> {duration_s:.2f}s, Synth: {synth_ms:.1f}ms, RTF: {rtf:.3f}")

if __name__ == "__main__":
    test_model("piper_ml_meera", True, MALAYALAM_PHRASES)
    test_model("piper_ml_arjun", True, MALAYALAM_PHRASES)
    test_model("piper_ta_roja", True, TAMIL_PHRASES)
    test_model("piper_ta_rasa_male", True, TAMIL_PHRASES)
    test_model("mms_mal", False, MALAYALAM_PHRASES)
    test_model("mms_tam", False, TAMIL_PHRASES)
