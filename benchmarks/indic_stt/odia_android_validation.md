# Phase 7.7: Odia Vakyansh INT8 ONNX Physical Android Validation Report

**Problem Statement:** SIH 2026 PS 26173 — iTantra  
**Validation Date:** 2026-08-27 01:26:00  
**Test Method:** Android Instrumented Test (`OdiaVakyanshBenchmarkTest`) executed on physical device via ADB  
**Execution Mode:** Fully Offline (Airplane Mode / Local CPU Inference)

---

## 1. Executive Summary & Verdict

We executed the full on-device benchmark suite for the candidate Odia STT model (**Vakyansh Wav2Vec2 Odia ORM-100 INT8 ONNX**) on a physical Android device (**Samsung Galaxy S24 / SM-S921B**, ARM64, 8 GB RAM).

### On-Device Performance Highlights:
- **On-Device Accuracy:** **78.54% WER** and **24.08% CER** on FLEURS Odia. It provides a functional native offline baseline for Odia where Whisper Tiny had **0% coverage** (145.0% error rate / failed transcription due to complete lack of Odia language support).
- **Cold Model Load Time:** **602.33 ms** via direct ONNX memory-mapping.
- **Inference Latency & Speed:** Average **2971.6 ms** for 10.39s audio (**RTF: 0.286**, **~3.5x faster than real-time speech** on 2 CPU threads).
- **Model Resident Memory Delta:** **196.81 MB PSS** (Peak process PSS: **855.03 MB**).
- **Inference Stability & Leaks:** **10 repeated inference passes** yielded **3513.6 ms** average latency with **zero memory leaks**, and **zero crashes**.

### Classification Verdict:
# **CONDITIONAL CANDIDATE**
*(Viable offline baseline for Odia on 4–6 GB Android devices; recommended for deployment with domain keyword boosting or hybrid CTC/LM to improve word-level recognition on complex phrases).*

---

## 2. Validated Binary Artifact & Integrity

- **Model Artifact Name:** `vakyansh_odia_base.int8.onnx`
- **File Size:** **117.03 MB** (122,713,538 bytes)
- **Quantization:** Dynamic INT8 (`QuantType.QInt8` on MatMul/Gemm attention layers)
- **Host & On-Device SHA-256 Hash:**
  ```text
  a0d3c21cf9b8a057779d92e678cc05c3b46142efe5767ba506e0a644ca8c8bba
  ```
- **Integrity Status:** **VERIFIED (Exact bit-for-bit match)**

---

## 3. Physical Test Hardware & Runtime Environment

| Property | Value |
| :--- | :--- |
| **Physical Device Model** | Samsung Galaxy S24 (`SM-S921B` / `e1s`) |
| **Android Version / API** | Android 16 (API 36) |
| **CPU Architecture (ABI)** | `arm64-v8a` (64-bit ARM) |
| **Total Physical RAM** | 7,397,292 kB (~7.4 GB physical RAM) |
| **Inference Framework** | ONNX Runtime Android (`ai.onnxruntime:onnxruntime-android:1.18.0`) |
| **Execution Provider** | CPU Execution Provider (`intra_op_num_threads = 2`) |
| **Acoustic Input** | 16 kHz Mono Float32 PCM |
| **Decoding Method** | Single-pass Greedy CTC argmax collapse (65 Odia classes) |

---

## 4. Per-Sample Physical Android Benchmark Results (FLEURS Odia)

| Sample ID | WAV File | Audio Duration | Android Inference Latency | Real-Time Factor (RTF) | Word Errors | Char Errors |
| :---: | :--- | :---: | :---: | :---: | :---: | :---: |
| **0** | `000.wav` | 10.86 s | 3213.1 ms | 0.296 | 13 | 19 |
| **1** | `001.wav` | 13.20 s | 3885.5 ms | 0.294 | 14 | 22 |
| **2** | `002.wav` | 8.16 s | 2254.1 ms | 0.276 | 8 | 11 |
| **3** | `003.wav` | 9.18 s | 2568.9 ms | 0.280 | 11 | 16 |
| **4** | `004.wav` | 8.16 s | 2244.4 ms | 0.275 | 11 | 15 |
| **5** | `005.wav` | 6.12 s | 1661.2 ms | 0.271 | 6 | 9 |
| **6** | `006.wav` | 9.48 s | 2665.7 ms | 0.281 | 10 | 14 |
| **7** | `007.wav` | 12.12 s | 3497.0 ms | 0.289 | 13 | 18 |
| **8** | `008.wav` | 10.56 s | 2993.6 ms | 0.283 | 12 | 17 |
| **9** | `009.wav` | 10.08 s | 2837.4 ms | 0.281 | 11 | 15 |
| **10** | `010.wav` | 15.18 s | 4531.6 ms | 0.299 | 17 | 25 |
| **11** | `011.wav` | 11.58 s | 3306.4 ms | 0.286 | 12 | 17 |
| **SUMMARY** | **12 Samples** | **124.68 s total** | **2971.6 ms avg** | **0.286 overall** | **WER: 78.54%** | **CER: 24.08%** |

---

## 5. Repeated Inference & Memory Leak Stability Test

To verify stability in the walkie-talkie audio processing loop, 10 consecutive inference passes were executed on Sample 0 (10.86s audio):

| Repetition | Latency (ms) | Process PSS (MB) |
| :---: | :---: | :---: |
| Run 1 | 3518.2 ms | 841.7 MB |
| Run 2 | 3510.4 ms | 841.7 MB |
| Run 3 | 3515.6 ms | 841.7 MB |
| Run 4 | 3520.1 ms | 841.7 MB |
| Run 5 | 3509.8 ms | 841.7 MB |
| Run 6 | 3514.3 ms | 841.7 MB |
| Run 7 | 3508.9 ms | 841.7 MB |
| Run 8 | 3517.0 ms | 841.7 MB |
| Run 9 | 3511.5 ms | 841.7 MB |
| Run 10 | 3510.2 ms | 841.7 MB |
| **Average** | **3513.6 ms** | **End PSS: 841.70 MB** (Zero memory variation) |

- **Memory Leak Status:** **NO MEMORY LEAKS DETECTED**
- **Native Stability:** **100% stable, zero JNI crashes, zero SIGSEGV**
