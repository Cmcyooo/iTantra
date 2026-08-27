import os
import sys
import time
import sherpa_onnx
import wave
import numpy as np

if sys.platform == "win32":
    sys.stdout.reconfigure(encoding="utf-8")

BASE_DIR = os.path.dirname(__file__)
MODEL_DIR = os.path.join(BASE_DIR, "models", "mms_ory")
OUTPUT_DIR = os.path.join(BASE_DIR, "output")
os.makedirs(OUTPUT_DIR, exist_ok=True)

ODIA_PHRASES = [
    ("emergency_1", "ଜରୁରୀ ସତର୍କତା, ଚାରି ନମ୍ବର ସେକ୍ଟରରେ ତୁରନ୍ତ ସାହାଯ୍ୟ ଆବଶ୍ୟକ।"),
    ("emergency_2", "ରେଡ୍ ଆଲର୍ଟ, ସମସ୍ତ ଦଳ ସଦସ୍ୟ ତୁରନ୍ତ ସୁରକ୍ଷିତ ସ୍ଥାନକୁ ଯାଆନ୍ତୁ।"),
    ("location", "ଆମେ ଷ୍ଟେସନ ଆଲଫାରେ ଅଛୁ, ମୁଖ୍ୟ ଫାଟକରୁ ପଚାଶ ମିଟର ଉତ୍ତରକୁ।"),
    ("numbers", "ଦଳରେ ମୋଟ ବାର ଜଣ ସଦସ୍ୟ ଅଛନ୍ତି, ବ୍ୟାଟେରୀ ସ୍ତର ପଞ୍ଚସ୍ତରୀ ପ୍ରତିଶତ।"),
    ("short", "ରେଡିଓ ଯାଞ୍ଚ, ମୋର ସ୍ୱର ଆପଣଙ୍କୁ ସ୍ପଷ୍ଟ ଶୁଣାଯାଉଛି କି?")
]

def save_wav(filename, samples, sample_rate):
    with wave.open(filename, "wb") as wf:
        wf.setnchannels(1)
        wf.setsampwidth(2)
        wf.setframerate(sample_rate)
        int_samples = (np.clip(samples, -1.0, 1.0) * 32767).astype(np.int16)
        wf.writeframes(int_samples.tobytes())

def main():
    print("==========================================")
    print("Testing Meta MMS Odia (mms_ory)")
    print("==========================================")

    model_path = os.path.join(MODEL_DIR, "model.onnx")
    tokens_path = os.path.join(MODEL_DIR, "tokens.txt")

    vits_config = sherpa_onnx.OfflineTtsVitsModelConfig(
        model=model_path,
        tokens=tokens_path,
        data_dir="",
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

    for tag, text in ODIA_PHRASES:
        t_start = time.perf_counter()
        audio = tts.generate(text, sid=0)
        synth_ms = (time.perf_counter() - t_start) * 1000

        duration_s = len(audio.samples) / audio.sample_rate if audio.sample_rate > 0 else 0
        rtf = (synth_ms / 1000.0) / duration_s if duration_s > 0 else 0

        out_wav = os.path.join(OUTPUT_DIR, f"mms_ory_{tag}.wav")
        save_wav(out_wav, audio.samples, audio.sample_rate)

        print(f"[{tag}] '{text[:30]}...' -> {duration_s:.2f}s, Synth: {synth_ms:.1f}ms, RTF: {rtf:.3f}")

if __name__ == "__main__":
    main()
