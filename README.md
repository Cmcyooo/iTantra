# iTantra 🚨📡

## Indian Multilingual TTS & STT Aided Neural Transceiver for Low-Bitrate Links

**SIH 2026 — Problem Statement 26173**

> **iTantra** is an offline-first Android communication system that converts speech into text locally, transmits compact text over available local communication links, and converts the received text back into speech on the destination device.

**Platform:** Android  
**Language:** Kotlin  
**Status:** 🚧 Active Development  
**Core Approach:** Local Edge AI + Text-Based Peer Communication  
**Target Hardware:** Low- and mid-range Android devices

---

# 📌 What is iTantra?

iTantra is designed for communication scenarios where conventional voice transmission is inefficient, unavailable, or unnecessarily bandwidth-intensive.

Instead of transmitting raw microphone audio:

```text
Traditional Voice Communication

🎙️ Speech
    ↓
Audio Encoding
    ↓
Network
    ↓
Audio Decoding
    ↓
🔊 Speech
```

iTantra uses:

```text
iTantra

🎙️ Speech
    ↓
Silero VAD
    ↓
Local STT
    ↓
📝 Text
    ↓
Wi-Fi / Wi-Fi Direct / Bluetooth
    ↓
📝 Text
    ↓
Local TTS
    ↓
🔊 Speech
```

The central idea is:

> **Process speech locally, transmit compact text efficiently, and reconstruct speech locally.**

This moves speech intelligence to the edge while keeping the communication payload small.

---

# 🚨 SIH 2026 Problem Statement Alignment

The project is being developed against **SIH 2026 Problem Statement 26173**, which requires an Android application with lightweight and highly accurate STT/TTS for 10 Indian languages, local processing on low-power devices, pause/stoppage based sentence formation, efficient text transmission over Wi-Fi/Bluetooth-connected devices or another phone, TTS-based playback, emergency/alert announcements, and a two-phone PTT walkie-talkie demonstration.

| Requirement | iTantra Status |
|---|---|
| Android application | ✅ |
| Offline/local STT | ✅ |
| Offline/local TTS | ✅ |
| Pause/stoppage detection | ✅ |
| Sentence/utterance finalization | ✅ |
| Compact text transmission | ✅ |
| Wi-Fi communication | ✅ |
| Wi-Fi Direct / P2P communication | ✅ |
| Bluetooth communication | ✅ |
| Two-phone communication | ✅ |
| Push-to-Talk walkie-talkie architecture | ✅ |
| Emergency / high-priority alerts | ✅ |
| ACK-based alert delivery | ✅ |
| Automatic emergency peer discovery | ✅ |
| 10-language STT architecture | ✅ |
| 10-language STT validation | ✅ |
| 10-language TTS audit | ✅ |
| Single-active-language model policy | ✅ |
| Full 10-language production-quality accuracy | 🔄 Ongoing optimization |
| 4 GB physical-device validation | ⏳ Pending |
| PTT-off phone-style mode | ⏳ Pending |
| Final full-system SIH validation | ⏳ Pending |

> **Important:** The project does not claim that all ten languages currently have identical recognition quality. Accuracy is being validated and improved language-by-language on Android hardware.

---

# 🌍 Target Languages

| # | Language | Code |
|---:|---|---|
| 1 | Hindi | `hi` |
| 2 | Gujarati | `gu` |
| 3 | Marathi | `mr` |
| 4 | Kannada | `kn` |
| 5 | Malayalam | `ml` |
| 6 | Tamil | `ta` |
| 7 | Telugu | `te` |
| 8 | Odia | `or` |
| 9 | Bengali | `bn` |
| 10 | English | `en` |

The architecture uses **language-specific STT/TTS model mappings** rather than forcing one speech model to perform equally across all languages.

---

# 🎯 Core Objectives

iTantra is designed to:

