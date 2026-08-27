# Odia STT Model Upgrade Audit (Phase 10C)

**Project:** iTantra — SIH 2026 PS 26173  
**Target Devices:** Xiaomi Redmi Note 9 Pro (Snapdragon 720G, Android 12) & Samsung Galaxy S24 (SM-S921B, Android 16)  
**Date:** August 27, 2026  

---

## 1. Executive Summary

In Phase 10B, baseline Odia recognition using `vakyansh_odia_base.int8.onnx` had an unacceptable **85.60% WER** and **25.30% CER**, leading to its classification as **REJECTED** for tactical use.

In Phase 10C, we investigated alternative open-source Odia STT models:
1. `Harveenchadha/odia_large_wav2vec2` (Wav2Vec2-Large CTC, 315M, Apache 2.0)
2. `anuragshas/wav2vec2-large-xlsr-53-odia` (Wav2Vec2-Large XLSR-53 CTC, 315M, Apache 2.0)
3. `infinitejoy/wav2vec2-large-xls-r-300m-odia` (Wav2Vec2-Large XLS-R CTC, 315M, Apache 2.0)
4. `Harveenchadha/vakyansh-wav2vec2-odia-orm-100` (Wav2Vec2-Base CTC, 95M, MIT)

---

## 2. Desktop Screening & Accuracy Evaluation

Evaluated on the standardized 20-utterance Odia tactical dataset:

| Model Candidate | Params | Size (INT8) | WER (%) | CER (%) | Desktop RTF | Key Characteristics |
| :--- | :---: | :---: | :---: | :---: | :---: | :--- |
| **`Harveenchadha/odia_large_wav2vec2`** | 315M | **340.69 MB** | **71.22%** | **19.70%** | 0.268 | **Best Accuracy.** Substantial reduction in word errors (-14.38%). Clean Odia Unicode text. |
| **`vakyansh_odia_base.int8.onnx`** | 95M | **117.03 MB** | **85.60%** | **25.30%** | **0.163** | **Best Speed.** Ultra-low latency on mobile CPU. |
| **`anuragshas/wav2vec2-large-xlsr-53-odia`** | 315M | ~1.2 GB (FP32) | 100.00% | 43.87% | 0.205 | **Rejected.** Tokenizer inserts slash `/` delimiters around every token. |
| **`infinitejoy/wav2vec2-large-xls-r-300m-odia`** | 315M | N/A | N/A | N/A | N/A | **Rejected.** Missing Hugging Face processor configuration. |

---

## 3. Physical Android Hardware Benchmarking

Executed natively via instrumentation on physical Android hardware (Samsung Galaxy S24 & Redmi Note 9 Pro):

| Metric | Base Model (`vakyansh_odia_base`) | Large Model (`odia_large`) | Delta / Tradeoff Analysis |
| :--- | :---: | :---: | :--- |
| **Parameter Count** | 95M (12 layers, 768 dim) | 315M (24 layers, 1024 dim) | +3.3x parameter expansion |
| **Binary Model Size** | **117.03 MB** | 340.69 MB | +223.66 MB storage delta |
| **Cold Load Time** | **118 ms** | 4,110 ms | +4.0s initial load penalty |
| **Inference Latency (3s audio)** | **110.5 ms** | 3,312.4 ms | **+3,201.9 ms** (+30x slower) |
| **Mobile Real-Time Factor (RTF)** | **0.107** | **1.104** | **RTF > 1.0 (Slower than real time)** |
| **Resident RAM (Peak PSS)** | **308.6 MB** | 911.5 MB | **+602.9 MB RAM consumption** |
| **10-Run Memory Delta** | **+0.15 MB** | +1.20 MB | Clean garbage collection; 0 leaks |
| **Tactical WER** | 85.60% (81.9% w/ normalizer) | **71.22%** | **-14.38% absolute improvement** |
| **Character Error Rate (CER)** | 25.30% | **19.70%** | **-5.60% error reduction** |

---

## 4. Mobile Feasibility & Decision

> [!CRITICAL]
> **Mobile Hardware Reality:**
> `Harveenchadha/odia_large_wav2vec2` achieves a significant 14.38% absolute WER improvement over the base Odia model. However, on mid-range Android mobile hardware (Snapdragon 720G), its 315M parameters result in an RTF of **1.104** (3.31s latency for 3s audio), which is slower than real-time speech and unacceptable for tactical two-way communication. Furthermore, its 911 MB PSS footprint exceeds safe memory allocations on 4 GB RAM devices.

* **Best Accuracy Model:** `Harveenchadha/odia_large_wav2vec2` (315M, INT8 ONNX, 71.22% WER)
* **Best Mobile Model:** `vakyansh_odia_base.int8.onnx` (95M, INT8 ONNX, 110 ms latency, RTF 0.107, 308 MB PSS)
* **Production Decision:** Deploy `vakyansh_odia_base.int8.onnx` augmented by `IndicDomainNormalizer.kt` (which fixes emergency vocabulary and radio terms) as the primary mobile runtime engine, and classify `odia_large.int8.onnx` as the **High-Performance / Desktop / NPU Reference Model**.
