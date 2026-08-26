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

## Native Model Loading
* **Asset Compression**: Disabled compression for `.onnx` files in `app/build.gradle.kts` using `androidResources.noCompress`. This is critical as ONNX Runtime needs to memory-map the model file directly from the APK assets, which fails if the file is compressed (deflated).
* **Model Source**: Official `silero_vad.onnx` (v4) from `snakers4/silero-vad` or `k2-fsa/sherpa-onnx` model releases.
* **Safe Initialization**: Added `try-catch` blocks around VAD initialization in `VadManager` to prevent app-wide crashes if the native layer fails to load the model (e.g., due to corrupted protobuf parsing), providing better diagnostic information in Logcat.
* **Min SDK 24**: Targets Android 7.0+ to cover a wide range of devices.
* **CPU-only Inference**: Ensures functionality on devices without high-end GPUs or NPUs.
