# Phase 7.6: Telugu Mobile STT Candidate Audit

**Problem Statement:** SIH 2026 PS 26173 — iTantra

**Date:** 2026-08-27 00:58:39

**Target Environment:** 4–6 GB RAM Android Mobile Devices (CPU-only, Fully Offline)


---

## 1. Executive Summary

In Phase 7.1, stock **Whisper Tiny Multilingual INT8** failed on Telugu with **119.7% WER** and **96.4% CER** (Severe Latin Transliteration / Hallucination).

In Phase 7.6, we audited open-source Telugu STT architectures and identified **Vakyansh Wav2Vec2 Telugu** as the primary lightweight candidate.

- **Accuracy Improvement:** WER dropped from **119.7%** (Whisper Tiny) to **33.84%** (Vakyansh INT8 ONNX), and CER dropped from **96.4%** to **6.67%**.
- **Quantization & Footprint:** Converted to dynamic INT8 ONNX (`vakyansh_telugu_base.int8.onnx`, **117.03 MB**).
- **CPU Speed:** Desktop RTF is **0.115** (~8-10x faster than real-time speech).


---

## 2. Telugu STT Benchmark Comparison (FLEURS Dataset)

| Metric | Whisper Tiny Multilingual INT8 (Baseline) | Vakyansh Telugu PyTorch FP32 | Vakyansh Telugu INT8 ONNX | Status / Delta |
| :--- | :---: | :---: | :---: | :---: |
| **Architecture** | Whisper (Autoregressive Seq2Seq) | Wav2Vec2 Base (CTC) | Wav2Vec2 Base (CTC) | Non-autoregressive CTC |
| **Model Footprint** | 98.81 MB | ~378 MB | **117.03 MB** | **Fits < 150 MB budget** |
| **WER (Word Error Rate)** | 119.7% (Catastrophic) | 36.87% | **33.84%** | **Dramatic reduction** |
| **CER (Char Error Rate)** | 96.4% | 7.06% | **6.67%** | **Substantial improvement** |
| **Avg STT Latency** | ~1200 ms | 810.8 ms | **1191.5 ms** | Fast single-pass execution |
| **Real-Time Factor (RTF)** | ~0.10 | 0.078 | **0.115** | Real-time CPU ready |
| **Licensing** | MIT / Apache 2.0 | MIT | **MIT** | Fully open-source |
| **Audit Status** | REJECTED (Severe Latin Transliteration / Hallucination) | Primary Mobile Candidate | **PRIMARY MOBILE CANDIDATE** | **Recommended for Android** |


---

## 3. Audited Model Families

| Model | Architecture | Params | Size (INT8) | License | Feasibility | Classification |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: |
| **Vakyansh Wav2Vec2 Telugu** | Wav2Vec2 Base CTC | 94.4M | **~117.0 MB** | MIT | HIGHLY FEASIBLE (4–6 GB) | **PRIMARY MOBILE CANDIDATE** |
| **AI4Bharat IndicConformer Telugu (120M)** | Conformer Hybrid CTC | 120M | ~120 MB | MIT / CC-BY | FEASIBLE (Mid-range) | **STRONG ALTERNATIVE** |
| **Whisper Tiny Multilingual INT8** | Whisper Autoregressive | 39M | 98.81 MB | MIT | FEASIBLE SIZE / UNUSABLE | **REJECTED (Looping)** |


---

## 4. Validated Binary Artifact Details

- **Artifact File:** `benchmarks/indic_stt/models/vakyansh_telugu_base.int8.onnx`
- **File Size:** **117.03 MB**
- **Cryptographic SHA-256 Hash:**
  ```text
  c64bab6c69e7965d512c3b6d70fcf5f8e4f2f6e52e6c06307612c489bfd964bf
  ```


---

## 5. Sample Transcriptions (INT8 ONNX Output)

### Sample #0 (8.28s audio)

- **Reference:**
  > `చిన్న ద్వీపాలలో చాలా వరకు స్వతంత్ర దేశాలు లేదా ఫ్రాన్స్ తో సంబంధం కలిగి ఉన్నాయి ఇంకా వీటిని లగ్జరీ బీచ్ రిసార్ట్ స్ అని పిలుస్తారు`

- **Vakyansh INT8 ONNX Output:**
  > `జిన్న ద్వేపాలలో చాలా వరకు స్వతంత్ర దేశాలు లేదా ఫ్రాన్స్ో సంబంధం కలిగి ఉన్నాయి ఇంకా వీటిని లగ్జరీ బీచ్ రిసల్ట్స్ అని పిలుస్తారు`

### Sample #1 (6.72s audio)

- **Reference:**
  > `కొన్ని క్రియలు ఆబ్జెక్టుల మధ్య తేడాను గుర్తించడానికి ఇది ఒక ముఖ్యమైన మార్గం`

- **Vakyansh INT8 ONNX Output:**
  > `కొన్ని క్రియలు అబ్జెక్టులు మధ్య తేడాను గుర్తించడానికి ఇది ఒక ముఖ్యమైన మార్గం`

### Sample #2 (10.08s audio)

- **Reference:**
  > `మీరు మీ స్వంతం ఆలోచనలతో కాకుండా ప్రభుత్వాల సలహా కూడా తీసుకోవాలని అనుకోవచ్చు అయితే వారి సలహా వారి పౌరుల కోసం రూపొందించబడింది`

- **Vakyansh INT8 ONNX Output:**
  > `మీరు మీ స్వొంతం ఆలోచనలతో కాకుండా ప్రభుత్వాల సహా కూడా తీసుకోవాలని అనుకోవచ్చు అయితే వారి సలహా వారి పౌరుల కోసం రూపొరూపొందించబడింది`

### Sample #3 (5.94s audio)

- **Reference:**
  > `ఈ దృశ్యాలు పిరమిడ్లపై ప్రదర్శించబడతాయి మరియు వేరే పిరమిడ్లను వెలిగించబడ్డాయి`

- **Vakyansh INT8 ONNX Output:**
  > `ఈ దృష్ట్యాలు పిరమిడ్లపై ప్రదర్శించబడతాయి మరియు వేరే పిరమిడ్లను విలిగించబడ్డాయి`

