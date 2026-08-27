iTantra

Indian Multilingual TTS & STT Aided Neural Transceiver for Low-Bitrate Links

SIH 2026 — Problem Statement 26173

iTantra is an offline, multilingual voice communication system for Android designed for low-bandwidth, constrained-device and emergency communication scenarios.

Instead of transmitting speech audio directly, iTantra converts speech into text locally, transmits the compact text representation over an available communication link, and reconstructs the speech on the receiving device.

Speech
   ↓
Offline STT
   ↓
Text
   ↓
Wi-Fi / Wi-Fi Direct / Bluetooth
   ↓
Text
   ↓
Offline TTS
   ↓
Speech

This approach significantly reduces the amount of data that needs to cross the communication link while keeping speech processing on-device.

🚨 Problem

Traditional voice communication requires transmission of audio data.

Audio transmission becomes inefficient when communication links have:

Low data rates

Limited throughput

Unstable connectivity

Limited device resources

Communication constraints during emergency situations

This is particularly important for:

Emergency and distress communication

Low-bandwidth environments

Areas with unreliable connectivity

Field communication

Local peer-to-peer communication

Situations where voice communication is more accessible than typing

iTantra addresses this by transmitting recognized text instead of the original speech waveform.

User Speech
     ↓
Local Speech Recognition
     ↓
Recognized Text
     ↓
Compact Network Packet
     ↓
Low-Bitrate Communication Link
     ↓
Received Text
     ↓
Local Speech Synthesis
     ↓
Voice Playback

Core Principle

Transmit the meaning, not the audio waveform.

🎯 Objectives

iTantra is designed to:

Work fully offline

Run AI inference locally on Android devices

Support the 10 languages specified by SIH PS 26173

Minimize RAM, CPU and storage usage

Maintain low end-to-end latency

Use open-source technologies and models

Support Wi-Fi-based peer communication

Support Wi-Fi Direct peer-to-peer communication

Support Bluetooth communication

Provide a walkie-talkie style Push-to-Talk experience

Provide high-priority emergency communication

Support modular language-specific STT/TTS models

Operate on low- and mid-range Android hardware

🌍 Required Languages

The problem statement specifies the following 10 languages:

Hindi

Gujarati

Marathi

Kannada

Malayalam

Tamil

Telugu

Odia

Bengali

English

iTantra uses language-specific model selection rather than assuming that one model will provide equally strong performance across every Indian language.

🧠 Core Architecture

                          PHONE A
                             │
                             ▼
                       🎙️ User Speech
                             │
                             ▼
                    ┌─────────────────┐
                    │  Audio Capture  │
                    └────────┬────────┘
                             │
                             ▼
                    ┌─────────────────┐
                    │   Silero VAD    │
                    └────────┬────────┘
                             │
                   Speech End / Release
                             │
                             ▼
                   ┌───────────────────┐
                   │  Language Manager │
                   └─────────┬─────────┘
                             │
                             ▼
                      Active STT Model
                             │
                             ▼
                           Text
                             │
                             ▼
                ┌────────────────────────┐
                │ Communication Manager  │
                └────────────┬───────────┘
                             │
                  ┌──────────┼──────────┐
                  │          │          │
                  ▼          ▼          ▼
                Wi-Fi    Wi-Fi Direct  Bluetooth
                  │          │          │
                  └──────────┼──────────┘
                             │
                             ▼
                           PHONE B
                             │
                         Received Text
                             │
                             ▼
                   ┌────────────────────┐
                   │   TTS Manager      │
                   └─────────┬──────────┘
                             │
                       Active TTS Voice
                             │
                             ▼
                         🔊 Speech

📡 Key Design Principle

Audio is not transmitted between phones.

The communication payload is:

Speech
  ↓
STT
  ↓
Text + Metadata
  ↓
Network
  ↓
Text + Metadata
  ↓
TTS
  ↓
Speech

The transport layer therefore carries compact text rather than raw microphone audio.