- Run speech processing locally on Android.
- Avoid mandatory cloud STT/TTS services.
- Convert speech to compact text before transmission.
- Use Wi-Fi, Wi-Fi Direct, and Bluetooth transports.
- Provide a walkie-talkie style Push-to-Talk experience.
- Detect pauses and speech stoppages to finalize utterances.
- Support multilingual speech recognition and synthesis.
- Provide high-priority emergency communication.
- Use a single active STT/TTS language model pair to control memory usage.
- Operate on low- and mid-range Android hardware.
- Measure accuracy, latency, memory, stability, and end-to-end behavior on physical devices.

---

# 🧠 Overall System Architecture

```text
                         iTantra
                            │
                ┌───────────┴───────────┐
                │                       │
             NORMAL                 EMERGENCY
                │                       │
                ▼                       ▼
             TALK MODE             🚨 HELP
                │                       │
                ▼                       ▼
          Audio Capture          Peer Discovery
                │                       │
                ▼                       ▼
           Silero VAD             Best Peer Selection
                │                       │
                ▼                       ▼
           Active STT             Auto Connection
                │                       │
                ▼                       ▼
              TEXT                  🎙️ SPEAK
                │                       │
                └──────────┬────────────┘
                           │
                           ▼
                  Communication Layer
                           │
             ┌─────────────┼─────────────┐
             │             │             │
            Wi-Fi       Wi-Fi Direct   Bluetooth
             │             │             │
             └─────────────┼─────────────┘
                           │
                           ▼
                     Remote Device
                           │
                           ▼
                    Language-aware TTS
                           │
                           ▼
                       🔊 SPEAKER
```

---

# 📡 Communication Principle

The primary payload is **recognized text plus compact metadata**, not raw microphone audio.

```text
Audio
  ↓
Local STT
  ↓
Text + Metadata
  ↓
Transport
  ↓
Text + Metadata
  ↓
Local TTS
  ↓
Audio
```

This allows the communication layer to remain independent from the speech engine.

---

# 🏗️ Software Architecture

```text
┌─────────────────────────────────────────────┐
│                  UI LAYER                   │
│ Language Selector / PTT / Alerts / Status │
└──────────────────────┬──────────────────────┘
                       │
┌──────────────────────▼──────────────────────┐
│                AUDIO LAYER                  │
│ AudioRecord / VAD / Buffer / AudioTrack     │
└──────────────────────┬──────────────────────┘
                       │
┌──────────────────────▼──────────────────────┐
│                  STT LAYER                  │
│ LanguageModelManager                        │
│ Whisper / Wav2Vec2 CTC / ONNX Runtime      │
└──────────────────────┬──────────────────────┘
                       │
┌──────────────────────▼──────────────────────┐
│                MESSAGE LAYER                │
│ Text / Language / Type / Priority / IDs     │
└──────────────────────┬──────────────────────┘
                       │
┌──────────────────────▼──────────────────────┐
│               TRANSPORT LAYER               │
│ Wi-Fi / Wi-Fi Direct / Bluetooth / ACK      │
└──────────────────────┬──────────────────────┘
                       │
┌──────────────────────▼──────────────────────┐
│                  TTS LAYER                  │
│ LanguageTtsManager / Piper / VITS / MMS     │
└──────────────────────┬──────────────────────┘
                       │
┌──────────────────────▼──────────────────────┐
│                AUDIO OUTPUT                 │
│                   Speaker                   │
└─────────────────────────────────────────────┘
```

---

# 🎙️ Push-to-Talk (PTT)

PTT is the primary walkie-talkie interaction.

```text
HOLD TALK
    ↓
Microphone Capture
    ↓
Silero VAD
    ↓
Speech Detected
    ↓
User Pause / PTT Release
    ↓
Utterance Finalized
    ↓
STT
    ↓
Text
    ↓
Transport
```

The audio pipeline includes a **pre-speech ring buffer** so that the beginning of an utterance is not lost before the VAD declares speech.

Current endpointing work includes:

- approximately 320 ms pre-speech buffering
- short minimum speech-duration handling
- pause-aware endpointing
- forced finalization on PTT release
- non-blocking UI updates

---

# 🧠 Speech-to-Text Architecture

## English

Current English baseline:

```text
Whisper Tiny INT8
       ↓
sherpa-onnx
```

## Indic Languages

The current mobile architecture primarily uses:

```text
Vakyansh Wav2Vec2 Base
        ↓
INT8 ONNX
        ↓
ONNX Runtime
        ↓
CTC Decoding
```

A common STT interface hides the model-specific runtime from the communication layer.

```text
                    SttEngine
                       │
             ┌─────────┴─────────┐
             │                   │
     SherpaOnnxSttEngine   GenericOnnxCtcSttEngine
             │                   │
          Whisper           Wav2Vec2 CTC
```

---

# 🔊 Multilingual TTS Architecture

The receiving device uses the language metadata associated with the message to select the appropriate voice.

```text
Received Message
       ↓
Language = hi
       ↓
LanguageTtsManager
       ↓
Hindi TTS Voice
       ↓
Speech
```

This prevents a Hindi message from accidentally being synthesized using an English voice.

---

# 🔄 Single-Active-Model Strategy

iTantra does **not** load all STT/TTS models into RAM simultaneously.

Instead:

```text
User selects Hindi
        ↓
Load Hindi STT
Load Hindi TTS
        ↓
Use Hindi
        ↓
User switches to Tamil
        ↓
Release Hindi models
        ↓
Load Tamil models
```

The intended runtime memory composition is approximately:

```text
VAD
+
ONE active STT model
+
ONE active TTS voice
+
Application/runtime
```

This is important for 4–6 GB Android devices.

---

# 📡 Connectivity

iTantra supports multiple local communication transports.

## Wi-Fi

Local Wi-Fi communication uses:

- Network Service Discovery
- TCP sockets
- Connection state handling

## Wi-Fi Direct

Wi-Fi Direct provides peer-to-peer communication without requiring the phones to join the same conventional Wi-Fi network.

The implementation contains:

- peer discovery
- P2P group formation
- connection-state management
- socket readiness checks
- retry handling
- watchdog timeouts
- lifecycle-safe receiver/channel handling

## Bluetooth

Bluetooth communication uses:

- device discovery
- bonded-device visibility
- friendly device naming
- RFCOMM transport
- reconnect handling

The UI separates discovery from actual transport connection.

---

# 🔗 Connection State Machine

Connections are represented explicitly:

```text
IDLE
  ↓
DISCOVERING
  ↓
PEER_FOUND
  ↓
CONNECTING
  ↓
CONNECTED
```

Failure states return to a recoverable condition:

```text
CONNECTING
     ↓
   FAILED
     ↓
[ CONNECT ]
```

The UI should not require the user to press **Disconnect** just to make **Connect** available again.

---

# 🚨 Emergency Communication

Emergency communication is a separate high-priority path designed for situations where the user has little time to manage network settings.

## Intended User Experience

```text
OPEN iTantra
      ↓
🚨 1-TOUCH HELP
      ↓
Automatic peer discovery
      ↓
Best reachable iTantra peer
      ↓
Automatic connection
      ↓
READY
      ↓
🎙️ Speak
      ↓
Silero VAD
      ↓
Offline STT
      ↓
HIGH-PRIORITY ALERT
      ↓
SEND
      ↓
WAIT FOR ACK
      ↓
✅ ALERT DELIVERED
```

The user should not need to manually open Android Bluetooth/P2P settings during an emergency.

---

# 🧭 Emergency Peer Selection

A lightweight peer registry aggregates discovered peers from the available transports.

The intended preference is approximately:

```text
Currently Connected
        ↓
Recently Validated
        ↓
Wi-Fi
        ↓
Wi-Fi Direct
        ↓
Bluetooth
```

Transport selection must still respect actual availability and connection health.

