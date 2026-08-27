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

BENGALI_PHRASES = [
    ("emergency_1", "জরুরী সতর্কতা, চার নম্বর সেক্টরে অবিলম্বে সহায়তা প্রয়োজন।"),
    ("emergency_2", "রেড অ্যালার্ট, দলের সকল সদস্য অবিলম্বে নিরাপদ স্থানে সরে যান।"),
    ("location", "আমরা স্টেশন আলফাতে আছি, প্রধান ফটক থেকে পঞ্চাশ মিটার উত্তরে।"),
    ("numbers", "দলে মোট বারো জন সদস্য আছেন, ব্যাটারি স্তর পঁচাত্তর শতাংশ।"),
    ("short", "রেডিও চেক, আমার কথা কি পরিষ্কার শোনা যাচ্ছে?")
]

MARATHI_PHRASES = [
    ("emergency_1", "तातडीचा इशारा, सेक्टर चारमध्ये तातडीने मदतीची गरज आहे."),
    ("emergency_2", "रेड अलर्ट, सर्व संघ सदस्यांनी त्वरित सुरक्षित स्थळी जावे."),
    ("location", "आम्ही स्टेशन अल्फा येथे आहोत, मुख्य गेटपासून पन्नास मीटर उत्तरेकडे."),
    ("numbers", "संघामध्ये एकूण बारा सदस्य आहेत, बॅटरी पातळी पंच्याहत्तर टक्के आहे."),
    ("short", "रेडिओ चेक, माझा आवाज तुम्हाला स्पष्ट येत आहे का?")
]

def save_wav(filename, samples, sample_rate):
    with wave.open(filename, "wb") as wf:
        wf.setnchannels(1)
        wf.setsampwidth(2)
        wf.setframerate(sample_rate)
        int_samples = (np.clip(samples, -1.0, 1.0) * 32767).astype(np.int16)
        wf.writeframes(int_samples.tobytes())

def test_model(model_name, is_piper, phrases, sid=0):
    print(f"\n==========================================")
    print(f"Testing Model: {model_name} (Piper: {is_piper}, sid: {sid})")
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
        audio = tts.generate(text, sid=sid)
        synth_ms = (time.perf_counter() - t_start) * 1000

        duration_s = len(audio.samples) / audio.sample_rate if audio.sample_rate > 0 else 0
        rtf = (synth_ms / 1000.0) / duration_s if duration_s > 0 else 0

        out_wav = os.path.join(OUTPUT_DIR, f"{model_name}_{tag}.wav")
        save_wav(out_wav, audio.samples, audio.sample_rate)

        print(f"[{tag}] '{text[:30]}...' -> {duration_s:.2f}s, Synth: {synth_ms:.1f}ms, RTF: {rtf:.3f}")

if __name__ == "__main__":
    test_model("piper_bn_google", True, BENGALI_PHRASES, sid=0)
    test_model("piper_mr_google", True, MARATHI_PHRASES, sid=0)
    test_model("mms_ben", False, BENGALI_PHRASES)
    test_model("mms_mar", False, MARATHI_PHRASES)
