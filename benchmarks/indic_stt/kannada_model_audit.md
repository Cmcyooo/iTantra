# Phase 7.7: Kannada Mobile STT Candidate Audit

**Problem Statement:** SIH 2026 PS 26173 — iTantra

**Date:** 2026-08-27 01:16:47

**Target Environment:** 4–6 GB RAM Android Mobile Devices (CPU-only, Fully Offline)


---

## 1. Executive Summary

In Phase 7.1, stock **Whisper Tiny Multilingual INT8** failed on Kannada with **128.4% WER** and **103.2% CER** (Repetition Loop / Latin Transliteration).

In Phase 7.7, we audited open-source Kannada STT architectures and identified **Vakyansh Wav2Vec2 Kannada** as the primary lightweight candidate.

- **Accuracy Improvement:** WER dropped from **128.4%** (Whisper Tiny) to **37.31%** (Vakyansh INT8 ONNX), and CER dropped from **103.2%** to **8.29%**.
- **Quantization & Footprint:** Converted to dynamic INT8 ONNX (`vakyansh_kannada_base.int8.onnx`, **117.03 MB**).
- **CPU Speed:** Desktop RTF is **0.123** (~8-10x faster than real-time speech).


---

## 2. Kannada STT Benchmark Comparison (FLEURS Dataset)

| Metric | Whisper Tiny Multilingual INT8 (Baseline) | Vakyansh Kannada PyTorch FP32 | Vakyansh Kannada INT8 ONNX | Status / Delta |
| :--- | :---: | :---: | :---: | :---: |
| **Architecture** | Whisper (Autoregressive Seq2Seq) | Wav2Vec2 Base (CTC) | Wav2Vec2 Base (CTC) | Non-autoregressive CTC |
| **Model Footprint** | 98.81 MB | ~378 MB | **117.03 MB** | **Fits < 150 MB budget** |
| **WER (Word Error Rate)** | 128.4% (Catastrophic) | 40.41% | **37.31%** | **Dramatic reduction** |
| **CER (Char Error Rate)** | 103.2% | 8.61% | **8.29%** | **Substantial improvement** |
| **Avg STT Latency** | ~1200 ms | 1091.5 ms | **1567.1 ms** | Fast single-pass execution |
| **Real-Time Factor (RTF)** | ~0.10 | 0.085 | **0.123** | Real-time CPU ready |
| **Licensing** | MIT / Apache 2.0 | MIT | **MIT** | Fully open-source |
| **Audit Status** | REJECTED (Repetition Loop / Latin Transliteration) | Primary Mobile Candidate | **PRIMARY MOBILE CANDIDATE** | **Recommended for Android** |


---

## 3. Audited Model Families

| Model | Architecture | Params | Size (INT8) | License | Feasibility | Classification |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: |
| **Vakyansh Wav2Vec2 Kannada** | Wav2Vec2 Base CTC | 94.4M | **~117.0 MB** | MIT | HIGHLY FEASIBLE (4–6 GB) | **PRIMARY MOBILE CANDIDATE** |
| **AI4Bharat IndicConformer Kannada (120M)** | Conformer Hybrid CTC | 120M | ~120 MB | MIT / CC-BY | FEASIBLE (Mid-range) | **STRONG ALTERNATIVE** |
| **Whisper Tiny Multilingual INT8** | Whisper Autoregressive | 39M | 98.81 MB | MIT | FEASIBLE SIZE / UNUSABLE | **REJECTED (Looping / Missing Language)** |


---

## 4. Validated Binary Artifact Details

- **Artifact File:** `benchmarks/indic_stt/models/vakyansh_kannada_base.int8.onnx`
- **File Size:** **117.03 MB**
- **Cryptographic SHA-256 Hash:**
  ```text
  9769b09b6c24d67acebc50f4436d1756f4a4b3ee3edec9c34099faa904f9f5a8
  ```


---

## 5. Sample Transcriptions (INT8 ONNX Output)

### Sample #0 (11.34s audio)

- **Reference:**
  > `ಆದರೆ ನಾಯಕನ ವಿಕೆಟ್ ಕಳೆದುಕೊಂಡ ನಂತರ ಭಾರತ 7 ವಿಕೆಟ್ ಕಳೆದುಕೊಂಡು ಕೇವಲ 36 ರನ್ಗಳಿಗೆ ತನ್ನ ಇನ್ನಿಂಗ್ಸ್ ಮುಗಿಸಿತು`

- **Vakyansh INT8 ONNX Output:**
  > `ಆದರೆ ನಾಯಕನ ವಿಕೆಟ್ ಕಳೆದುಕೊಂಡ ನಂತರ ಭಾರತ ಯಳು ವಿಕೆಟ್ ಕಳೆದುಕೊಂಡು ಕೇವಲ ಮೂವತ್ತಾರು ರನಗಳಿಗೆ ತನ್ನ ಇನ್ನಿಂಗ್ಸ್ ಮುಗಿಸಿತು`

### Sample #1 (9.30s audio)

- **Reference:**
  > `ಪಿರಮಿಡ್ ಧ್ವನಿ ಮತ್ತು ಬೆಳಕಿನ ಪ್ರದರ್ಶನವು ಈ ಭಾಗದ ಮಕ್ಕಳಿಗೆ ಅತ್ಯಂತ ಆಸಕ್ತಿದಾಯಕ ವಿಷಯವಾಗಿದೆ`

- **Vakyansh INT8 ONNX Output:**
  > `ಪಿರಮಿಡ್ ಧ್ವನಿ ಮತ್ತು ಬೆಳಕಿನ ಪ್ರದರ್ಶನವೂ ಈ ಭಾಗದ ಮಕ್ಕಳಿಗೆ ಅತ್ಯಂತ ಆಸಕ್ತಿ ದಾಯಕ ವಿಷ ವಿಷಯವಾಗಿದೆ`

### Sample #2 (6.42s audio)

- **Reference:**
  > `ಸಹ ಕುಸ್ತಿಪಟುಗಳು ಸಹ ಲೂನಾ ಅವರಿಗೆ ಗೌರವ ಸಲ್ಲಿಸಿದರು`

- **Vakyansh INT8 ONNX Output:**
  > `ಸಹ ಕುಸ್ತಿಪಟುಗಳು ಸಹ ಲೂನಾ ಅವರಿಗೆ ಗೌರವ ಸಲ್ಲಿಸಿದರು`

### Sample #3 (11.04s audio)

- **Reference:**
  > `ಮಹಿಳೆ ಮಹಿಳಾ ಪ್ರವಾಸಿಗರು ತಮ್ಮ ನಿಜವಾದ ವೈವಾಹಿಕ ಸ್ಥಾನಮಾನ ಎನೇ ಇದ್ದರೂ ತಾವು ವಿವಾಹಿತರು ಎಂದು ಹೇಳಿಕೊಳ್ಳುವುದು ಸೂಕ್ತವಾಗಿದೆ`

- **Vakyansh INT8 ONNX Output:**
  > `ಮಹಿಳೆ ಮಹಿಳಾ ಪ್ರವಾಸಿಗರು ತಮ್ಮ ನಿಜವಾದ ವೈವಾಹಿಕ ಸ್ಥಾನಮಾನ ಏನೇ ಇದ್ದರೂ ತವಿವಾಹಿತರು ಎಂದು ಹೇಳಿಕೊಳ್ಳುವುದು ಸೂಕ್ತವಾಗಿದೆ`

