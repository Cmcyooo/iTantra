# Phase 2 — Microphone + Audio + VAD (Step 1: Microphone → 16 kHz PCM audio capture)

Establish a lightweight, reusable PCM audio pipeline using Android's native `AudioRecord` API, targeting low-end and mid-range devices.

## User Review Required

> [!IMPORTANT]
> The app will require `RECORD_AUDIO` permission. I will implement a runtime request for this.

## Proposed Changes

### Android Manifest

#### [MODIFY] [AndroidManifest.xml](file:///D:/iTantra/app/src/main/AndroidManifest.xml)
- Add `android.permission.RECORD_AUDIO`.

### Audio Pipeline

#### [NEW] [AudioCaptureManager.kt](file:///D:/iTantra/app/src/main/java/com/itantra/app/audio/AudioCaptureManager.kt)
- Manages `AudioRecord` lifecycle.
- Configured for 16 kHz, Mono, 16-bit PCM.
- Uses a background Coroutine to read PCM data.
- Calculates real-time stats: Duration, Sample Count, and RMS.
- Exposes states for UI via `StateFlow`.

### UI Components

#### [NEW] [MicrophoneTestScreen.kt](file:///D:/iTantra/app/src/main/java/com/itantra/app/ui/MicrophoneTestScreen.kt)
- Displays permission status, recording status, and audio stats.
- Handles runtime permission request using `rememberLauncherForActivityResult`.
- Start/Stop controls.

#### [MODIFY] [MainActivity.kt](file:///D:/iTantra/app/src/main/java/com/itantra/app/MainActivity.kt)
- Integrate `MicrophoneTestScreen` as the main content.

## Verification Plan

### Manual Verification
1. Deploy to the physical Samsung device.
2. Grant microphone permission.
3. Test START/STOP multiple times.
4. Verify:
   - Sample count increases during recording.
   - Duration is correct (`samples / 16000`).
   - RMS level fluctuates with voice input.
   - No crashes or UI freezes.
   - Resources (AudioRecord) are released on stop.
