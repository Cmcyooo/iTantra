# Phase 7.6: Bengali Mobile STT Candidate Audit

**Problem Statement:** SIH 2026 PS 26173 — iTantra

**Date:** 2026-08-27 01:01:49

**Target Environment:** 4–6 GB RAM Android Mobile Devices (CPU-only, Fully Offline)


---

## 1. Executive Summary

In Phase 7.1, stock **Whisper Tiny Multilingual INT8** failed on Bengali with **122.6% WER** and **98.7% CER** (Repetition Loop / Latin Token Pollution).

In Phase 7.6, we audited open-source Bengali STT architectures and identified **Vakyansh Wav2Vec2 Bengali** as the primary lightweight candidate.

- **Accuracy Improvement:** WER dropped from **122.6%** (Whisper Tiny) to **55.98%** (Vakyansh INT8 ONNX), and CER dropped from **98.7%** to **16.37%**.
- **Quantization & Footprint:** Converted to dynamic INT8 ONNX (`vakyansh_bengali_base.int8.onnx`, **117.03 MB**).
- **CPU Speed:** Desktop RTF is **0.120** (~8-10x faster than real-time speech).


---

## 2. Bengali STT Benchmark Comparison (FLEURS Dataset)

| Metric | Whisper Tiny Multilingual INT8 (Baseline) | Vakyansh Bengali PyTorch FP32 | Vakyansh Bengali INT8 ONNX | Status / Delta |
| :--- | :---: | :---: | :---: | :---: |
| **Architecture** | Whisper (Autoregressive Seq2Seq) | Wav2Vec2 Base (CTC) | Wav2Vec2 Base (CTC) | Non-autoregressive CTC |
| **Model Footprint** | 98.81 MB | ~378 MB | **117.03 MB** | **Fits < 150 MB budget** |
| **WER (Word Error Rate)** | 122.6% (Catastrophic) | 53.85% | **55.98%** | **Dramatic reduction** |
| **CER (Char Error Rate)** | 98.7% | 14.92% | **16.37%** | **Substantial improvement** |
| **Avg STT Latency** | ~1200 ms | 1080.8 ms | **1569.7 ms** | Fast single-pass execution |
| **Real-Time Factor (RTF)** | ~0.10 | 0.082 | **0.120** | Real-time CPU ready |
| **Licensing** | MIT / Apache 2.0 | MIT | **MIT** | Fully open-source |
| **Audit Status** | REJECTED (Repetition Loop / Latin Token Pollution) | Primary Mobile Candidate | **PRIMARY MOBILE CANDIDATE** | **Recommended for Android** |


---

## 3. Audited Model Families

| Model | Architecture | Params | Size (INT8) | License | Feasibility | Classification |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: |
| **Vakyansh Wav2Vec2 Bengali** | Wav2Vec2 Base CTC | 94.4M | **~117.0 MB** | MIT | HIGHLY FEASIBLE (4–6 GB) | **PRIMARY MOBILE CANDIDATE** |
| **AI4Bharat IndicConformer Bengali (120M)** | Conformer Hybrid CTC | 120M | ~120 MB | MIT / CC-BY | FEASIBLE (Mid-range) | **STRONG ALTERNATIVE** |
| **Whisper Tiny Multilingual INT8** | Whisper Autoregressive | 39M | 98.81 MB | MIT | FEASIBLE SIZE / UNUSABLE | **REJECTED (Looping)** |


---

## 4. Validated Binary Artifact Details

- **Artifact File:** `benchmarks/indic_stt/models/vakyansh_bengali_base.int8.onnx`
- **File Size:** **117.03 MB**
- **Cryptographic SHA-256 Hash:**
  ```text
  8aec0865d879c1428f413fe70e6c5f9ff22a2dd678c66f529be4f5794e49b961
  ```


---

## 5. Sample Transcriptions (INT8 ONNX Output)

### Sample #0 (16.20s audio)

- **Reference:**
  > `জার্মানির অনেক বেক করা খাবারগুলিতে বাদাম হ্যাজনেলট এবং অন্যান্য বাদামের উপাদান পাওয়া যায় পছন্দের কেকগুলি প্রায়শই একটি কাপ ভরা কফির সাথে ভাল মানায়`

- **Vakyansh INT8 ONNX Output:**
  > `জারনমানের অনেক বেগ করা খাবার গুলিতে বাদাম হ্যাঁজ নেল এবং অন্যান্য বাদামের উপাদান পাওয়া যায় পছন্দের কি গুলি প্রায়সায় একটি কাবধরা কফির সাথে ভালোমানায`

### Sample #1 (10.44s audio)

- **Reference:**
  > `একজন শুধুমাত্র আশ্চর্য হতে পারে এই ভেবে যে যখন নতুন কিছু আসে তখন কী-বোর্ড কীরকম হবে`

- **Vakyansh INT8 ONNX Output:**
  > `একজন শুতমাত্র আশ্চর্য হতে পারে এই ভেবে যে যখন নতুন কিছু আসে তখন কি বোট কি রকম হবে`

### Sample #2 (14.58s audio)

- **Reference:**
  > `হাইতিয়ান ইনস্টিটিউট ফর জাস্টিস অ্যান্ড ডেমোক্রেসি এর স্বতন্ত্র সমীক্ষাতে উল্লেখ করা হয়েছে যে নেপালি জাতিসংঘের শান্তিরক্ষা বাহিনী অজান্তেই হাইতিতে এই রোগটি নিয়ে এসেছিল`

- **Vakyansh INT8 ONNX Output:**
  > `হায়িতিয়ান ইন নস্টিটিউট ফর জাস্টিস এন্ড ডেমুক্রেসি এর স্বতন্ত্র সমীক্ষাতে উল্লেখ করা হয়েছে যে নেপালি যাতি সঙ্গের শান্তি রক্ষা বাহিনি অজানতেই হায়িতিতে এই রকটি নিয়ে এসেছিল`

### Sample #3 (10.32s audio)

- **Reference:**
  > `দুটি পয়েন্টের মাঝে দুজন ড্রাইভার এবং তাদের যানের মধ্যে যে চলন ও কথাবার্তা চলে সেই বিদ্যাকেই ট্রাফিক ফ্লো বলে`

- **Vakyansh INT8 ONNX Output:**
  > `তুটি পয়েন্টের মাঝে দুজন ড্রাইভার এবং তাদের জানের মধ্যে যে চলন ও কথাবাততরা চলে সেই বিদ্যাকেই ট্রাফিক ফ্লো বলে`

