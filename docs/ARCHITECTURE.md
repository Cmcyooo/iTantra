# iTantra Architecture

## Audio & Speech Pipeline
1. **Microphone Capture**: `AudioCaptureManager` uses `AudioRecord` (16 kHz, Mono, Float32 / PCM16) with rolling pre-speech circular ring buffer (1500 ms) to capture speech onset.
2. **Voice Activity Detection (VAD)**: `VadManager` uses `sherpa-onnx` with `Silero VAD v4` model (300 ms silence timeout).
3. **Automatic Language Identification (Auto-LID)**: `LanguageModelManager` coordinates local spoken language identification using `SherpaSpokenLanguageIdentifier` (`sherpa-onnx` Whisper Tiny INT8 encoder/decoder):
   - In `AUTO` mode (default): evaluates first utterance to establish `currentSessionLanguage`, debounces language changes requiring 2 consecutive high-confidence detections, and re-verifies every 5 utterances.
   - In `MANUAL` mode: uses explicit user selection without invoking the LID runtime.
4. **Multilingual Speech-to-Text (STT)**: `LanguageModelManager` coordinates offline transcription via a **Dual-Runtime Engine**:
   - `SherpaOnnxSttEngine`: Runs English `sherpa-onnx-whisper-tiny` (INT8).
   - `GenericOnnxCtcSttEngine`: Runs Indian-language `vakyansh-wav2vec2-*-base` (dynamic INT8 ONNX).
   - *Single-Active Policy*: Exactly one STT model is active in RAM at any instant; releasing the previous engine before allocating the target engine.
5. **Text-to-Speech (TTS)**: `LanguageTtsManager` uses `sherpa-onnx` with `VITS (Piper)` and `Meta MMS` models, managing single-active voice allocations with warm synthesis caching.
6. **Text Transport**: `CommunicationManager` coordinates low-bitrate data exchange via `Transport` interface:
   - `WiFiTransport`: Local Wi-Fi AP + Network Service Discovery (NSD) TCP.
   - `WiFiDirectTransport`: Peer-to-peer Wi-Fi Direct (P2P Group Owner / Client) TCP.
   - `BluetoothTransport`: Classic Bluetooth RFCOMM serial socket.
7. **Device Discovery**:
   - `DiscoveryManager`: Handles mDNS/NSD for local Wi-Fi.
   - `WiFiDirectManager`: Manages P2P discovery and group formation with defensive permission handling.
   - `BluetoothDiscoveryManager`: Manages classic Bluetooth scanning.
8. **Identity Management**: `CallSignManager` persists user identities and peer callsign mappings.
9. **Peer Directory (`PeerRegistry`)**: Aggregates discovered stations across Wi-Fi NSD, Wi-Fi Direct, and Bluetooth into an in-memory directory with ranking (Connected > Validated > Wi-Fi > Wi-Fi Direct > Bluetooth).
10. **Zero-Configuration Emergency (`ZeroConfigEmergencyManager`)**: Orchestrates 1-touch autonomous emergency pipeline (Auto-discovery, best transport connection, fallback, voice capture, STT alert, and remote ACK verification).
11. **Transceiver Coordinator**: `TransceiverManager` wires all components into a unified PTT-based walkie-talkie flow, ensuring one logical message per PTT utterance (`utteranceId`).
12. **Audio Playback Subsystem**: `AlertPlaybackManager` coordinates priority audio with AudioFocus preemption, universal deduplication (`messageId` and `utteranceId`), language-aware routing, and telemetry tracking.
13. **UI State**: Compose-based UI driven by StateFlow from managers, featuring real-time connection feedback via SnackBar, pre-emergency readiness badges, explicit state indicators, and AUTO/MANUAL language chips.

