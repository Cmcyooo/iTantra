import subprocess

full_decisions = subprocess.check_output(['git', 'show', 'a9f7a05:docs/DECISIONS.md']).decode('utf-8')

phase8_decisions = """
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
    * Identified that CRLF line endings (`\\r\\n`) in `tokens.txt` cause `sherpa-onnx`'s C++ `std::istringstream` to misparse single space tokens (`  3\\r\\n`), erroneously inserting duplicate tokens. Resolved by enforcing strict UNIX LF (`\\n`) formatting.
* **Architecture Design (`LanguageTtsManager`)**:
    * Formalized `docs/TTS_ARCHITECTURE.md` specifying a strict single-active voice policy, lazy loading, and total decoupling of `TransceiverManager` and `AlertPlaybackManager` from model-specific TTS runtimes.
"""

with open('docs/DECISIONS.md', 'w', encoding='utf-8') as f:
    f.write(full_decisions.strip() + '\n' + phase8_decisions.strip() + '\n')

print("Updated docs/DECISIONS.md successfully")