The application should select the **best reachable iTantra peer**, not claim physical “nearest device” detection unless real proximity measurement is available.

---

# 🚨 Emergency State Machine

```text
IDLE
  ↓
SEARCHING
  ↓
SELECTING_PEER
  ↓
CONNECTING
  ↓
READY
  ↓
LISTENING
  ↓
PROCESSING
  ↓
SENDING
  ↓
WAITING_FOR_ACK
  ↓
DELIVERED
```

Failure:

```text
ANY STATE
   ↓
FAILED
   ↓
RETRY / EXIT
```

---

# 🔔 ACK-Based Delivery

The system does not claim successful delivery merely because a socket accepted a write.

```text
Sender
  ↓
ALERT
  ↓
Receiver
  ↓
Process ALERT
  ↓
ACK
  ↓
Sender
  ↓
✅ DELIVERED
```

If the acknowledgement is not received within the defined timeout:

```text
🚨 ALERT NOT DELIVERED
```

is reported honestly.

---

# 📊 Current STT Status

Current lightweight mobile STT results show a strong accuracy/latency trade-off for some languages and significant accuracy gaps for others.

| Language | Current Direction |
|---|---|
| Hindi | 🟢 Strong mobile candidate |
| Telugu | 🟢 Strong mobile candidate |
| English | 🟢 Strong mobile candidate |
| Tamil | 🟢 Strong mobile candidate |
| Gujarati | 🟡 Conditional |
| Kannada | 🟡 Conditional |
| Bengali | 🟠 Accuracy improvement needed |
| Malayalam | 🟠 Accuracy improvement needed |
| Marathi | 🟠 Accuracy improvement needed |
| Odia | 🔴 Major accuracy improvement needed |

The project continues evaluating lightweight IndicConformer, Zipformer, IndicWav2Vec and other practical open-source candidates.

---

# 🔊 Current TTS Status

Physical Android validation has identified strong Piper/VITS voices for several languages and conditional Meta MMS fallbacks for others.

| Language | Current Voice / Model | Mobile RTF | Status |
|---|---|---:|---|
| English | Piper English | ~0.150 | ✅ Baseline |
| Hindi | Piper Priyamvada | 0.171 | ✅ Production Candidate |
| Telugu | Piper Maya | 0.197 | ✅ Production Candidate |
| Malayalam | Piper Meera | 0.188 | ✅ Production Candidate |
| Tamil | Piper Rasa | 0.196 | ✅ Production Candidate |
| Bengali | Piper Google | 0.136 | ✅ Production Candidate |
| Marathi | Piper Google | 0.127 | ✅ Production Candidate |
| Gujarati | Meta MMS | 1.119 | 🟡 Conditional |
| Kannada | Meta MMS | 1.072 | 🟡 Conditional |
| Odia | Meta MMS | 1.033 | 🟡 Conditional |

These are device-specific engineering measurements, not universal guarantees.

---

# ⚖️ Accuracy vs Latency

A core engineering constraint is that **accuracy and mobile responsiveness must both be acceptable**.

Large models were evaluated for weak languages. They improved benchmark accuracy, but on the Snapdragon 720G they exceeded the desired mobile real-time envelope and consumed substantially more memory.

```text
Large 315M-class model
        ↓
Better WER
        ↓
~1.08–1.10 RTF
        ↓
~900 MB PSS
        ↓
❌ Not suitable for the mobile walkie-talkie path
```

Compared with:

```text
95M Base INT8 model
        ↓
Higher WER in some languages
        ↓
~0.11 RTF
        ↓
~310 MB PSS
        ↓
✅ Mobile-feasible
```

The current optimization target is therefore:

> **Find the best accuracy/latency/RAM trade-off rather than simply selecting the lowest-WER model.**

---

# 🧪 Validation & Benchmarking

The project maintains dedicated benchmark suites for:

### STT