This makes the communication architecture suitable for low-data-rate links.

🏗️ System Architecture

iTantra is divided into modular layers.

┌─────────────────────────────────────────────┐
│                 UI LAYER                    │
│ Language Selection / PTT / Alerts / Status │
└──────────────────────┬──────────────────────┘
                       │
┌──────────────────────▼──────────────────────┐
│                AUDIO LAYER                  │
│ AudioRecord / VAD / Buffer / AudioTrack     │
└──────────────────────┬──────────────────────┘
                       │
┌──────────────────────▼──────────────────────┐
│                 STT LAYER                   │
│ LanguageModelManager                        │
│ Whisper / Wav2Vec2 CTC / ONNX Runtime       │
└──────────────────────┬──────────────────────┘
                       │
┌──────────────────────▼──────────────────────┐
│                 TEXT LAYER                  │
│ Normalization / Language / Metadata         │
└──────────────────────┬──────────────────────┘
                       │
┌──────────────────────▼──────────────────────┐
│              TRANSPORT LAYER                │
│ Wi-Fi / Wi-Fi Direct / Bluetooth / ACK      │
└──────────────────────┬──────────────────────┘
                       │
┌──────────────────────▼──────────────────────┐
│                 TTS LAYER                   │
│ LanguageTtsManager / Piper / VITS / MMS     │
└──────────────────────┬──────────────────────┘
                       │
┌──────────────────────▼──────────────────────┐
│                AUDIO OUTPUT                 │
│                   Speaker                   │
└─────────────────────────────────────────────┘

🛠️ Technology Stack

Android

Kotlin

Jetpack Compose

Android SDK

Minimum SDK: API 24

Audio

Android AudioRecord

Android AudioTrack

16 kHz microphone processing

Mono PCM audio

Audio buffer management

Voice Activity Detection

Silero VAD

Offline execution

ONNX-based inference

Speech start/end detection

Speech-to-Text

English

Whisper Tiny

INT8 ONNX

sherpa-onnx

Indic Languages

Vakyansh Wav2Vec2 CTC

INT8 ONNX

Generic ONNX Runtime Android

The architecture supports both runtime types behind a common STT interface.

Text-to-Speech

Piper / VITS

sherpa-onnx

ONNX-based local inference

Offline speech synthesis

Android AudioTrack

Communication

Local Wi-Fi

Network Service Discovery (NSD)

TCP sockets

Wi-Fi Direct / Android Wi-Fi P2P

Bluetooth

ACK-based delivery confirmation

Connection state management

Retry and reconnect mechanisms

Development

Android Studio

Git

GitHub

Git LFS

Python

ONNX tooling

Physical Android benchmark harness

AI-assisted development tools

📱 Current System

The current production architecture supports:

🎙️ Speech
   ↓
Silero VAD
   ↓
Language-specific STT
   ↓
📝 Text
   ↓
Wi-Fi / Wi-Fi Direct / Bluetooth
   ↓
📝 Text
   ↓
Language-specific TTS
   ↓
🔊 Speech

The application also provides a Push-to-Talk interaction:

HOLD TALK
    ↓
Speak
    ↓
Pause / Release
    ↓
STT
    ↓
Text
    ↓
Send
    ↓
Remote TTS
    ↓
Speaker

🎙️ Push-to-Talk

PTT is the primary walkie-talkie interaction.

Flow

PTT Press
   ↓
Microphone Capture
   ↓
VAD
   ↓
Speech Detection
   ↓
PTT Release / Pause
   ↓
Utterance Finalized
   ↓
STT
   ↓
Text Message
   ↓
Network

The application does not transmit raw microphone audio during the normal text-transceiver path.

🧠 Multilingual STT

The initial experiment with a single multilingual Whisper Tiny model demonstrated that model size alone does not guarantee useful Indian-language recognition.

