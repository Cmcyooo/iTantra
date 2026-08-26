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

## TTS Performance (Phase 4)
* **Model**: VITS Piper en_US-amy-low (ONNX)
* **Model Size**: ~63 MB
* **Device**: Samsung Galaxy S24 (SM-S921B)
* **Android Version**: Android 14 (API 34)
* **RTF (Real-Time Factor)**: ~0.150 (Verified offline)
* **First-audio Latency**: ~300ms (for ~2s audio)
* **Synthesis Time (Short)**: ~300ms

## Transport Performance (Phase 5)
* **Mechanism**: Local TCP Sockets (Wi-Fi/Hotspot)
* **Message Payload Size**: ~150-300 bytes (JSON)
* **Transport Latency (Avg)**: ~2-15ms (Tested on local Wi-Fi)

## End-to-End Latency (Phase 6 Optimized)
* **Path**: Phone A (Speech) -> STT -> Network -> Phone B (Text) -> TTS -> Audio
* **VAD Silence Threshold**: 300ms (Reduced from 500ms for faster turn-around)
* **STT Time (Whisper Tiny INT8, 2 threads)**: ~300-450ms (Optimized from ~491ms)
* **Network Time (TCP NoDelay)**: ~5-25ms (Optimized from ~126ms baseline in some tests)
* **TTS Time (Piper INT8, 2 threads)**: ~150-250ms (Optimized from ~246ms)
* **Approx. Total (Endpoint to Audio)**: ~500-750ms (Target: < 1.0s for fluid interaction)

## Battery Impact
* **Energy Consumption per hour of active use**: TBD