- WER
- CER
- latency
- RTF
- model size
- PSS/RAM
- VAD endpoint behavior
- decoder correctness
- stability

### TTS

- model load time
- synthesis latency
- RTF
- model size
- PSS
- memory delta
- intelligibility
- repeated synthesis stability

### Connectivity

- discovery time
- connection establishment
- transport RTT
- reconnection
- failure behavior

### End-to-End

```text
PTT Release
    ↓
STT Complete
    ↓
Packet Send
    ↓
Packet Receive
    ↓
TTS Start
    ↓
Audio Playback
```

Cross-device one-way latency is not calculated by subtracting independent phone wall clocks.

---

# 📱 Physical Hardware Strategy

## Samsung Galaxy S24

**Model:** `SM-S921B`

Used as the primary development/reference device for:

- STT validation
- TTS integration
- language switching
- P2P testing
- connectivity testing
- regression testing

## Xiaomi Redmi Note 9 Pro

Representative configuration:

```text
Snapdragon 720G
~6 GB RAM
Android 12
```

Used as the main mid-range acceptance device for:

- TTS validation
- STT model benchmarking
- emergency communication
- memory profiling
- latency testing

## 4 GB Device

A representative physical 4 GB Android phone remains required for final low-end validation.

---

# 🧪 Emergency Validation

The zero-configuration emergency architecture has been validated through Android instrumentation on the Redmi Note 9 Pro.

The validation covered:

```text
Peer registry aggregation
Best-candidate selection
Zero-config emergency workflow
No-peer failure handling
```

The reported instrumented suite completed:

```text
3 / 3 tests passed
```

The implementation intentionally reports failure when no reachable iTantra peer exists rather than falsely claiming delivery.

---

# 🔒 Privacy & Offline Operation

The normal communication path is designed so that raw speech is not sent as the communication payload.

```text
Raw Speech
   ↓
Local STT
   ↓
Text
   ↓
Transport
```

Core inference does not require:

- cloud STT
- cloud TTS
- hosted speech APIs
- remote LLMs

---

# 🧩 Message Architecture

A communication message may contain metadata such as:

```text
sessionId
messageId
sequenceNumber
timestamp
language
messageType
priority
text
checksum
```

Control messages such as `ACK` and `PING` are kept separate from normal user text.

---

# 🛠️ Technology Stack

| Component | Technology |
|---|---|
| Platform | Android |
| Language | Kotlin |
| UI | Jetpack Compose |
| VAD | Silero VAD |
| English STT | Whisper Tiny INT8 + sherpa-onnx |
| Indic STT | Vakyansh Wav2Vec2 CTC + ONNX Runtime |
| TTS | Piper / VITS / Meta MMS fallback |
| Audio Capture | Android AudioRecord |
| Audio Playback | Android AudioTrack |
| Wi-Fi | TCP + NSD |
| Wi-Fi Direct | Android Wi-Fi P2P |
| Bluetooth | Bluetooth Classic / RFCOMM |
| Serialization | Kotlinx Serialization |
| Model Format | ONNX / runtime-specific assets |
| Version Control | Git + Git LFS |

---

# 📁 Repository Structure

```text
iTantra/
│
├── app/
│   └── src/
│       ├── main/
│       │   ├── java/com/itantra/app/
│       │   │   ├── audio/
│       │   │   ├── comm/
│       │   │   └── ui/
│       │   ├── assets/
│       │   └── jniLibs/
│       └── androidTest/
│
├── benchmarks/
│   ├── multilingual_stt/
│   ├── indic_stt/
│   ├── stt_accuracy/
│   ├── tts/
│   └── device_compatibility/
│
├── docs/
│   ├── ARCHITECTURE.md
│   ├── MULTILINGUAL_STT_ARCHITECTURE.md
│   ├── TTS_ARCHITECTURE.md
│   ├── EMERGENCY_ALERT_SPEC.md
│   ├── CONNECTIVITY_UI_BEHAVIOR.md
│   ├── DEVICE_COMPATIBILITY.md
│   ├── BENCHMARKS.md
│   ├── DECISIONS.md
│   ├── PHASE_STATUS.md
│   └── THIRD_PARTY_LICENSES.md
│
├── build.gradle.kts
├── settings.gradle.kts
├── gradlew
├── .gitattributes
└── README.md
```