The resulting architecture uses language-specific STT candidates.

                     STT
                      │
              LanguageModelManager
                      │
          ┌───────────┴────────────┐
          │                        │
       English                    Indic
          │                        │
     sherpa-onnx             ONNX Runtime
          │                        │
      Whisper                Wav2Vec2 CTC

Model Loading Strategy

Only the currently selected language should be active.

User selects Hindi
        ↓
Load Hindi STT
        ↓
Use Hindi
        ↓
User switches Tamil
        ↓
Release Hindi STT
        ↓
Load Tamil STT

The application is therefore not intended to keep all 10 STT models resident in RAM simultaneously.

🔊 Multilingual TTS

TTS follows the same modular design.

Received Text
      ↓
Language Metadata
      ↓
LanguageTtsManager
      ↓
Selected Voice
      ↓
Offline TTS
      ↓
AudioTrack
      ↓
Speaker

Only the active TTS voice should remain loaded.

🚨 Emergency Communication

iTantra includes a dedicated emergency/alert communication subsystem.

Emergency messages are separate from normal communication messages.

Normal Message
messageType = NORMAL
priority    = NORMAL

Emergency Message
messageType = ALERT
priority    = HIGH

🚨 Emergency Alert Flow

Emergency Mode
      ↓
Hold TALK
      ↓
Speak
      ↓
Pause / Release
      ↓
Offline STT
      ↓
HIGH Priority ALERT
      ↓
Wi-Fi / Wi-Fi Direct / Bluetooth
      ↓
Receiving Device
      ↓
High-Priority TTS
      ↓
🔊 Alert Playback

Implemented emergency behavior includes:

High-priority alert messages

Explicit ACK-based delivery confirmation

Alert queue

Duplicate message protection

High-priority application audio playback

Normal speech interruption when necessary

Connection failure feedback

Reconnect and retry

🔔 Emergency Alert Reliability

A sender does not display:

✓ Delivered

simply because the packet was written to a socket.

Instead:

Sender
   ↓
Send ALERT
   ↓
Receiver
   ↓
Process ALERT
   ↓
Send ACK
   ↓
Sender
   ↓
✓ Delivered

If the transport is unavailable:

🚨 Alert not delivered
Check connection

is displayed.

Duplicate alerts are identified using their message IDs.

📡 Device Discovery

iTantra supports automatic device discovery.

Wi-Fi

Network Service Discovery (NSD) is used to advertise and discover iTantra services.

Wi-Fi Direct

Android Wi-Fi P2P discovery is used to find nearby devices.

Bluetooth

Bluetooth discovery is used to identify nearby compatible devices.

The UI attempts to use:

Call Sign
   ↓
System Device Name
   ↓
Friendly Fallback

instead of exposing raw addresses or internal identifiers.

🔗 Connection State

Connections are represented with explicit states:

IDLE
  ↓
DISCOVERING
  ↓
PEER FOUND
  ↓
CONNECTING
  ↓
CONNECTED

Failure:

CONNECTING
     ↓
   FAILED

The UI reflects the connection state:

[ CONNECT ]

       ↓

[ CONNECTING... ]

       ↓

[ CONNECTED ✓ ]

After successful connection, the user receives confirmation such as:

✓ Connected to Station Alpha • Wi-Fi Direct

🔄 P2P Reliability

Wi-Fi Direct connection establishment can involve a delay between group formation and network readiness.

The P2P transport therefore uses:

Defensive channel initialization

Runtime permission checks

Peer discovery state handling

Connection timeout

Socket retry mechanism

Lifecycle-safe cleanup

Explicit connection state transitions

The goal is to prevent stale or incomplete P2P states from appearing as successful connections.

🧪 Multilingual STT Validation

The project has benchmarked all 10 target languages on physical Android hardware.

The model evaluation includes:

WER

CER

Latency

RTF

Model size

RAM

Stability

Native-script output

Current physical-device results demonstrated stronger candidates for languages such as:

Hindi
Gujarati
Telugu
Kannada

