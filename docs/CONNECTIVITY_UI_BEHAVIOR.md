# Connectivity UI Behavior & State Machine Specification

This document details the user experience, state machines, and lifecycle transitions for iTantra communication modes (Local Wi-Fi, Wi-Fi Direct / P2P, and Bluetooth Classic RFCOMM), as well as the Push-to-Talk (PTT) interaction flow.

---

## 1. Push-to-Talk (Hold-to-Speak) UX Flow

The primary voice communication model is a physical-radio-style Push-to-Talk button. To ensure intuitive feedback and eliminate missed audio, the button and top status reflect distinct responsive states:

| Step | User Action | Button State | Top Status Header | Audio Pipeline Behavior |
|---|---|---|---|---|
| **1. Idle** | Default state | `[ TALK ]` / `HOLD TO TALK` | `READY` | Audio hardware idle. VAD reset. |
| **2. Active Hold** | Press and hold button | `[ RELEASE TO SEND ]` | `RECORDING...` | Immediate `AudioRecord` read. PCM samples continuously stream to rolling `pttAccumulator` (max 30s) and VAD. |
| **3. Release** | User lifts finger | `[ PROCESSING... ]` | `PROCESSING...` | Audio recording stops. If VAD speech accumulator is empty (e.g. short/quiet utterance), `pttAccumulator` is finalized and passed to Whisper STT. |
| **4. Transmission** | STT complete | `[ PROCESSING... ]` | `FORWARDING...` | Transcribed text is packaged into `P2PMessage` (JSON framed) and written to the active transport socket. |
| **5. Success** | Packet sent | `[ SENT ✓ ]` (Green) | `SENT ✅` | Displayed for 1.5s, then automatically returns to `[ TALK ]` / `READY`. |
| **6. Error** | Network/STT failure | `[ FAILED ]` (Red) | `FAILED` | Displayed for 1.5s, then automatically returns to `[ TALK ]` / `READY`. |

> [!NOTE]
> In **Emergency Alert Mode**, the states adapt with high-contrast red styling (`🚨 EMERGENCY ALERT MODE`, `RECORDING ALERT...`, `BROADCASTING ALERT...`, `ALERT SENT ✅`).

---

## 2. Transport Selection & Discovery

Users switch transports using the top `FilterChip` selector (`Wi-Fi`, `P2P`, `BT`):

### A. Wi-Fi Direct (P2P)
* **9-State Machine**:
  `IDLE` $\rightarrow$ `DISCOVERING` $\rightarrow$ `PEER_FOUND` $\rightarrow$ `CONNECTING` $\rightarrow$ `GROUP_FORMING` $\rightarrow$ `SOCKET_CONNECTING` $\rightarrow$ `CONNECTED` (Failures: `FAILED`, `DISCONNECTED`).
* **UI States**:
  - Unpermitted: `"Nearby devices permission required"`
  - Disabled/Unsupported: `"Wi-Fi Direct unavailable"`
  - Discovery Active: `"Searching for nearby Wi-Fi Direct devices..."`
  - Empty: `"No nearby Wi-Fi Direct devices found. Tap Rescan."`
* **Lifecycle & Resilience**:
  - Automatically recovers using `ChannelListener` if framework disconnects.
  - Registers system broadcast receiver with `Context.RECEIVER_EXPORTED` on Android 14+.
  - 15-second connection watchdog timeout.
  - Client performs 12 DHCP resolution retries while waiting for group owner address assignment.

### B. Bluetooth Classic (RFCOMM)
* **Pre-Population**: Bonded/paired devices appear instantly without waiting for inquiry scan completion.
* **Rescan Safety**: Clicking `Ready (Rescan)` cancels any active discovery, clears stale peers, and launches a clean scan without duplicate receiver leaks.
* **Friendly Name Resolution Priority**:
  1. User-assigned **Call Sign** (e.g. `"Station Alpha"`, from `CallSignManager`)
  2. Cached remote Bluetooth name (parsed from EIR `EXTRA_NAME` or `ACTION_NAME_CHANGED`)
  3. `BluetoothDevice.name` (if queryable from cache)
  4. System device name
  5. Fallback: `"Nearby iTantra Device"`
  * **Zero Raw MACs**: MAC addresses are never shown as the primary display name.
* **Bluetooth Disabled Card**: If Bluetooth is turned off, an error card prompts `"Bluetooth is turned off"` with a direct `ENABLE` action.

### C. Local Wi-Fi (UDP / TCP)
* Uses UDP broadcast discovery (`WifiDiscoveryManager`) and TCP socket connection (`WifiTransport`).
* Displays peer IP and friendly name or Call Sign.

