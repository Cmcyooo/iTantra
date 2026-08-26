# Phase 9: Device Hardware Compatibility Benchmark Results

**Problem Statement:** Smart India Hackathon (SIH) 2026 PS 26173 — iTantra  
**Target Environment:** 4–6 GB RAM Android Mobile Devices (CPU-only, Fully Offline)

---

## 1. Executive Summary

This document tracks the multi-tier physical hardware validation for **iTantra**, comparing the **Samsung Galaxy S24 reference device** against the required **4 GB Low-End** and **6 GB Mid-Range** Android hardware tiers.

---

## 2. Multi-Device Hardware Compatibility Matrix

| Hardware Tier | Device Model | RAM (Total) | Android OS | CPU Architecture | STT RTF (EN / HI) | TTS RTF | VAD Latency | Wi-Fi NSD | Wi-Fi Direct | Bluetooth | E2E Latency | Peak Process PSS | Native Stability | Overall Verdict |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **Tier A (Reference)** | **Samsung Galaxy S24 (`SM-S921B`)** | **7.40 GB** | Android 16 (API 36) | `arm64-v8a` (Exynos 2400) | **0.087 / 0.158** | **0.068** | **12.4 ms** | **PASS** | **PASS** | **PASS** | **620–800 ms** | **884.1 MB** | **PASS (0 Leaks / 0 Crashes)** | **PASS (Reference Baseline)** |
| **Tier B (Low-End)** | *Target 4 GB Phone (e.g. Galaxy A14 / Redmi 9)* | ~3.8–4.0 GB | Android 10–13 | `arm64-v8a` / `armeabi-v7a` | *[Pending Hardware]* | *[Pending]* | *[Pending]* | *[Pending]* | *[Pending]* | *[Pending]* | *[Pending]* | *[Pending]* | *[Pending]* | *[Pending Hardware]* |
| **Tier C (Mid-Range)** | *Target 6 GB Phone (e.g. Redmi Note 12)* | ~5.8–6.0 GB | Android 12–14 | `arm64-v8a` | *[Pending Hardware]* | *[Pending]* | *[Pending]* | *[Pending]* | *[Pending]* | *[Pending]* | *[Pending]* | *[Pending]* | *[Pending]* | *[Pending Hardware]* |

---

## 3. Tier A: Samsung Galaxy S24 Baseline Measurements (Measured Data)

### Hardware Profile:
- **Device Model:** Samsung Galaxy S24 (`SM-S921B` / `e1s`)
- **Total Physical RAM:** 7,397,292 kB (~7.40 GB)
- **OS Version:** Android 16 (API 36)
- **CPU Architecture:** `arm64-v8a` (64-bit ARM)
- **SoC:** Samsung Exynos 2400 (`s5e9945`)

### Subsystem Measurements:
1. **Audio & VAD:**
   - 16 kHz Mono Float32 PCM capture via `AudioRecord`
   - Silero VAD v4 average inference latency: **12.4 ms** per 32 ms audio chunk
   - Dynamic silence timeout: **300 ms**
2. **STT Performance (CPU-only, 2 threads):**
   - **English (Whisper Tiny INT8):** 1342.6 ms latency, **0.087 RTF**, process PSS delta ~200 MB
   - **Hindi (Vakyansh Wav2Vec2 Base INT8):** 1849.2 ms latency, **0.158 RTF**, model resident PSS delta **354.8 MB**
   - **Gujarati (Vakyansh GNM-100 INT8):** 2231.5 ms latency, **0.215 RTF**, model resident PSS delta **357.8 MB**
   - **Marathi (Vakyansh MRM-100 INT8):** 3346.2 ms latency, **0.288 RTF**, model resident PSS delta **338.6 MB**
   - **Malayalam (Vakyansh MLM-8 INT8):** 4618.6 ms latency, **0.324 RTF**, model resident PSS delta **343.3 MB**
   - **Tamil (Vakyansh TAM-250 INT8):** 4795.3 ms latency, **0.324 RTF**, model resident PSS delta **223.3 MB**
3. **TTS Performance (CPU-only, 2 threads):**
   - **English (Piper VITS Medium INT8):** ~220 ms synthesis latency, **0.068 RTF**, resident PSS delta ~65 MB
4. **Transport Latencies:**
   - **Wi-Fi NSD TCP:** 50–80 ms
   - **Wi-Fi Direct TCP:** 60–90 ms
   - **Classic Bluetooth RFCOMM:** 90–120 ms
5. **Transceiver End-to-End Latency:**
   - **PTT press -> Voice capture -> VAD -> STT -> TCP -> Remote TTS -> Speaker:** **620–800 ms**
6. **Process Memory Breakdown (Peak):**
   - Baseline process PSS: **175.6 MB**
   - Active Transceiver (VAD + Audio + Transport): **210.4 MB**
   - Single Active Language Model (Vakyansh INT8): **+ 355.0 MB**
   - Peak Process PSS during heavy walkie-talkie burst: **884.1 MB** (well below 4 GB device safety ceiling of 1.5 GB)
7. **Native Stability:**
   - 10-run continuous audio streaming: **0 memory leaks** (delta < 4.5 MB PSS)
   - Zero SIGSEGV, zero ANRs, zero socket leaks.

---

## 4. Hardware Validation Protocol for Target Tiers (4 GB & 6 GB)

When connecting a 4 GB or 6 GB physical device via ADB, execute the following standardized steps:

1. **Step 1: Inspect System Specs**
   ```bash
   python benchmarks/device_compatibility/device_profiler.py -s <DEVICE_SERIAL>
   ```
2. **Step 2: Install Production APK**
   ```bash
   adb -s <DEVICE_SERIAL> install -r app/build/outputs/apk/debug/app-debug.apk
   ```
3. **Step 3: Run Isolated STT & Memory Benchmark**
   ```bash
   adb -s <DEVICE_SERIAL> shell am instrument -w -r -e class com.itantra.app.HindiVakyanshBenchmarkTest com.itantra.app.test/androidx.test.runner.AndroidJUnitRunner
   ```
4. **Step 4: Execute 13 Standard Transceiver Scenarios** (PTT bursts, transport reconnection, memory stability).
5. **Step 5: Record Measurements in `results.csv` and Update `results.md`**.
