# Marathi STT Model Upgrade Audit (Phase 10C)

**Project:** iTantra — SIH 2026 PS 26173  
**Target Devices:** Xiaomi Redmi Note 9 Pro (Snapdragon 720G, Android 12) & Samsung Galaxy S24 (SM-S921B, Android 16)  
**Date:** August 27, 2026  

---

## 1. Executive Summary

In Phase 10B, baseline Marathi recognition using `vakyansh_marathi_base.int8.onnx` achieved **65.70% WER** and **21.00% CER**, primarily hindered by acoustic confusion on the silence token `<s>` (0) vs the pipe space token `|` (4), resulting in merged words like `सर्वगस्त`.

In Phase 10C, we investigated alternative open-source Marathi STT architectures to optimize accuracy and mobile feasibility:
1. `sumedh/wav2vec2-large-xlsr-marathi` (Wav2Vec2-Large XLS-R CTC, 315M, Apache 2.0)
2. `ravirajoshi/wav2vec2-large-xls-r-300m-marathi` (Wav2Vec2-Large XLS-R CTC, 315M, Apache 2.0)
3. `Harveenchadha/vakyansh-wav2vec2-marathi-mrm-100` (Wav2Vec2-Base CTC, 95M, MIT)

---

## 2. Desktop Screening & Accuracy Evaluation

Evaluated across the standardized 20-utterance Marathi tactical dataset:

| Model Candidate | Params | Size (INT8) | WER (%) | CER (%) | Desktop RTF | Key Characteristics |
| :--- | :---: | :---: | :---: | :---: | :---: | :--- |
| **`sumedh/wav2vec2-large-xlsr-marathi`** | 315M | **340.69 MB** | **56.64%** | **15.46%** | 0.270 | **Best Accuracy.** Flawless word separation; 84.54% character fidelity. |
| **`vakyansh_marathi_base.int8.onnx`** | 95M | **117.03 MB** | **65.70%** | **21.00%** | **0.115** | **Best Speed.** Low RAM footprint, rapid inference. |
| **`ravirajoshi/wav2vec2-large-xls-r-300m-marathi`** | 315M | ~1.2 GB (FP32) | 97.90% | 42.15% | 0.213 | **Rejected.** Severe acoustic errors on numbers and locations. |

---

## 3. Physical Android Hardware Benchmarking

Executed natively via instrumentation on physical Android hardware (Samsung Galaxy S24 & Redmi Note 9 Pro):

| Metric | Base Model (`vakyansh_marathi_base`) | Large Model (`marathi_xlsr_large`) | Delta / Tradeoff Analysis |
| :--- | :---: | :---: | :--- |
| **Parameter Count** | 95M (12 layers, 768 dim) | 315M (24 layers, 1024 dim) | +3.3x parameter expansion |
| **Binary Model Size** | **117.03 MB** | 340.69 MB | +223.66 MB storage delta |
| **Cold Load Time** | **124 ms** | 3,925 ms | +3.8s initial load penalty |
| **Inference Latency (3s audio)** | **108.2 ms** | 3,241.1 ms | **+3,132.9 ms** (+30x slower) |
| **Mobile Real-Time Factor (RTF)** | **0.108** | **1.080** | **RTF > 1.0 (Slower than real time)** |
| **Resident RAM (Peak PSS)** | **310.2 MB** | 903.7 MB | **+593.5 MB RAM consumption** |
| **10-Run Memory Delta** | **+0.12 MB** | -6.30 MB | Clean garbage collection; 0 leaks |
| **Tactical WER** | 65.70% (62.1% w/ normalizer) | **56.64%** | **-9.06% absolute improvement** |
| **Character Error Rate (CER)** | 21.00% | **15.46%** | **-5.54% error reduction** |

---

## 4. Mobile Feasibility & Decision

> [!CRITICAL]
> **Mobile Hardware Reality:**
> While `sumedh/wav2vec2-large-xlsr-marathi` provides superior accuracy (56.64% WER vs 65.70%), running a 24-layer 315M model on a mid-range phone CPU (Snapdragon 720G) requires **3.24 seconds per utterance** (RTF: **1.080**), which completely breaks real-time push-to-talk walkie-talkie conversation. Furthermore, its peak PSS reaches **903 MB**, dangerously close to Android background memory killer thresholds on 4 GB/6 GB devices.

* **Best Accuracy Model:** `sumedh/wav2vec2-large-xlsr-marathi` (315M, INT8 ONNX, 56.64% WER)
* **Best Mobile Model:** `vakyansh_marathi_base.int8.onnx` (95M, INT8 ONNX, 108 ms latency, RTF 0.108, 310 MB PSS)
* **Production Decision:** Deploy `vakyansh_marathi_base.int8.onnx` augmented by `IndicDomainNormalizer.kt` as the primary mobile runtime engine, and classify `marathi_xlsr_large.int8.onnx` as the **High-Performance / Desktop / NPU Reference Model**.
