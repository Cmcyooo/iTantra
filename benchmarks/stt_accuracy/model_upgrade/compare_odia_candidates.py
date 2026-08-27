#!/usr/bin/env python3
"""
Desktop screening of Odia STT candidates on the standardized 20 Odia tactical utterances.
Candidates:
  1. Harveenchadha/odia_large_wav2vec2 (Wav2Vec2 Large)
  2. anuragshas/wav2vec2-large-xlsr-53-odia (XLSR-53 Odia)
  3. Harveenchadha/vakyansh-wav2vec2-odia-orm-100 (Baseline)
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
    ("Harveenchadha/odia_large_wav2vec2", "Vakyansh Large Odia"),
    ("anuragshas/wav2vec2-large-xlsr-53-odia", "XLSR-53 Odia"),
    ("infinitejoy/wav2vec2-large-xls-r-300m-odia", "XLS-R 300M Odia")
]

def main():
    print("=" * 80)
    print("SCREENING ODIA STT CANDIDATES")
    print("=" * 80)

    with open(DATASET_PATH, "r", encoding="utf-8") as f:
        dataset = json.load(f)
    samples = dataset["or"]["samples"]

    # Preload audio
    audio_items = []
    for s in samples:
        audio_path = AUDIO_DIR / f"{s['id']}.wav"
        speech, sr = sf.read(str(audio_path), dtype="float32")
        if len(speech.shape) > 1:
            speech = speech.mean(axis=1)
        audio_items.append((s["text"], speech))

    for model_id, name in CANDIDATES:
        print(f"\n>>> Evaluating Candidate: {name} ({model_id})...")
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

            for ref_text, speech in audio_items:
                dur_s = len(speech) / 16000.0
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

                refs.append(ref_text)
                hyps.append(hyp)

            wer = jiwer.wer(refs, hyps) * 100.0
            cer = jiwer.cer(refs, hyps) * 100.0
            avg_dur = float(np.mean(durations))
            avg_lat = float(np.mean(latencies))
            rtf = avg_lat / (avg_dur * 1000.0)

            print(f"    --> WER: {wer:.2f}% | CER: {cer:.2f}% | Latency: {avg_lat:.1f}ms | RTF: {rtf:.3f}")
            for i in range(2):
                print(f"    Sample {i}:")
                print(f"      REF: {refs[i]}")
                print(f"      HYP: {hyps[i]}")
        except Exception as e:
            print(f"    [ERROR] Failed to evaluate {model_id}: {e}")

if __name__ == "__main__":
    main()
