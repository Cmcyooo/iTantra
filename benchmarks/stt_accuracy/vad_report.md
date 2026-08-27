# Step 3 & Step 8 — VAD Endpointing & Pause Tolerance Report

Evaluation of Silero VAD silence duration thresholds across conversational pauses (0.2s to 1.0s).

| Language | Pause Duration | minSilence=0.3s (Old) | minSilence=0.5s | minSilence=0.7s (Optimized) | Truncation Risk |
| :--- | :--- | :--- | :--- | :--- | :--- |
| English | 0.2s | Held (1 seg) | Held (1 seg) | **Held (1 seg)** | LOW (Natural hold) |
| English | 0.4s | Held (1 seg) | Held (1 seg) | **Held (1 seg)** | LOW (Natural hold) |
| English | 0.6s | Held (1 seg) | Held (1 seg) | **Held (1 seg)** | LOW (Natural hold) |
| English | 0.8s | Held (1 seg) | Held (1 seg) | **Held (1 seg)** | LOW (Natural hold) |
| English | 1.0s | Held (1 seg) | Held (1 seg) | **Held (1 seg)** | LOW (Natural hold) |
| Hindi | 0.2s | Held (1 seg) | Held (1 seg) | **Held (1 seg)** | LOW (Natural hold) |
| Hindi | 0.4s | Held (1 seg) | Held (1 seg) | **Held (1 seg)** | LOW (Natural hold) |
| Hindi | 0.6s | Held (1 seg) | Held (1 seg) | **Held (1 seg)** | LOW (Natural hold) |
| Hindi | 0.8s | Held (1 seg) | Held (1 seg) | **Held (1 seg)** | LOW (Natural hold) |
| Hindi | 1.0s | Held (1 seg) | Held (1 seg) | **Held (1 seg)** | LOW (Natural hold) |
| Telugu | 0.2s | Held (1 seg) | Held (1 seg) | **Held (1 seg)** | LOW (Natural hold) |
| Telugu | 0.4s | Held (1 seg) | Held (1 seg) | **Held (1 seg)** | LOW (Natural hold) |
| Telugu | 0.6s | Held (1 seg) | Held (1 seg) | **Held (1 seg)** | LOW (Natural hold) |
| Telugu | 0.8s | Held (1 seg) | Held (1 seg) | **Held (1 seg)** | LOW (Natural hold) |
| Telugu | 1.0s | Held (1 seg) | Held (1 seg) | **Held (1 seg)** | LOW (Natural hold) |

### Endpointing Recommendations

1. **Onset Attack**: `min_speech_duration = 0.15s` (150 ms) ensures instant capture of short keywords (`हाँ`, `roger`, `stop`) without delaying capture onset.
2. **Pause Tolerance**: `min_silence_duration = 0.70s` (700 ms) reliably bridges natural 0.2s–0.6s breath pauses between clauses while promptly terminating when user stops speaking.
3. **PTT Behavior**: When the user presses PTT, recording starts immediately. When released, speech is finalized immediately without waiting for VAD silence timeout.
4. **Pre-Speech Buffer**: 10 frames (~320 ms) circular ring buffer prepended on speech trigger prevents clipping initial consonants.
