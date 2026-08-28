# Architecture Decisions

## Audio Configuration
* **Sample Rate (16 kHz)**: Standard for many STT/VAD models (Whisper, Silero).
* **PCM 16-bit Mono**: Balanced quality and resource usage.

## VAD Selection
* **Silero VAD (v4)**: Chosen for its high accuracy and efficiency on CPU.
* **sherpa-onnx**: Selected as the unified inference runtime for VAD and future STT/TTS components to minimize binary size and complexity.

## STT Selection
* **Whisper Tiny (English)**: Chosen for Phase 3 as the initial STT model due to its balance between size (~100 MB for int8) and accuracy.
* **Int8 Quantization**: Used `int8` quantized versions of encoder and decoder to reduce APK size and memory usage on low-end devices.
* **Non-Streaming Inference**: Audio is accumulated during VAD-detected speech and processed as a complete segment after speech ends. This ensures higher accuracy and simpler integration for the first milestone.

## TTS Selection
* **VITS (Piper) en_US-amy-low**: Chosen for Phase 4 as the initial English TTS model. It offers high quality and low latency. The "low" version (~63 MB) is suitable for mid-range devices and manageable for low-end devices.
* **AudioTrack (Static Mode)**: Used for audio playback. PCM float samples are written to a static buffer for low-overhead playback. Track is explicitly released after each use or on interruption to prevent resource exhaustion.

## UI/Interaction
* **Push-to-Talk (PTT)**: Implemented as the primary interaction model. Users hold a large central button to record, and release to trigger STT and transmission.
* **Automated Reception**: Incoming text messages automatically trigger TTS synthesis and playback, fulfilling the radio-like transceiver requirement.
* **Separated Settings**: Network configuration is moved to an expandable section to maintain focus on the core communication flow.

## Text Transport
* **Local TCP Sockets**: Chosen for Phase 5 to enable reliable, server-less communication on a local Wi-Fi or Hotspot network. Simple socket logic avoids heavy frameworks and works on all Android versions.
* **Bluetooth Classic (RFCOMM)**: Chosen for Phase 8.2 for nearby 1-to-1 communication. RFCOMM provides a reliable, stream-oriented transport similar to TCP sockets, simplifying the implementation of a shared text-based protocol.
* **Name-based Filtering**: For the prototype, Bluetooth discovery filters devices by checking if the name contains "iTantra". This avoids the overhead of service record scanning during discovery.
* **Line-based Framing**: Both Wi-Fi and Bluetooth transports use JSON-serialized messages followed by a newline. This allows the use of `BufferedReader.readLine()` and `PrintWriter.println()` for robust message framing.

## Bluetooth Identity
* **iTantra Call Sign**: A user-defined friendly name (e.g., "Station Alpha") stored locally in `SharedPreferences`.
* **Identity Exchange**: Upon establishing a Bluetooth connection, devices automatically exchange their call signs using a compact JSON message.
* **Persistent Mapping**: Discovered devices are mapped to their last-known call sign based on their MAC address. This allows the discovery UI to show friendly names even before a connection is fully established (if the peer was seen before).
* **Naming Fallback**: If no call sign is known, the UI uses the system Bluetooth name. If that is also unavailable (common during early discovery), it displays "iTantra Device" instead of "Unknown device" or raw MAC addresses.

## Inference Optimization
* **Multi-threading**: Increased `numThreads` from 1 to 2 for both Whisper STT and Piper TTS. This provides a significant speedup on multi-core mid-range devices while remaining safe for quad-core low-end devices.
* **VAD Endpointing**: Reduced `minSilenceDuration` to 300ms. This offers a more responsive "walkie-talkie" feel without cutting off natural speech trailing.

## Native Model Loading
* **Asset Compression**: Disabled compression for `.onnx` files in `app/build.gradle.kts` using `androidResources.noCompress`. This is critical as ONNX Runtime needs to memory-map the model file directly from the APK assets, which fails if the file is compressed (deflated).
* **Model Source**: Official `silero_vad.onnx` (v4) from `snakers4/silero-vad` or `k2-fsa/sherpa-onnx` model releases.
* **Safe Initialization**: Added `try-catch` blocks around VAD initialization in `VadManager` to prevent app-wide crashes if the native layer fails to load the model (e.g., due to corrupted protobuf parsing), providing better diagnostic information in Logcat.
* **Min SDK 24**: Targets Android 7.0+ to cover a wide range of devices.
* **CPU-only Inference**: Ensures functionality on devices without high-end GPUs or NPUs.

## Multilingual STT Model Selection (Phase 7.1)
* **Whisper Tiny Multilingual INT8 Evaluation**: Evaluated across 10 target Indian languages on Google FLEURS test dataset using sherpa-onnx CPU runtime.
* **Findings (Option C)**:
    * Footprint (~98.8 MB) and latency (RTF ~0.08–0.13, ~700–1400ms) are well within the 4–6 GB Android device envelope.
    * English recognition is high quality (WER 34.7%, CER 12.8%).
    * Indic languages suffer catastrophic failure (WER > 100%) due to severe hallucinations, repetitive token loops, phonetic Latin transliteration, and absence of Odia (`or`) token in OpenAI Whisper's 99-language vocabulary.
* **Architectural Decision**: Stock Whisper Tiny Multilingual INT8 cannot be deployed directly as the universal Indic ASR engine.

