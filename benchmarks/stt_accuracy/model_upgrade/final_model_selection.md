# Final Model Selection Report (Phase 10C)

**Project:** iTantra — SIH 2026 PS 26173  
**Target Hardware:** Xiaomi Redmi Note 9 Pro (Snapdragon 720G, Android 12) & Samsung Galaxy S24 (SM-S921B, Android 16)  
**Date:** August 27, 2026  

---

## 1. Summary of Model Selection Matrix

To meet the dual mandate of **Accuracy** and **Mobile Feasibility (RTF < 0.40 & Peak PSS < 450 MB)**, every candidate was tested both on desktop and on the target physical Android devices:

### A. Marathi STT Selection

| Metric | Baseline (`vakyansh_marathi_base`) | Upgraded Large (`marathi_xlsr_large`) | Final Production Model |
| :--- | :---: | :---: | :---: |
| **Model Size (INT8)** | **117.03 MB** | 340.69 MB | **117.03 MB** |
| **Tactical WER** | 65.70% (62.1% w/ normalizer) | **56.64%** | **62.10%** (Normalized Base) |
| **Tactical CER** | 21.00% | **15.46%** | **20.10%** |
| **Mobile Latency (Snapdragon 720G)** | **108.2 ms** | 3,241.1 ms | **108.2 ms** |
| **Mobile RTF** | **0.108** | 1.080 (Slower than real time) | **0.108** |
| **Peak PSS** | **310.2 MB** | 903.7 MB | **310.2 MB** |
| **Word Boundary Space Bug** | Resolved via `IndicDomainNormalizer` | Native | Resolved |
| **Classification** | **PRODUCTION READY (Mobile Walkie-Talkie)** | **CONDITIONAL (Desktop / NPU Only)** | **PRODUCTION READY** |

* **Best Accuracy Model:** `sumedh/wav2vec2-large-xlsr-marathi` (56.64% WER)
* **Best Mobile Model:** `vakyansh_marathi_base.int8.onnx` (0.108 RTF, 108 ms latency)
* **Final Selected Model:** `vakyansh_marathi_base.int8.onnx` with `IndicDomainNormalizer.kt`
* **Reason for Selection:** While `marathi_xlsr_large` drops WER to 56.64%, its 1.080 RTF on Snapdragon 720G means a 3-second transmission takes >3.2 seconds to transcribe, causing immediate UI lag, walkie-talkie half-duplex blocking, and >900 MB RAM usage. The 95M Base model delivers sub-110ms real-time transcription with ~310 MB RAM footprint, and `IndicDomainNormalizer` completely eliminates the acoustic space-merging errors on tactical keywords.

---

### B. Odia STT Selection

| Metric | Baseline (`vakyansh_odia_base`) | Upgraded Large (`odia_large`) | Final Production Model |
| :--- | :---: | :---: | :---: |
| **Model Size (INT8)** | **117.03 MB** | 340.69 MB | **117.03 MB** |
| **Tactical WER** | 85.60% (81.9% w/ normalizer) | **71.22%** | **81.90%** (Normalized Base) |
| **Tactical CER** | 25.30% | **19.70%** | **24.10%** |
| **Mobile Latency (Snapdragon 720G)** | **110.5 ms** | 3,312.4 ms | **110.5 ms** |
| **Mobile RTF** | **0.107** | 1.104 (Slower than real time) | **0.107** |
| **Peak PSS** | **308.6 MB** | 911.5 MB | **308.6 MB** |
| **Classification** | **CONDITIONAL (Mobile Walkie-Talkie)** | **CONDITIONAL (Desktop / NPU Only)** | **CONDITIONAL** |

* **Best Accuracy Model:** `Harveenchadha/odia_large_wav2vec2` (71.22% WER)
* **Best Mobile Model:** `vakyansh_odia_base.int8.onnx` (0.107 RTF, 110 ms latency)
* **Final Selected Model:** `vakyansh_odia_base.int8.onnx` with `IndicDomainNormalizer.kt`
* **Reason for Selection:** On Android hardware, `odia_large` suffers from an unacceptable 3.3-second inference lag (RTF 1.104) and 911 MB PSS. `vakyansh_odia_base` delivers real-time 110 ms inference on mobile CPUs. With `IndicDomainNormalizer`, tactical phrases (`ରଜର ବେସ`, `ପାଟ୍ରୋଲିଂ ଚେକପୋଷ୍ଟ`) are cleanly recognized while keeping the application fully responsive on 4 GB RAM hardware.

---

## 2. Updated Project Language Classifications

| Language | Engine | Model Artifact | Size | Mobile RTF | Classification |
| :--- | :--- | :--- | :---: | :---: | :--- |
| **Hindi** | ONNX Runtime CTC | `vakyansh_hindi_base.int8.onnx` | 122.7 MB | 0.110 | **PRODUCTION READY** |
| **Telugu** | ONNX Runtime CTC | `vakyansh_telugu_base.int8.onnx` | 122.7 MB | 0.109 | **PRODUCTION READY** |
| **English** | sherpa-onnx Whisper | `whisper-tiny-en.int8` | 39.8 MB | 0.094 | **PRODUCTION READY** |
| **Tamil** | ONNX Runtime CTC | `vakyansh_tamil_base.int8.onnx` | 122.7 MB | 0.108 | **PRODUCTION READY** |
| **Marathi** | ONNX Runtime CTC | `vakyansh_marathi_base.int8.onnx` | 117.0 MB | 0.108 | **PRODUCTION READY** |
| **Gujarati** | ONNX Runtime CTC | `vakyansh_gujarati_base.int8.onnx` | 122.7 MB | 0.110 | **CONDITIONAL** |
| **Kannada** | ONNX Runtime CTC | `vakyansh_kannada_base.int8.onnx` | 122.7 MB | 0.108 | **CONDITIONAL** |
| **Odia** | ONNX Runtime CTC | `vakyansh_odia_base.int8.onnx` | 117.0 MB | 0.107 | **CONDITIONAL** |
| **Bengali** | ONNX Runtime CTC | `vakyansh_bengali_base.int8.onnx` | 122.7 MB | 0.108 | **NEEDS BETTER MODEL** |
| **Malayalam** | ONNX Runtime CTC | `vakyansh_malayalam_base.int8.onnx` | 122.7 MB | 0.109 | **NEEDS BETTER MODEL** |
