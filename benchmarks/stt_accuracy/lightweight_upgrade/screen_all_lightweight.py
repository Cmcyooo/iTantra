#!/usr/bin/env python3
"""
Phase 10D: Desktop Screening for Lightweight (50M-150M) STT Candidates
across Bengali, Malayalam, Marathi, and Odia.
Measures WER, CER, Latency, and RTF against the standardized 20-utterance dataset.
"""

import sys
import time
import json
import torch
import numpy as np
import soundfile as sf
from pathlib import Path
import jiwer
from transformers import Wav2Vec2ForCTC, Wav2Vec2Processor

if sys.platform == "win32":
    sys.stdout.reconfigure(encoding="utf-8")

ROOT_DIR = Path(__file__).resolve().parent.parent.parent.parent
DATASET_PATH = ROOT_DIR / "benchmarks" / "stt_accuracy" / "test_dataset_all_10_languages.json"
AUDIO_DIR = ROOT_DIR / "benchmarks" / "stt_accuracy" / "audio"

CANDIDATES = [
    # Bengali
    ("bn", "Bengali", "bangla-speech-processing/BanglaASR", "BanglaASR Base (95M)"),
    ("bn", "Bengali", "ai4bharat/indicwav2vec_v1_bengali", "IndicWav2Vec Bengali (95M)"),
    ("bn", "Bengali", "addy88/wav2vec2-bengali-stt", "Addy88 Bengali Base (95M)"),
    # Malayalam
    ("ml", "Malayalam", "Bluecast/wav2vec2-Malayalam", "Bluecast Malayalam Base (95M)"),
    ("ml", "Malayalam", "addy88/wav2vec2-malayalam-stt", "Addy88 Malayalam Base (95M)"),
    # Marathi
    ("mr", "Marathi", "addy88/wav2vec2-marathi-stt", "Addy88 Marathi Base (95M)"),
    # Odia
    ("or", "Odia", "addy88/wav2vec-odia-stt", "Addy88 Odia Base (95M)")
]

def benchmark_candidate(lang_code, lang_name, model_id, desc, samples):
    print(f"\n>>> Benchmarking {lang_name} Candidate: {desc} ({model_id})...")
    try:
        processor = Wav2Vec2Processor.from_pretrained(model_id)
        model = Wav2Vec2ForCTC.from_pretrained(model_id)
        model.eval()

        vocab = processor.tokenizer.get_vocab()
        inv_vocab = {v: k for k, v in vocab.items()}
        pad_id = processor.tokenizer.pad_token_id

        refs = []
        hyps = []
        durations = []
        latencies = []

        for s in samples:
            audio_path = AUDIO_DIR / f"{s['id']}.wav"
            speech, sr = sf.read(str(audio_path), dtype="float32")
            if len(speech.shape) > 1:
                speech = speech.mean(axis=1)

            dur_s = len(speech) / sr
            durations.append(dur_s)

            speech_norm = (speech - np.mean(speech)) / np.sqrt(np.var(speech) + 1e-7)
            inp = torch.tensor(speech_norm, dtype=torch.float32).unsqueeze(0)

            t0 = time.perf_counter()
            with torch.no_grad():
                logits = model(inp).logits
                pred_ids = torch.argmax(logits, dim=-1)[0].numpy()
            t1 = time.perf_counter()

            infer_ms = (t1 - t0) * 1000.0
            latencies.append(infer_ms)

            collapsed = []
            prev = -1
            for tid in pred_ids:
                if tid != prev:
                    collapsed.append(tid)
                    prev = tid

            tokens = [inv_vocab.get(tid, "") for tid in collapsed if tid != pad_id]
            hyp = "".join(tokens).replace("|", " ").replace("<s>", "").replace("</s>", "").replace("<unk>", "").strip()
            hyp = " ".join(hyp.split())

            refs.append(s["text"])
            hyps.append(hyp)

        wer = jiwer.wer(refs, hyps) * 100.0
        cer = jiwer.cer(refs, hyps) * 100.0
        avg_dur = float(np.mean(durations))
        avg_lat = float(np.mean(latencies))
        rtf = avg_lat / (avg_dur * 1000.0)

        print(f"    --> WER: {wer:.2f}% | CER: {cer:.2f}% | Latency: {avg_lat:.1f}ms | RTF: {rtf:.3f}")
        for i in range(2):
            print(f"      Sample {i}:")
            print(f"        REF: {refs[i]}")
            print(f"        HYP: {hyps[i]}")

        return {
            "lang": lang_code,
            "model_id": model_id,
            "desc": desc,
            "wer": wer,
            "cer": cer,
            "lat": avg_lat,
            "rtf": rtf,
            "status": "OK"
        }
    except Exception as e:
        print(f"    [FAILED] Error evaluating {model_id}: {e}")
        return {
            "lang": lang_code,
            "model_id": model_id,
            "desc": desc,
            "wer": 999.0,
            "cer": 999.0,
            "lat": 0.0,
            "rtf": 0.0,
            "status": f"FAILED: {e}"
        }

def main():
    print("=" * 80)
    print("PHASE 10D: LIGHTWEIGHT CANDIDATE SCREENING")
    print("=" * 80)

    with open(DATASET_PATH, "r", encoding="utf-8") as f:
        dataset = json.load(f)

    results = []
    for lang_code, lang_name, model_id, desc in CANDIDATES:
        samples = dataset[lang_code]["samples"]
        res = benchmark_candidate(lang_code, lang_name, model_id, desc, samples)
        results.append(res)

    print("\n" + "=" * 80)
    print("LIGHTWEIGHT CANDIDATE SCREENING SUMMARY")
    print("=" * 80)
    print(f"{'Language':<12} | {'Model':<35} | {'WER (%)':<10} | {'CER (%)':<10} | {'RTF':<8}")
    print("-" * 80)
    for r in results:
        if r["status"] == "OK":
            print(f"{r['lang']:<12} | {r['desc']:<35} | {r['wer']:<10.2f} | {r['cer']:<10.2f} | {r['rtf']:<8.3f}")
        else:
            print(f"{r['lang']:<12} | {r['desc']:<35} | FAILED")

if __name__ == "__main__":
    main()
