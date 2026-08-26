# Architecture Decisions

... [Existing content] ...

## Wi-Fi Direct (P2P) Stabilization
* **Safe Initialization**: Wrapped `WifiP2pManager.initialize` in try-catch and added null checks for `Channel`. Many Android devices report P2P service availability but fail to initialize if Wi-Fi is in a transient state.
* **Permission Guardrails**: Added explicit runtime permission checks before calling `discoverPeers` or `requestPeers` to prevent `SecurityException` crashes.
* **Reliable State Machine**: `WiFiDirectTransport` now follows a strict flow: `DISCONNECTED` -> `CONNECTING` (Group Formation) -> `CONNECTING` (Socket Setup) -> `CONNECTED`. Socket connection includes a 10-attempt retry loop to handle the lag between group formation and network availability.
* **Connection Timeout**: Implemented a 15-second timeout for socket establishment to prevent the UI from being stuck in "CONNECTING" state indefinitely.

## UI Connection Feedback
* **Positive Reinforcement**: Added a temporary Snackbar popup ("Connected to <device name>") that appears for 2-3 seconds upon successful transport connection.
* **Button State Persistence**: The "Connect" button in discovery lists now transitions through `CONNECT`, `CONNECTING...`, and `CONNECTED ✓` to provide immediate feedback to user actions.
* **Visual Status Indicators**: Connection indicators now include emoji/color coding (🟢 CONNECTED, 🟡 CONNECTING, 🔴 ERROR) for high-glanceability in the field.
* **Device Name Priority**:
    1. User-defined CallSign (stored from previous interactions).
    2. Resolved Peer Name (from Bluetooth/P2P system name).
    3. Friendly fallback ("Nearby iTantra Device").
    * Raw MAC addresses and IP addresses are hidden from the primary display labels.
