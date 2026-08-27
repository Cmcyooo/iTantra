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

TELUGU_PHRASES = [
    ("emergency_1", "అత్యవసర హెచ్చరిక, సెక్టార్ నాలుగులో తక్షణ సహాయం అవసరం."),
    ("emergency_2", "రెడ్ అలర్ట్, బృందం సభ్యులందరూ వెంటనే సురక్షిత ప్రాంతానికి చేరుకోండి."),
    ("location", "మేము స్టేషన్ ఆల్ఫా వద్ద ఉన్నాము, ప్రధాన ద్వారానికి ఉత్తరంగా యాభై మీటర్ల దూరంలో."),
    ("numbers", "బృందంలో పన్నెండు మంది సభ్యులు ఉన్నారు, బ్యాటరీ డెబ్బై ఐదు శాతం ఉంది."),
    ("short", "రేడియో తనిఖీ, నా స్వరం మీకు స్పష్టంగా వినిపిస్తుందా?")
]

KANNADA_PHRASES = [
    ("emergency_1", "ತುರ್ತು ಎಚ್ಚರಿಕೆ, ಸೆಕ್ಟರ್ ನಾಲ್ಕರಲ್ಲಿ ತಕ್ಷಣದ ಸಹಾಯದ ಅಗತ್ಯವಿದೆ."),
    ("emergency_2", "ರೆಡ್ ಅಲರ್ಟ್, ಎಲ್ಲಾ ತಂಡದ ಸದಸ್ಯರು ತಕ್ಷಣ ಸುರಕ್ಷಿತ ಸ್ಥಳಕ್ಕೆ ತೆರಳಿ."),
    ("location", "ನಾವು ಸ್ಟೇಷನ್ ಆಲ್ಫಾದಲ್ಲಿದ್ದೇವೆ, ಮುಖ್ಯ ದ್ವಾರದಿಂದ ಐವತ್ತು ಮೀಟರ್ ಉತ್ತರಕ್ಕೆ."),
    ("numbers", "ತಂಡದಲ್ಲಿ ಒಟ್ಟು ಹನ್ನೆರಡು ಸದಸ್ಯರಿದ್ದಾರೆ, ಬ್ಯಾಟರಿ ಮಟ್ಟ ಎಪ್ಪತ್ತೈದು ಪ್ರತಿಶತ ಇದೆ."),
    ("short", "ರೇಡಿಯೋ ಪರಿಶೀಲನೆ, ನನ್ನ ಧ್ವನಿ ನಿಮಗೆ ಕೇಳಿಸುತ್ತಿದೆಯೇ?")
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
    test_model("piper_te_maya", True, TELUGU_PHRASES)
    test_model("piper_te_venkatesh", True, TELUGU_PHRASES)
    test_model("mms_tel", False, TELUGU_PHRASES)
    test_model("mms_kan", False, KANNADA_PHRASES)
