# iTantra Architecture

## Audio & Speech Pipeline
1. **Microphone Capture**: `AudioCaptureManager` uses `AudioRecord` (16 kHz, Mono, Float32 / PCM16).
2. **Voice Activity Detection (VAD)**: `VadManager` uses `sherpa-onnx` with `Silero VAD v4` model (300 ms silence timeout).
3. **Multilingual Speech-to-Text (STT)**: `LanguageModelManager` coordinates offline transcription via a **Dual-Runtime Engine**:
   - `SherpaOnnxSttEngine`: Runs English `sherpa-onnx-whisper-tiny` (INT8).
   - `GenericOnnxCtcSttEngine`: Runs Indian-language `vakyansh-wav2vec2-*-base` (dynamic INT8 ONNX).
4. **Text-to-Speech (TTS)**: `TtsManager` uses `sherpa-onnx` with `VITS (Piper)` model and `AudioTrack` for playback.
5. **Text Transport**: `CommunicationManager` coordinates low-bitrate data exchange via `Transport` interface:
   - `WiFiTransport`: Local Wi-Fi AP + Network Service Discovery (NSD) TCP.
   - `WiFiDirectTransport`: Peer-to-peer Wi-Fi Direct (P2P Group Owner / Client) TCP.
   - `BluetoothTransport`: Classic Bluetooth RFCOMM serial socket.
6. **Device Discovery**:
   - `DiscoveryManager`: Handles mDNS/NSD for local Wi-Fi.
   - `WiFiDirectManager`: Manages P2P discovery and group formation with defensive permission handling.
   - `BluetoothDiscoveryManager`: Manages classic Bluetooth scanning.
7. **Identity Management**: `CallSignManager` persists user identities and peer callsign mappings.
8. **Peer Directory (`PeerRegistry`)**: Aggregates discovered stations across Wi-Fi NSD, Wi-Fi Direct, and Bluetooth into an in-memory directory with ranking (Connected > Validated > Wi-Fi > Wi-Fi Direct > Bluetooth).
9. **Zero-Configuration Emergency (`ZeroConfigEmergencyManager`)**: Orchestrates 1-touch autonomous emergency pipeline (Auto-discovery, best transport connection, fallback, voice capture, STT alert, and remote ACK verification).
10. **Transceiver Coordinator**: `TransceiverManager` wires all components into a unified PTT-based walkie-talkie flow, ensuring one logical message per PTT utterance (`utteranceId`).
11. **Audio Playback Subsystem**: `AlertPlaybackManager` coordinates priority audio with AudioFocus preemption, universal deduplication (`messageId` and `utteranceId`), language-aware routing, and telemetry tracking.
12. **UI State**: Compose-based UI driven by StateFlow from managers, featuring real-time connection feedback via SnackBar, pre-emergency readiness badges, and explicit state indicators.

## Utterance Lifecycle & Language Routing (Phase 13)
* **Utterance Continuity**: Each PTT button press generates a unique `utteranceId = UUID`. This ID travels unaltered through `AudioCaptureManager` -> `SttManager` -> `TransceiverManager` -> `CommunicationManager` -> `P2PMessage` -> `AlertPlaybackManager` -> `LanguageTtsManager`.
* **Language Propagation**: Outgoing packets explicitly specify their spoken language code (`en`, `hi`, `te`, `mr`, `ta`, `bn`). The receiving device synthesizes audio in that exact language, ignoring local UI language selections.
* **Warm State Caching**: `LanguageTtsManager` preserves the loaded neural voice model across sequential utterances in the same language, delivering sub-180ms synthesis latency on mobile CPUs without native memory leaks.
* **Bandwidth Optimization**: Speech is transmitted entirely as low-overhead text packets (197 bytes), yielding a 99.75% data reduction compared to raw 16 kHz uncompressed PCM audio (80,000 bytes).

## Design Principles
* **Offline First**: All speech, audio, and network processing occurs 100% on-device without internet.
* **Resource Efficient**: Enforces Single-Active Model Policy and resource cleanup.
* **Fault Tolerant**: Defensive initialization of hardware services (P2P, Bluetooth) to prevent crashes on unsupported devices.
