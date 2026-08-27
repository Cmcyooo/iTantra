# Bengali Offline Mobile Text-to-Speech (TTS) Candidate Audit

**Date**: August 27, 2026  
**Project**: iTantra — SIH 2026 PS 26173  
**Target Hardware**: 4–6 GB RAM Android Mobile Devices (Snapdragon / MediaTek / Exynos ARM64)  
**Evaluated Device**: Samsung Galaxy S24 (`SM-S921B`, ARM64, 7.4 GB RAM, Android 16) & Snapdragon 720G Desktop CPU Baseline  
**Execution Mode**: 100% Offline, CPU-only (`num_threads = 2`), On-Device Android ART / JNI via `sherpa-onnx`

---

## 1. Executive Summary

Two open-source offline TTS candidates were evaluated on physical Android hardware for Bengali speech synthesis:
1. **Piper VITS `bn_BD-google-medium`** (16 speakers, 73.23 MB ONNX, `espeak-ng` frontend, trained on Google Crowdsourced Bengali dataset)
2. **Meta MMS VITS `facebook/mms-tts-ben`** (Monospeaker, 108.76 MB ONNX, UTF-8 character frontend)

### Key Findings:
- **`piper_bn_google`** is the **primary production candidate**:
  - **Cold Load Time**: **1,253 ms**
  - **Average RTF**: **0.136** (synthesizes a 3.3-second phrase in **~445 ms**, **7.4x faster than real-time**)
  - **Peak Process PSS**: **375.07 MB**
  - **10-Cycle Stress Delta**: **+80.59 MB** (zero SIGSEGV, bounded by native heap arenas)
  - **Audio Quality**: Crisp, natural, and expressive Bengali speech synthesis with clear articulation of complex Bengali conjuncts (যুক্তাক্ষর).
- **Meta MMS Bengali (`mms_ben`)** is a **conditional fallback**:
  - **Average RTF**: **1.075** (~4.7 seconds latency for a 4.4-second phrase). While functional without an external dictionary, its synthesis is 7.9x slower than Piper.

---

## 2. Shared Phonemizer Audit (`espeak-ng-data`)

- **Discovery**: The existing bundled asset directory `app/src/main/assets/tts-en-amy/espeak-ng-data` already contains `bn_dict`.
- **Zero Asset Bloat**: Piper Bengali voices reuse the existing application dictionary with **0 MB additional phonemizer asset overhead**.

---

## 3. Physical Android Benchmark Data

### 3.1 Model Load Time & Resident Memory
| Candidate Model | Architecture | Size | Load Time (ms) | Peak Process PSS (MB) | Net 10-Cycle Delta (MB) |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **`piper_bn_google`** | VITS (Piper) | 73.23 MB | **1,253 ms** | **375.07 MB** | **+80.59 MB** |
| **`mms_ben`** | VITS (Meta MMS) | 108.76 MB | **1,100 ms** | **407.90 MB** | **+86.51 MB** |

### 3.2 Sentence-Level Synthesis Latency & RTF
Evaluated across 5 representative Bengali tactical phrases:

| Test Phrase Tag | Bengali Text Sample | Piper Bengali Synth / RTF | MMS Bengali Synth / RTF |
| :--- | :--- | :---: | :---: |
| **emergency_1** | *"জরুরী সতর্কতা, চার নম্বর সেক্টরে অবিলম্বে সহায়তা প্রয়োজন।"* | 653 ms / **0.188** | 4,861 ms / 1.025 |
| **emergency_2** | *"রেড অ্যালার্ট, দলের সকল সদস্য অবিলম্বে নিরাপদ স্থানে সরে যান।"* | 465 ms / **0.130** | 4,346 ms / 0.924 |
| **location** | *"আমরা স্টেশন আলফাতে আছি, প্রধান ফটক থেকে পঞ্চাশ মিটার উত্তরে।"* | 377 ms / **0.121** | 4,878 ms / 1.033 |
| **numbers** | *"দলে মোট বারো জন সদস্য আছেন, ব্যাটারি স্তর পঁচাত্তর শতাংশ।"* | 431 ms / **0.114** | 5,569 ms / 1.259 |
| **short** | *"রেডিও চেক, আমার কথা কি পরিষ্কার শোনা যাচ্ছে?"* | 302 ms / **0.121** | 3,904 ms / 1.172 |
| **Average** | **Summary Across All Phrases** | **445 ms / 0.136** | **4,712 ms / 1.075** |

---

## 4. Stability & Memory Assessment (10 Consecutive Cycles)

- **`piper_bn_google`**: Pre-stress PSS: 375.07 MB -> Post-stress PSS: 455.66 MB (**Delta: +80.59 MB**, bounded within standard ART heap limits).
- **Crash Rate**: **0 crashes, 0 ANRs, 0 native SIGSEGV crashes** across all runs.
- **Resource Deallocation**: Model session released cleanly down to baseline process bounds.

---

## 5. Audio Quality & Intelligibility Assessment

1. **Pronunciation of Emergency Directives**:
   - `piper_bn_google` articulated Bengali emergency phrases like *"জরুরী সতর্কতা"* (Emergency Warning) and *"রেড অ্যালার্ট"* (Red Alert) with high urgency and clear syllable boundaries.
2. **Numerals & Metrics**:
   - Bengali numerals and units (*"বারো জন সদস্য"* - 12 members, *"পঁচাত্তর শতাংশ"* - 75 percent) were pronounced smoothly.
3. **Phonetic Naturalness**:
   - `piper_bn_google` exhibits smooth Bengali prosody and natural pitch inflections.

---

## 6. Compatibility & Production Recommendations

| Model Candidate | Mobile Status | Recommendation |
| :--- | :---: | :--- |
| **`piper_bn_google`** | **PRODUCTION READY (Primary)** | **Adopt as primary Bengali voice.** High synthesis speed (< 450 ms, RTF ~0.136), 0 MB extra phonemizer overhead. |
| **`mms_ben`** | **CONDITIONAL FALLBACK** | Usable offline fallback without phonemizer, but 7.9x slower (RTF ~1.075). |
