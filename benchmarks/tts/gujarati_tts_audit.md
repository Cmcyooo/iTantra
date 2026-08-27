# Gujarati Offline Mobile Text-to-Speech (TTS) Candidate Audit

**Date**: August 27, 2026  
**Project**: iTantra — SIH 2026 PS 26173  
**Target Hardware**: 4–6 GB RAM Android Mobile Devices (Snapdragon / MediaTek / Exynos ARM64)  
**Evaluated Device**: Xiaomi Redmi Note 9 Pro (`curtana`, Snapdragon 720G, 5.7 GB RAM / 6 GB Mid-Range Target, Android 12)  
**Execution Mode**: 100% Offline, CPU-only (`num_threads = 2`), On-Device Android ART / JNI via `sherpa-onnx`

---

## 1. Executive Summary

An audit and physical mobile benchmark were conducted across open-source offline TTS solutions for Gujarati speech synthesis:
1. **Meta MMS VITS `facebook/mms-tts-guj`** (Validated on physical hardware; 108.75 MB ONNX, character frontend)
2. **`piper-gujarati-male` by Arjun4707** (Audited architecture; trained on AI4Bharat Rasa Gujarati dataset, ~63 MB Piper ONNX, `espeak-ng` frontend)
3. **AI4Bharat Indic-TTS / IndicF5** (Audited; polyglot diffusion / FastSpeech2 + HiFi-GAN, > 150 MB PyTorch, non-viable for standalone low-RAM edge CPU deployment)

### Key Findings:
- **`mms_guj`** was successfully validated on physical Android hardware:
  - **Load Time**: 1194 ms
  - **Average RTF**: **1.119** (borderline real-time on mid-range Snapdragon 720G; synthesizes a 3.8-second phrase in **~4.0 seconds**)
  - **Peak Process PSS**: **396.77 MB**
  - **10-Cycle Stress Delta**: **+21.08 MB**
  - **Audio Quality**: Pronunciation of native Gujarati characters and numerals is clear and intelligible, with a somewhat mechanical/flat prosodic cadence.
- **Feasibility Verdict for Gujarati**:
  - `mms_guj` provides a **functional, completely working offline Gujarati baseline** that requires zero external phonemizer libraries.
  - However, because its RTF is ~1.11 on mid-range hardware (and would likely exceed 1.5–2.0 on 4 GB low-end quad-core processors), **an INT8-quantized Piper Gujarati voice (such as `piper-gujarati-male` adapted for sherpa-onnx) should be prioritized for production deployment** to achieve the target RTF < 0.30.

---

## 2. Gujarati Candidate Model Landscape

| Parameter | Meta MMS Gujarati (`mms_guj`) | Piper Gujarati Male (Arjun4707) | AI4Bharat Indic-TTS / IndicF5 |
| :--- | :--- | :--- | :--- |
| **Model ID / Source** | `facebook/mms-tts-guj` / `willwade` | `Arjun4707/piper-gujarati-male` | `ai4bharat/indic-tts` |
| **Architecture** | VITS (End-to-End Variational Autoencoder) | VITS (Piper) | FastSpeech2 / Flow-matching + Vocoder |
| **Precision / Format** | FP32 ONNX | FP32/INT8 ONNX | PyTorch (`.pt`) |
| **Model Size** | 108.75 MB | ~63 MB | > 180 MB |
| **Phonemizer Frontend** | Character Token Table (Native UTF-8) | `espeak-ng` (`gu_dict`) | Custom Rule-based G2P |
| **Sample Rate** | 16,000 Hz | 22,050 Hz | 22,050 Hz |
| **License** | CC-BY-NC 4.0 | Apache 2.0 (Gated Hub) | MIT / Open |
| **Hardware Status** | **VALIDATED ON HARDWARE** | Audited Architecture | Infeasible for CPU Edge |

---

## 3. Physical Android Benchmark Data (`mms_guj` on `curtana` / Snapdragon 720G)