while several languages require further STT accuracy improvement.

The project intentionally does not treat every language as production-ready simply because the model executes successfully.

🔊 Multilingual TTS Validation

Offline TTS voices have been benchmarked on a physical 6 GB Android device.

Production-ready voices have been identified for several languages, including:

Language

Primary Voice

RTF

Status

English

Piper English

~0.15

✅ Baseline

Hindi

Piper Priyamvada

0.171

✅ Production Ready

Telugu

Piper Maya

0.197

✅ Production Ready

Malayalam

Piper Meera

0.188

✅ Production Ready

Tamil

Piper Rasa

0.196

✅ Production Ready

Bengali

Piper Google

0.136

✅ Production Ready

Marathi

Piper Google

0.127

✅ Production Ready

Gujarati

MMS baseline

1.119

🟡 Conditional

Kannada

MMS baseline

1.072

🟡 Conditional

Odia

Validation ongoing

—

🔄

The preferred direction is lightweight Piper/VITS voices where they provide sufficient quality and mobile performance.

📊 Current Development Benchmarks

Benchmarks are collected from actual physical Android devices whenever possible.

Representative TTS Results

Hindi       → RTF 0.171
Telugu      → RTF 0.197
Malayalam   → RTF 0.188
Tamil       → RTF 0.196
Bengali     → RTF 0.136
Marathi     → RTF 0.127

Emergency Validation

8 / 8 automated tests passed
10 alert cycles
~+3.27 MB PSS delta
0 crashes
0 ANRs
0 SIGSEGV

Important

Benchmark values are device-specific engineering measurements.

They are not universal guarantees for every Android device.

⚡ Performance Strategy

iTantra is designed around constrained-device execution.

Lazy Model Loading

Only the active language model should be loaded.

Device Storage
      ↓
Selected Language
      ↓
Active STT/TTS Model
      ↓
RAM

Inactive models should be released before switching languages.

CPU-Oriented Inference

The architecture does not depend on:

Dedicated GPUs

Vendor-specific NPUs

Cloud inference

Remote speech APIs

VAD Optimization

VAD prevents unnecessary STT processing during silence.

Audio Processing

Audio capture and inference are kept away from the main UI thread where possible.

Text-Based Networking

Only compact text and metadata are transmitted in the core communication path.

📱 Hardware Strategy

The application is being validated across multiple Android hardware classes.

Development / Reference Device

Samsung Galaxy S24

Used for:

development

STT validation

language switching

connectivity testing

production integration testing

Mid-Range Validation Device

Xiaomi Redmi Note 9 Pro

Representative configuration:

Snapdragon 720G
~6 GB RAM
Android 12

Used for:

Emergency Mode validation

multilingual TTS validation

resource profiling

latency testing

Low-End Validation

A representative 4 GB RAM Android device remains required for final physical validation.

The project does not claim complete 4 GB compatibility until that testing is performed.

💾 Memory Strategy

The application is explicitly designed to avoid loading every language model into memory simultaneously.

Bad design:

10 STT models
+
10 TTS models
+
VAD

Preferred design:

VAD
+
ONE active STT model
+
ONE active TTS voice
+
Application Runtime

When the user changes language:

Old STT
   ↓
Release

Old TTS
   ↓
Release

New STT
   ↓
Load

New TTS
   ↓
Load

This architecture is intended to make the system practical for low- and mid-range Android hardware.

🔐 Offline-First Design

The core AI pipeline does not require internet access.

The application does not require:

Cloud STT

Cloud TTS

Hosted LLM APIs

Remote speech recognition

Remote speech synthesis

The intended processing path is:

Microphone
   ↓
Local VAD
   ↓
Local STT
   ↓
Local Text Processing
   ↓
Local Transport
   ↓
Local TTS
   ↓
Speaker

🔒 Privacy

The normal communication path does not transmit raw microphone audio.

Instead:

Raw Speech
   ↓