---

## 3. Connection State Machine & Button Progression

In the discovery cards, device items progress through verified connection states:

| Connection Phase | Button Text | Button State | Top Status | Snackbar Notification |
|---|---|---|---|---|
| **Disconnected** | `[ CONNECT ]` | Enabled | `⚪ DISCONNECTED` | None |
| **Connecting** | `[ CONNECTING... ]` | Disabled | `🟡 CONNECTING` | None |
| **Connected** | `[ CONNECTED ✓ ]` | Disabled (Green) | `🟢 CONNECTED` | `"Connected to <Device Name> • <Transport>"` (3s) |
| **Failed** | `[ CONNECTION FAILED ]` | Enabled (Red) | `🔴 ERROR` | `"Connection failed"` (3s) |
| **Disconnect** | `[ DISCONNECT ]` | Enabled (Red full-width) | `⚪ DISCONNECTED` | `"Disconnected from <Device Name>"` (3s) |

> [!IMPORTANT]
> The UI strictly gates `CONNECTED` on the active transport socket. It never claims `CONNECTED` prematurely during initial Wi-Fi Direct group negotiation or Bluetooth SDP handshake.

---

## 4. Zero-Configuration Emergency Autonomous UI (Phase 11)

### A. Pre-Emergency Readiness State Banner
Permanently visible below the main header in Normal Mode:
* **🟢 Ready • Connected**: A transport link is already established.
* **🟢 Ready • Reachable: [Station Alpha]**: A peer has been discovered and cached in `PeerRegistry` (Wi-Fi, P2P, or Bluetooth).
* **🟡 Searching for nearby iTantra devices...**: Background discovery is querying for local peers.
* **🔴 Searching for nearby iTantra devices...**: No devices currently detected; background scanner active.

### B. 1-Touch Emergency Flow
* Accessible via prominent red **🚨 1-TOUCH HELP / EMERGENCY** button or Mode chip.
* Bypasses manual Bluetooth settings, network scanning, pairing PIN dialogs, and P2P group configuration.

### C. 11-State Emergency UI Progression Card
When emergency mode is active, the dedicated `ZeroConfigEmergencySection` takes focus:

| State | UI Badge & Indicators | Action Area | Description |
|---|---|---|---|
| `IDLE` | `IDLE` (Yellow) | `[ START EMERGENCY CONNECTION ]` | Initial quiescent state. |
| `SEARCHING` | `FINDING NEAREST DEVICE...` (Yellow Spinner) | Spinner + status text | Aggregates peers from `PeerRegistry` across Wi-Fi, Wi-Fi Direct, and Bluetooth. |
| `SELECTING_PEER` | `SELECTING PEER...` (Yellow) | Station & Transport metadata updated | Evaluates priority: Connected > Validated > Wi-Fi > Wi-Fi Direct > Bluetooth. |
| `CONNECTING` | `CONNECTING...` (Red Spinner) | Spinner + target station name | Initiates automated transport connection with fallback to secondary transport. |
| `READY` | `READY` (Green Badge) | **[ HOLD TO SPEAK ]** (Big Crimson Mic) | Socket connected and audio pipeline primed. |
| `LISTENING` | `RECORDING SPEECH...` (Amber/Purple) | **[ RECORDING ]** (Pulsing Mic) | User holding button. Silero VAD active with 320ms pre-speech buffer. |
| `PROCESSING` | `PROCESSING SPEECH...` (Yellow Spinner) | Spinner + "Processing audio..." | Offline STT transcription and domain normalization active. |
| `SENDING` | `TRANSMITTING ALERT...` (Yellow Spinner) | Spinner + "Sending alert..." | Packaging compact `ALERT` packet with `priority=HIGH`. |
| `WAITING_FOR_ACK` | `WAITING FOR ACK...` (Yellow Spinner) | Spinner + "Waiting for confirmation..." | Socket write complete. 5-second watchdog listening for receiver ACK. |
| `DELIVERED` | `ALERT DELIVERED ✓` (Green Checkmark) | `[ SEND ANOTHER ALERT ]` | Remote receiver confirmed cryptographic ACK. Sender verified. |
| `FAILED` | `ALERT NOT DELIVERED` (Red Alert) | `[ RETRY ]` `[ RETURN TO NORMAL ]` | Honest non-delivery notice on connection failure or ACK timeout. |

### D. Normal Mode Non-Regression
* Toggling between Normal and Emergency modes preserves normal PTT transceiver functionality, language selector, TTS playback, and live transcripts.

