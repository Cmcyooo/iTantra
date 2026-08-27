import os
import sys
import time
import sherpa_onnx
import wave

if sys.platform == "win32":
    sys.stdout.reconfigure(encoding="utf-8")

BASE_DIR = os.path.dirname(__file__)
MODELS_DIR = os.path.join(BASE_DIR, "models")
ESPEAK_DIR = os.path.abspath(os.path.join(BASE_DIR, "..", "..", "app", "src", "main", "assets", "tts-en-amy", "espeak-ng-data"))
OUTPUT_DIR = os.path.join(BASE_DIR, "output")
os.makedirs(OUTPUT_DIR, exist_ok=True)

# Test phrases
HINDI_PHRASES = [
    ("emergency_1", "आपातकालीन चेतावनी, सेक्टर चार में तुरंत सहायता की आवश्यकता है।"),
    ("emergency_2", "रेड अलर्ट, सभी टीमें सुरक्षित स्थान पर पीछे हटें।"),
    ("location", "हम स्टेशन अल्फा पर हैं, मुख्य द्वार से पचास मीटर उत्तर की ओर।"),
    ("numbers", "टीम में कुल बारह सदस्य हैं, बैटरी स्तर पचहत्तर प्रतिशत है।"),
    ("short", "रेडियो चेक, क्या आपको मेरी आवाज़ आ रही है?")
]

GUJARATI_PHRASES = [
    ("emergency_1", "કટોકટી ચેતવણી, સેક્ટર ચારમાં તાત્કાલિક સહાયની જરૂર છે."),
    ("emergency_2", "રેડ એલર્ટ, બધા સભ્યો તાત્કાલિક સુરક્ષિત સ્થળે પહોંચો."),
    ("location", "અમે સ્ટેશન આલ્ફા પર છીએ, મુખ્ય ગેટથી પચાસ મીટર ઉત્તરમાં."),
    ("numbers", "ટીમમાં બાર સભ્યો છે, બેટરી પંચોતેર ટકા છે."),
    ("short", "રેડિયો ચેક, શું તમને મારો અવાજ સંભળાય છે?")
]

def save_wav(filename, samples, sample_rate):
    with wave.open(filename, "wb") as wf:
        wf.setnchannels(1)
        wf.setsampwidth(2) # 16-bit
        wf.setframerate(sample_rate)
        # Convert float32 [-1, 1] to int16
        import numpy as np
        int_samples = (np.clip(samples, -1.0, 1.0) * 32767).astype(np.int16)
        wf.writeframes(int_samples.tobytes())

def test_piper_hindi(model_name):
    print(f"\n==========================================")
    print(f"Testing Piper Hindi Model: {model_name}")
    print(f"==========================================")
    model_dir = os.path.join(MODELS_DIR, model_name)
    model_path = os.path.join(model_dir, "model.onnx")
    tokens_path = os.path.join(model_dir, "tokens.txt")
    
    # If tokens.txt doesn't exist for piper, piper uses embedded tokens from onnx.json
    # In sherpa-onnx OfflineTtsVitsModelConfig:
    vits_config = sherpa_onnx.OfflineTtsVitsModelConfig(
        model=model_path,
        tokens=tokens_path if os.path.exists(tokens_path) else "",
        data_dir=ESPEAK_DIR,
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

    for tag, text in HINDI_PHRASES:
        t_start = time.perf_counter()
        audio = tts.generate(text)
        synth_ms = (time.perf_counter() - t_start) * 1000
        
        duration_s = len(audio.samples) / audio.sample_rate if audio.sample_rate > 0 else 0
        rtf = (synth_ms / 1000.0) / duration_s if duration_s > 0 else 0
        
        out_wav = os.path.join(OUTPUT_DIR, f"{model_name}_{tag}.wav")
        save_wav(out_wav, audio.samples, audio.sample_rate)
        
        print(f"[{tag}] '{text}'")
        print(f"  Duration: {duration_s:.2f}s, Synth: {synth_ms:.1f}ms, RTF: {rtf:.3f}, Saved: {os.path.basename(out_wav)}")

def test_mms(lang, model_dir_name, phrases):
    print(f"\n==========================================")
    print(f"Testing Meta MMS VITS Model: {lang} ({model_dir_name})")
    print(f"==========================================")
    model_dir = os.path.join(MODELS_DIR, model_dir_name)
    model_path = os.path.join(model_dir, "model.onnx")
    tokens_path = os.path.join(model_dir, "tokens.txt")
    
    vits_config = sherpa_onnx.OfflineTtsVitsModelConfig(
        model=model_path,
        tokens=tokens_path,
        data_dir="", # MMS doesn't use espeak-ng
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
    
    try:
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
            
            out_wav = os.path.join(OUTPUT_DIR, f"{model_dir_name}_{tag}.wav")
            save_wav(out_wav, audio.samples, audio.sample_rate)
            
            print(f"[{tag}] '{text}'")
            print(f"  Duration: {duration_s:.2f}s, Synth: {synth_ms:.1f}ms, RTF: {rtf:.3f}, Saved: {os.path.basename(out_wav)}")
    except Exception as e:
        print(f"Error testing MMS model {model_dir_name}: {e}")

if __name__ == "__main__":
    try:
        test_piper_hindi("piper_hi_priyamvada")
    except Exception as e:
        print("Piper Priyamvada failed:", e)

    try:
        test_piper_hindi("piper_hi_rohan")
    except Exception as e:
        print("Piper Rohan failed:", e)

    test_mms("Hindi", "mms_hin", HINDI_PHRASES)
    test_mms("Gujarati", "mms_guj", GUJARATI_PHRASES)