Local STT
   ↓
Text
   ↓
Network

Diagnostic logging is designed to avoid unnecessary recording of personal speech or sensitive content.

🧪 Testing Strategy

Each subsystem is tested independently before full integration.

Audio

Microphone
     ↓
PCM
     ↓
VAD

STT

Speech
   ↓
STT
   ↓
Text

TTS

Text
 ↓
TTS
 ↓
Speech

Transport

Text
 ↓
Wi-Fi / Wi-Fi Direct / Bluetooth
 ↓
Text

Emergency

Alert
 ↓
Priority Queue
 ↓
Transport
 ↓
ACK
 ↓
High Priority TTS

End-to-End

Speech
 ↓
STT
 ↓
Transport
 ↓
TTS
 ↓
Speech

🧪 Physical Device Validation

The project uses automated Android instrumentation where possible.

Tests include:

STT model loading

Repeated STT inference

Repeated TTS synthesis

Language switching

Emergency alerts

Connection failures

Reconnection

Memory stability

Transport reliability

PTT behavior

The goal is to measure real device behavior rather than relying only on desktop benchmarks.

📊 Performance Measurement

The end-to-end chain is divided into measurable stages:

Speech End
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

Measured boundaries include:

STT Latency
Transport Latency
TTS Latency
End-to-End Latency

This allows individual bottlenecks to be identified and optimized independently.

📦 Message Architecture

Each message can contain metadata such as:

sessionId
messageId
sequenceNumber
timestamp
language
messageType
priority
text
checksum

Control messages such as:

ACK
PING

are separated from normal user messages.

🏗️ Production Multilingual Architecture

The production STT architecture uses a common interface:

                 SttEngine
                    │
        ┌───────────┴────────────┐
        │                        │
SherpaOnnxSttEngine      GenericOnnxCtcSttEngine
        │                        │
     Whisper                Wav2Vec2 CTC

The communication layer does not need to know which engine is active.

Similarly, TTS is designed around:

LanguageTtsManager
        ↓
Language
        ↓
Active Voice
        ↓
TTS Engine

This allows individual language models to be replaced without rewriting the transceiver.

📁 Project Structure

iTantra/
│
├── app/
│   └── src/
│       ├── main/
│       │   ├── java/
│       │   │   └── com/itantra/app/
│       │   │       ├── audio/
│       │   │       ├── comm/
│       │   │       └── ui/
│       │   │
│       │   ├── assets/
│       │   │   ├── STT models
│       │   │   ├── TTS models
│       │   │   └── VAD model
│       │   │
│       │   └── jniLibs/
│       │
│       └── androidTest/
│
├── benchmarks/
│   ├── multilingual_stt/
│   ├── indic_stt/
│   ├── tts/
│   └── device_compatibility/
│
├── docs/
│   ├── ARCHITECTURE.md
│   ├── MULTILINGUAL_STT_ARCHITECTURE.md
│   ├── TTS_ARCHITECTURE.md
│   ├── DEVICE_COMPATIBILITY.md
│   ├── EMERGENCY_ALERT_SPEC.md
│   ├── CONNECTIVITY_UI_BEHAVIOR.md
│   ├── BENCHMARKS.md
│   ├── DECISIONS.md
│   ├── PHASE_STATUS.md
│   └── THIRD_PARTY_LICENSES.md
│
├── gradle/
├── build.gradle.kts
├── settings.gradle.kts
├── gradlew
├── .gitattributes
└── README.md

✅ Development Status

Phase

Status

Phase 0 — Project & Git setup

✅ Complete

Phase 1 — Android foundation

✅ Complete

Phase 2 — Microphone + PCM + Silero VAD

✅ Complete

Phase 3 — Offline English STT

✅ Complete

Phase 4 — Offline English TTS

✅ Complete

Phase 5 — Wi-Fi text transport

✅ Complete

Phase 6 — Full voice transceiver

✅ Complete

