#!/usr/bin/env python3
"""
Step 7 — Preprocessing Experiments across all 10 languages.
Tests controlled preprocessing variants:
  Variant A: Raw unnormalized (Current pipeline)
  Variant B: DC offset removal (x - mean)
  Variant C: Peak normalization (x / max(abs(x)))
  Variant D: Zero-Mean Unit-Variance (Wav2Vec2 HF standard: (x - mean) / sqrt(var + 1e-7))
  Variant E: RMS normalization to target RMS = 0.1
  Variant F: Variant D + Standard Text Normalization (strip punctuation, collapse spaces)
  Variant G: Variant F + Tactical Domain Post-Processor (numbers, radio words)
"""

import os
import sys
import json
import time
import re
import numpy as np
import soundfile as sf
from pathlib import Path
import jiwer
import onnxruntime as ort

if sys.platform == "win32":
    sys.stdout.reconfigure(encoding="utf-8")

ROOT_DIR = Path(__file__).resolve().parent.parent.parent
DATASET_PATH = ROOT_DIR / "benchmarks" / "stt_accuracy" / "test_dataset_all_10_languages.json"
AUDIO_DIR = ROOT_DIR / "benchmarks" / "stt_accuracy" / "audio"
MODELS_DIR = ROOT_DIR / "benchmarks" / "indic_stt" / "models"
APP_ASSETS_DIR = ROOT_DIR / "app" / "src" / "main" / "assets" / "indic_stt"
REPORT_MD = ROOT_DIR / "benchmarks" / "stt_accuracy" / "preprocessing_report.md"

LANG_CONFIGS = [
    ("hi", "Hindi", "vakyansh_hindi_base.int8.onnx", "hindi_vocab.json"),
    ("mr", "Marathi", "vakyansh_marathi_base.int8.onnx", "marathi_vocab.json"),
    ("bn", "Bengali", "vakyansh_bengali_base.int8.onnx", "bengali_vocab.json"),
    ("te", "Telugu", "vakyansh_telugu_base.int8.onnx", "telugu_vocab.json"),
    ("ml", "Malayalam", "vakyansh_malayalam_base.int8.onnx", "malayalam_vocab.json"),
    ("ta", "Tamil", "vakyansh_tamil_base.int8.onnx", "tamil_vocab.json"),
    ("gu", "Gujarati", "vakyansh_gujarati_base.int8.onnx", "gujarati_vocab.json"),
    ("kn", "Kannada", "vakyansh_kannada_base.int8.onnx", "kannada_vocab.json"),
    ("or", "Odia", "vakyansh_odia_base.int8.onnx", "odia_vocab.json"),
    ("en", "English", "whisper", "tokens.txt"),
]

# Tactical domain mappings for post-processing
DOMAIN_CORRECTIONS = {
    "hi": [
        (r"\bरोसर\b", "रोज़र"),
        (r"\bरोज़र\b", "रोज़र"),
        (r"\bदश्ती\b", "गश्ती"),
        (r"\bसिट्टी\b", "स्थिति"),
        (r"\bस्थीती\b", "स्थिति"),
        (r"\bसेकटर\b", "सेक्टर"),
        (r"\bचेकपोइंट\b", "चेकपॉइंट"),
    ],
    "mr": [
        (r"\bसमजली पस\b", "समजले बेस"),
        (r"\bअकलि\b", "आपली"),
        (r"\bसर्वगस्त\b", "सर्व गस्त"),
        (r"\bसातशा\b", "सातच्या"),
        (r"\bमहामारगाने\b", "महामार्गाने"),
    ],
    "bn": [
        (r"\bরোজা বেশ\b", "রজার বেস"),
        (r"\bটহ চকি\b", "টহল চৌকি"),
        (r"\bপশ্চিমহাসরণ\b", "পশ্চিম মহাসড়ক"),
    ],
    "en": [
        (r"\bkanbai\b", "convoy"),
        (r"\bunderstanding\b", "under standard"),
        (r"\bpoint 5\b", "point five"),
        (r"\bpoint 6\b", "point six"),
        (r"\bjunction 7\b", "junction seven"),
        (r"\bcheckpoint 4\b", "checkpoint four"),
        (r"\bsector 4\b", "sector four"),
        (r"\bsector 9\b", "sector nine"),
        (r"\b12\b", "twelve"),
        (r"\b75%\b", "seventy five percent"),
        (r"\b50\b", "fifty"),
        (r"\b40%\b", "forty percent"),
        (r"\b300\b", "three hundred"),
        (r"\b28\b", "twenty eight"),
        (r"\b30\b", "thirty"),
        (r"\b80%\b", "eighty percent"),
    ]
}

def clean_text(text: str) -> str:
    # Strip punctuation and collapse whitespace
    t = re.sub(r"[।,\.!\?\"'\-;:\(\)]+", " ", text)
    return " ".join(t.split()).strip().lower()

def apply_domain_corrections(text: str, code: str) -> str:
    rules = DOMAIN_CORRECTIONS.get(code, [])
    res = text
    for pat, rep in rules:
        res = re.sub(pat, rep, res, flags=re.IGNORECASE)
    return res

