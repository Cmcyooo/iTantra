# Phase 7.5: Malayalam Mobile STT Candidate Audit

**Problem Statement:** SIH 2026 PS 26173 — iTantra

**Date:** 2026-08-27 00:38:19

**Target Environment:** 4–6 GB RAM Android Mobile Devices (CPU-only, Fully Offline)


---

## 1. Executive Summary

In Phase 7.1, stock **Whisper Tiny Multilingual INT8** failed on Malayalam with **193.8% WER** and **147.2% CER** (Extreme Hallucination / English Loop).

In Phase 7.5, we audited open-source Malayalam STT architectures and identified **Vakyansh Wav2Vec2 Malayalam** as the primary lightweight candidate.

- **Accuracy Improvement:** WER dropped from **193.8%** (Whisper Tiny) to **54.55%** (Vakyansh INT8 ONNX), and CER dropped from **147.2%** to **14.24%**.
- **Quantization & Footprint:** Converted to dynamic INT8 ONNX (`vakyansh_malayalam_base.int8.onnx`, **117.03 MB**).
- **CPU Speed:** Desktop RTF is **0.123** (~8-10x faster than real-time speech).


---

## 2. Malayalam STT Benchmark Comparison (FLEURS Dataset)

| Metric | Whisper Tiny Multilingual INT8 (Baseline) | Vakyansh Malayalam PyTorch FP32 | Vakyansh Malayalam INT8 ONNX | Status / Delta |
| :--- | :---: | :---: | :---: | :---: |
| **Architecture** | Whisper (Autoregressive Seq2Seq) | Wav2Vec2 Base (CTC) | Wav2Vec2 Base (CTC) | Non-autoregressive CTC |
| **Model Footprint** | 98.81 MB | ~378 MB | **117.03 MB** | **Fits < 150 MB budget** |
| **WER (Word Error Rate)** | 193.8% (Catastrophic) | 51.70% | **54.55%** | **Dramatic reduction** |
| **CER (Char Error Rate)** | 147.2% | 13.60% | **14.24%** | **Substantial improvement** |
| **Avg STT Latency** | ~1200 ms | 1238.7 ms | **1760.2 ms** | Fast single-pass execution |
| **Real-Time Factor (RTF)** | ~0.10 | 0.087 | **0.123** | Real-time CPU ready |
| **Licensing** | MIT / Apache 2.0 | MIT | **MIT** | Fully open-source |
| **Audit Status** | REJECTED (Extreme Hallucination / English Loop) | Primary Mobile Candidate | **PRIMARY MOBILE CANDIDATE** | **Recommended for Android** |


---

## 3. Audited Model Families

| Model | Architecture | Params | Size (INT8) | License | Feasibility | Classification |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: |
| **Vakyansh Wav2Vec2 Malayalam** | Wav2Vec2 Base CTC | 94.4M | **~117.0 MB** | MIT | HIGHLY FEASIBLE (4–6 GB) | **PRIMARY MOBILE CANDIDATE** |
| **AI4Bharat IndicConformer Malayalam (120M)** | Conformer Hybrid CTC | 120M | ~120 MB | MIT / CC-BY | FEASIBLE (Mid-range) | **STRONG ALTERNATIVE** |
| **Whisper Tiny Multilingual INT8** | Whisper Autoregressive | 39M | 98.81 MB | MIT | FEASIBLE SIZE / UNUSABLE | **REJECTED (Looping)** |


---

## 4. Validated Binary Artifact Details

- **Artifact File:** `benchmarks/indic_stt/models/vakyansh_malayalam_base.int8.onnx`
- **File Size:** **117.03 MB**
- **Cryptographic SHA-256 Hash:**
  ```text
  c0922d209f67461c740784770c2c53ec6160d69c25b1d8c912eee692b8b2eea6
  ```


---

## 5. Sample Transcriptions (INT8 ONNX Output)

### Sample #0 (11.40s audio)

- **Reference:**
  > `ഈ നഗരം രാജ്യത്തെ മറ്റ് നഗരങ്ങളിൽ നിന്ന് പൂർണ്ണമായും വ്യത്യസ്തമാണ് കാരണം ഇതിന് ആഫ്രിക്കൻ വാസനയേക്കാൾ അറബിവാസനയാണ് കൂടുതൽ`

- **Vakyansh INT8 ONNX Output:**
  > `ഈ നഗരം രാജ്യത്തെ മറ്റു നഗരങ്ങളിൽ നിന്ന് പൂർണമായം വ്യദ്യസ്ഥമാണ് കാരണം ഇതിന് ആഫ്രിക്കൻ വാസനേക്കാൾ ആറവി വാസനയാണ് കൂടുതൽ`

### Sample #1 (23.58s audio)

- **Reference:**
  > `പാലത്തിനു താഴെയുള്ള കുത്തനെയുള്ള ഉയരം 15 മീറ്ററാണ് ഇതിൻ്റെ നിർമ്മാണം 2011 ഓഗസ്റ്റിൽ പൂർത്തിയാക്കിയതാണ് എന്നാൽ ഇത് 2017 മാർച്ച് വരെ ഗതാഗതത്തിനായി തുറന്ന് കൊടുത്തിട്ടില്ല`

- **Vakyansh INT8 ONNX Output:**
  > `പാലത്തിൻറെ താഴയുള്ള കുത്തനെയുള്ള ഉയരം പതിനഞ്ച് മീറ്ററാണ് ഇതിൻറെ നിർമാണ്ം രണ്ടായിരത്തി പതിനുന്ന് ഓഗസ്റ്റിൽ പൂർത്തി ആക്കിയതാണ് എന്നാൽ ഇത് രണ്ടായിരത്തി പതിനേഴ മാർച്ച് വരെ ഗതാഗദത്തിന ആായി തുറന്ന് കൊടുത്തിട്ടില്ല`

### Sample #2 (25.44s audio)

- **Reference:**
  > `ഇത് ബന്ധപ്പെട്ടിരിക്കുന്നു എന്നാൽ സാധാരണയായി ആൽപൈൻ ശൈലിയിലുള്ള സ്കീ ടൂറിംഗോ പർവ്വതാരോഹണമോ ഉൾപ്പെടുന്നില്ല ഇതിൽ രണ്ടാമത്തേത് കുത്തനെയുള്ള ഭൂപ്രദേശങ്ങളിൽ ചെയ്യുന്നതും കൂടുതൽ കട്ടിയുള്ള സ്‌കീകളും ബൂട്ടുകളും ആവശ്യമായതുമാണ്`

- **Vakyansh INT8 ONNX Output:**
  > `ഇത് ബന്ധപ്പെട്ടിരിക്കുന്നു എന്നാൽ സാധാരണായി ആൽ്പായൻ ശൈലിയിലുള്ള സ്കീ ടൂറിങോ പറവദാരോഹണമോ ഉൾപ്പെടുന്നില്ല ഇതിൽ രണ്ടാമത്തേത് കുത്തനെയുള്ള ബൂപ്രദേശങ്ങളിൽ ചെയ്യുന്നതും കൂടുതൽ കട്ടിയുള്ള സ്കീഗളും ബൂട്ടുകളും ആവശ്യമായതുമാണ്`

### Sample #3 (6.84s audio)

- **Reference:**
  > `സഹ ഗുസ്തിക്കാരും ലൂണയ്ക്ക് ആദരാഞ്ജലികൾ അർപ്പിച്ചു`

- **Vakyansh INT8 ONNX Output:**
  > `സഹ ഗുസ്തിക്കാരുലക്ക് ആദരാൺജലികൾ അറിപ്പിച്ചു`