### 3.1 Model Load Time & Resident Memory
- **Model Load Time**: **1,194 ms**
- **Pre-Load Process PSS**: 355.0 MB
- **Post-Load Process PSS**: 364.6 MB
- **Net Load Delta**: **+9.6 MB** (very lightweight initial resident allocation)

### 3.2 Sentence-Level Synthesis Latency & RTF
Evaluated across the 5 representative Gujarati tactical phrases:

| Test Phrase Tag | Gujarati Text Sample | Synthesis Time (ms) | Audio Duration (s) | Real-Time Factor (RTF) | Process PSS (MB) |
| :--- | :--- | :---: | :---: | :---: | :---: |
| **emergency_1** | *"કટોકટી ચેતવણી, સેક્ટર ચારમાં તાત્કાલિક સહાયની જરૂર છે."* | 3,664 ms | 3.95 s | **0.927** | 359.30 MB |
| **emergency_2** | *"રેડ એલર્ટ, બધા સભ્યો તાત્કાલિક સુરક્ષિત સ્થળે પહોંચો."* | 3,747 ms | 3.73 s | **1.004** | 377.86 MB |
| **location** | *"અમે સ્ટેશન આલ્ફા પર છીએ, મુખ્ય ગેટથી પચાસ મીટર ઉત્તરમાં."* | 5,168 ms | 4.57 s | **1.131** | 396.51 MB |
| **numbers** | *"ટીમમાં બાર સભ્યો છે, બેટરી પંચોતેર ટકા છે."* | 5,316 ms | 3.24 s | **1.643** | 396.54 MB |
| **short** | *"રેડિયો ચેક, શું તમને મારો અવાજ સંભળાય છે?"* | 2,540 ms | 2.77 s | **0.917** | 396.76 MB |
| **Summary** | **Overall Sentence Averages** | **4,087 ms** | **3.65 s** | **1.119** | **396.77 MB** |

---

## 4. Stability & Memory Leak Assessment (10 Consecutive Cycles)

- **Initial Process PSS**: 396.77 MB
- **Post-10 Inferences PSS**: 417.85 MB
- **Net Stress Delta**: **+21.08 MB** across 10 complete utterances (~2.1 MB per synthesis, bounded by native heap arenas)
- **Zero Crashes / Zero SIGSEGV**: 100% crash-free execution over sustained stress testing.
- **Resource Deallocation**: Model session released cleanly down to baseline process bounds.

---

## 5. Audio Quality & Intelligibility Assessment

1. **Intelligibility**:
   - Vowel markers (માત્રા) and conjunct consonants (જોડાક્ષરો) such as *"કટોકટી"*, *"તાત્કાલિક"*, and *"પંચોતેર"* were synthesized with accurate phonetic rendering.
2. **Prosody & Naturalness**:
   - As a character-level monospeaker model, prosody is somewhat monotone and robotic compared to Piper's phoneme-level neural voices, but every emergency directive is clear and intelligible.
3. **Phonemizer Advantage**:
   - Does not require external dictionary files; character tokens map directly from UTF-8 Gujarati script characters.

---

## 6. Compatibility & Production Recommendations

| Category | Assessment | Recommendation |
| :--- | :--- | :--- |
| **Mid-Range Devices (6 GB RAM)** | **FUNCTIONAL BASELINE** | `mms_guj` functions reliably with RTF ~1.0. Acceptable for alert playback where audio plays as it buffers. |
| **Low-End Devices (4 GB RAM)** | **CONDITIONAL / NEEDS PIPER** | On quad-core 4 GB devices, synthesis will likely take 5–8 seconds (RTF > 1.5). |
| **Production Target Voice** | **Piper Gujarati INT8** | For final multilingual production, export and integrate `piper-gujarati-male` (Apache 2.0, AI4Bharat dataset) to achieve RTF < 0.25 matching English and Hindi Piper voices. |
