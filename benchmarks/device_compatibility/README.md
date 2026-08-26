# Phase 9: Multi-Device Hardware Compatibility & Validation Framework

**Problem Statement:** Smart India Hackathon (SIH) 2026 PS 26173 — iTantra  
**Target Device Profile:** 4–6 GB RAM Android Mobile Devices (API 24+, Android 7.0+)  
**Runtime Requirement:** 100% Offline, CPU-Only Inference (No GPU/NPU or cloud dependencies)

---

## 1. Purpose & Scope

This framework provides a **standardized, repeatable benchmark and validation procedure** to verify that the single production APK of **iTantra** operates reliably across the full spectrum of low-end and mid-range Android hardware.

### Hardware Validation Matrix Tiers:
1. **Tier A: Development Reference Device** (Samsung Galaxy S24, ~7.4 GB RAM, Android 16)
2. **Tier B: Low-End Target Device** (~4 GB Physical RAM, e.g. Redmi 9 / Samsung Galaxy A14 / Moto G32)
3. **Tier C: Mid-Range Target Device** (~6 GB Physical RAM, e.g. Redmi Note 12 / OnePlus Nord CE 3 Lite)

> [!IMPORTANT]
> **Single APK Policy**: The exact same production release APK binary (`app-release.apk` or `app-debug.apk`) and exact model binaries must be evaluated on all hardware tiers. **No device-specific builds, lower-resolution models, or architectural forks are permitted.**

---

## 2. Compatibility Test Matrix (9 Subsystems)

| Subsystem | Metric | Target Low-End (4 GB) | Target Mid-Range (6 GB) | Reference S24 (7.4 GB) |
| :--- | :--- | :---: | :---: | :---: |
| **1. System** | OS & Architecture | Android 7.0–14 (ARM64/ARMv7) | Android 10–15 (ARM64) | Android 16 (ARM64) |
| **2. App Install** | Package Install & Native ABI | Clean install, no missing `.so` | Clean install, no missing `.so` | Passed |
| **3. Audio** | 16 kHz Capture & Playback | Stable PCM, zero buffer underrun | Stable PCM, zero buffer underrun | Verified (16 kHz Float32) |
| **4. VAD** | Silero VAD Inference & Latency | < 25 ms / chunk, 300 ms silence | < 20 ms / chunk, 300 ms silence | 10–15 ms / chunk |
| **5. STT (Offline)** | Inference RTF & Memory PSS | RTF < 0.40, Model PSS < 400 MB | RTF < 0.30, Model PSS < 400 MB | RTF: 0.087 (EN), 0.158 (HI) |
| **6. TTS (Offline)** | Synthesis RTF & Playback | RTF < 0.20, Model PSS < 150 MB | RTF < 0.15, Model PSS < 150 MB | RTF: 0.06–0.08 |
| **7. Network** | Wi-Fi / NSD / Wi-Fi Direct / BT | P2P discovery & socket transfer | P2P discovery & socket transfer | All 3 transports verified |
| **8. Transceiver** | End-to-End Latency (PTT -> Audio)| < 1200 ms | < 900 ms | ~600–800 ms |
| **9. Stability** | Repeated 10-Run Leak & Crash | Delta < 20 MB PSS, 0 crashes | Delta < 15 MB PSS, 0 crashes | Delta < 4.5 MB, 0 crashes |

---

## 3. Test Execution Scenarios (13 Standard Scenarios)

The validation protocol requires running 13 standardized test scenarios in sequence:

1. **Clean App Launch:** Measure cold boot time, native library loading, and initial process PSS.
2. **Idle Baseline (2 Minutes):** Confirm background CPU usage stays near 0% and memory remains flat.
3. **Short PTT Utterances (10x, 1–3s):** Rapid push-to-talk cycles verifying VAD boundary detection.
4. **Medium Utterances (10x, 4–8s):** Normal walkie-talkie communication assessing STT chunking.
5. **Long Utterances (5x, 10–20s):** Extended speech testing single-pass acoustic memory buffer safety.
6. **Repeated STT Inferences (10x):** Re-transcribing fixed audio to detect any native memory leaks.
7. **Repeated TTS Syntheses (10x):** Generating audio from fixed text to verify memory return.
8. **Transceiver E2E Loop (10x):** Audio capture -> VAD -> STT -> Network transmission -> Remote TTS -> Audio output.
9. **Wi-Fi Transport Reconnection:** Disconnecting and reconnecting Wi-Fi AP while maintaining NSD state.
10. **Wi-Fi Direct Group Reconnection:** P2P group re-formation and socket re-establishment.
11. **Bluetooth RFCOMM Reconnection:** Re-pairing and socket recovery without device restart.
12. **App Background/Foreground Cycling:** Moving app to background during active transport and resuming.
13. **Screen Lock/Unlock Power State:** Ensuring wake locks and background services prevent socket termination.

---

## 4. Performance Acceptance Criteria

Each metric is graded strictly on measured evidence:

* **`PASS`**: Meets latency, RTF, memory, and zero-crash requirements on the target hardware.
* **`CONDITIONAL`**: Operates without crashing but exceeds one latency or memory threshold (requires runtime tuning or domain vocabulary optimization).
* **`FAIL`**: Crashes (SIGSEGV/ANR), native memory leak > 50 MB over 10 runs, or RTF > 1.0 (slower than real-time speech).

---

## 5. Automated Device Profiling Tool

Use `device_profiler.py` to automatically collect hardware specifications, memory statistics via ADB, and log validation entries:

```bash
# Query attached Android device and generate compatibility entry
python benchmarks/device_compatibility/device_profiler.py --device-id <ADB_DEVICE_SERIAL>
```