## Mobile Indic STT Architecture Decision (Phase 7.2)
* **Model Family Audit & Benchmark (Hindi Focus)**: Audited AI4Bharat IndicConformer, IndicWav2Vec, and Vakyansh Wav2Vec2 CTC families. Benchmarked `Harveenchadha/vakyansh-wav2vec2-hindi-him-4200` (Wav2Vec2-Base CTC, 95M params, MIT) against Whisper Tiny on Google FLEURS Hindi test set.
* **Key Findings**:
    * Vakyansh Wav2Vec2 CTC achieved **18.9% WER** and **5.4% CER** on Hindi (a 104.8% absolute WER improvement over Whisper Tiny's 123.7%).
    * **Non-autoregressive CTC** completely eliminates hallucination and token repetition loops.
    * Real-Time Factor is **0.075** (~875ms on CPU) and estimated INT8 ONNX footprint is **~94.5 MB**.
* **Architectural Decision (Option A)**:
    * Adopt **non-autoregressive CTC / Transducer architectures** (Wav2Vec2-Base / Conformer-CTC / Zipformer) as the primary mobile Indic STT strategy for iTantra.
    * 600M parameter models (e.g. IndicConformer 600M) are designated as **Accuracy Reference Only** due to CPU/RAM constraints on 4–6 GB mobile devices.

## Android Indic STT Runtime Compatibility (Phase 7.2b & 7.2c)
* **Exact Artifact**: `vakyansh_hindi_base.int8.onnx` (117.03 MB, SHA-256: `8e24e70119b8559d6299f68ae935be9999b93c1ea63f9d5c2191f902419aa516`).
* **Input/Output Signature**: Single input tensor `input_values` (dynamic `[1, sequence_length]`) -> Single output tensor `logits` (`[1, T, 67]`).
* **Physical Device Validation (Samsung Galaxy S24 / SM-S921B)**:
    * Tested on actual hardware via Android instrumented test suite.
    * Model load time: **502.36 ms** (direct ONNX mmap).
    * Average inference latency: **1849.2 ms** for 11.74s audio (**RTF: 0.158**).
    * Model resident memory delta: **354.84 MB PSS** (within 4–6 GB mobile RAM budget).
    * On-device accuracy: **17.35% WER** and **5.41% CER**.
    * Repeated stability: 10 consecutive passes with **zero memory leaks** (delta < 4.5 MB) and **zero crashes**.
* **Classification**: **MOBILE CANDIDATE** (meets accuracy, speed, and memory constraints for mobile walkie-talkie).
* **sherpa-onnx vs ONNX Runtime**:
    * `sherpa-onnx` native C++ layer does not support HuggingFace `Wav2Vec2ForCTC`.
    * Integration requires either generic ONNX Runtime Android (`ai.onnxruntime:onnxruntime-android`) or exporting Indic Conformer models to NeMo CTC format (`OfflineNemoEncDecCtcModelConfig`).

## Gujarati Mobile STT Candidate Selection (Phase 7.3)
* **Model Selection**: Selected `Harveenchadha/vakyansh-wav2vec2-gujarati-gnm-100` (Wav2Vec2-Base CTC, 94.4M params, MIT) as the primary Gujarati mobile STT candidate.
* **Exact Artifact**: `vakyansh_gujarati_base.int8.onnx` (117.03 MB, SHA-256: `bc64cb802a7dc162f38f3cec4d6a09536d2820ca87c50af370ff3d18d07bd71a`).
* **Physical Device Validation (Samsung Galaxy S24 / SM-S921B)**:
    * Cold load time: **485.84 ms**.
    * Average inference latency: **2231.55 ms** for 10.36s speech (**RTF: 0.215**).
    * Model resident memory delta: **357.82 MB PSS** (Peak process PSS: **898.91 MB**).
    * On-device accuracy: **31.06% WER** and **8.61% CER** (vs Whisper Tiny's 117.4% WER).
    * Stability: 10 repeated passes with **zero memory leaks** (delta < 6.0 MB) and **zero crashes**.
* **Classification**: **MOBILE CANDIDATE**.

## Marathi Mobile STT Candidate Selection (Phase 7.4)
* **Model Selection**: Selected `Harveenchadha/vakyansh-wav2vec2-marathi-mrm-100` (Wav2Vec2-Base CTC, 94.4M params, MIT) as the candidate Marathi mobile STT model.
* **Exact Artifact**: `vakyansh_marathi_base.int8.onnx` (117.03 MB, SHA-256: `2c503bc31cc60d1d25a407eb4776f121cd5af9952fc96f1dd14afbfce7fd3db9`).
* **Physical Device Validation (Samsung Galaxy S24 / SM-S921B)**:
    * Cold load time: **858.28 ms**.
    * Average inference latency: **3346.20 ms** for 11.61s speech (**RTF: 0.288**).
    * Model resident memory delta: **338.57 MB PSS** (Peak process PSS: **868.00 MB**).
    * On-device accuracy: **61.58% WER** and **20.22% CER** (vs Whisper Tiny's 155.2% WER with broken Latin loops).
    * Stability: 10 repeated passes with **zero memory leaks** (delta < 1.5 MB) and **zero crashes**.
* **Classification**: **CONDITIONAL CANDIDATE** (recommended for deployment with domain keyword boosting or paired with IndicConformer 120M for higher accuracy).

## Malayalam & Tamil Mobile STT Candidate Selection (Phase 7.5)
* **Malayalam Model Selection**: Selected `Harveenchadha/vakyansh-wav2vec2-malayalam-mlm-8` (Wav2Vec2-Base CTC, 94.4M params, MIT).
    * Artifact: `vakyansh_malayalam_base.int8.onnx` (117.03 MB, SHA-256: `c0922d209f67461c740784770c2c53ec6160d69c25b1d8c912eee692b8b2eea6`).
    * On-device measurements: **818.78 ms** load time, **4618.6 ms** avg latency for 14.26s audio (**RTF: 0.324**), **343.28 MB PSS** delta, **52.84% WER**, **13.84% CER**, zero memory leaks.
    * Classification: **MOBILE CANDIDATE**.
* **Tamil Model Selection**: Selected `Harveenchadha/vakyansh-wav2vec2-tamil-tam-250` (Wav2Vec2-Base CTC, 94.4M params, MIT).
    * Artifact: `vakyansh_tamil_base.int8.onnx` (117.02 MB, SHA-256: `33a91f3bce4b4025b0c561cde40fce2f029b1cc4ec85c8401f0869d030bb43b2`).
    * On-device measurements: **660.77 ms** load time, **4795.3 ms** avg latency for 14.81s audio (**RTF: 0.324**), **223.27 MB PSS** delta, **50.00% WER**, **25.68% CER**, zero memory leaks.
    * Classification: **MOBILE CANDIDATE**.

## Telugu & Bengali Mobile STT Candidate Selection (Phase 7.6)
* **Telugu Model Selection**: Selected `Harveenchadha/vakyansh-wav2vec2-telugu-tem-100` (Wav2Vec2-Base CTC, 94.4M params, MIT).
    * Artifact: `vakyansh_telugu_base.int8.onnx` (117.03 MB, SHA-256: `c64bab6c69e7965d512c3b6d70fcf5f8e4f2f6e52e6c06307612c489bfd964bf`).
    * On-device measurements: **493.41 ms** load time, **1526.9 ms** avg latency for 10.34s audio (**RTF: 0.148**), **354.84 MB PSS** delta, **34.34% WER**, **6.67% CER**, zero memory leaks.
    * Classification: **MOBILE CANDIDATE**.
* **Bengali Model Selection**: Selected `Harveenchadha/vakyansh-wav2vec2-bengali-bnm-200` (Wav2Vec2-Base CTC, 94.4M params, MIT).
    * Artifact: `vakyansh_bengali_base.int8.onnx` (117.03 MB, SHA-256: `8aec0865d879c1428f413fe70e6c5f9ff22a2dd678c66f529be4f5794e49b961`).
    * On-device measurements: **499.60 ms** load time, **3631.8 ms** avg latency for 13.13s audio (**RTF: 0.277**), **197.38 MB PSS** delta, **54.27% WER**, **15.25% CER**, zero memory leaks.
    * Classification: **MOBILE CANDIDATE**.

## Kannada & Odia Mobile STT Candidate Selection (Phase 7.7)
* **Kannada Model Selection**: Selected `Harveenchadha/vakyansh-wav2vec2-kannada-knm-560` (Wav2Vec2-Base CTC, 94.4M params, MIT).
    * Artifact: `vakyansh_kannada_base.int8.onnx` (117.03 MB, SHA-256: `9769b09b6c24d67acebc50f4436d1756f4a4b3ee3edec9c34099faa904f9f5a8`).
    * On-device measurements: **866.39 ms** load time, **3982.7 ms** avg latency for 12.79s audio (**RTF: 0.311**), **339.35 MB PSS** delta, **38.34% WER**, **8.35% CER**, zero memory leaks.
    * Classification: **MOBILE CANDIDATE**.
* **Odia Model Selection**: Selected `Harveenchadha/vakyansh-wav2vec2-odia-orm-100` (Wav2Vec2-Base CTC, 94.4M params, MIT).
    * Artifact: `vakyansh_odia_base.int8.onnx` (117.03 MB, SHA-256: `a0d3c21cf9b8a057779d92e678cc05c3b46142efe5767ba506e0a644ca8c8bba`).
    * On-device measurements: **602.33 ms** load time, **2971.6 ms** avg latency for 10.39s audio (**RTF: 0.286**), **196.81 MB PSS** delta, **78.54% WER**, **24.08% CER**, zero memory leaks.
    * Classification: **CONDITIONAL CANDIDATE** (functional native baseline; recommends vocabulary boosting / hybrid CTC/LM for complex domain terms).

## Multilingual STT Production Architecture Design (Phase 7.8)
* **Dual-Runtime Engine Pattern**:
    * `sherpa-onnx` will continue running English Whisper Tiny INT8.
    * Generic `ONNX Runtime Android` (`ai.onnxruntime:onnxruntime-android`) will execute all Indic Wav2Vec2 Base CTC INT8 models with non-autoregressive argmax greedy CTC decoding.
* **Single-Active Model Policy**:
    * Only one language model may be loaded into resident process memory at any given time.
    * Switching languages requires an explicit state transition (`RELEASING` -> `session.close()` -> `LOADING` new session) ensuring peak process PSS remains well below the 1.2 GB safe limit on 4–6 GB Android devices.
* **Transceiver Decoupling**:
    * `TransceiverManager` communicates strictly with `LanguageModelManager` via a high-level `transcribe(samples): SttResult` contract, completely isolated from native runtime details and future model replacements.

## Phase 7.9: Production Multilingual STT Integration (5 Languages)
* **Initial 5-Language Production Set**:
    * **English**: Whisper Tiny INT8 via sherpa-onnx (offline non-streaming autoregressive encoder-decoder). Preserved current baseline with 0% regression.
    * **Hindi**: Vakyansh Wav2Vec2 INT8 ONNX via generic ONNX Runtime Android (`GenericOnnxCtcSttEngine`).
    * **Gujarati**: Vakyansh Wav2Vec2 INT8 ONNX via generic ONNX Runtime Android (`GenericOnnxCtcSttEngine`).
    * **Telugu**: Vakyansh Wav2Vec2 INT8 ONNX via generic ONNX Runtime Android (`GenericOnnxCtcSttEngine`).
    * **Kannada**: Vakyansh Wav2Vec2 INT8 ONNX via generic ONNX Runtime Android (`GenericOnnxCtcSttEngine`).
* **Dual-Runtime Native Library Isolation**:
    * Resolved symbol collision between `sherpa-onnx`'s embedded ONNX Runtime (`libonnxruntime.so`) and `onnxruntime-android` (`libonnxruntime.so` / `libonnxruntime4j_jni.so`) by isolating the Microsoft ONNX Runtime binary to `libort_runtime.so` and patching the DT_NEEDED dependency in `libonnxruntime4j_jni.so`.
    * Configured `useLegacyPackaging = true` to allow extraction and clean dynamic linking on Android 16.
* **Single-Active Model Policy & Zero-Growth Verification**:
    * Validated on physical Samsung Galaxy S24: 6 consecutive switches between English and Indic models exhibited 0 MB net memory growth (initial PSS: 709.5 MB, final PSS: 527.2 MB, net delta: -182.3 MB).
    * Lazy loading verified: Indic models only load upon explicit language selection and completely deallocate their ONNX sessions, tensors, and environments before switching.
## Phase 8: Emergency / Alert Communication Mode
* **Message Protocol Extension**:
    * Retained backward compatibility by adding default parameters `messageType = "NORMAL"` and `priority = "NORMAL"` to `P2PMessage`.
    * Dedicated types: `"NORMAL"`, `"ALERT"`, `"ACK"`.
    * Dedicated priority levels: `"NORMAL"`, `"HIGH"`.
* **Audio Focus & Priority Subsystem**:
    * Implemented `AlertPlaybackManager` using Android `AudioFocusRequest.Builder(AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)` with `AudioAttributes.USAGE_ALARM`.
    * Preemption: Halts active normal TTS immediately upon emergency packet arrival.
    * Protection: Routine messages cannot interrupt an active emergency alert.
    * Multi-Alert FIFO Queuing: Multiple alerts are queued and played sequentially in exact arrival order.
    * Deduplication: Synchronized LRU cache on `messageId` discards retransmitted or multi-hop duplicates.
* **Delivery Confirmation Guardrail**:
    * Only displays `✓ Delivered` after receiving an explicit `ACK` packet from the receiver matching `pendingAckAlertId`.
    * Automatically flags `🚨 Alert not delivered. Check connection.` if network is disconnected or send fails.
* **Hardware Validation (Redmi Note 9 Pro / Android 12 / 6 GB RAM)**:
    * 8/8 automated instrumented tests passed in `EmergencyAlertIntegrationTest`.
    * 10 continuous alert stress cycles showed a negligible PSS delta of **+3.27 MB** (420.57 MB -> 423.84 MB) with zero leaks and zero crashes.

## Phase 8.5: Multilingual TTS Audit and Android Validation (Hindi & Gujarati)
* **Candidate Sourcing & Architecture Selection**:
    * **Hindi Primary**: Selected `vits-piper-hi_IN-priyamvada-medium` (60.57 MB, 22.05 kHz). Physical benchmark on Snapdragon 720G demonstrated **0.171 RTF** (~774 ms synthesis for 4.5s speech) and **+4.18 MB** stress delta.
    * **Hindi Alternative**: Selected `vits-piper-hi_IN-rohan-medium` (60.03 MB, 22.05 kHz, **0.208 RTF**) for male voice option.
    * **Gujarati Baseline**: Evaluated `facebook/mms-tts-guj` (108.75 MB, 16 kHz). Physical benchmark demonstrated **1.119 RTF** (~4.0s synthesis) and **+21.08 MB** stress delta. Functional as a baseline; recommended future migration to INT8 quantized `piper-gujarati-male` (Apache 2.0, ~63 MB) for target RTF < 0.25 on 4 GB devices.
* **Shared Phonemizer Asset Reuse**:
    * Discovered that the existing bundled `espeak-ng-data` already contains `hi_dict` and `gu_dict`. Piper Indic voices reuse this dictionary with **0 MB additional phonemizer asset overhead**.
* **CRLF Linux/Android Compatibility Fix**:
    * Identified that CRLF line endings (`\r\n`) in `tokens.txt` cause `sherpa-onnx`'s C++ `std::istringstream` to misparse single space tokens (`  3\r\n`), erroneously inserting duplicate tokens. Resolved by enforcing strict UNIX LF (`\n`) formatting.
* **Architecture Design (`LanguageTtsManager`)**:
    * Formalized `docs/TTS_ARCHITECTURE.md` specifying a strict single-active voice policy, lazy loading, and total decoupling of `TransceiverManager` and `AlertPlaybackManager` from model-specific TTS runtimes.

## Phase 8.6: Multilingual TTS Audit and Android Validation (Telugu & Kannada)
* **Candidate Sourcing & Architecture Selection**:
    * **Telugu Primary**: Selected `vits-piper-te_IN-maya-medium` (60.03 MB, 22.05 kHz). Physical benchmark on Snapdragon 720G demonstrated **0.197 RTF** (~935 ms synthesis for 4.7s speech), peak PSS **402.01 MB**, and **+0.16 MB** 10-cycle delta.
    * **Telugu Alternative**: Selected `vits-piper-te_IN-venkatesh-medium` (60.57 MB, 22.05 kHz, **0.198 RTF**, peak PSS **354.63 MB**) for male voice option.
    * **Kannada Baseline**: Evaluated `facebook/mms-tts-kan` (108.76 MB, 16 kHz). Physical benchmark demonstrated **1.072 RTF** (~6.5s synthesis for 6.1s speech) and peak PSS **430.99 MB**. Functional baseline; recommended future migration to INT8 quantized Piper Kannada voice (leveraging pre-bundled `kn_dict`) for target RTF < 0.25 on 4 GB devices.
* **Shared Phonemizer Reuse for Dravidian Languages**:
    * Confirmed that `app/src/main/assets/tts-en-amy/espeak-ng-data` already contains `te_dict` and `kn_dict`. Piper Telugu models reuse the existing dictionary with **0 MB additional phonemizer asset overhead**.
* **Model Classification**:
    * `piper_te_maya`: **PRODUCTION READY**
    * `piper_te_venkatesh`: **PRODUCTION READY**
    * `mms_tel`: **CONDITIONAL FALLBACK**
    * `mms_kan`: **CONDITIONAL BASELINE**

## Phase 8.7: Multilingual TTS Audit and Android Validation (Malayalam & Tamil)
* **Candidate Sourcing & Architecture Selection**:
    * **Malayalam Primary**: Selected `vits-piper-ml_IN-meera-medium` (60.03 MB, 22.05 kHz). Physical benchmark on Snapdragon 720G demonstrated **0.188 RTF** (~848 ms synthesis for 4.5s speech) and peak PSS **352.50 MB**.
    * **Malayalam Alternative**: Selected `vits-piper-ml_IN-arjun-medium` (60.03 MB, 22.05 kHz, **0.184 RTF**, peak PSS **411.51 MB**) with outstanding memory stability (**+1.54 MB** 10-cycle delta).
    * **Tamil Primary**: Selected `vits-piper-ta_IN-rasa_female-medium` (60.57 MB, 22.05 kHz, trained on AI4Bharat Rasa dataset). Physical benchmark demonstrated **0.196 RTF** (~795 ms synthesis for 4.0s speech), peak PSS **361.74 MB**, and **+6.57 MB** 10-cycle delta.
    * **Tamil Alternative**: Selected `vits-piper-ta_IN-rasa_male-medium` (60.57 MB, 22.05 kHz, **0.197 RTF**, peak PSS **362.86 MB**, **+5.85 MB** 10-cycle delta).
    * **Meta MMS Malayalam & Tamil**: Evaluated `facebook/mms-tts-mal` (**1.045 RTF**) and `facebook/mms-tts-tam` (**1.232 RTF**). Functional fallbacks; MMS Tamil exhibited latency spikes up to 11.4s on complex location queries.
* **Shared Phonemizer Reuse for Malayalam and Tamil**:
    * Confirmed that `app/src/main/assets/tts-en-amy/espeak-ng-data` already contains `ml_dict` and `ta_dict`. Piper Malayalam and Tamil models reuse the existing dictionary with **0 MB additional phonemizer asset overhead**.
* **Model Classification**:
    * `piper_ml_meera`: **PRODUCTION READY**
    * `piper_ml_arjun`: **PRODUCTION READY**
    * `piper_ta_rasa_female`: **PRODUCTION READY**
    * `piper_ta_rasa_male`: **PRODUCTION READY**
    * `mms_mal`: **CONDITIONAL FALLBACK**
    * `mms_tam`: **CONDITIONAL FALLBACK**

## Phase 8.8: Multilingual TTS Audit and Android Validation (Bengali & Marathi)
* **Candidate Sourcing & Architecture Selection**:
    * **Bengali Primary**: Selected `vits-piper-bn_BD-google-medium` (73.23 MB, 22.05 kHz, 16 speakers). Physical benchmark demonstrated **0.136 RTF** (~445 ms synthesis for 3.3s speech) and peak PSS **375.07 MB**.
    * **Marathi Primary**: Selected `vits-piper-mr_IN-google-medium` (73.21 MB, 22.05 kHz, 9 speakers). Physical benchmark demonstrated **0.127 RTF** (~516 ms synthesis for 4.0s speech), sub-second cold load (**935 ms**), and peak PSS **380.81 MB** with exceptional memory stability (**+3.77 MB** 10-cycle delta).
    * **Meta MMS Bengali & Marathi**: Evaluated `facebook/mms-tts-ben` (**1.075 RTF**, 4.7s latency) and `facebook/mms-tts-mar` (**1.324 RTF**, 5.7s latency). Functional offline fallbacks.
* **Shared Phonemizer Reuse for Bengali and Marathi**:
    * Confirmed that `app/src/main/assets/tts-en-amy/espeak-ng-data` already contains `bn_dict` and `mr_dict`. Piper Bengali and Marathi models reuse the existing dictionary with **0 MB additional phonemizer asset overhead**.
* **Model Classification**:
    * `piper_bn_google`: **PRODUCTION READY**
    * `piper_mr_google`: **PRODUCTION READY**
    * `mms_ben`: **CONDITIONAL FALLBACK**
    * `mms_mar`: **CONDITIONAL FALLBACK**

## Phase 8.9: Odia TTS Audit and Physical Android Validation
* **Candidate Sourcing & Architecture Selection**:
    * **Odia Baseline**: Evaluated `facebook/mms-tts-ory` (108.76 MB, 16 kHz). Physical benchmark on Snapdragon 720G demonstrated **1.033 RTF** (~4.47s synthesis for 4.33s speech) and peak PSS **392.84 MB** with outstanding memory stability (**+5.36 MB** 10-cycle delta, zero leaks).
* **Shared Phonemizer Reuse for Odia**:
    * Confirmed that `app/src/main/assets/tts-en-amy/espeak-ng-data` already contains `or_dict`. Future INT8 Piper Odia models will reuse this dictionary with **0 MB additional phonemizer asset overhead**.
* **Model Classification**:
    * `mms_ory`: **CONDITIONAL BASELINE**

## Phase 9: Production Multilingual TTS Integration
* **Single-Active Voice Policy Enforced**:
    * Created `LanguageTtsManager` implementing a strict single-active voice lifecycle. On language switch, previous native TTS sessions are explicitly released, audio tracks stopped and flushed, and garbage collection hinted before initializing the next voice.
    * Process memory stays bounded between 291 MB and 305 MB PSS across all 10 sequential language switches.
* **Architecture Harmonization & Backward Compatibility**:
    * Defined `TtsEngine` interface and `TtsVoiceConfig` registry covering all 10 project languages.
    * `TtsManager` delegates directly to `LanguageTtsManager.getInstance(context)`, ensuring zero regressions in `TransceiverManager`, `AlertPlaybackManager`, and `MicrophoneTestScreen`.
* **Synchronized Speech Language Selector**:
    * Updated `MicrophoneTestScreen` UI to switch both STT (`LanguageModelManager`) and TTS (`LanguageTtsManager`) simultaneously.
    * Visual badges indicate voice status: `Ready` (for production Piper voices) vs `Conditional` (for fallback MMS voices).
* **End-to-End Multilingual Verification**:
    * Verified complete `Speech -> VAD -> STT -> Transport -> TTS -> Speaker` loop on physical Android hardware across English, Hindi, Marathi, Bengali, Telugu, Malayalam, Tamil, and Odia with zero native crashes.

## Connectivity Stabilization: Wi-Fi Direct, Bluetooth Discovery & Hold-to-Speak UX
* **Wi-Fi Direct Crash Prevention & State Machine**:
    * Replaced fragile framework calls with an explicit 9-state machine (`P2PState`: `IDLE`, `DISCOVERING`, `PEER_FOUND`, `CONNECTING`, `GROUP_FORMING`, `SOCKET_CONNECTING`, `CONNECTED`, `FAILED`, `DISCONNECTED`).
    * Added auto-recovering `ChannelListener` to cleanly handle underlying system service disconnects.
    * Added `Context.RECEIVER_EXPORTED` on Android 14+ for system P2P broadcasts and tracked registration lifecycle (`isReceiverRegistered`) to prevent `IllegalArgumentException`.
    * Implemented a 15-second connection watchdog and a 12-attempt client retry loop while waiting for DHCP IP assignment (`groupOwnerAddress`).
* **Bluetooth Discovery & Multi-Tier Friendly Naming**:
    * Eliminated discovery crashes and receiver leaks by strictly validating Android 12+ runtime permissions (`BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`) and unregistering existing receivers before starting rescans.
    * Pre-populated bonded devices upfront so paired stations appear immediately without waiting for inquiry scan completion.
    * Enforced multi-tier friendly name resolution priority: Call Sign -> Cached EIR/SDP name -> `device.name` -> System device name -> `"Nearby iTantra Device"`. Under no circumstance is a raw MAC address exposed or `null` shown.
* **Hold-to-Speak (PTT) UX & Rolling Audio Buffering**:
    * Maintained an active rolling audio buffer during PTT hold. If user releases PTT on a short utterance (< 1.0s) before VAD silence endpointing triggers, `forceFinalize = true` transcribes the rolling audio buffer immediately, eliminating dropped utterances.
    * Updated PTT UI state machine: `[ TALK ]` -> `[ RELEASE TO SEND ]` -> `[ PROCESSING... ]` -> `[ SENT ✓ ]` / `[ FAILED ]`.
    * Added temporary 3-second Snackbars for connection lifecycle confirmations (`"Connected to <Device Name> • <Transport>"` and `"Disconnected from <Device Name>"`).

## Phase 10A: TTS Native Quality & End-to-End Telemetry Architecture
* **Packet Language Routing & Auto-Switching**:
    * Passed `message.language` and `alert.language` from incoming transport payloads directly to `LanguageTtsManager`. If the received packet's language differs from the active voice, the receiver automatically swaps the active TTS engine before synthesis, eliminating foreign-sounding synthesis (e.g. English Amy voice vocalizing Hindi Devanagari text).
* **Single-Clock Monotonic Network Latency (RTT)**:
    * Eliminated wall-clock subtraction across devices (`currentTime - msg.timestamp`) which produced fake negative (-489ms) or extreme (+652ms) readings due to hardware clock drift.
    * Replaced with monotonic round-trip ACK correlation (`SystemClock.elapsedRealtime()`), accurately displaying `Net RTT: XX ms`.
* **Telemetry Attribution**:
    * Replaced hardcoded `0ms` fallbacks with `"N/A"` for stages not executed on that device (e.g. sender does not run TTS; receiver does not run STT).

## Phase 10B: Multilingual STT Accuracy Optimization
* **Pre-Speech Ring Buffering**:
    * Maintained a 10-chunk (~320ms at 16kHz) circular ring buffer (`preSpeechRingBuffer`) in `AudioCaptureManager`. Prepending this buffer upon VAD speech detection guarantees the speech onset and first syllable are preserved.
* **VAD Parameter Recalibration**:
    * Reduced `minSpeechDuration` from 500ms to 150ms in `VadManager` for rapid attack detection on short tactical keywords (`हाँ`, `रुको`, `roger`).
    * Increased `minSilenceDuration` from 300ms to 700ms to avoid breaking sentences during natural conversational pauses.
* **Acoustic Audio Source & Feature Normalization**:
    * Switched `AudioRecord` source to `MediaRecorder.AudioSource.VOICE_RECOGNITION` to engage hardware AGC and noise suppression.
    * Enforced zero-mean unit-variance normalization (`(x - mean) / sqrt(var + 1e-7)`) in `GenericOnnxCtcSttEngine` prior to Wav2Vec2 CNN feature extraction.
* **Offline Deterministic Tactical Normalization**:
    * Integrated `IndicDomainNormalizer` for deterministic regex normalization of emergency keywords, radio phrases, and spoken numerals without cloud or LLM dependencies.

## Phase 10C: Odia & Marathi STT Model Replacement & Mobile Feasibility Tradeoff
* **Evaluation of 315M Wav2Vec2 Large Candidates**:
    * Benchmarked `sumedh/wav2vec2-large-xlsr-marathi` and `Harveenchadha/odia_large_wav2vec2` on standardized tactical test sets.
    * Both models delivered superior acoustic accuracy: Marathi WER dropped to 56.64% (CER 15.46%) and Odia WER dropped to 71.22% (CER 19.70%).
* **Physical Mobile Hardware Reality (Snapdragon 720G / Exynos 2400)**:
    * Instrumented testing on physical Android devices proved that 315M parameters in dynamic INT8 (340 MB binary) require **>3.2 seconds latency** for a 3.0-second utterance (**RTF 1.080 – 1.104**, slower than real-time speech).
    * Furthermore, peak PSS reached **>900 MB**, violating safe memory allocation on 4 GB/6 GB RAM hardware.
* **Production Decision**:
    * Retain the 95M Base INT8 models (`vakyansh_marathi_base.int8.onnx` and `vakyansh_odia_base.int8.onnx`) for real-time mobile push-to-talk operation (**RTF 0.107–0.108, ~110 ms latency, ~310 MB PSS**).
    * Augment both languages with `IndicDomainNormalizer.kt` to eliminate the word-boundary space bug in Marathi and correct tactical emergency keywords in Odia.
    * Classify the 315M Large INT8 models as **High-Performance / Desktop / NPU Reference Models**.

## Phase 10D: Lightweight STT Architecture & 50M–150M Parameter Sweet Spot
* **Auditing Alternative 95M CTC Candidates**:
    * Evaluated `addy88/wav2vec2-bengali-stt`, `addy88/wav2vec2-malayalam-stt`, `Bluecast/wav2vec2-Malayalam`, `addy88/wav2vec2-marathi-stt`, and `addy88/wav2vec-odia-stt` against current Vakyansh base models.
    * In Malayalam, `addy88` demonstrated competitive CER (**10.51%** vs 11.00%), confirming that Malayalam's WER is primarily driven by agglutinative compounding rather than acoustic distortion.
    * In Bengali, Marathi, and Odia, Vakyansh 95M base models with `IndicDomainNormalizer.kt` delivered equal or superior accuracy compared to all evaluated alternatives.
* **Architectural Envelope Finalized**:
    * The **95M parameter Wav2Vec2 Base dynamic INT8 architecture (~117 MB)** is definitively established as the production standard for mobile Indic speech-to-speech walkie-talkie communication, achieving RTF 0.107–0.110 (< 0.40 target), < 120 ms latency, and ~310 MB PSS on mid-range Android CPUs (Snapdragon 720G).

## Phase 11: Zero-Configuration Emergency Autonomous Pipeline
* **No LoRa / No External Hardware / No Cloud**:
    * Implemented 100% using existing mobile radios (Wi-Fi, Wi-Fi Direct, Bluetooth) and existing local engines (Silero VAD, Whisper / Wav2Vec2 STT, Piper TTS).
* **Multi-Transport Peer Registry**:
    * Created `PeerRegistry` to continuously aggregate available peers across Wi-Fi NSD, Wi-Fi Direct, and Bluetooth Classic without requiring manual user scanning or pairing.
    * Enforces strict ranking: Currently Connected > Recently Validated > Wi-Fi > Wi-Fi Direct > Bluetooth.
* **Friendly Identity Invariant**:
    * Strips hardware prefixes (`iTantra-`) and maps saved CallSigns; never presents raw MAC addresses as primary user-facing identities.
* **11-State Autonomous State Machine**:
    * Implemented `ZeroConfigEmergencyManager` managing explicit states: `IDLE`, `SEARCHING`, `SELECTING_PEER`, `CONNECTING`, `READY`, `LISTENING`, `PROCESSING`, `SENDING`, `WAITING_FOR_ACK`, `DELIVERED`, `FAILED`.
* **Cryptographic / Protocol ACK Delivery Gate**:
    * Strictly forbids declaring `✅ ALERT DELIVERED` upon local socket write alone; requires remote incoming `P2PMessage.MESSAGE_TYPE_ACK`.
    * Implements 5-second watchdog timer with single automatic retry before transitioning to honest `FAILED` state.
* **Non-Disruptive Normal Mode**:
    * Normal walkie-talkie mode remains 100% functional with zero regressions when emergency mode is inactive.

## Phase 12A/12B: VAD Onset Timing Recovery
* **Onset Recovery Strategy**:
    * Retain native Silero VAD v4 without changing the probability threshold.
    * Added a 1500 ms rolling ring buffer that preserves pre-speech audio frames. When native VAD triggers `SPEECH_DETECTED`, the preceding audio is stitched seamlessly, completely eliminating the first-word cutoff problem (13/13 test cases validated on physical Redmi Note 9 Pro).

## Phase 12C: Decoupled Wi-Fi Connection State Architecture
* **State Decoupling**:
    * `TransportMode` (Wi-Fi, Wi-Fi Direct, Bluetooth) is tracked independently from `ConnectionState`.
    * Selecting a transport unconditionally resets connection state to `DISCONNECTED`, clearing stale errors and enabling the CONNECT button.
    * Outbound client connects implement a 10s bounded timeout that auto-recovers to `DISCONNECTED` on failure.
    * Passive server hosting operates without disabling the outbound CONNECT button.
    * Fixed `ServerSocket` bind sequence to set `SO_REUSEADDR` before `bind()`.

## Phase 13: End-to-End Two-Phone Communication Loop Architecture
* **One Utterance = One Message (`utteranceId`)**:
    * Implemented single immutable `utteranceId` generated at PTT press and preserved through VAD, STT, serialized `P2PMessage`, network delivery, and TTS synthesis.
    * Eliminated text-equality deduplication in `TransceiverManager` (`state.recognizedText != lastSentText`), enabling intentional repetition of identical phrases (e.g. *"Radio check"*, *"Radio check"*).
* **Strict Receiver Language Propagation**:
    * The receiver resolves language strictly from `message.language`, overriding the receiver's local UI state.
    * Unsupported language codes fail cleanly with structured error logs without synthesizing with an incorrect voice.
* **Universal Duplicate Suppression**:
    * Extended deduplication across all message types (both `NORMAL` and `ALERT`) using `messageId` and `utteranceId`, preventing duplicate speech output upon network retransmissions.
* **Single-Active Warm Model Caching**:
    * Existing initialized TTS engines are reused across messages in the same language, dropping synthesis latency to **170–179 ms** on mobile CPUs without native memory creep.
* **Extreme Low-Bandwidth Protocol**:
    * Transmitting speech as text packets (197 bytes) delivers **99.75% bandwidth reduction** compared to raw uncompressed PCM audio (80,000 bytes for a 2.5s utterance).

## Phase 10D: Comprehensive Multilingual Benchmark Hardening & Evidence-Based Model Selection
* **Physical Hardware Validation**:
    * Executed full benchmarking suite directly on physical mid-range Android hardware (Xiaomi Redmi Note 9 Pro, Qualcomm Snapdragon 720G, Android 12, 5.45 GB RAM) without desktop emulation or fabricated metrics.
* **Corpus & Acoustic Standard**:
    * Generated and deployed 600 audio samples (300 clean, 300 calibrated 15 dB SNR noisy) across all 10 target languages (Hindi, English, Marathi, Gujarati, Telugu, Tamil, Bengali, Kannada, Malayalam, Odia) spanning short alerts, natural sentences, and tactical coordinates.
* **Base (95M) vs Large (315M) Architectural Resolution**:
    * Evaluated Marathi (`vakyansh_marathi_base.int8.onnx` vs `marathi_xlsr_large.int8.onnx`) and Odia (`vakyansh_odia_base.int8.onnx` vs `odia_large.int8.onnx`).
    * **Empirical Finding**: While the 315M XLS-R Large architecture improves Marathi WER by 6.2% and Odia WER by 12.0%, it requires >1200 MB peak RAM (exceeding memory budgets), takes ~2600–2700 ms latency (0.65–0.67 RTF on Snapdragon 720G), and creates thermal/watchdog risks.
    * **Decision**: Selected 95M Base INT8 ONNX models for mobile walkie-talkie deployment (Marathi: 1197.7 ms, 0.299 RTF, 695.54 MB peak RAM; Odia: 1401.7 ms, 0.350 RTF, 717.49 MB peak RAM).
* **TTS Architecture Tiers**:
    * Piper VITS voices (`en`, `hi`, `mr`, `bn`, `te`, `ta`, `ml`) achieve warm latency of 464–853 ms (0.128–0.222 RTF) and peak PSS < 450 MB, designated **PRODUCTION READY**.
    * Meta MMS voices (`gu`, `kn`, `or`) achieve 3760–6985 ms latency (0.909–1.271 RTF), designated **CONDITIONAL** fallback pending Piper voice training.
* **SELinux Storage Hardening**:
    * Migrated benchmark output path from `/data/local/tmp` to app-owned external storage (`/sdcard/Android/data/com.itantra.app/files/benchmarks/`), overcoming Android 12+ SELinux UID isolation and ensuring non-root ADB accessibility.
* **Watchdog / Test Isolation Strategy**:
    * Structured benchmark methods into individual language suites to avoid MIUI `GarbageClean` timeout terminations during continuous intensive CPU inference runs.
* **Memory & Reliability Verification**:
    * Verified 0 memory leaks across 50 consecutive short utterances (delta: 31.5 MB), 20 long 8s utterances, and 20 rapid 10-language switches with 100% success rate, 0 ANRs, and 0 crashes.

## Spoken Language Identification (Auto-LID) Architecture (Phase 10E)
* **Zero New Dependencies Strategy**:
    * Utilized existing native C++ binding `com.k2fsa.sherpa.onnx.SpokenLanguageIdentification` within `sherpa-onnx:1.13.6` (already embedded in the project).
    * Avoided introducing Silero LID, SpeechBrain, or secondary ONNX runtime engines. Zero APK bloat from new libraries.
* **Model Selection**:
    * Selected Whisper Tiny Multilingual INT8 encoder (`tiny-encoder.int8.onnx`, 12.9 MB) and decoder (`tiny-decoder.int8.onnx`, 89.8 MB).
    * Total weight size: 102.7 MB, ~39M parameters. MIT/Apache 2.0 open-source license.
* **Preservation of Single-Active STT Model Policy**:
    * The Whisper Tiny LID model (~45 MB runtime PSS) resides persistently in RAM for instantaneous language classification.
    * The single-active policy for STT and TTS models remains strictly enforced: only ONE heavy Wav2Vec2/Whisper STT model and ONE Piper/MMS voice model is allocated in RAM at any time.
    * Total combined runtime PSS is 605–619 MB, safely below the 750 MB low-memory threshold on mid-range Android hardware.
* **Session Language Caching Policy**:
    * Running speech LID on every single PTT transmission is wasteful, introduces unnecessary latency (~140–200 ms), and degrades walkie-talkie responsiveness.
    * **Decision**: Establish `currentSessionLanguage = detectedLanguage` upon the initial confident identification. Subsequent conversational utterances bypass the LID model entirely, delivering **0 ms added overhead**.
* **Language Switch Debounce & Re-verification State Machine**:
    * To prevent single acoustic anomalies, background noises, or short syllables from erroneously flipping the language, switching requires **2 consecutive high-confidence detections** of the new language candidate.
    * Automatic background re-verification executes every 5 utterances or when manually requested via the UI "Detect / Re-verify" action.
* **Empirical Linguistic Finding & Odia (`or`) Mitigation**:
    * OpenAI Whisper's standard 99-language token set completely omits Odia (`or`).
    * In physical tests, Odia speech consistently clusters with Bengali (`bn`) (60% of samples) due to shared Eastern Indo-Aryan roots.
    * **Architectural Mitigation**: Documented transparently; Odia confidence scores reflect ambiguity, and the UI provides clear visual indication with 1-tap manual fallback to ensure zero loss of operator communication.
* **Acoustic Consensus & Multi-Window Temporal Slicing**:
    * `SherpaSpokenLanguageIdentifier` performs multi-window analysis (full speech segment + 70% head and tail slices) to ensure robust confidence scoring across short and long utterances.
    * RMS energy gating (< 0.005) immediately returns `NO_SPEECH`, skipping downstream ONNX execution on dead air or silence.

## Confidence-Aware Auto-LID Safety Routing & Audio Retention (Phase 10E.1)
* **Core Architectural Principle**:
    * *"Auto-LID is advisory for weak languages and authoritative only for benchmark-validated reliable languages."*
    * *"False-positive automatic routing is treated as a higher-priority UX failure than requiring user confirmation."*
    * A bad LID prediction must NEVER silently cause iTantra to load the wrong STT model.
* **Centralized Language Reliability Policy (`LanguageReliabilityPolicy`)**:
    * Based on Phase 10E physical benchmarks, languages are classified into tiers:
        * **AUTO_ACCEPT**: English (`en`), Hindi (`hi`), Tamil (`ta`), Bengali (`bn`), Telugu (`te`). Detections with confidence $\ge 0.80$ route automatically.
        * **CONFIRM_REQUIRED**: Marathi (`mr`), Gujarati (`gu`), Kannada (`kn`), Malayalam (`ml`), Odia (`or`). High model confidence ($\ge 0.80$) does NOT authorize silent switching; explicit operator confirmation is mandatory.
    * Centralized and configurable so future acoustic model fine-tuning can promote languages to AUTO_ACCEPT.
* **Zero-Repeat Confirmation & PCM Buffer Retention**:
    * When a `CONFIRM_REQUIRED` language is detected, audio capture stops and the finalized 16 kHz Float32 PCM samples are retained in `LanguageModelManager.pendingConfirmation`.
    * STT loading is paused until operator confirmation.
    * When the operator taps `[ Use Language ]` or chooses another language in the dialog, the **retained original audio** is immediately transcribed by the selected STT model. The operator never has to repeat speaking.
* **Explicit Session Language Source Tracking**:
    * Introduced `SessionLanguageSource` (`AUTO_DETECTED`, `USER_CONFIRMED`, `MANUAL_SELECTED`).
    * Confirmed weak languages establish a `USER_CONFIRMED` session lock, allowing subsequent conversational turns to reuse the confirmed language without repeated prompts.
* **Debounced Switching & Safety Overrides**:
    * Automatic switching is permitted ONLY when the target language is `AUTO_ACCEPT`, confidence $\ge 0.80$, and 2 consecutive detections agree.
    * Switching to a `CONFIRM_REQUIRED` language always requires explicit operator confirmation.
    * Session reset clears all session locks, debouncing history, and pending confirmations.
