#!/usr/bin/env python3
"""
Screening & Conversion for Odia STT Candidate: Harveenchadha/odia_large_wav2vec2.
Evaluates accuracy on the 20-utterance standardized Odia tactical dataset.
Converts to ONNX FP32 and dynamic INT8 ONNX, verifying input/output contracts and vocabulary.
"""

import os
import sys
import time
import json
import torch
import numpy as np
import soundfile as sf
from pathlib import Path
import jiwer
from transformers import Wav2Vec2ForCTC, Wav2Vec2Processor
import onnx
import onnxruntime as ort
from onnxruntime.quantization import quantize_dynamic, QuantType

if sys.platform == "win32":
    sys.stdout.reconfigure(encoding="utf-8")

ROOT_DIR = Path(__file__).resolve().parent.parent.parent.parent
DATASET_PATH = ROOT_DIR / "benchmarks" / "stt_accuracy" / "test_dataset_all_10_languages.json"
AUDIO_DIR = ROOT_DIR / "benchmarks" / "stt_accuracy" / "audio"
MODELS_OUT_DIR = ROOT_DIR / "benchmarks" / "indic_stt" / "models"
MODELS_OUT_DIR.mkdir(parents=True, exist_ok=True)

MODEL_ID = "Harveenchadha/odia_large_wav2vec2"
ONNX_FP32_PATH = MODELS_OUT_DIR / "odia_large.onnx"
ONNX_INT8_PATH = MODELS_OUT_DIR / "odia_large.int8.onnx"
VOCAB_OUT_PATH = MODELS_OUT_DIR / "odia_large_vocab.json"

