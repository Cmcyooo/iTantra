# iTantra

### Indian Multilingual TTS & STT Aided Neural Transceiver for Low-Bitrate Links

**SIH 2026 — Problem Statement 26173**

iTantra is an **offline, multilingual voice communication system for Android** designed for low-bandwidth and constrained-device environments.

Instead of transmitting audio directly, iTantra converts speech into text, transmits the compact text representation over a local link, and reconstructs the speech on the receiving device.

This enables a lightweight, low-bitrate, voice-first communication system that can operate **without internet connectivity**.

---

## 🚨 Problem

Voice communication is data-intensive, making direct audio transmission inefficient over low-data-rate links.

This is particularly important in:

* Emergency and distress communication
* Low-bandwidth environments
* Areas with unreliable connectivity
* Situations where voice is more inclusive than written communication

iTantra addresses this by transmitting **text instead of raw audio**.

```text
Speech
  ↓
Offline STT
  ↓
Text
  ↓
Low-Bitrate Local Transport
  ↓
Text
  ↓
Offline TTS
  ↓
Speech
```

---

## 🎯 Objectives

iTantra is designed to:

* Work **fully offline**
* Run on **low-end and mid-range Android devices**
* Support **10 required languages**
* Minimize RAM, CPU and storage usage
* Maintain low end-to-end latency
* Use open-source technologies
* Support Wi-Fi and Bluetooth based peer-to-peer communication
* Provide a walkie-talkie style push-to-talk experience
* Support phone-like continuous communication
* Support high-priority emergency/alert announcements

### Required Languages

1. Hindi
2. Gujarati
3. Marathi
4. Kannada
5. Malayalam
6. Tamil
7. Telugu
8. Odia
9. Bengali
10. English

---

## 🧠 Core Architecture

```text
                     PHONE A
                        │
                    🎙️ Speech
                        │
                        ▼
                 ┌─────────────┐
                 │ Silero VAD  │
                 └──────┬──────┘
                        │
                  Speech segment
                        │
                        ▼
              ┌──────────────────┐
              │ Whisper STT       │
              │ Offline / INT8    │
              └────────┬─────────┘
                       │
                     Text
                       │
                       ▼
             ┌────────────────────┐
             │ Wi-Fi / Bluetooth  │
             │ Local Transport    │
             └─────────┬──────────┘
                       │
                       │ Text
                       ▼
                     PHONE B
                       │
                       ▼
              ┌──────────────────┐
              │ Lightweight TTS  │
              │ Piper / VITS     │
              └────────┬─────────┘
                       │
                       ▼
                    🔊 Speech
```

### Key design principle

**Audio is not transmitted between phones.**

Only compact text is transmitted:

```text
Speech → STT → Text → Network → Text → TTS → Speech
```

This significantly reduces communication bandwidth compared with transmitting raw audio.

---

## 🛠️ Technology Stack

### Android

* Kotlin
* Jetpack Compose
* Android SDK
* Minimum SDK: API 24

### Audio

* Android `AudioRecord`
* 16 kHz
* Mono
* PCM 16-bit

### Voice Activity Detection

* Silero VAD
* Offline execution
* sherpa-onnx / ONNX Runtime

### Speech-to-Text

* Whisper Tiny
* INT8 quantized ONNX model
* sherpa-onnx / ONNX Runtime
* CPU-oriented mobile inference

### Text-to-Speech

* Piper / VITS
* Offline ONNX model
* sherpa-onnx / ONNX Runtime
* Android `AudioTrack` playback

### Communication

* Local Wi-Fi peer-to-peer transport
* TCP sockets
* Bluetooth planned through the same transport abstraction

### Development

* Android Studio
* Git + GitHub
* AI-assisted development through coding agents
* Python/benchmarking tools for model evaluation

---

## 📱 Current System

The current working English pipeline is:

```text
🎙️ User Speech
      ↓
Silero VAD
      ↓
Whisper Tiny INT8
      ↓
📝 Text
      ↓
Wi-Fi TCP
      ↓
📝 Text
      ↓
Piper/VITS TTS
      ↓
🔊 Remote Speech
```

The two-phone workflow uses a push-to-talk interaction:

```text
HOLD TO TALK
      ↓
Speak
      ↓
Release
      ↓
STT
      ↓
Send text
      ↓
Remote TTS
      ↓
Speaker
```

---

## ✅ Development Status

| Phase                                       | Status         |
| ------------------------------------------- | -------------- |
| Phase 0 — Project & Git setup               | ✅ Complete     |
| Phase 1 — Android foundation                | ✅ Complete     |
| Phase 2 — Microphone + PCM + Silero VAD     | ✅ Complete     |
| Phase 3 — Offline English STT               | ✅ Complete     |
| Phase 4 — Offline English TTS               | ✅ Complete     |
| Phase 5 — Wi-Fi text transport              | ✅ Complete     |
| Phase 6 — Full voice transceiver            | ✅ Complete     |
| Phase 6.1 — Latency & pause optimization    | 🔵 In Progress |
| Phase 7 — Multilingual expansion            | ⏳ Planned      |
| Phase 8 — Bluetooth + phone/emergency modes | ⏳ Planned      |
| Phase 9 — Low-end/mid-range optimization    | ⏳ Planned      |
| Phase 10 — Final benchmarking & SIH demo    | ⏳ Planned      |

---

## 📊 Current Development Benchmarks

Current measurements are from the **development Samsung device** and are not yet representative of final low-end device performance.

