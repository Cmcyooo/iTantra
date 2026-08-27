# Lightweight STT Accuracy Upgrade Benchmark Report (Phase 10D)

**Project:** iTantra — SIH 2026 PS 26173  
**Target Hardware:** Xiaomi Redmi Note 9 Pro (Snapdragon 720G, Android 12) & Samsung Galaxy S24 (SM-S921B, Android 16)  
**Date:** August 27, 2026  

---

## 1. Executive Summary

In Phase 10C, physical mobile benchmarking proved that large 315M+ parameter models (e.g. XLS-R 300M) exceed real-time limits on mid-range Android mobile hardware (RTF 1.080–1.104, >3.2s latency, >900 MB PSS).

In Phase 10D, we investigated the **50M–150M parameter lightweight envelope** across the four weak Indic languages:
1. **Bengali (`bn`)**
2. **Malayalam (`ml`)**
3. **Marathi (`mr`)**
4. **Odia (`or`)**

Every candidate was evaluated on the standardized 20-utterance tactical and conversational dataset with physical Android validation.

---

## 2. Cross-Model Benchmark Results

| Language | Model Candidate | Architecture | Params | Size (INT8) | WER (%) | CER (%) | Mobile Latency | Mobile RTF | Peak PSS | Verdict |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :--- |
| **Bengali** | `vakyansh_bengali_base.int8.onnx` | Wav2Vec2 Base | 95M | 117.0 MB | **52.40%** | **16.40%** | **98.6 ms** | **0.108** | **310.2 MB** | **PRODUCTION BASELINE** |
| Bengali | `addy88/wav2vec2-bengali-stt` | Wav2Vec2 Base | 95M | 117.0 MB | 57.93% | 17.75% | 96.4 ms | 0.106 | 309.8 MB | Rejected (+5.53% WER) |
| Bengali | `bangla-speech-processing/BanglaASR` | Wav2Vec2 Base | 95M | 117.0 MB | 100.00% | 100.00% | N/A | N/A | N/A | Rejected (Vocab mismatch) |
| **Malayalam**| `vakyansh_malayalam_base.int8.onnx` | Wav2Vec2 Base | 95M | 117.0 MB | **52.00%** | 11.00% | **119.3 ms** | **0.109** | **312.1 MB** | **PRODUCTION BASELINE** |
| Malayalam | `addy88/wav2vec2-malayalam-stt` | Wav2Vec2 Base | 95M | 117.0 MB | 52.85% | **10.51%** | 118.0 ms | 0.108 | 311.5 MB | Strong Alternative |
| Malayalam | `Bluecast/wav2vec2-Malayalam` | Wav2Vec2 Base | 95M | 117.0 MB | 73.17% | 16.52% | 122.5 ms | 0.112 | 314.0 MB | Rejected (+21.17% WER) |
| **Marathi** | `vakyansh_marathi_base.int8.onnx` + Norm | Wav2Vec2 Base | 95M | 117.0 MB | **62.10%** | **20.10%** | **108.2 ms** | **0.108** | **310.2 MB** | **PRODUCTION BASELINE** |
| Marathi | `addy88/wav2vec2-marathi-stt` | Wav2Vec2 Base | 95M | 117.0 MB | 67.13% | 20.16% | 107.5 ms | 0.107 | 310.0 MB | Rejected (+5.03% WER) |
| **Odia** | `vakyansh_odia_base.int8.onnx` + Norm | Wav2Vec2 Base | 95M | 117.0 MB | 81.90% | **24.10%** | **110.5 ms** | **0.107** | **308.6 MB** | **CONDITIONAL BASELINE** |
| Odia | `addy88/wav2vec-odia-stt` | Wav2Vec2 Base | 95M | 117.0 MB | **81.29%** | 24.51% | 109.8 ms | 0.106 | 308.0 MB | Strong Alternative (-0.61% WER) |

---

## 3. Analysis & Key Insights

1. **The 95M Architecture represents the True Mobile Sweet Spot**:
   - In all 4 languages, 95M parameter INT8 models deliver **sub-120 ms latency** and **RTF ~0.107–0.109** (9x faster than real-time) on mobile CPU.
   - Resident RAM remains bounded at **~310 MB PSS**, allowing safe, leak-free operation on 4 GB and 6 GB Android hardware.
2. **Acoustic vs. Morphological Error Analysis**:
   - In **Malayalam**, CER is extremely low (**10.51% – 11.00%**, meaning ~89% of all characters are exact), but WER is ~52%. This is because Malayalam's agglutinative morphology joins multiple words together; character-level CTC models often place a space where the written standard omits it, or vice versa.
   - In **Bengali**, Vakyansh Base achieves 52.40% WER and 16.40% CER, cleanly transcribing short commands (`রজার বেস`, `টহল চৌকি`).
   - In **Marathi**, `vakyansh_marathi_base` with `IndicDomainNormalizer.kt` achieves 62.10% WER, completely resolving the missing space bug on tactical terms.
   - In **Odia**, both `vakyansh_base` and `addy88` achieve ~81% WER and ~24% CER.
