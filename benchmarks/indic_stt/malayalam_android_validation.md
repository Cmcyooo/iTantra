# Phase 7.5: Malayalam Vakyansh INT8 ONNX Physical Android Validation Report

**Problem Statement:** SIH 2026 PS 26173 — iTantra  
**Validation Date:** 2026-08-27 00:45:00  
**Test Method:** Android Instrumented Test (`MalayalamVakyanshBenchmarkTest`) executed on physical device via ADB  
**Execution Mode:** Fully Offline (Airplane Mode / Local CPU Inference)

---

## 1. Executive Summary & Verdict

We executed the full on-device benchmark suite for the candidate Malayalam STT model (**Vakyansh Wav2Vec2 Malayalam MLM-8 INT8 ONNX**) on a physical Android device (**Samsung Galaxy S24 / SM-S921B**, ARM64, 8 GB RAM).

### On-Device Performance Highlights:
- **On-Device Accuracy:** **52.84% WER** and **13.84% CER** on FLEURS Malayalam. It completely eliminates Whisper Tiny's catastrophic **193.8% WER** and **147.2% CER** (which hallucinated repeating English text).
- **Cold Model Load Time:** **818.78 ms** via direct ONNX memory-mapping.
- **Inference Latency & Speed:** Average **4618.6 ms** for 14.26s audio (**RTF: 0.324**, ~3.1x faster than real-time on 2 CPU threads).
- **Model Resident Memory Delta:** **343.28 MB PSS**.
- **Inference Stability & Leaks:** **10 repeated inference passes** yielded **3441.1 ms** average latency with **< 0.5 MB PSS variation**, **zero memory leaks**, and **zero crashes**.

### Classification Verdict:
# **MOBILE CANDIDATE**
*(Feasible for 4–6 GB RAM Android devices with CPU-only inference; outputs clean Malayalam script).*

---

## 2. Validated Binary Artifact & Integrity

- **Model Artifact Name:** `vakyansh_malayalam_base.int8.onnx`
- **File Size:** **117.03 MB** (122,718,942 bytes)
- **Quantization:** Dynamic INT8 (`QuantType.QInt8` on MatMul/Gemm attention layers)
- **Host & On-Device SHA-256 Hash:**
  ```text
  c0922d209f67461c740784770c2c53ec6160d69c25b1d8c912eee692b8b2eea6
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
| **Decoding Method** | Single-pass Greedy CTC argmax collapse (72 Malayalam classes) |

---

## 4. Per-Sample Physical Android Benchmark Results (FLEURS Malayalam)

| Sample ID | WAV File | Audio Duration | Android Inference Latency | Real-Time Factor (RTF) | Word Errors | Char Errors |
| :---: | :--- | :---: | :---: | :---: | :---: | :---: |
| **0** | `000.wav` | 11.40 s | 3765.7 ms | 0.330 | 5 | 8 |
| **1** | `001.wav` | 23.58 s | 8698.5 ms | 0.369 | 8 | 13 |
| **2** | `002.wav` | 25.44 s | 8750.0 ms | 0.344 | 11 | 21 |
| **3** | `003.wav` | 6.84 s | 1950.2 ms | 0.285 | 3 | 5 |
| **4** | `004.wav` | 11.94 s | 3574.2 ms | 0.299 | 8 | 14 |
| **5** | `005.wav` | 14.88 s | 4690.9 ms | 0.315 | 8 | 12 |
| **6** | `006.wav` | 11.40 s | 3434.5 ms | 0.301 | 7 | 11 |
| **7** | `007.wav` | 18.24 s | 5887.4 ms | 0.323 | 9 | 15 |
| **8** | `008.wav` | 8.94 s | 2785.4 ms | 0.312 | 8 | 13 |
| **9** | `009.wav` | 15.84 s | 4982.0 ms | 0.315 | 11 | 18 |
| **10** | `010.wav` | 8.76 s | 2550.7 ms | 0.291 | 8 | 12 |
| **11** | `011.wav` | 13.92 s | 4353.1 ms | 0.313 | 11 | 18 |
| **SUMMARY** | **12 Samples** | **171.18 s total** | **4618.6 ms avg** | **0.324 overall** | **WER: 52.84%** | **CER: 13.84%** |

---

## 5. Repeated Inference & Memory Leak Stability Test

To verify stability in the walkie-talkie audio processing loop, 10 consecutive inference passes were executed on Sample 0 (11.40s audio):

| Repetition | Latency (ms) | Process PSS (MB) |
| :---: | :---: | :---: |
| Run 1 | 3448.2 ms | 1226.4 MB |
| Run 2 | 3438.6 ms | 1226.4 MB |
| Run 3 | 3442.1 ms | 1226.4 MB |
| Run 4 | 3439.8 ms | 1226.4 MB |
| Run 5 | 3445.0 ms | 1226.4 MB |
| Run 6 | 3441.7 ms | 1226.4 MB |
| Run 7 | 3436.4 ms | 1226.4 MB |
| Run 8 | 3440.2 ms | 1226.4 MB |
| Run 9 | 3443.1 ms | 1226.4 MB |
| Run 10 | 3435.8 ms | 1226.3 MB |
| **Average** | **3441.1 ms** | **End PSS: 1226.34 MB** (Delta < 0.2 MB over 10 runs) |

- **Memory Leak Status:** **NO MEMORY LEAKS DETECTED**
- **Native Stability:** **100% stable, zero JNI crashes, zero SIGSEGV**
