# SIH 26173 — iTantra: Connectivity Stabilization Plan

This plan focuses on validating and stabilizing the current NSD + Wi-Fi transport layer to ensure a reliable P2P voice transceiver experience.

## User Review Required

> [!IMPORTANT]
> This pass does not introduce new features. It strictly stabilizes the existing local-network discovery and communication pipeline.

## Proposed Changes

### Documentation Updates

#### [MODIFY] [PHASE_STATUS.md](file:///D:/iTantra/docs/PHASE_STATUS.md)
Update Phase 5 and 6 to include NSD discovery and stabilization.

#### [MODIFY] [ARCHITECTURE.md](file:///D:/iTantra/docs/ARCHITECTURE.md)
Add `DiscoveryManager` to the architecture overview.

#### [NEW] [CONNECTIVITY_VALIDATION.md](file:///D:/iTantra/docs/CONNECTIVITY_VALIDATION.md)
Document the verification results of the two-phone test.

### UI Polish

#### [MODIFY] [MicrophoneTestScreen.kt](file:///D:/iTantra/app/src/main/java/com/itantra/app/ui/MicrophoneTestScreen.kt)
Add status labels: `Local Wi-Fi` and `Internet not required`.

### Code Stabilization

#### [MODIFY] [DiscoveryManager.kt](file:///D:/iTantra/app/src/main/java/com/itantra/app/comm/DiscoveryManager.kt)
* Ensure `resolveService` failures don't hang discovery.
* Improve stale device removal logic.

#### [MODIFY] [WiFiTransport.kt](file:///D:/iTantra/app/src/main/java/com/itantra/app/comm/WiFiTransport.kt)
* Verify `ServerSocket` is properly closed on disconnect to allow immediate restart as Host.

## Verification Plan

### Automated Tests
- Build the project to ensure no regressions.

### Manual Verification (Two Phones)
- Verify automatic discovery on launch.
- Verify one-tap connection.
- Verify 5+ successful two-way voice exchanges.
- Verify recovery after Wi-Fi toggle.