| Component          | Current measurement |
| ------------------ | ------------------: |
| STT latency        |             ~491 ms |
| Wi-Fi transport    |             ~126 ms |
| TTS latency        |             ~246 ms |
| End-to-end latency |         ~600–900 ms |
| STT RTF            |          ~0.08–0.22 |
| TTS RTF            |               ~0.15 |
| English TTS model  |              ~63 MB |
| STT model assets   |             ~104 MB |

These values are tracked during development and will be re-measured on low-end and mid-range hardware during the optimization phase.

**No performance claim is considered final until it is validated on target hardware.**

---

## ⚡ Performance Strategy

iTantra is designed around constrained-device execution.

### Model loading

Only the active language model should be loaded into RAM.

```text
Device Storage
      ↓
Selected Language
      ↓
Active STT/TTS Model
      ↓
RAM
```

### CPU efficiency

The system avoids requiring:

* Dedicated GPU
* NPU
* Vendor-specific acceleration
* Cloud inference

### Audio processing

* VAD prevents unnecessary STT processing during silence
* PCM buffers are reused where practical
* Processing runs off the Compose UI thread
* Audio segments are processed incrementally

### Communication efficiency

Only text packets are sent over the network.

---

## 🔐 Offline-First Design

The AI pipeline is intended to work without internet access.

There are no runtime dependencies on:

* Cloud STT
* Cloud TTS
* Hosted LLM APIs
* Remote inference services

The core communication and AI pipeline operate locally.

---

## 📁 Project Structure

```text
iTantra/
│
├── app/
│   └── src/
│       └── main/
│           ├── java/
│           │   └── com/itantra/app/
│           │       ├── audio/
│           │       ├── ui/
│           │       └── ...
│           ├── assets/
│           │   ├── STT models
│           │   ├── TTS models
│           │   └── VAD model
│           └── AndroidManifest.xml
│
├── docs/
│   ├── PROJECT_CONTEXT.md
│   ├── PHASE_STATUS.md
│   ├── ARCHITECTURE.md
│   ├── DECISIONS.md
│   └── BENCHMARKS.md
│
├── gradle/
├── build.gradle.kts
├── settings.gradle.kts
├── gradlew
└── README.md
```

The `docs/` directory acts as the project's persistent technical memory and records architecture, decisions, phase progress and measured benchmarks.

---

## 🧪 Testing Strategy

Each subsystem is tested independently before being connected to the complete pipeline.

### Audio

```text
Microphone
→ PCM
→ VAD
```

### STT

```text
Speech
→ Whisper
→ Text
```

### TTS

```text
Text
→ Piper/VITS
→ Speech
```

### Transport

```text
Text
→ Wi-Fi
→ Text
```

### End-to-end

```text
Speech
→ STT
→ Transport
→ TTS
→ Speech
```

This modular testing approach makes it easier to identify performance and reliability bottlenecks.

---

## 🛣️ Roadmap

### Phase 6.1 — Latency & Pause Optimization

Current focus:

* Reduce VAD endpoint delay
* Improve pause recognition
* Reduce unnecessary STT preparation
* Reuse STT recognizer/model
* Reduce TTS startup overhead
* Measure component-level latency

Current baseline:

```text
STT      ≈ 491 ms
Network  ≈ 126 ms
TTS      ≈ 246 ms
```

### Phase 7 — Multilingual Support

Expand the architecture to:

* Hindi
* Gujarati
* Marathi
* Kannada
* Malayalam
* Tamil
* Telugu
* Odia
* Bengali
* English

Before integration, each model will be evaluated for:

* Model size
* RAM
* CPU usage
* Latency
* RTF
* Accuracy
* Android compatibility
* Offline support

### Phase 8 — Remaining Communication Modes

Planned:

* Bluetooth transport
* Phone-like continuous mode
* Emergency/alert mode
* High-priority voice announcements
* Improved peer discovery

### Phase 9 — Low-End & Mid-Range Optimization

Final validation on target constrained devices:

* RAM
* CPU
* Battery/resource usage
* Model footprint
* STT WER
* TTS quality
* RTF
* End-to-end latency
* Idle CPU usage

### Phase 10 — SIH Validation

Final system evaluation:

* 10-language accuracy
* Model/app footprint
* CPU/RAM efficiency
* STT/TTS latency
* End-to-end voice latency
* Offline operation
* Two-phone communication
* Walkie-talkie demonstration

---

## 🚀 Demo Concept

The final demonstration is designed around two Android devices:

```text
             PHONE A
                │
          🎙️ Speak Kannada
                │
                ▼
             Offline
               STT
                │
                ▼
              Text
                │
          Local Wi-Fi/BT
                │
                ▼
              Text
                │
             Offline
               TTS
                │
                ▼
             🔊 Speech
             PHONE B
```

The goal is to demonstrate that meaningful voice communication can occur **without transmitting audio and without relying on internet-hosted speech APIs**.

---

## ⚠️ Current Limitations

The current prototype has several known limitations:

* English is currently the validated AI language.
* Wi-Fi is currently the validated transport.
* Bluetooth integration is still pending.
* Peer communication is currently 1-to-1.
* Client connection currently uses manual IP configuration.
* Low-end hardware validation is not yet complete.
* Multilingual model integration is still pending.
* Final production-grade security hardening is still pending.

These are tracked as future development phases rather than hidden limitations.

---

## 🤝 Development Philosophy

iTantra is built incrementally.

Each major subsystem is:

1. Implemented
2. Tested independently
3. Benchmarked
4. Optimized
5. Integrated into the next phase

The project prioritizes **practical mobile performance over unnecessarily large models**, especially because the target environment includes low-end and mid-range devices.

---

## 📜 License

This project is being developed for **Smart India Hackathon 2026**.

Individual dependencies and AI models remain subject to their respective open-source licenses. Refer to each dependency/model's license before redistribution.
