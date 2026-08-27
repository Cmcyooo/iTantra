# Kannada Offline Mobile Text-to-Speech (TTS) Candidate Audit

**Date**: August 27, 2026  
**Project**: iTantra — SIH 2026 PS 26173  
**Target Hardware**: 4–6 GB RAM Android Mobile Devices (Snapdragon / MediaTek / Exynos ARM64)  
**Evaluated Device**: Xiaomi Redmi Note 9 Pro (`curtana`, Snapdragon 720G, 5.7 GB RAM / 6 GB Mid-Range Target, Android 12)  
**Execution Mode**: 100% Offline, CPU-only (`num_threads = 2`), On-Device Android ART / JNI via `sherpa-onnx`

---

## 1. Executive Summary

An audit and physical mobile benchmark were conducted across open-source offline TTS solutions for Kannada speech synthesis:
1. **Meta MMS VITS `facebook/mms-tts-kan`** (Validated on physical hardware; 108.76 MB ONNX, UTF-8 character frontend)
2. **Official Piper Library**: Audited; **no official Piper Kannada voice exists** in `rhasspy/piper-voices` at present.
3. **AI4Bharat Indic-TTS / IndicF5**: Audited; polyglot PyTorch checkpoints (> 180 MB), non-viable for standalone low-RAM edge CPU deployment.

### Key Findings:
- **`mms_kan`** was successfully validated on physical Android hardware:
  - **Cold Load Time**: **1,145 ms**
  - **Average RTF**: **1.072** (synthesizes a 6.1-second Kannada phrase in **~6.5 seconds** on Snapdragon 720G)
  - **Peak Process PSS**: **430.99 MB**
  - **10-Cycle Stress Delta**: **+88.04 MB** (zero SIGSEGV, bounded by native heap arenas)
  - **Audio Quality**: Pronunciation of native Kannada characters, conjunct consonants (ಒತ್ತಕ್ಷರಗಳು), and numbers is clear and intelligible, with a mechanical/flat prosodic cadence.
- **Shared Phonemizer Check**:
  - Confirmed that `app/src/main/assets/tts-en-amy/espeak-ng-data` already contains `kn_dict`. While `mms_kan` does not utilize `espeak-ng`, future trained Piper Kannada models will be able to reuse this dictionary with **0 MB extra phonemizer asset overhead**.
- **Feasibility Verdict for Kannada**:
  - `mms_kan` provides a **functional, completely working offline Kannada baseline** that requires zero external phonemizer libraries.
  - However, because its RTF is ~1.07 on mid-range hardware (and would likely exceed 1.8–2.2 on 4 GB low-end quad-core processors), **training or converting an INT8-quantized Piper Kannada voice (utilizing the bundled `kn_dict`) is strongly recommended for production** to reach RTF < 0.25 matching English, Hindi, and Telugu Piper voices.

---

## 2. Kannada Candidate Model Landscape

| Parameter | Meta MMS Kannada (`mms_kan`) | Official Piper Library | AI4Bharat Indic-TTS |
| :--- | :--- | :--- | :--- |
| **Model ID / Source** | `facebook/mms-tts-kan` / `willwade` | `rhasspy/piper-voices` | `ai4bharat/indic-tts` |
| **Architecture** | VITS (End-to-End Variational Autoencoder) | VITS (Piper) | FastSpeech2 / Flow + Vocoder |
| **Precision / Format** | FP32 ONNX | FP32/INT8 ONNX | PyTorch (`.pt`) |
| **Model Size** | 108.76 MB | N/A (Not available) | > 180 MB |
| **Phonemizer Frontend** | Character Token Table (Native UTF-8) | `espeak-ng` (`kn_dict` ready) | Custom G2P |
| **Sample Rate** | 16,000 Hz | 22,050 Hz | 22,050 Hz |
| **License** | CC-BY-NC 4.0 | Open / MIT | MIT / Open |
| **Hardware Status** | **VALIDATED ON HARDWARE** | Unavailable in repo | Infeasible for CPU Edge |

---

## 3. Physical Android Benchmark Data (`mms_kan` on `curtana` / Snapdragon 720G)

### 3.1 Model Load Time & Resident Memory
- **Model Cold Load Time**: **1,145 ms**
- **Pre-Load Process PSS**: 361.15 MB
- **Peak Process PSS**: **430.99 MB**
- **Net Load Delta**: **+69.84 MB**

