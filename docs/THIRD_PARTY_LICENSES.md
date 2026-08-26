# Third-Party License Audit - iTantra

This document outlines the open-source components, models, and libraries used in the iTantra project (SIH 2026 Problem Statement 26173) to ensure compliance with the "Open-Source Only" and "Fully Offline" requirements.

## Summary Table

| Component | Version/Model | Source | License | Redistribution | Attribution | SIH Status | Notes |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **sherpa-onnx** | 1.13.6 | [k2-fsa/sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) | Apache-2.0 | Yes | Required | COMPLIANT | Core inference framework. |
| **ONNX Runtime** | ~1.17 (Bundled) | [microsoft/onnxruntime](https://github.com/microsoft/onnxruntime) | MIT | Yes | Required | COMPLIANT | Inference engine backend. |
| **Silero VAD** | v4 | [snakers4/silero-vad](https://github.com/snakers4/silero-vad) | MIT | Yes | Required | COMPLIANT | Voice activity detection model. |
| **Whisper Tiny EN** | INT8 ONNX | [openai/whisper](https://github.com/openai/whisper) | MIT | Yes | Required | COMPLIANT | Offline English STT model. |
| **Piper TTS Amy** | en_US-amy-low | [rhasspy/piper](https://github.com/rhasspy/piper) | CC BY 4.0 | Yes | Required | COMPLIANT | Initial English TTS voice. |
| **espeak-ng-data** | 1.51+ | [espeak-ng/espeak-ng](https://github.com/espeak-ng/espeak-ng) | GPLv3 | Yes | Required | COMPLIANT | Phonemizer data for Piper TTS. |
| **Kotlin/Compose** | BOM 2026.02.01 | [Google Maven](https://maven.google.com/) | Apache-2.0 | Yes | Required | COMPLIANT | Foundation and UI framework. |
| **Kotlinx Serialization**| 1.6.3 | [Kotlin/kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) | Apache-2.0 | Yes | Required | COMPLIANT | Protocol serialization (JSON). |
| **AndroidX Core** | 1.10.1 | [AndroidX](https://developer.android.com/jetpack/androidx) | Apache-2.0 | Yes | Required | COMPLIANT | Core Android extensions. |

## Model Asset Details

### 1. VAD: Silero VAD v4
*   **File:** `app/src/main/assets/silero_vad.onnx`
*   **License:** MIT
*   **Audit Status:** VERIFIED (Via official repository model card).

### 2. STT: Whisper Tiny (English)
*   **Files:** `app/src/main/assets/whisper-tiny-en/encoder.onnx`, `decoder.onnx`, `tokens.txt`
*   **License:** MIT (Original OpenAI license).
*   **Audit Status:** VERIFIED (Redistributed INT8 conversion by k2-fsa under project terms).

### 3. TTS: Piper en_US-amy-low
*   **Files:** `app/src/main/assets/tts-en-amy/model.onnx`, `tokens.txt`
*   **License:** CC BY 4.0
*   **Audit Status:** VERIFIED (Amy voice uses dataset requiring attribution, compatible with open source use).

### 4. TTS Phonemizer: espeak-ng-data
*   **Files:** `app/src/main/assets/tts-en-amy/espeak-ng-data/` (Multiple language and dict files).
*   **License:** GPLv3
*   **Audit Status:** VERIFIED (Required for Piper's phonemization step).

### 5. Indic STT: Vakyansh Wav2Vec2 Base CTC Models (Hindi, Gujarati, Telugu, Kannada)
*   **Hindi Model:** `vakyansh_hindi_base.int8.onnx` + `hindi_vocab.json` (Source: `Harveenchadha/vakyansh-wav2vec2-hindi-him-4200`)
*   **Gujarati Model:** `vakyansh_gujarati_base.int8.onnx` + `gujarati_vocab.json` (Source: `Harveenchadha/vakyansh-wav2vec2-gujarati-gnm-100`)
*   **Telugu Model:** `vakyansh_telugu_base.int8.onnx` + `telugu_vocab.json` (Source: `Harveenchadha/vakyansh-wav2vec2-telugu-tem-100`)
*   **Kannada Model:** `vakyansh_kannada_base.int8.onnx` + `kannada_vocab.json` (Source: `Harveenchadha/vakyansh-wav2vec2-kannada-knm-560`)
*   **License:** MIT License (Vakyansh / AI4Bharat)
*   **Audit Status:** VERIFIED (Open-source weights and tokenizer vocabularies, INT8 quantized for mobile CPU inference).

## Compliance Verification

1.  **Open Source Only**: All components are licensed under OSI-approved licenses (Apache 2.0, MIT, GPLv3) or standard Creative Commons licenses (CC BY 4.0). No proprietary SDKs or binary-only commercial blobs are present.
2.  **Fully Offline**: All models operate locally on-device without internet or cloud connectivity.
3.  **Redistribution**: Permitted for all components under their respective licenses.
4.  **Commercial Use**: Permitted for all components.

### Overall Status: **COMPLIANT**