---

# 🚀 Getting Started

## Requirements

- Android Studio
- Android SDK
- JDK compatible with the project
- Android device with USB debugging enabled
- Git
- Git LFS

## Clone

```bash
git clone https://github.com/Cmcyooo/iTantra.git
cd iTantra
```

## Git LFS

```bash
git lfs install
git lfs pull
```

## Build

Open the project in Android Studio and allow Gradle synchronization to complete.

Then:

```powershell
.\gradlew.bat assembleDebug
```

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Install with ADB

Check devices:

```powershell
adb devices -l
```

Install:

```powershell
adb install -r -d -t app/build/outputs/apk/debug/app-debug.apk
```

---

# 📱 Two-Phone Demonstration

The primary demonstration uses two Android devices.

## Phone A

```text
Select language
      ↓
Connect / Ready
      ↓
Hold TALK
      ↓
Speak
      ↓
Release
```

## Processing

```text
Speech
 ↓
VAD
 ↓
STT
 ↓
Text
 ↓
Wi-Fi / Wi-Fi Direct / Bluetooth
```

## Phone B

```text
Received Text
      ↓
Language-aware TTS
      ↓
🔊 Speech
```

The reverse direction should also work.

---

# 🚨 Emergency Demonstration

```text
Phone A
   ↓
🚨 1-TOUCH HELP
   ↓
Automatic peer discovery
   ↓
Best reachable peer
   ↓
Automatic connection
   ↓
🎙️ Speak
   ↓
VAD
   ↓
STT
   ↓
HIGH Priority ALERT
   ↓
Transport
   ↓
Phone B
   ↓
Alert TTS
   ↓
🔊 Emergency announcement
   ↓
ACK
   ↓
✅ Delivered
```

If no peer is reachable:

```text
🚨 No reachable iTantra device
```

The application must not falsely report successful delivery.

---

# 🧪 Testing Checklist

## Normal Communication

- [ ] App starts successfully
- [ ] Language selector works
- [ ] TALK starts recording immediately
- [ ] Speech is detected
- [ ] Pause finalizes the utterance correctly
- [ ] STT returns text
- [ ] Text is transmitted
- [ ] Receiver selects correct TTS language
- [ ] Receiver speaks the message

## Wi-Fi

- [ ] Device discovery works
- [ ] CONNECT is immediately available when disconnected
- [ ] CONNECTING state appears during connection
- [ ] CONNECTED state appears only after transport is ready
- [ ] DISCONNECT works
- [ ] Connection failure returns to a usable state

## Wi-Fi Direct

- [ ] P2P section opens without crashing
- [ ] Peer discovery works
- [ ] Group formation works
- [ ] Socket connection works
- [ ] Retry logic works
- [ ] Failure is recoverable

## Bluetooth

- [ ] Bluetooth state is detected
- [ ] Nearby/bonded devices are visible
- [ ] Rescan works
- [ ] Friendly device names are displayed
- [ ] RFCOMM connection works
- [ ] Disconnect/reconnect works

## Emergency

- [ ] One-touch emergency opens
- [ ] Peer discovery starts automatically
- [ ] Best reachable peer is selected
- [ ] Automatic connection works
- [ ] Emergency speech is captured
- [ ] ALERT packet is sent
- [ ] Receiver speaks the alert
- [ ] ACK is received
- [ ] Delivered state is shown
- [ ] No-peer failure is reported honestly

---

# 🛣️ Development Roadmap

## ✅ Completed

