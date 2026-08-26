# Phase 7.1 — Multilingual STT Feasibility Benchmark

## Overview
This benchmark evaluates the feasibility of **sherpa-onnx Whisper Tiny Multilingual INT8** as the primary offline Speech-to-Text (STT) engine for iTantra (SIH 2026 Problem Statement 26173) across the **10 target Indian languages**:

1. Hindi (`hi`)
2. Gujarati (`gu`)
3. Marathi (`mr`)
4. Kannada (`kn`)
5. Malayalam (`ml`)
6. Tamil (`ta`)
7. Telugu (`te`)
8. Odia (`or`)
9. Bengali (`bn`)
10. English (`en`)

## Hardware Constraints
* **Target Mobile Hardware:** 4–6 GB RAM Android devices (API 24+).
* **Execution:** CPU-only, fully offline, multi-threaded (`num_threads = 2`).

## Dataset
* **Source:** [Google FLEURS](https://huggingface.co/datasets/google/fleurs) (16 kHz mono, human-annotated speech and transcripts).
* **Configs:** `en_us`, `hi_in`, `gu_in`, `mr_in`, `kn_in`, `ml_in`, `ta_in`, `te_in`, `or_in`, `bn_in`.

## Model Information
* **Model Name:** `csukuangfj/sherpa-onnx-whisper-tiny`
* **Architecture:** Whisper Tiny (Encoder INT8 + Decoder INT8)
* **Parameters:** ~39M
* **Model Files:**
  - `tiny-encoder.int8.onnx` (~12.9 MB)
  - `tiny-decoder.int8.onnx` (~89.9 MB)
  - `tiny-tokens.txt` (~0.8 MB)
  - **Total Footprint:** ~103.6 MB

## Running the Benchmark
```bash
pip install -r requirements.txt
python run_benchmark.py
```

## Generated Artifacts
* `results.csv`: Raw tabular metrics (WER, CER, STT latency, RTF, Peak RAM per language).
* `results.md`: Complete executive summary, qualitative comparison, and architectural analysis.
