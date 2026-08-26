# iTantra Benchmarks

## Device Categories
* **Low-end**: e.g., Quad-core, 2GB RAM (Target: < 50% CPU during VAD)
* **Mid-range**: e.g., Octa-core, 4GB+ RAM (Target: < 15% CPU during VAD)

## VAD Performance (Phase 2)
* **Model**: Silero VAD v4 (ONNX)
* **Initialization Time**: TBD (Target: < 200ms)
* **Inference Latency**: TBD (Target: < 10ms per 512-sample chunk)
* **CPU Usage (Idle/Recording)**: TBD
* **Memory Footprint (Resident)**: TBD (~2-5 MB for model)

## STT Performance (Phase 3)
* **Model**: Whisper Tiny EN (int8 ONNX)
* **Total Model Size (Encoder + Decoder)**: ~103 MB
* **Device**: Samsung Galaxy S24 (SM-S921B)
* **Android Version**: Android 14 (API 34)
* **RTF (Real-Time Factor)**: ~0.08 - 0.22 (Verified offline)
* **Avg Latency (5s audio)**: ~300 - 500ms
* **Peak RAM during inference**: ~200 - 300 MB (Estimated)

## Battery Impact
* **Energy Consumption per hour of active use**: TBD
