# iTantra Architecture

## Audio & Speech Pipeline
1. **Microphone Capture**: `AudioCaptureManager` uses `AudioRecord` (16 kHz, Mono, Float32 / PCM16).
2. **Voice Activity Detection (VAD)**: `VadManager` uses `sherpa-onnx` with `Silero VAD v4` model (300 ms silence timeout).
3. **Multilingual Speech-to-Text (STT)**: `LanguageModelManager` coordinates offline transcription via a **Dual-Runtime Engine**:
   - `SherpaOnnxSttEngine`: Runs English `sherpa-onnx-whisper-tiny` (INT8).
   - `GenericOnnxCtcSttEngine`: Runs Indian-language `vakyansh-wav2vec2-*-base` (dynamic INT8 ONNX with greedy CTC argmax collapse).
   - Enforces a strict **Single-Active Model Policy** to guarantee total process RAM stays under 1.2 GB on 4–6 GB Android devices.
4. **Text-to-Speech (TTS)**: `TtsManager` uses `sherpa-onnx` with `VITS (Piper)` model and `AudioTrack` for playback.
5. **Text Transport**: `CommunicationManager` coordinates low-bitrate data exchange via `Transport` interface:
   - `WiFiTransport`: Local Wi-Fi AP + Network Service Discovery (NSD) TCP.
   - `WiFiDirectTransport`: Peer-to-peer Wi-Fi Direct (P2P Group Owner / Client) TCP.
   - `BluetoothTransport`: Classic Bluetooth RFCOMM serial socket.
6. **Device Discovery**: `DiscoveryManager` (Wi-Fi NSD), `WiFiDirectManager`, and `BluetoothDiscoveryManager` automatically find nearby iTantra peers.
7. **Identity Management**: `CallSignManager` persists user identities and peer callsign mappings.
8. **Transceiver Coordinator**: `TransceiverManager` wires all components into a unified PTT-based walkie-talkie flow.
9. **Speaker**: Audio playback via low-latency `AudioTrack`.

## Design Principles
* **Offline First**: All speech, audio, and network processing occurs 100% on-device without internet.
* **Resource Efficient**: Multi-threaded CPU-only execution (`intra_op_num_threads = 2`), single-active model memory management, and zero GPU/NPU dependencies for 4–6 GB Android target devices.
* **Modular & Pluggable**: Decouples audio capture from neural inference, transports, and UI.

For complete multilingual STT architectural specifications, refer to [MULTILINGUAL_STT_ARCHITECTURE.md](file:///d:/iTantra/docs/MULTILINGUAL_STT_ARCHITECTURE.md).