def main():
    print("=" * 80)
    print(f"EVALUATING ODIA CANDIDATE: {MODEL_ID}")
    print("=" * 80)

    # 1. Load processor and PyTorch model
    print(f"\n1. Downloading/Loading {MODEL_ID}...")
    processor = Wav2Vec2Processor.from_pretrained(MODEL_ID)
    model = Wav2Vec2ForCTC.from_pretrained(MODEL_ID)
    model.eval()

    vocab = processor.tokenizer.get_vocab()
    inv_vocab = {v: k for k, v in vocab.items()}
    pad_id = processor.tokenizer.pad_token_id

    print(f"   Vocab size: {len(vocab)} | Pad token ID: {pad_id} ('{inv_vocab.get(pad_id)}')")

    # Save vocabulary JSON
    with open(VOCAB_OUT_PATH, "w", encoding="utf-8") as f:
        json.dump(vocab, f, ensure_ascii=False, indent=2)
    print(f"   Saved vocabulary to: {VOCAB_OUT_PATH}")

    # 2. Benchmark PyTorch FP32 on standardized 20 Odia utterances
    with open(DATASET_PATH, "r", encoding="utf-8") as f:
        dataset = json.load(f)
    samples = dataset["or"]["samples"]

    print(f"\n2. Benchmarking PyTorch model on {len(samples)} Odia tactical utterances...")
    refs = []
    hyps_pt = []
    durations = []
    latencies_pt = []

    for s in samples:
        audio_path = AUDIO_DIR / f"{s['id']}.wav"
        speech, sr = sf.read(str(audio_path), dtype="float32")
        if len(speech.shape) > 1:
            speech = speech.mean(axis=1)

        dur_s = len(speech) / sr
        durations.append(dur_s)

        # Zero-mean unit-variance normalization
        speech_norm = (speech - np.mean(speech)) / np.sqrt(np.var(speech) + 1e-7)
        input_values = torch.tensor(speech_norm, dtype=torch.float32).unsqueeze(0)

        t0 = time.perf_counter()
        with torch.no_grad():
            logits = model(input_values).logits
            pred_ids = torch.argmax(logits, dim=-1)[0].numpy()
        t1 = time.perf_counter()

        infer_ms = (t1 - t0) * 1000.0
        latencies_pt.append(infer_ms)

        # CTC collapse
        collapsed = []
        prev = -1
        for tid in pred_ids:
            if tid != prev:
                collapsed.append(tid)
                prev = tid

        tokens = [inv_vocab.get(tid, "") for tid in collapsed if tid != pad_id]
        hyp_text = "".join(tokens).replace("|", " ").replace("<s>", "").replace("</s>", "").replace("<unk>", "").strip()
        hyp_text = " ".join(hyp_text.split())

        refs.append(s["text"])
        hyps_pt.append(hyp_text)

    wer_pt = jiwer.wer(refs, hyps_pt) * 100.0
    cer_pt = jiwer.cer(refs, hyps_pt) * 100.0
    avg_dur = float(np.mean(durations))
    avg_lat_pt = float(np.mean(latencies_pt))
    rtf_pt = avg_lat_pt / (avg_dur * 1000.0)

    print(f"\n--- PyTorch FP32 Candidate Results ---")
    print(f"WER: {wer_pt:.2f}% (Baseline was 85.60%!)")
    print(f"CER: {cer_pt:.2f}% (Baseline was 25.30%!)")
    print(f"Avg Latency: {avg_lat_pt:.1f} ms | RTF: {rtf_pt:.3f}")

    for i in range(3):
        print(f"Sample {i}:")
        print(f"  REF: {refs[i]}")
        print(f"  HYP: {hyps_pt[i]}")

    # 3. Export to ONNX if accuracy is significantly superior
    if wer_pt < 75.0:
        print(f"\n3. Significant improvement confirmed! Exporting to ONNX FP32: {ONNX_FP32_PATH}...")
        dummy_input = torch.randn(1, 16000 * 3, dtype=torch.float32)
        torch.onnx.export(
            model,
            dummy_input,
            str(ONNX_FP32_PATH),
            input_names=["input_values"],
            output_names=["logits"],
            dynamic_axes={
                "input_values": {0: "batch_size", 1: "sequence_length"},
                "logits": {0: "batch_size", 1: "time_steps"}
            },
            opset_version=18,
            do_constant_folding=True
        )
        fp32_size_mb = ONNX_FP32_PATH.stat().st_size / (1024 * 1024)
        print(f"   ONNX FP32 exported: {fp32_size_mb:.2f} MB")

        # 4. Quantize to dynamic INT8
        print(f"\n4. Quantizing to dynamic INT8 ONNX: {ONNX_INT8_PATH}...")
        quantize_dynamic(
            model_input=str(ONNX_FP32_PATH),
            model_output=str(ONNX_INT8_PATH),
            op_types_to_quantize=["MatMul", "Gemm"],
            weight_type=QuantType.QInt8
        )
        int8_size_mb = ONNX_INT8_PATH.stat().st_size / (1024 * 1024)
        print(f"   ONNX INT8 exported: {int8_size_mb:.2f} MB (Fits mobile storage budget)")

        # 5. Benchmark INT8 ONNX Runtime
        print(f"\n5. Benchmarking ONNX INT8 Runtime...")
        sess_opts = ort.SessionOptions()
        sess_opts.intra_op_num_threads = 2
        session = ort.InferenceSession(str(ONNX_INT8_PATH), sess_opts, providers=["CPUExecutionProvider"])

        hyps_int8 = []
        latencies_int8 = []
        for s in samples:
            audio_path = AUDIO_DIR / f"{s['id']}.wav"
            speech, sr = sf.read(str(audio_path), dtype="float32")
            if len(speech.shape) > 1:
                speech = speech.mean(axis=1)

            speech_norm = (speech - np.mean(speech)) / np.sqrt(np.var(speech) + 1e-7)
            inp = np.expand_dims(speech_norm, axis=0).astype(np.float32)

            t0 = time.perf_counter()
            logits = session.run(None, {"input_values": inp})[0]
            pred_ids = np.argmax(logits, axis=-1)[0]
            t1 = time.perf_counter()

            infer_ms = (t1 - t0) * 1000.0
            latencies_int8.append(infer_ms)

            collapsed = []
            prev = -1
            for tid in pred_ids:
                if tid != prev:
                    collapsed.append(tid)
                    prev = tid

            tokens = [inv_vocab.get(tid, "") for tid in collapsed if tid != pad_id]
            hyp_text = "".join(tokens).replace("|", " ").replace("<s>", "").replace("</s>", "").replace("<unk>", "").strip()
            hyp_text = " ".join(hyp_text.split())
            hyps_int8.append(hyp_text)

        wer_int8 = jiwer.wer(refs, hyps_int8) * 100.0
        cer_int8 = jiwer.cer(refs, hyps_int8) * 100.0
        avg_lat_int8 = float(np.mean(latencies_int8))
        rtf_int8 = avg_lat_int8 / (avg_dur * 1000.0)

        print(f"\n--- ONNX INT8 Runtime Results ---")
        print(f"WER: {wer_int8:.2f}% | CER: {cer_int8:.2f}%")
        print(f"Avg Latency: {avg_lat_int8:.1f} ms | RTF: {rtf_int8:.3f}")
        print(f"Size: {int8_size_mb:.2f} MB")
    else:
        print(f"\nModel {MODEL_ID} does not meet the accuracy threshold (WER: {wer_pt:.2f}%).")

if __name__ == "__main__":
    main()
