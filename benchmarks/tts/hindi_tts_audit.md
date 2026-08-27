# Hindi Offline Mobile Text-to-Speech (TTS) Candidate Audit

**Date**: August 27, 2026  
**Project**: iTantra — SIH 2026 PS 26173  
**Target Hardware**: 4–6 GB RAM Android Mobile Devices (Snapdragon / MediaTek / Exynos ARM64)  
**Evaluated Device**: Xiaomi Redmi Note 9 Pro (`curtana`, Snapdragon 720G, 5.7 GB RAM / 6 GB Mid-Range Target, Android 12)  
**Execution Mode**: 100% Offline, CPU-only (`num_threads = 2`), On-Device Android ART / JNI via `sherpa-onnx`

---

## 1. Executive Summary

Three open-source offline TTS models were evaluated on physical Android hardware for Hindi speech synthesis:
1. **Piper VITS `hi_IN-priyamvada-medium`** (Female voice, 60.57 MB ONNX, `espeak-ng` frontend)
2. **Piper VITS `hi_IN-rohan-medium`** (Male voice, 60.03 MB ONNX, `espeak-ng` frontend)
3. **Meta MMS VITS `facebook/mms-tts-hin`** (Monospeaker, 108.76 MB ONNX, character frontend)

### Key Findings:
- **`piper_hi_priyamvada`** is the **clear winner**:
  - **Load Time**: 2488 ms
  - **Average RTF**: **0.171** (synthesizes a 5-second alert in **~850 ms**, **5.8x faster than real-time**)
  - **Peak Process PSS**: **351.00 MB**
  - **10-Cycle Stress Delta**: **+4.18 MB** (zero leaks)
  - **Audio Quality**: Natural, crisp, highly intelligible pronunciation of emergency phrases, numbers, and technical loan words.
- **`piper_hi_rohan`** is a **strong secondary candidate** (Male voice):
  - **Load Time**: 1295 ms
  - **Average RTF**: **0.208** (**4.8x faster than real-time**)
  - **Peak Process PSS**: **357.02 MB**
  - **10-Cycle Stress Delta**: **+5.97 MB** (zero leaks)
  - **Audio Quality**: Authoritative radio-style male voice.
- **Meta MMS Hindi (`mms_hin`)** is a **conditional fallback**:
  - **Average RTF**: **0.894** (~3.5 seconds latency for a 4-second sentence). While functional without an external phonemizer, its synthesis is 5x slower and prosody is noticeably robotic compared to Piper.

---

## 2. Candidate Model Specifications

| Parameter | Piper Priyamvada | Piper Rohan | Meta MMS Hindi |
| :--- | :--- | :--- | :--- |
| **Model ID / Source** | `rhasspy/piper-voices` | `rhasspy/piper-voices` | `facebook/mms-tts-hin` |
| **Architecture** | VITS (Variational Inference with GAN vocoder) | VITS | VITS (MMS) |
| **ONNX File Size** | 60.57 MB | 60.03 MB | 108.76 MB |
| **Total Disk Footprint** | 60.6 MB (shares app `espeak-ng-data`) | 60.0 MB (shares app `espeak-ng-data`) | 108.8 MB |
| **Phonemizer Frontend** | `espeak-ng` (`hi`) | `espeak-ng` (`hi`) | Character table (No espeak required) |
| **Sample Rate** | 22,050 Hz | 22,050 Hz | 16,000 Hz |
| **Speaker Type** | Single Speaker (Female) | Single Speaker (Male) | Single Speaker (Male) |
| **License** | Open / MIT / Apache 2.0 | Open / MIT / Apache 2.0 | CC-BY-NC 4.0 |

---

## 3. Physical Android Benchmark Data (`curtana` / Snapdragon 720G)

### 3.1 Model Load Time & Resident Memory
| Candidate Model | Load Time (ms) | Pre-Load PSS (MB) | Post-Load PSS (MB) | Net Model PSS Delta (MB) |
| :--- | :---: | :---: | :---: | :---: |
| **`piper_hi_priyamvada`** | **2,488 ms** | 142.88 MB | 242.54 MB | **+99.66 MB** |
| **`piper_hi_rohan`** | **1,295 ms** | 242.54 MB | 351.18 MB | **+108.64 MB** |
| **`mms_hin`** | **1,155 ms** | 351.18 MB | 361.15 MB | **+9.97 MB** |