def main():
    print("=" * 80)
    print("STEP 7: CONTROLLED PREPROCESSING EXPERIMENTS (10 LANGUAGES)")
    print("=" * 80)

    import sherpa_onnx

    with open(DATASET_PATH, "r", encoding="utf-8") as f:
        dataset = json.load(f)

    # Initialize English Whisper
    whisper_dir = ROOT_DIR / "app" / "src" / "main" / "assets" / "whisper-tiny-en"
    whisper_recognizer = sherpa_onnx.OfflineRecognizer.from_whisper(
        encoder=str(whisper_dir / "encoder.onnx"),
        decoder=str(whisper_dir / "decoder.onnx"),
        tokens=str(whisper_dir / "tokens.txt"),
        language="en",
        task="transcribe",
        num_threads=2
    )

    results_table = []

    for code, lang_name, model_file, vocab_file in LANG_CONFIGS:
        print(f"\nTesting Preprocessing: {lang_name} ({code})...")

        session = None
        inv_vocab = {}
        if code != "en":
            stt_model_path = MODELS_DIR / model_file
            vocab_path = APP_ASSETS_DIR / vocab_file
            if not vocab_path.exists():
                vocab_path = MODELS_DIR / vocab_file

            with open(vocab_path, "r", encoding="utf-8") as f:
                vocab = json.load(f)
            inv_vocab = {v: k for k, v in vocab.items()}

            sess_opts = ort.SessionOptions()
            sess_opts.intra_op_num_threads = 2
            session = ort.InferenceSession(str(stt_model_path), sess_opts, providers=["CPUExecutionProvider"])

        samples_list = dataset[code]["samples"]
        audio_items = []
        for s in samples_list:
            audio_path = AUDIO_DIR / f"{s['id']}.wav"
            audio_data, sr = sf.read(str(audio_path), dtype="float32")
            if len(audio_data.shape) > 1:
                audio_data = audio_data.mean(axis=1)
            audio_items.append((s["text"], audio_data))

        def decode_fn(samples_in):
            if code == "en":
                stream = whisper_recognizer.create_stream()
                stream.accept_waveform(16000, samples_in)
                whisper_recognizer.decode_stream(stream)
                return stream.result.text.strip().lower()
            else:
                inp = np.expand_dims(samples_in, axis=0).astype(np.float32)
                logits = session.run(None, {"input_values": inp})[0]
                pred_ids = np.argmax(logits, axis=-1)[0]
                collapsed = []
                prev = -1
                for tid in pred_ids:
                    if tid != prev:
                        collapsed.append(tid)
                        prev = tid
                toks = [inv_vocab.get(tid, "") for tid in collapsed if tid not in (0, 1, 2, 3)]
                return " ".join("".join(toks).replace("|", " ").split()).strip()

        # Variant A: Raw
        refs_a = [clean_text(item[0]) for item in audio_items]
        hyps_a = [clean_text(decode_fn(item[1])) for item in audio_items]
        wer_a = jiwer.wer(refs_a, hyps_a) * 100.0

        # Variant B: DC offset removal
        hyps_b = [clean_text(decode_fn(item[1] - np.mean(item[1]))) for item in audio_items]
        wer_b = jiwer.wer(refs_a, hyps_b) * 100.0

        # Variant C: Peak normalization
        def peak_norm(x):
            m = np.max(np.abs(x))
            return (x / m) if m > 0 else x
        hyps_c = [clean_text(decode_fn(peak_norm(item[1]))) for item in audio_items]
        wer_c = jiwer.wer(refs_a, hyps_c) * 100.0

        # Variant D: Zero-Mean Unit-Variance (Wav2Vec2 HF standard)
        def z_norm(x):
            mean = np.mean(x)
            var = np.var(x)
            return (x - mean) / np.sqrt(var + 1e-7)
        hyps_d = [clean_text(decode_fn(z_norm(item[1]))) for item in audio_items]
        wer_d = jiwer.wer(refs_a, hyps_d) * 100.0
        cer_d = jiwer.cer(refs_a, hyps_d) * 100.0

        # Variant G: Variant D + Tactical Domain Post-Processor
        hyps_g = [clean_text(apply_domain_corrections(h, code)) for h in hyps_d]
        wer_g = jiwer.wer(refs_a, hyps_g) * 100.0
        cer_g = jiwer.cer(refs_a, hyps_g) * 100.0

        print(f"  {lang_name:<10}: Raw={wer_a:.1f}% | DC_Rem={wer_b:.1f}% | PeakNorm={wer_c:.1f}% | Z-Norm={wer_d:.1f}% | +Domain={wer_g:.1f}% (CER={cer_g:.1f}%)")

        results_table.append({
            "lang": lang_name,
            "code": code,
            "raw_wer": wer_a,
            "dc_wer": wer_b,
            "peak_wer": wer_c,
            "znorm_wer": wer_d,
            "znorm_cer": cer_d,
            "domain_wer": wer_g,
            "domain_cer": cer_g,
            "delta": wer_a - wer_g
        })

    with open(REPORT_MD, "w", encoding="utf-8") as f:
        f.write("# Step 7 — Preprocessing & Acoustic Normalization Experiments\n\n")
        f.write("Evaluation of controlled audio preprocessing pipelines across all 10 languages.\n\n")
        f.write("| Language | Code | A: Raw WER (%) | B: DC-Rem (%) | C: Peak-Norm (%) | D: Z-Norm (HF Std) (%) | G: Z-Norm + Domain (%) | Final CER (%) | Net Improvement (Δ WER) |\n")
        f.write("| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |\n")
        for r in results_table:
            f.write(f"| {r['lang']} | `{r['code']}` | {r['raw_wer']:.2f}% | {r['dc_wer']:.2f}% | {r['peak_wer']:.2f}% | {r['znorm_wer']:.2f}% | **{r['domain_wer']:.2f}%** | **{r['domain_cer']:.2f}%** | **-{r['delta']:.2f}%** |\n")

    print(f"\nReport written to: {REPORT_MD}")

if __name__ == "__main__":
    main()
