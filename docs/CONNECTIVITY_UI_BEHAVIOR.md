# Connectivity UI Behavior

This document describes the expected user experience and state transitions for iTantra connectivity.

## 1. Discovery
* **Action**: User opens "CONNECTION SETTINGS" and selects a transport mode (Wi-Fi, P2P, or BT).
* **Behavior**:
    - App automatically starts scanning for nearby iTantra devices.
    - Status: "Scanning..." with a circular progress indicator.
    - Devices appear in a list with a friendly name and their identifier (IP/MAC).
    - If no devices found: "No nearby iTantra devices found."
    - "Ready (Rescan)" allows manual restart of discovery.

## 2. Connecting
* **Action**: User taps "Connect" on a specific device.
* **Behavior**:
    - Button changes to `CONNECTING...` and is disabled to prevent duplicate attempts.
    - Top status indicator changes to `🟡 CONNECTING`.
    - Snackbar: None (yet).
* **Timeout**: If connection is not established within 15 seconds, the state transitions to `ERROR`.

## 3. Connected
* **Trigger**: Transport socket is successfully opened and verified.
* **Behavior**:
    - Button in the list changes to `CONNECTED ✓` (Green).
    - Top status indicator changes to `🟢 CONNECTED`.
    - **Confirmation Popup**: A Snackbar appears for ~3 seconds:
      `Connected to <Device Name> • <Transport Type>`
    - Transceiver Mode: Becomes ready for PTT communication.

## 4. Connection Failed
* **Trigger**: Permission denied, timeout reached, or socket error.
* **Behavior**:
    - Top status indicator changes to `🔴 ERROR`.
    - Snackbar: `Connection failed`.
    - Button returns to `CONNECT` state.
    - Detailed error logged to Logcat for diagnostics.

## 5. Disconnected
* **Action**: User taps "DISCONNECT" or peer leaves.
* **Behavior**:
    - Top status indicator changes to `⚪ DISCONNECTED`.
    - All buttons return to `CONNECT` state.
    - Transport resources are released immediately.
