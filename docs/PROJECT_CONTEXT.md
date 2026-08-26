# iTantra Project Context

## Project Overview
* **Problem Statement:** SIH 2026 PS 26173
* **Goal:** iTantra – Indian Multilingual TTS & STT Aided Neural Transceiver Radio Access for low bitrate links.
* **Requirements:** 
    * Fully offline operation.
    * Support for 10 Indian languages.
    * Compatible with low-end and mid-range Android devices.
    * Native Android (Kotlin/Compose), API 24+.

## Hardware Constraints
* Limited RAM and CPU performance.
* No dedicated AI accelerator assumed (CPU-only inference).
* Limited storage and battery.

## Current Architecture
* `Microphone` -> `AudioRecord` -> `16 kHz PCM` -> `Silero VAD` -> (Future components)
* Common Runtime: `sherpa-onnx`