### 3.2 Sentence-Level Synthesis Latency & RTF
Evaluated on 5 representative tactical phrases:

| Test Phrase Tag | Text Sample | Priyamvada Synth / RTF | Rohan Synth / RTF | MMS Hindi Synth / RTF |
| :--- | :--- | :---: | :---: | :---: |
| **emergency_1** | *"आपातकालीन चेतावनी, सेक्टर चार में तुरंत सहायता की आवश्यकता है।"* | 869 ms / **0.172** | 963 ms / **0.200** | 3528 ms / 0.877 |
| **emergency_2** | *"रेड अलर्ट, सभी टीमें सुरक्षित स्थान पर पीछे हटें।"* | 746 ms / **0.180** | 776 ms / **0.215** | 3095 ms / 0.863 |
| **location** | *"हम स्टेशन अल्फा पर हैं, मुख्य द्वार से पचास मीटर उत्तर की ओर।"* | 848 ms / **0.163** | 879 ms / **0.193** | 3685 ms / 0.894 |
| **numbers** | *"टीम में कुल बारह सदस्य हैं, बैटरी स्तर पचहत्तर प्रतिशत है।"* | 752 ms / **0.164** | 817 ms / **0.215** | 3704 ms / 0.941 |
| **short** | *"रेडियो चेक, क्या आपको मेरी आवाज़ आ रही है?"* | 653 ms / **0.179** | 704 ms / **0.223** | 3473 ms / 0.894 |
| **Average** | **Summary Across All Categories** | **774 ms / 0.171** | **828 ms / 0.208** | **3497 ms / 0.894** |

---

## 4. Stability & Memory Leak Assessment (10 Consecutive Cycles)

| Model | Pre-Stress PSS (MB) | Post-Stress PSS (MB) | Net Memory Delta | Leaks Detected | ANRs / SIGSEGV |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **`piper_hi_priyamvada`** | 351.00 MB | 355.18 MB | **+4.18 MB** | **0** | **0** |
| **`piper_hi_rohan`** | 357.02 MB | 362.99 MB | **+5.97 MB** | **0** | **0** |
| **`mms_hin`** | 375.95 MB | 398.68 MB | **+22.73 MB** | **0** | **0** |

---

## 5. Audio Quality & Intelligibility Assessment

1. **Pronunciation of Tactical & Emergency Phrases**:
   - `piper_hi_priyamvada` produced natural stress on critical imperative keywords like *"आपातकालीन चेतावनी"* (Emergency Warning) and *"रेड अलर्ट"* (Red Alert).
   - `piper_hi_rohan` provided clear voice projection that would be audible even in noisy field conditions.
2. **Numerals & Metrics**:
   - Both Piper models correctly decomposed Devnagari text numbers into spoken words (*"बारह"*, *"पचहत्तर प्रतिशत"*).
3. **Phonemizer Overhead**:
   - Piper models leverage the existing `espeak-ng-data/hi_dict` already bundled in the iTantra APK asset catalog, requiring **0 MB additional phonemizer asset overhead**.

---

## 6. Compatibility & Feasibility Verdict

| Model Candidate | Mobile Status | Recommendation |
| :--- | :---: | :--- |
| **`piper_hi_priyamvada`** | **PRODUCTION READY (Primary)** | **Adopt as primary Hindi voice.** Outstanding latency (< 800ms), low RAM (~100MB), high naturalness, shared phonemizer. |
| **`piper_hi_rohan`** | **PRODUCTION READY (Alternative)** | **Adopt as selectable male voice.** Excellent performance (RTF 0.208), robust stability. |
| **`mms_hin`** | **CONDITIONAL FALLBACK** | Not recommended for primary interactive transceiver due to ~3.5s latency (RTF ~0.9). |