Phase 6.x — PTT / pause / latency fixes

✅ Complete

Phase 7 — Multilingual STT research & validation

✅ Complete

Phase 7.x — Multilingual STT architecture

✅ Complete

Phase 7.9 — Initial production multilingual STT integration

✅ Complete

Phase 8 — Emergency / Alert communication

✅ Complete

Phase 8.x — P2P stability & connection UX

✅ Complete

Phase 8.5 — Hindi/Gujarati TTS validation

✅ Complete

Phase 8.6 — Telugu/Kannada TTS validation

✅ Complete

Phase 8.7 — Malayalam/Tamil TTS validation

✅ Complete

Phase 8.8 — Bengali/Marathi TTS validation

✅ Complete

Phase 8.9 — Odia TTS validation

🔄 Next

Final multilingual TTS integration

⏳

4 GB hardware validation

⏳

Full multilingual end-to-end testing

⏳

PTT-off phone mode

⏳

Final SIH hardening

⏳

🛣️ Roadmap

Phase 8.9 — Odia TTS

Final TTS language validation:

Odia TTS candidate search

Desktop screening

Physical Android validation

Latency

RTF

RAM

Intelligibility

Stability

Phase 9 — Multilingual STT + TTS Integration

After the TTS model audit is complete:

User selects language
       ↓
LanguageModelManager
       ↓
Active STT
       +
LanguageTtsManager
       ↓
Active TTS

The user should only need to select the communication language.

Example:

Language: ಕನ್ನಡ

should automatically select the corresponding STT and TTS pipeline.

Phase 9.1 — Full End-to-End Multilingual Communication

The final communication loop should work as:

PHONE A

🎙️ Speak
   ↓
VAD
   ↓
STT
   ↓
Text
   ↓
Wi-Fi / Bluetooth
   ↓

PHONE B

Text
   ↓
TTS
   ↓
🔊 Speech

The same flow should work across the validated language set.

Phase 9.2 — PTT Walkie-Talkie Validation

Two phones should behave as:

PHONE A
PTT
 ↓
Speech
 ↓
STT
 ↓
Text
 ↓
Transport
 ↓
TTS
 ↓
PHONE B

The system should support repeated communication without requiring application restart.

Phase 9.3 — Phone Mode

The problem statement also specifies phone-like operation when PTT is disabled.

The future flow is:

PTT ON
   ↓
Walkie-Talkie Mode

PTT OFF
   ↓
Phone / Continuous Conversation Mode

This remains part of the remaining implementation.

Phase 9.4 — Low-End Hardware Validation

Final validation should include:

Samsung Galaxy S24
        ↓
Reference

6 GB Mid-Range Device
        ↓
Validation

4 GB Low-End Device
        ↓
Validation

The following should be measured:

RAM

CPU

STT latency

TTS latency

RTF

Model footprint

End-to-end latency

Battery/resource behavior

Stability

Language switching

Phase 10 — Final SIH Hardening

Final testing will include:

Clean installation

Offline operation

Two-phone communication

PTT reliability

Emergency alerts

Wi-Fi

Wi-Fi Direct

Bluetooth

Language switching

STT/TTS stability

Low-end hardware

Mid-range hardware

Long-duration stress testing

Final documentation

Backup APK

Reproducible judge demonstration

🚀 Demo Concept

The primary demonstration uses two Android phones.

                 PHONE A
                    │
                    ▼
              🎙️ Speak
                    │
                    ▼
               Silero VAD
                    │
                    ▼
                Offline STT
                    │
                    ▼
                  Text
                    │
             Wi-Fi / Bluetooth
                    │
                    ▼
                  Text
                    │
                    ▼
               Offline TTS
                    │
                    ▼
               🔊 Speech
                    │
                 PHONE B

🚨 Emergency Demo

PHONE A
   ↓
Emergency Mode
   ↓
Hold TALK
   ↓
Speak
   ↓
Offline STT
   ↓