- Android application foundation
- Local microphone capture
- Silero VAD
- Offline English STT
- Offline English TTS
- Wi-Fi transport
- Wi-Fi Direct architecture
- Bluetooth transport
- Device discovery
- PTT walkie-talkie foundation
- Pause/end detection
- Multilingual STT architecture
- Multilingual STT benchmarking
- Multilingual TTS benchmarking
- Language-aware TTS routing
- Single-active-model strategy
- Emergency alert subsystem
- ACK-based emergency delivery
- Zero-configuration emergency architecture
- Physical 6 GB device validation
- Connectivity stability and PTT instrumentation

## 🔄 Current Work

- Multilingual STT accuracy improvement
- Lightweight IndicConformer/AI4Bharat candidate evaluation
- Real Android microphone recognition tuning
- Wi-Fi/Bluetooth/P2P UX hardening
- End-to-end two-phone validation
- Model accuracy/latency optimization

## ⏳ Remaining

- Stronger STT models for weak languages
- Full 10-language end-to-end production validation
- Physical 4 GB device validation
- PTT-off phone-style mode
- Final latency optimization
- Long-duration stress testing
- Final SIH demonstration hardening

---

# ⚠️ Current Limitations

The project is actively being optimized.

### STT Accuracy

Recognition quality is not yet equal across all 10 languages. Current lightweight models provide excellent mobile latency but weaker accuracy in some languages.

### TTS Quality

Gujarati, Kannada, and Odia currently use slower conditional fallback voices in the TTS matrix.

### Hardware Coverage

A physical 4 GB Android validation device remains outstanding.

### Phone Mode

PTT-off continuous phone-style interaction is still under development.

### Connectivity

Local communication requires a reachable iTantra peer or supported connected device. The application cannot transmit an emergency message when there is no reachable communication path.

### Long-Range Communication

The current mandatory communication implementation is based on:

- Wi-Fi
- Wi-Fi Direct
- Bluetooth

Dedicated long-range radio/LoRa hardware is not part of the current mandatory core implementation.

---

# 🧭 Engineering Principles

## Offline First

Core speech processing remains local.

## Accuracy + Latency

A model must satisfy both recognition quality and mobile performance requirements.

## Single Active Model

Only the selected language model pair should be active in memory.

## Text-Based Communication

Speech is converted to compact text before transmission.

## Evidence-Based Validation

Physical Android measurements are preferred over desktop assumptions.

## Honest Failure Handling

A message is not considered delivered without actual receiver acknowledgement.

## Modular Architecture

Models and runtime implementations can be replaced without rewriting the transport system.

---

# 🔐 Open-Source & Licensing

iTantra uses open-source frameworks and model technologies where licensing permits.

Third-party licensing and attribution information is maintained in:

```text
docs/THIRD_PARTY_LICENSES.md
```

Each production model and voice must be checked for:

- license
- redistribution rights
- attribution requirements
- commercial-use restrictions

---

# 🎯 Final Vision

iTantra aims to provide an efficient multilingual communication system for constrained environments by moving speech intelligence to the edge.

```text
                  HUMAN
                    │
                    ▼
                🎙️ SPEECH
                    │
                    ▼
                LOCAL AI
                    │
                    ▼
                 📝 TEXT
                    │
                    ▼
           LOW-BANDWIDTH LINK
                    │
                    ▼
                 📝 TEXT
                    │
                    ▼
                LOCAL AI
                    │
                    ▼
                 🔊 SPEECH
                    │
                    ▼
                  HUMAN
```

The final objective is:

> **Understand speech locally, communicate efficiently, and reconstruct speech locally — across languages and constrained communication links.**

---

# 🇮🇳 iTantra

### Indian Multilingual TTS & STT Aided Neural Transceiver for Low-Bitrate Links

**SIH 2026 — Problem Statement 26173**

```text
Offline AI
+
Multilingual Speech
+
Low-Bandwidth Communication
+
Emergency Communication
+
Android Edge Inference
```

**🚧 Active Development**
