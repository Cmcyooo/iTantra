# iTantra 🚨📡

## Indian Multilingual TTS & STT Aided Neural Transceiver for Low-Bitrate Links

**SIH 2026 — Problem Statement 26173**

> **iTantra** is an offline-first Android communication system that converts speech into text locally, transmits compact text over available local communication links, and converts the received text back into speech on the destination device.

**Platform:** Android
**Language:** Kotlin
**Release:** 🚀 v1.0.0
**Core Approach:** Local Edge AI + Text-Based Peer Communication
**Target Hardware:** Low- and mid-range Android devices

---

# 📱 Download iTantra

## 🚀 Latest Release

**[⬇️ Download iTantra v1.0.0](https://github.com/Cmcyooo/iTantra/releases/latest)**

iTantra uses a **Modular Language Pack Architecture** so users do not need to download all language models at once.

### Installation flow

```text
Download Base APK
        ↓
Install iTantra
        ↓
Open Language Packs
        ↓
Choose the languages you need
        ↓
Download / Import language packs
        ↓
Use speech recognition + speech synthesis offline
```

### Base APK

For most modern Android phones:

**Recommended:** `app-arm64-v8a-release.apk`
Approximate size: **96 MB**

The ARM64 base application contains:

* iTantra UI
* Push-to-Talk engine
* audio capture and playback
* Silero VAD
* Auto-LID
* communication transports
* emergency communication subsystem
* Language Pack Manager
* shared runtime components

A **Universal APK** is also provided for broader CPU compatibility.

### Language Packs

Language packs are downloaded separately so users install only the languages they need.

Supported languages:

| Language  | Code |
| --------- | ---- |
| Hindi     | `hi` |
| Gujarati  | `gu` |
| Marathi   | `mr` |
| Kannada   | `kn` |
| Malayalam | `ml` |
| Tamil     | `ta` |
| Telugu    | `te` |
| Odia      | `or` |
| Bengali   | `bn` |
| English   | `en` |

Each language pack contains the required **offline STT and TTS models** for that language.

### Offline operation

After a language pack has been installed:

```text
Microphone
   ↓
Local VAD
   ↓
Local STT
   ↓
Text
   ↓
Local Wi-Fi / Wi-Fi Direct / Bluetooth
   ↓
Text
   ↓
Local TTS
   ↓
Speaker
```

Core speech inference does not require a cloud STT, cloud TTS, hosted speech API, or remote LLM.

---

# 📥 Installation Guide

## Step 1 — Download the Base APK

Open:

**[iTantra GitHub Releases](https://github.com/Cmcyooo/iTantra/releases/latest)**

Download:

```text
app-arm64-v8a-release.apk
```

This is the recommended build for modern ARM64 Android phones.

For devices with different CPU architectures, use the appropriate APK from the release.

---

## Step 2 — Install the APK

Transfer the APK to your Android phone and open it.

Android may ask you to allow installation from an unknown source.

Enable the required permission and install the application.

The application package is:

```text
com.itantra.app
```

---

## Step 3 — Open iTantra

Launch the application.

On the first launch, iTantra provides a **Language Packs** setup interface.

You can also open the Language Pack Manager later from the application UI.

---

## Step 4 — Choose Your Languages

Open:

```text
Language Packs
```

You will see the available languages with their installation status and approximate storage/download requirements.

Choose only the languages you need.

For example:

```text
☑ English
☑ Hindi
☐ Gujarati
☐ Marathi
☐ Kannada
☐ Malayalam
☐ Tamil
☐ Telugu
☐ Odia
☐ Bengali
```

---

## Step 5 — Download the Language Pack

Press:

```text
INSTALL
```

for the desired language.

The application downloads the corresponding language pack from the GitHub Release and installs the required models locally.

Language packs can also be imported manually as ZIP files.

This is useful for:

* offline installation
* USB transfer
* SD-card transfer
* controlled/demo environments without Internet access

---

## Step 6 — Use iTantra Offline

Once the required language pack is installed:

```text
Select Language
        ↓
Connect to another iTantra device
        ↓
Hold TALK
        ↓
Speak
        ↓
Release
        ↓
Local STT
        ↓
Compact text transmission
        ↓
Remote device
        ↓
Local TTS
        ↓
Speech
```

The actual speech recognition and synthesis for installed language packs runs locally on the device.

---

# 📌 What is iTantra?

iTantra is designed for communication scenarios where conventional voice transmission is inefficient, unavailable, or unnecessarily bandwidth-intensive.

Instead of transmitting raw microphone audio, iTantra converts speech into compact text before transmission.

### Traditional Voice Communication

```text
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

### iTantra

```text
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

The project is being developed against **SIH 2026 Problem Statement 26173**.

| Requirement                                  | iTantra Status          |
| -------------------------------------------- | ----------------------- |
| Android application                          | ✅                       |
| Offline/local STT                            | ✅                       |
| Offline/local TTS                            | ✅                       |
| Pause/stoppage detection                     | ✅                       |
| Sentence/utterance finalization              | ✅                       |
| Compact text transmission                    | ✅                       |
| Wi-Fi communication                          | ✅                       |
| Wi-Fi Direct / P2P communication             | ✅                       |
| Bluetooth communication                      | ✅                       |
| Two-phone communication                      | ✅                       |
| Push-to-Talk walkie-talkie architecture      | ✅                       |
| Emergency / high-priority alerts             | ✅                       |
| ACK-based alert delivery                     | ✅                       |
| Automatic emergency peer discovery           | ✅                       |
| 10-language STT architecture                 | ✅                       |
| 10-language STT validation                   | ✅                       |
| 10-language TTS audit                        | ✅                       |
| Single-active-language model policy          | ✅                       |
| Full 10-language production-quality accuracy | 🔄 Ongoing optimization |
| 4 GB physical-device validation              | ⏳ Pending               |
| PTT-off phone-style mode                     | ⏳ Pending               |
| Final full-system SIH validation             | ⏳ Pending               |

> **Important:** Recognition quality is not currently identical across all ten languages. Accuracy is being validated and improved language-by-language on physical Android hardware.

---

# 🌍 Supported Languages

|  # | Language  | Code |
| -: | --------- | ---- |
|  1 | Hindi     | `hi` |
|  2 | Gujarati  | `gu` |
|  3 | Marathi   | `mr` |
|  4 | Kannada   | `kn` |
|  5 | Malayalam | `ml` |
|  6 | Tamil     | `ta` |
|  7 | Telugu    | `te` |
|  8 | Odia      | `or` |
|  9 | Bengali   | `bn` |
| 10 | English   | `en` |

The architecture uses **language-specific STT and TTS model mappings** rather than forcing one speech model to perform equally across all languages.

---

# 🎯 Core Objectives

iTantra is designed to:

* Run speech processing locally on Android.
* Avoid mandatory cloud STT/TTS services.
* Convert speech to compact text before transmission.
* Use Wi-Fi, Wi-Fi Direct, and Bluetooth transports.
* Provide a walkie-talkie style Push-to-Talk experience.
* Detect pauses and speech stoppages to finalize utterances.
* Support multilingual speech recognition and synthesis.
* Provide high-priority emergency communication.
* Use a single active STT/TTS language model pair to control memory usage.
* Operate on low- and mid-range Android hardware.
* Measure accuracy, latency, memory, stability, and end-to-end behavior on physical devices.

---

# 🧠 Overall System Architecture

```text
                          iTantra
                            │
                 ┌──────────┴──────────┐
                 │                     │
              NORMAL               EMERGENCY
                 │                     │
                 ▼                     ▼
             TALK MODE              🚨 HELP
                 │                     │
                 ▼                     ▼
           Audio Capture         Peer Discovery
                 │                     │
                 ▼                     ▼
            Silero VAD          Best Peer Selection
                 │                     │
                 ▼                     ▼
            Active STT             Auto Connection
                 │                     │
                 ▼                     ▼
               TEXT                 🎙️ SPEAK
                 │                     │
                 └──────────┬──────────┘
                            │
                            ▼
                    Communication Layer
                            │
                 ┌──────────┼──────────┐
                 │          │          │
                Wi-Fi   Wi-Fi Direct Bluetooth
                 │          │          │
                 └──────────┼──────────┘
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

The primary communication payload is:

**recognized text + compact metadata**

rather than raw microphone audio.

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
│ Language Selector / PTT / Alerts / Status  │
└──────────────────────┬──────────────────────┘
                       │
┌──────────────────────▼──────────────────────┐
│                AUDIO LAYER                  │
│ AudioRecord / VAD / Buffer / AudioTrack    │
└──────────────────────┬──────────────────────┘
                       │
┌──────────────────────▼──────────────────────┐
│                  STT LAYER                  │
│ LanguageModelManager                       │
│ Whisper / Wav2Vec2 CTC / ONNX Runtime     │
└──────────────────────┬──────────────────────┘
                       │
┌──────────────────────▼──────────────────────┐
│                MESSAGE LAYER                │
│ Text / Language / Type / Priority / IDs    │
└──────────────────────┬──────────────────────┘
                       │
┌──────────────────────▼──────────────────────┐
│               TRANSPORT LAYER               │
│ Wi-Fi / Wi-Fi Direct / Bluetooth / ACK     │
└──────────────────────┬──────────────────────┘
                       │
┌──────────────────────▼──────────────────────┐
│                  TTS LAYER                  │
│ LanguageTtsManager / Piper / VITS / MMS    │
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

The audio pipeline includes a **pre-speech ring buffer** so the beginning of an utterance is not lost before the VAD declares speech.

Current endpointing work includes:

* approximately 320 ms pre-speech buffering
* short minimum speech-duration handling
* pause-aware endpointing
* forced finalization on PTT release
* non-blocking UI updates

---

# 🧠 Speech-to-Text Architecture

## English

```text
Whisper Tiny INT8
       ↓
sherpa-onnx
```

## Indic Languages

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
              ┌────────┴────────┐
              │                 │
      SherpaOnnxSttEngine   GenericOnnxCtcSttEngine
              │                 │
           Whisper          Wav2Vec2 CTC
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

# 📦 Modular Language Pack Architecture

The Base APK contains shared runtime components, while language-specific speech models are distributed separately.

```text
                 iTantra Base APK
                       │
                 Language Manager
                       │
       ┌───────────────┼───────────────┐
       │               │               │
       ▼               ▼               ▼
     Hindi          Kannada         Telugu
      Pack            Pack            Pack
       │               │               │
    STT + TTS       STT + TTS       STT + TTS
```

### Advantages

* Smaller initial APK download.
* Users choose only required languages.
* Lower storage consumption.
* Easier model updates.
* Language models can be removed when no longer required.
* Offline model installation can be performed through downloaded/imported packages.
* Core application remains independent from individual language models.

---

# 📡 Connectivity

iTantra supports multiple local communication transports.

## Wi-Fi

Local Wi-Fi communication uses:

* Network Service Discovery
* TCP sockets
* connection state handling

## Wi-Fi Direct

Wi-Fi Direct provides peer-to-peer communication without requiring the phones to join the same conventional Wi-Fi network.

The implementation contains:

* peer discovery
* P2P group formation
* connection-state management
* socket readiness checks
* retry handling
* watchdog timeouts
* lifecycle-safe receiver/channel handling

## Bluetooth

Bluetooth communication uses:

* device discovery
* bonded-device visibility
* friendly device naming
* RFCOMM transport
* reconnect handling

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

The user should not need to manually open Android Bluetooth or P2P settings during an emergency.

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

If acknowledgement is not received within the defined timeout:

```text
🚨 ALERT NOT DELIVERED
```

is reported honestly.

---

# 📊 Current STT Status

Current lightweight mobile STT results show a strong accuracy/latency trade-off for some languages and significant accuracy gaps for others.

| Language  | Current Direction                    |
| --------- | ------------------------------------ |
| Hindi     | 🟢 Strong mobile candidate           |
| Telugu    | 🟢 Strong mobile candidate           |
| English   | 🟢 Strong mobile candidate           |
| Tamil     | 🟢 Strong mobile candidate           |
| Gujarati  | 🟡 Conditional                       |
| Kannada   | 🟡 Conditional                       |
| Bengali   | 🟠 Accuracy improvement needed       |
| Malayalam | 🟠 Accuracy improvement needed       |
| Marathi   | 🟠 Accuracy improvement needed       |
| Odia      | 🔴 Major accuracy improvement needed |

The project continues evaluating lightweight IndicConformer, Zipformer, IndicWav2Vec, and other practical open-source candidates.

---

# 🔊 Current TTS Status

Physical Android validation has identified strong Piper/VITS voices for several languages and conditional Meta MMS fallbacks for others.

| Language  | Current Voice / Model | Status                 |
| --------- | --------------------- | ---------------------- |
| English   | Piper English         | ✅ Baseline             |
| Hindi     | Piper Priyamvada      | ✅ Production Candidate |
| Telugu    | Piper Maya            | ✅ Production Candidate |
| Malayalam | Piper Meera           | ✅ Production Candidate |
| Tamil     | Piper Rasa            | ✅ Production Candidate |
| Bengali   | Piper Google          | ✅ Production Candidate |
| Marathi   | Piper Google          | ✅ Production Candidate |
| Gujarati  | Meta MMS              | 🟡 Conditional         |
| Kannada   | Meta MMS              | 🟡 Conditional         |
| Odia      | Meta MMS              | 🟡 Conditional         |

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
Higher mobile compute cost
        ↓
Higher RAM usage
        ↓
❌ Not suitable for the preferred mobile walkie-talkie path
```

Compared with lightweight INT8 models:

```text
Lightweight INT8 model
        ↓
Practical mobile latency
        ↓
Lower memory
        ↓
✅ Mobile-feasible
```

The current optimization target is therefore:

> **Find the best accuracy/latency/RAM trade-off rather than simply selecting the lowest-WER model.**

---

# 🧪 Validation & Benchmarking

The project maintains dedicated benchmark suites for:

### STT

* WER
* CER
* latency
* RTF
* model size
* PSS/RAM
* VAD endpoint behavior
* decoder correctness
* stability

### TTS

* model load time
* synthesis latency
* RTF
* model size
* PSS
* memory delta
* intelligibility
* repeated synthesis stability

### Connectivity

* discovery time
* connection establishment
* transport RTT
* reconnection
* failure behavior

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

Used as a primary development/reference device for:

* STT validation
* TTS integration
* language switching
* P2P testing
* connectivity testing
* regression testing

## Xiaomi Redmi Note 9 Pro

Representative configuration:

```text
Snapdragon 720G
~6 GB RAM
Android 12
```

Used as the main mid-range acceptance device for:

* TTS validation
* STT model benchmarking
* emergency communication
* memory profiling
* latency testing

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

* cloud STT
* cloud TTS
* hosted speech APIs
* remote LLMs

Only the compact text message and required metadata are transmitted between peer devices.

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

| Component       | Technology                           |
| --------------- | ------------------------------------ |
| Platform        | Android                              |
| Language        | Kotlin                               |
| UI              | Jetpack Compose                      |
| VAD             | Silero VAD                           |
| English STT     | Whisper Tiny INT8 + sherpa-onnx      |
| Indic STT       | Vakyansh Wav2Vec2 CTC + ONNX Runtime |
| TTS             | Piper / VITS / Meta MMS fallback     |
| Audio Capture   | Android AudioRecord                  |
| Audio Playback  | Android AudioTrack                   |
| Wi-Fi           | TCP + NSD                            |
| Wi-Fi Direct    | Android Wi-Fi P2P                    |
| Bluetooth       | Bluetooth Classic / RFCOMM           |
| Serialization   | Kotlinx Serialization                |
| Model Format    | ONNX / runtime-specific assets       |
| Version Control | Git + Git LFS                        |

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

# 🚀 Developer Setup

## Requirements

* Android Studio
* Android SDK
* JDK compatible with the project
* Android device with USB debugging enabled
* Git
* Git LFS

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

Check connected devices:

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

* [ ] App starts successfully
* [ ] Language selector works
* [ ] TALK starts recording immediately
* [ ] Speech is detected
* [ ] Pause finalizes the utterance correctly
* [ ] STT returns text
* [ ] Text is transmitted
* [ ] Receiver selects correct TTS language
* [ ] Receiver speaks the message

## Language Packs

* [ ] Base APK installs successfully
* [ ] Language Pack Manager opens
* [ ] All 10 languages are displayed
* [ ] Install button works
* [ ] Download progress is shown
* [ ] Pack integrity is verified
* [ ] Installed language is recognized correctly
* [ ] Offline STT works
* [ ] Offline TTS works
* [ ] Pack can be removed
* [ ] Active language cannot be removed incorrectly

## Wi-Fi

* [ ] Device discovery works
* [ ] CONNECT is immediately available when disconnected
* [ ] CONNECTING state appears during connection
* [ ] CONNECTED state appears only after transport is ready
* [ ] DISCONNECT works
* [ ] Connection failure returns to a usable state

## Wi-Fi Direct

* [ ] P2P section opens without crashing
* [ ] Peer discovery works
* [ ] Group formation works
* [ ] Socket connection works
* [ ] Retry logic works
* [ ] Failure is recoverable

## Bluetooth

* [ ] Bluetooth state is detected
* [ ] Nearby/bonded devices are visible
* [ ] Rescan works
* [ ] Friendly device names are displayed
* [ ] RFCOMM connection works
* [ ] Disconnect/reconnect works

## Emergency

* [ ] One-touch emergency opens
* [ ] Peer discovery starts automatically
* [ ] Best reachable peer is selected
* [ ] Automatic connection works
* [ ] Emergency speech is captured
* [ ] ALERT packet is sent
* [ ] Receiver speaks the alert
* [ ] ACK is received
* [ ] Delivered state is shown
* [ ] No-peer failure is reported honestly

---

# 🛣️ Development Roadmap

## ✅ Completed

* Android application foundation
* Local microphone capture
* Silero VAD
* Offline English STT
* Offline English TTS
* Wi-Fi transport
* Wi-Fi Direct architecture
* Bluetooth transport
* Device discovery
* PTT walkie-talkie foundation
* Pause/end detection
* Multilingual STT architecture
* Multilingual STT benchmarking
* Multilingual TTS benchmarking
* Language-aware TTS routing
* Single-active-model strategy
* Emergency alert subsystem
* ACK-based emergency delivery
* Zero-configuration emergency architecture
* Physical 6 GB device validation
* Connectivity stability and PTT instrumentation
* Modular language-pack architecture
* Base APK size optimization
* 10-language language-pack distribution
* UI/UX refinement

## 🔄 Current Work

* Multilingual STT accuracy improvement
* Lightweight IndicConformer / AI4Bharat candidate evaluation
* Real Android microphone recognition tuning
* Wi-Fi/Bluetooth/P2P UX hardening
* End-to-end two-phone validation
* Model accuracy/latency optimization

## ⏳ Remaining

* Stronger STT models for weak languages
* Full 10-language end-to-end production validation
* Physical 4 GB device validation
* PTT-off phone-style mode
* Final latency optimization
* Long-duration stress testing
* Final SIH demonstration hardening

---

# ⚠️ Current Limitations

The project is actively being optimized.

### STT Accuracy

Recognition quality is not yet equal across all 10 languages. Current lightweight models provide good mobile latency but weaker accuracy in some languages.

### TTS Quality

Gujarati, Kannada, and Odia currently use slower conditional fallback voices.

### Hardware Coverage

A physical 4 GB Android validation device remains outstanding.

### Phone Mode

PTT-off continuous phone-style interaction is still under development.

### Connectivity

Local communication requires a reachable iTantra peer or supported connected device.

The application cannot transmit an emergency message when there is no reachable communication path.

### Long-Range Communication

The current communication implementation is based on:

* Wi-Fi
* Wi-Fi Direct
* Bluetooth

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

* license
* redistribution rights
* attribution requirements
* commercial-use restrictions

---

# 📚 Research & Technology References

The following research papers, open-source projects, and official Android documentation were used during the design and development of iTantra.

### Speech Recognition

**Vakyansh — ASR Toolkit for Low Resource Indic Languages**
https://arxiv.org/abs/2203.16512

**Whisper — Robust Speech Recognition via Large-Scale Weak Supervision**
https://arxiv.org/abs/2212.04356

### Voice Activity Detection

**Silero VAD — Voice Activity Detection**
https://github.com/snakers4/silero-vad

### Speech Processing & Inference

**ONNX Runtime — Machine Learning Inference Runtime**
https://github.com/microsoft/onnxruntime

**sherpa-onnx — Offline Speech Processing Toolkit**
https://github.com/k2-fsa/sherpa-onnx

### Text-to-Speech

**Piper — Fast Local Neural Text-to-Speech**
https://github.com/OHF-voice/piper1-gpl

### Android Communication

**Android Wi-Fi Direct / Wi-Fi P2P**
https://developer.android.com/develop/connectivity/wifi/wifip2p

**Android Bluetooth**
https://developer.android.com/develop/connectivity/bluetooth

---

# 🔗 Project Repository

**iTantra — Source Code and Project Documentation**

https://github.com/Cmcyooo/iTantra

**Latest Release**

https://github.com/Cmcyooo/iTantra/releases/latest

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

### Core System Concept

iTantra keeps speech processing on the device:

```text
Speech
  ↓
VAD
  ↓
Offline STT
  ↓
Text + Metadata
  ↓
Local Link
  ↓
Text + Metadata
  ↓
Offline TTS
  ↓
Speech
```

Only the compact text message and required metadata are transmitted between peer devices; raw speech audio is not transmitted.

> **Transmit the message, not the waveform.** 🇮🇳📡
