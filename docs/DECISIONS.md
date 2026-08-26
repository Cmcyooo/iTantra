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
* **Host/Client Roles**: To simplify connection without complex discovery (NSD), one device acts as Host (Server) and the other as Client (requires manual IP entry).
* **Message Model**: JSON-serialized `P2PMessage` containing ID, timestamp, language, and text.

## Native Model Loading
* **Asset Compression**: Disabled compression for `.onnx` files in `app/build.gradle.kts` using `androidResources.noCompress`. This is critical as ONNX Runtime needs to memory-map the model file directly from the APK assets, which fails if the file is compressed (deflated).
* **Model Source**: Official `silero_vad.onnx` (v4) from `snakers4/silero-vad` or `k2-fsa/sherpa-onnx` model releases.
* **Safe Initialization**: Added `try-catch` blocks around VAD initialization in `VadManager` to prevent app-wide crashes if the native layer fails to load the model (e.g., due to corrupted protobuf parsing), providing better diagnostic information in Logcat.
* **Min SDK 24**: Targets Android 7.0+ to cover a wide range of devices.
* **CPU-only Inference**: Ensures functionality on devices without high-end GPUs or NPUs.