## Spoken Language Identification (Auto-LID) Architecture (Phase 10E & 10E.1)
* **Zero New Dependencies**: Leverages `com.github.k2-fsa.sherpa-onnx:sherpa-onnx:1.13.6`'s native C++ `SpokenLanguageIdentification` API and Whisper Tiny INT8 encoder/decoder (`tiny-encoder.int8.onnx` [12.9 MB], `tiny-decoder.int8.onnx` [89.8 MB]).
* **Single-Active Model Policy Coexistence**: The lightweight LID model (~102 MB storage, ~45 MB runtime PSS) resides peacefully alongside exactly one active STT model (~122 MB) and one active TTS model (~63 MB). Total app RAM remains strictly between 500–629 MB, well within mid-range Android memory limits.
* **Confidence-Aware Safety Routing (Phase 10E.1)**:
  - **Key Principle 1**: *"Auto-LID is advisory for weak languages and authoritative only for benchmark-validated reliable languages."*
  - **Key Principle 2**: *"False-positive automatic routing is treated as a higher-priority UX failure than requiring user confirmation."*
  - Grounded in empirical Phase 10E hardware benchmarks:
    - **AUTO_ACCEPT Languages**: English (`en`), Hindi (`hi`), Tamil (`ta`), Bengali (`bn`), Telugu (`te`). Detections with confidence $\ge 0.80$ route automatically.
    - **CONFIRM_REQUIRED Languages**: Kannada (`kn`), Gujarati (`gu`), Marathi (`mr`), Malayalam (`ml`), Odia (`or`). Predictions are advisory and require explicit operator confirmation before STT loading.
* **Zero-Repeat Confirmation Audio Retention**:
  - When `CONFIRM_REQUIRED` is triggered, audio capture stops and the finalized PCM buffer is retained temporarily in `LanguageModelManager`.
  - The STT engine is paused, and the UI displays confirmation chips (`[ Use Language ]` / `[ Choose Language ]`).
  - Upon user decision, the retained audio buffer is transcribed immediately. The operator **never has to re-record their utterance**.
* **Safe Session Language State Machine**:
  - Tracks origin: `AUTO_DETECTED`, `USER_CONFIRMED`, `MANUAL_SELECTED`.
  - For `AUTO_ACCEPT`: Utterance 1 establishes session lock; utterances 2, 3, 4 reuse cached language with **0 ms** LID overhead.
  - For `CONFIRM_REQUIRED`: Automatic session lock is **never** established until explicit operator confirmation. Once confirmed, subsequent utterances reuse the language as `USER_CONFIRMED`.
  - Automatic language switching requires **2 consecutive high-confidence detections** of an `AUTO_ACCEPT` language. For weak targets, switching always prompts the operator.
* **Manual Override**: The user can toggle between `AUTO` and `MANUAL` at any time. Selecting `MANUAL` locks the chosen language and completely skips LID.

## Utterance Lifecycle & Language Routing (Phase 13)
* **Utterance Continuity**: Each PTT button press generates a unique `utteranceId = UUID`. This ID travels unaltered through `AudioCaptureManager` -> `LanguageModelManager` (Auto-LID + STT) -> `TransceiverManager` -> `CommunicationManager` -> `P2PMessage` -> `AlertPlaybackManager` -> `LanguageTtsManager`.
* **Language Propagation**: Outgoing packets explicitly specify their spoken language code (`en`, `hi`, `te`, `mr`, `ta`, `bn`, etc.). The receiving device synthesizes audio in that exact language, ignoring local UI language selections.
* **Warm State Caching**: `LanguageTtsManager` preserves the loaded neural voice model across sequential utterances in the same language, delivering sub-180ms synthesis latency on mobile CPUs without native memory leaks.
* **Bandwidth Optimization**: Speech is transmitted entirely as low-overhead text packets (197 bytes), yielding a 99.75% data reduction compared to raw 16 kHz uncompressed PCM audio (80,000 bytes).

## Design Principles
* **Offline First**: All speech, audio, and network processing occurs 100% on-device without internet.
* **Resource Efficient**: Enforces Single-Active Model Policy and resource cleanup.
* **Fault Tolerant**: Defensive initialization of hardware services (P2P, Bluetooth) to prevent crashes on unsupported devices.