HIGH Priority Alert
   ↓
Wi-Fi / Bluetooth
   ↓
PHONE B
   ↓
Emergency Playback
   ↓
🔊 ALERT

The receiving phone prioritizes the emergency message over normal application speech.

📡 Communication Demo

The communication layer is designed around multiple local transports:

             iTantra Transport
                    │
        ┌───────────┼───────────┐
        │           │           │
       Wi-Fi    Wi-Fi Direct  Bluetooth
        │           │           │
        └───────────┼───────────┘
                    │
                Text Packet

The application uses a shared transport abstraction so that speech-processing logic remains independent from the communication technology.

⚠️ Current Limitations

iTantra is still under active development.

Current limitations include:

STT accuracy is not equal across all 10 target languages.

Several Indic STT models require further accuracy improvement.

Gujarati TTS currently has a slower validated baseline and needs a faster production voice.

Kannada TTS currently has a slower validated baseline and needs a faster production voice.

Odia TTS validation remains.

Full production integration of all 10 TTS languages is not yet complete.

A representative physical 4 GB Android device is still required for final hardware validation.

PTT-off phone-style continuous communication remains under development.

Final full-system stress testing remains.

These limitations are tracked explicitly instead of being hidden.

🔐 Open-Source Strategy

iTantra uses open-source software, frameworks and model technologies wherever possible.

Current technologies include:

sherpa-onnx

ONNX Runtime

Whisper

Vakyansh

Piper / VITS

Silero VAD

Android platform APIs

License information for individual third-party components and models is maintained in:

docs/THIRD_PARTY_LICENSES.md

Every new model or voice must be independently checked for:

License

Attribution requirements

Redistribution rights

Commercial-use conditions

🧭 Design Philosophy

iTantra follows a vertical-slice development strategy.

Each subsystem is:

Implemented
   ↓
Tested
   ↓
Benchmarked
   ↓
Optimized
   ↓
Integrated

The project prioritizes:

Offline First

Core inference does not rely on cloud services.

Text Over Audio

The communication channel carries recognized text instead of raw speech.

Device Awareness

Models are selected based on actual mobile measurements.

Modular AI

Language-specific models can be replaced independently.

Single Active Model

Inactive language models are released from memory.

Measurable Performance

Latency and resource usage are measured on physical hardware.

Recoverable Networking

Connection failures, acknowledgements, retries and reconnect states are explicitly handled.

📊 What Makes iTantra Different?

Traditional voice communication:

Voice
 ↓
Audio Encoding
 ↓
Network
 ↓
Audio Decoding
 ↓
Voice

iTantra:

Voice
 ↓
Local AI
 ↓
Text
 ↓
Low-Bitrate Link
 ↓
Text
 ↓
Local AI
 ↓
Voice

The communication link therefore does not need to carry the original voice waveform.

🏁 Final Vision

iTantra aims to provide a practical multilingual communication system for constrained environments by moving speech intelligence to the edge.

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

The final goal is:

Understand speech locally, communicate efficiently, and reconstruct speech locally.

🇮🇳 iTantra

Indian Multilingual TTS & STT Aided Neural Transceiver for Low-Bitrate Links

SIH 2026 — Problem Statement 26173

Offline AI • Multilingual Speech • Low-Bandwidth Communication • Emergency Communication • Android Edge Inference

Current Status

Core Offline Transceiver        ✅
Silero VAD                      ✅
English STT                     ✅
English TTS                     ✅
Wi-Fi                           ✅
Wi-Fi Direct                    ✅
Bluetooth                       ✅
Automatic Discovery             ✅
PTT Walkie-Talkie               ✅
Emergency Alerts                ✅
Multilingual STT Validation     ✅
Initial Multilingual STT        ✅
Multilingual TTS Validation     🔄
4 GB Hardware Validation        ⏳
Full 10-Language Integration    ⏳
Phone Mode                      ⏳
Final SIH Hardening             ⏳
