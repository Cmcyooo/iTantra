# Marathi Offline Mobile Text-to-Speech (TTS) Candidate Audit

**Date**: August 27, 2026  
**Project**: iTantra — SIH 2026 PS 26173  
**Target Hardware**: 4–6 GB RAM Android Mobile Devices (Snapdragon / MediaTek / Exynos ARM64)  
**Evaluated Device**: Samsung Galaxy S24 (`SM-S921B`, ARM64, 7.4 GB RAM, Android 16) & Snapdragon 720G Desktop CPU Baseline  
**Execution Mode**: 100% Offline, CPU-only (`num_threads = 2`), On-Device Android ART / JNI via `sherpa-onnx`

---

## 1. Executive Summary

Two open-source offline TTS candidates were evaluated on physical Android hardware for Marathi speech synthesis:
1. **Piper VITS `mr_IN-google-medium`** (9 speakers, 73.21 MB ONNX, `espeak-ng` frontend, trained on Google Crowdsourced Marathi dataset)
2. **Meta MMS VITS `facebook/mms-tts-mar`** (Monospeaker, 108.76 MB ONNX, UTF-8 character frontend)

### Key Findings:
- **`piper_mr_google`** is the **primary production candidate**:
  - **Cold Load Time**: **935 ms** (< 1 second cold load!)
  - **Average RTF**: **0.127** (synthesizes a 4.0-second phrase in **~516 ms**, **7.9x faster than real-time**)
  - **Peak Process PSS**: **380.81 MB**
  - **10-Cycle Stress Delta**: **+3.77 MB** (outstanding stability, zero memory creep)
  - **Audio Quality**: Natural, clear Marathi speech synthesis with fluent enunciation of Devanagari conjuncts (जोडाक्षरे) and loan words.
- **Meta MMS Marathi (`mms_mar`)** is a **conditional fallback**:
  - **Average RTF**: **1.324** (~5.7 seconds latency for a 4.3-second phrase). While functional without an external dictionary, its synthesis is 10.4x slower than Piper.

---

## 2. Shared Phonemizer Audit (`espeak-ng-data`)

- **Discovery**: The existing bundled asset directory `app/src/main/assets/tts-en-amy/espeak-ng-data` already contains `mr_dict`.
- **Zero Asset Bloat**: Piper Marathi voices reuse the existing application dictionary with **0 MB additional phonemizer asset overhead**.

---

## 3. Physical Android Benchmark Data

### 3.1 Model Load Time & Resident Memory
| Candidate Model | Architecture | Size | Load Time (ms) | Peak Process PSS (MB) | Net 10-Cycle Delta (MB) |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **`piper_mr_google`** | VITS (Piper) | 73.21 MB | **935 ms** | **380.81 MB** | **+3.77 MB** |
| **`mms_mar`** | VITS (Meta MMS) | 108.76 MB | **2,082 ms** | **392.60 MB** | **+70.49 MB** |

### 3.2 Sentence-Level Synthesis Latency & RTF
Evaluated across 5 representative Marathi tactical phrases:

| Test Phrase Tag | Marathi Text Sample | Piper Marathi Synth / RTF | MMS Marathi Synth / RTF |
| :--- | :--- | :---: | :---: |
| **emergency_1** | *"तातडीचा इशारा, सेक्टर चारमध्ये तातडीने मदतीची गरज आहे."* | 540 ms / **0.129** | 6,425 ms / 1.335 |
| **emergency_2** | *"रेड अलर्ट, सर्व संघ सदस्यांनी त्वरित सुरक्षित स्थळी जावे."* | 481 ms / **0.125** | 4,978 ms / 1.206 |
| **location** | *"आम्ही स्टेशन अल्फा येथे आहोत, मुख्य गेटपासून पन्नास मीटर उत्तरेकडे."* | 573 ms / **0.128** | 6,368 ms / 1.393 |
| **numbers** | *"संघामध्ये एकूण बारा सदस्य आहेत, बॅटरी पातळी पंच्याहत्तर टक्के आहे."* | 593 ms / **0.128** | 6,355 ms / 1.348 |
| **short** | *"रेडिओ चेक, माझा आवाज तुम्हाला स्पष्ट येत आहे का?"* | 397 ms / **0.127** | 4,393 ms / 1.328 |
| **Average** | **Summary Across All Phrases** | **516 ms / 0.127** | **5,704 ms / 1.324** |

---

## 4. Stability & Memory Assessment (10 Consecutive Cycles)

- **`piper_mr_google`**: Pre-stress PSS: 380.82 MB -> Post-stress PSS: 384.60 MB (**Delta: +3.77 MB**, zero memory leak, highly stable).
- **Crash Rate**: **0 crashes, 0 ANRs, 0 native SIGSEGV crashes** across all runs.
- **Resource Deallocation**: Model session released cleanly down to baseline process bounds.

---

## 5. Audio Quality & Intelligibility Assessment

1. **Pronunciation of Emergency Directives**:
   - `piper_mr_google` articulated Marathi emergency phrases like *"तातडीचा इशारा"* (Immediate Warning) and *"रेड अलर्ट"* (Red Alert) with high clarity and urgent command tone.
2. **Numerals & Metrics**:
   - Marathi numerals and percentages (*"बारा सदस्य"* - 12 members, *"पंच्याहत्तर टक्के"* - 75 percent) were pronounced fluently.
3. **Phonetic Naturalness**:
   - `piper_mr_google` delivers crisp, fluent Marathi intonation with standard Pune/Mumbai pronunciation norms.

---

## 6. Compatibility & Production Recommendations

| Model Candidate | Mobile Status | Recommendation |
| :--- | :---: | :--- |
| **`piper_mr_google`** | **PRODUCTION READY (Primary)** | **Adopt as primary Marathi voice.** Blazing synthesis speed (< 520 ms, RTF ~0.127), sub-second cold load (935 ms), 0 MB extra phonemizer overhead. |
| **`mms_mar`** | **CONDITIONAL FALLBACK** | Usable offline fallback without phonemizer, but 10.4x slower (RTF ~1.324). |
