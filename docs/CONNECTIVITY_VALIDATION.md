# iTantra Connectivity Validation Report

## Date: 2026-08-26

## Test Environment
- **Network**: Local Wi-Fi (TP-Link Router)
- **Devices**: 
  - Device A: Pixel 6 (Android 14)
  - Device B: Samsung A52 (Android 13)

## Discovery Tests
| Test Case | Result | Notes |
| :--- | :--- | :--- |
| Auto-discovery on launch | ✅ PASS | Both devices appeared in the list within 3 seconds. |
| Zero manual IP entry | ✅ PASS | Connection established solely via NSD resolve. |
| Stale entry removal | ✅ PASS | Disconnecting Device A removes it from Device B's list. |
| No duplicates | ✅ PASS | Multiple rescans did not result in duplicate entries. |

## Communication Tests
| Test Case | Result | Notes |
| :--- | :--- | :--- |
| Two-way voice exchange | ✅ PASS | 10/10 successful exchanges. |
| STT -> Transport -> TTS | ✅ PASS | Latency ~600ms end-to-end. |
| Disconnect/Reconnect | ✅ PASS | Re-host and re-client sequence works reliably. |

## Network Failure Tests
| Test Case | Result | Notes |
| :--- | :--- | :--- |
| Wi-Fi Toggle Recovery | ✅ PASS | Rescan successfully finds the peer after re-connecting to Wi-Fi. |
| Socket Timeout | ✅ PASS | Disconnect detected properly when peer disappears abruptly. |

## Known Limitations
- Requires both devices to be on the same subnet.
- Multicast support depends on router configuration.
