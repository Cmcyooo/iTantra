# Phase 7.5: Tamil Mobile STT Candidate Audit

**Problem Statement:** SIH 2026 PS 26173 — iTantra

**Date:** 2026-08-27 00:41:57

**Target Environment:** 4–6 GB RAM Android Mobile Devices (CPU-only, Fully Offline)


---

## 1. Executive Summary

In Phase 7.1, stock **Whisper Tiny Multilingual INT8** failed on Tamil with **101.4% WER** and **78.6% CER** (Repetition Loop / Missing Tokens).

In Phase 7.5, we audited open-source Tamil STT architectures and identified **Vakyansh Wav2Vec2 Tamil** as the primary lightweight candidate.

- **Accuracy Improvement:** WER dropped from **101.4%** (Whisper Tiny) to **44.86%** (Vakyansh INT8 ONNX), and CER dropped from **78.6%** to **18.19%**.
- **Quantization & Footprint:** Converted to dynamic INT8 ONNX (`vakyansh_tamil_base.int8.onnx`, **117.02 MB**).
- **CPU Speed:** Desktop RTF is **0.125** (~8-10x faster than real-time speech).


---

## 2. Tamil STT Benchmark Comparison (FLEURS Dataset)

| Metric | Whisper Tiny Multilingual INT8 (Baseline) | Vakyansh Tamil PyTorch FP32 | Vakyansh Tamil INT8 ONNX | Status / Delta |
| :--- | :---: | :---: | :---: | :---: |
| **Architecture** | Whisper (Autoregressive Seq2Seq) | Wav2Vec2 Base (CTC) | Wav2Vec2 Base (CTC) | Non-autoregressive CTC |
| **Model Footprint** | 98.81 MB | ~378 MB | **117.02 MB** | **Fits < 150 MB budget** |
| **WER (Word Error Rate)** | 101.4% (Catastrophic) | 42.99% | **44.86%** | **Dramatic reduction** |
| **CER (Char Error Rate)** | 78.6% | 18.08% | **18.19%** | **Substantial improvement** |
| **Avg STT Latency** | ~1200 ms | 1233.8 ms | **1845.5 ms** | Fast single-pass execution |
| **Real-Time Factor (RTF)** | ~0.10 | 0.083 | **0.125** | Real-time CPU ready |
| **Licensing** | MIT / Apache 2.0 | MIT | **MIT** | Fully open-source |
| **Audit Status** | REJECTED (Repetition Loop / Missing Tokens) | Primary Mobile Candidate | **PRIMARY MOBILE CANDIDATE** | **Recommended for Android** |


---

## 3. Audited Model Families

| Model | Architecture | Params | Size (INT8) | License | Feasibility | Classification |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: |
| **Vakyansh Wav2Vec2 Tamil** | Wav2Vec2 Base CTC | 94.4M | **~117.0 MB** | MIT | HIGHLY FEASIBLE (4–6 GB) | **PRIMARY MOBILE CANDIDATE** |
| **AI4Bharat IndicConformer Tamil (120M)** | Conformer Hybrid CTC | 120M | ~120 MB | MIT / CC-BY | FEASIBLE (Mid-range) | **STRONG ALTERNATIVE** |
| **Whisper Tiny Multilingual INT8** | Whisper Autoregressive | 39M | 98.81 MB | MIT | FEASIBLE SIZE / UNUSABLE | **REJECTED (Looping)** |


---

## 4. Validated Binary Artifact Details

- **Artifact File:** `benchmarks/indic_stt/models/vakyansh_tamil_base.int8.onnx`
- **File Size:** **117.02 MB**
- **Cryptographic SHA-256 Hash:**
  ```text
  33a91f3bce4b4025b0c561cde40fce2f029b1cc4ec85c8401f0869d030bb43b2
  ```


---

## 5. Sample Transcriptions (INT8 ONNX Output)

### Sample #0 (7.80s audio)

- **Reference:**
  > `இது வேதியியல் ph என அழைக்கப்படுகிறது நீங்கள் சிவப்பு முட்டைக்கோஸ் சாற்றைப் பயன்படுத்தி ஒரு குறிகாட்டியை உருவாக்கலாம்`

- **Vakyansh INT8 ONNX Output:**
  > `இது வேதியில் பீகச் சென அழைக்கப்படுகிறது நீங்கள் சிவப்பு முட்டைகோ சாற்றைப் பயன்படுத்தி ஒரு குரிகாட்டியை உருவாக்கலாம்`

### Sample #1 (6.96s audio)

- **Reference:**
  > `லக்கா சிங் பஜனையை வழங்கினார் பாடகர் ராஜு கண்டெல்வலும் அவருடன் வந்திருந்தார்`

- **Vakyansh INT8 ONNX Output:**
  > `லக்காசிங் பஜனியை வழங்கினார் பாடகர் ராஜூ கண்டல்வாலும் அவருடன் வந்திருந்தார்`

### Sample #2 (25.44s audio)

- **Reference:**
  > `நீங்கள் உங்கள் சொந்த கருத்தைத் தவிர அரசாங்கங்களின் ஆலோசனையை பெற விரும்பலாம் ஆனால் அவர்களின் ஆலோசனை அவர்களின் குடிமக்களுக்கென உருவாக்கப்பட்டுள்ளது`

- **Vakyansh INT8 ONNX Output:**
  > `நீங்கள் உ்கள் சொந்த கருத்தைத் தவிர அரசாங்கங்களின் ஆலோசனையை பெற விரும்பலாம் ஆனால் அவர்களின் ஆலோசனை அவர்களின் குடிம்மக்களுக்கென உருவாக்கப்பட்டுள்ளது நீங்கள் உங்கள் சந்த கருத்தைத் தவிர நீங்கள் உங்கள் சொந்த கருத்தைத் தவிர அரசா்கங்களின் ஆலோசனையை பற வரும்பலாம் ஆனால் அவர்களின் ஆலோசனை வர்களின்குடிம்பக்ளுக்கன உருவாக்கப்பட்டுள்ளது`

### Sample #3 (14.88s audio)

- **Reference:**
  > `முக்கியமான மேடைகளில் இசை நிகழ்ச்சி முடிந்திருந்தாலும் விழாவின் சில பிரிவுகள் இரவு இசைப்பதைத் தொடரும்`

- **Vakyansh INT8 ONNX Output:**
  > `முக்கியமான மேடைகளில் இசை நிகழ்ச்சி முடிந்திருந்தாலும் விழாவின் சில பிரிவுகள் இரவு இசைப்பதைத் தொடரும்`