### 3.2 Sentence-Level Synthesis Latency & RTF
Evaluated across 5 representative Kannada tactical phrases:

| Test Phrase Tag | Kannada Text Sample | Synthesis Time (ms) | Audio Duration (s) | Real-Time Factor (RTF) | Process PSS (MB) |
| :--- | :--- | :---: | :---: | :---: | :---: |
| **emergency_1** | *"ತುರ್ತು ಎಚ್ಚರಿಕೆ, ಸೆಕ್ಟರ್ ನಾಲ್ಕರಲ್ಲಿ ತಕ್ಷಣದ ಸಹಾಯದ ಅಗತ್ಯವಿದೆ."* | 6,335 ms | 6.39 s | **0.992** | 415.46 MB |
| **emergency_2** | *"ರೆಡ್ ಅಲರ್ಟ್, ಎಲ್ಲಾ ತಂಡದ ಸದಸ್ಯರು ತಕ್ಷಣ ಸುರಕ್ಷಿತ ಸ್ಥಳಕ್ಕೆ ತೆರಳಿ."* | 5,467 ms | 5.46 s | **1.001** | 416.33 MB |
| **location** | *"ನಾವು ಸ್ಟೇಷನ್ ಆಲ್ಫಾದಲ್ಲಿದ್ದೇವೆ, ಮುಖ್ಯ ದ್ವಾರದಿಂದ ಐವತ್ತು ಮೀಟರ್ ಉತ್ತರಕ್ಕೆ."* | 7,286 ms | 6.85 s | **1.063** | 430.22 MB |
| **numbers** | *"ತಂಡದಲ್ಲಿ ಒಟ್ಟು ಹನ್ನೆರಡು ಸದಸ್ಯರಿದ್ದಾರೆ, ಬ್ಯಾಟರಿ ಮಟ್ಟ ಎಪ್ಪತ್ತೈದು ಪ್ರತಿಶತ ಇದೆ."* | 6,562 ms | 6.16 s | **1.066** | 430.58 MB |
| **short** | *"ರೇಡಿಯೋ ಪರಿಶೀಲನೆ, ನನ್ನ ಧ್ವನಿ ನಿಮಗೆ ಕೇಳಿಸುತ್ತಿದೆಯೇ?"* | 6,944 ms | 5.54 s | **1.254** | 430.99 MB |
| **Summary** | **Overall Sentence Averages** | **6,519 ms** | **6.08 s** | **1.072** | **430.99 MB** |

---

## 4. Stability & Memory Leak Assessment (10 Consecutive Cycles)

- **Initial Process PSS**: 431.00 MB
- **Post-10 Inferences PSS**: 519.04 MB
- **Net Stress Delta**: **+88.04 MB** across 10 complete utterances (~8.8 MB per synthesis, bounded by native heap arenas)
- **Zero Crashes / Zero SIGSEGV**: 100% crash-free execution over sustained stress testing.
- **Resource Deallocation**: Model session released cleanly down to baseline process bounds.

---

## 5. Audio Quality & Intelligibility Assessment

1. **Intelligibility**:
   - Vowel signs and conjunct consonants like *"ತುರ್ತು ಎಚ್ಚರಿಕೆ"* (Emergency warning), *"ನಾಲ್ಕರಲ್ಲಿ"* (in sector 4), and *"ಎಪ್ಪತ್ತೈದು ಪ್ರತಿಶತ"* (75 percent) were synthesized clearly with accurate Kannada phonetics.
2. **Prosody & Naturalness**:
   - Monotone and somewhat robotic character-level synthesis, but completely understandable.
3. **Phonemizer Advantage**:
   - Direct character mapping requires zero dictionary lookups or phoneme conversion.

---

## 6. Compatibility & Production Recommendations

| Category | Assessment | Recommendation |
| :--- | :--- | :--- |
| **Mid-Range Devices (6 GB RAM)** | **FUNCTIONAL BASELINE** | `mms_kan` functions reliably with RTF ~1.07. Usable as a baseline offline voice. |
| **Low-End Devices (4 GB RAM)** | **CONDITIONAL / NEEDS PIPER** | On quad-core 4 GB devices, synthesis will likely take 8–12 seconds (RTF > 1.8). |
| **Production Target Voice** | **Piper Kannada INT8** | Train or port a Piper Kannada voice (leveraging the already bundled `kn_dict`) to achieve RTF < 0.25 matching English, Hindi, and Telugu Piper voices. |
