# iTantra Architecture

## Audio Pipeline
1.  **Microphone Capture**: `AudioCaptureManager` uses `AudioRecord` (16 kHz, Mono, PCM16).
2.  **Voice Activity Detection (VAD)**: `VadManager` uses `sherpa-onnx` with `Silero VAD` model.
3.  **STT (Future)**: Speech-to-Text conversion using Whisper (likely via `sherpa-onnx`).
4.  **Text Transport (Future)**: Transmission over low bitrate links (Wi-Fi/Bluetooth).
5.  **TTS (Future)**: Text-to-Speech on the receiving end.
6.  **Speaker**: Audio playback.

## Design Principles
* **Offline First**: All processing must happen on-device.
* **Resource Efficient**: Optimize for low-end hardware (minimal allocations, CPU-only).
* **Modular**: Decouple audio capture from inference and UI.
