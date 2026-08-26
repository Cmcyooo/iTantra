#!/usr/bin/env python3
"""
Downloads and prepares 12 audio samples per language for the 10 target languages from Google FLEURS.
Saves audio as 16kHz Mono PCM16 WAV files and writes a manifest.json per language.
"""

import os
import sys
import io
import time
import json
from pathlib import Path

# Ensure UTF-8 standard IO
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(encoding="utf-8")

import soundfile as sf
from datasets import load_dataset, Audio

SCRIPT_DIR = Path(__file__).resolve().parent
DATA_DIR = SCRIPT_DIR / "data"

LANGUAGES = [
    ("English", "en_us", "en", "Latin"),
    ("Hindi", "hi_in", "hi", "Devanagari"),
    ("Gujarati", "gu_in", "gu", "Gujarati"),
    ("Marathi", "mr_in", "mr", "Devanagari"),
    ("Kannada", "kn_in", "kn", "Kannada"),
    ("Malayalam", "ml_in", "ml", "Malayalam"),
    ("Tamil", "ta_in", "ta", "Tamil"),
    ("Telugu", "te_in", "te", "Telugu"),
    ("Odia", "or_in", "", "Odia"),
    ("Bengali", "bn_in", "bn", "Bengali"),
]

SAMPLES_PER_LANGUAGE = 12
SAMPLE_RATE = 16000


def download_all():
    DATA_DIR.mkdir(parents=True, exist_ok=True)

    for lang_name, config, token, script in LANGUAGES:
        lang_dir = DATA_DIR / config
        lang_dir.mkdir(parents=True, exist_ok=True)
        manifest_path = lang_dir / "manifest.json"

        # Check if already complete
        if manifest_path.exists():
            try:
                with open(manifest_path, "r", encoding="utf-8") as f:
                    records = json.load(f)
                if len(records) >= SAMPLES_PER_LANGUAGE:
                    print(f"[Ready] {lang_name} ({config}) has {len(records)} samples cached.")
                    continue
            except Exception:
                pass

        print(f"[Fetching] {lang_name} ({config})...")
        success = False
        for attempt in range(1, 4):
            try:
                ds = load_dataset("google/fleurs", config, split="test", streaming=True)
                ds = ds.cast_column("audio", Audio(decode=False))

                records = []
                count = 0

                for item in ds:
                    if count >= SAMPLES_PER_LANGUAGE:
                        break

                    audio_entry = item.get("audio", {})
                    audio_bytes = audio_entry.get("bytes")
                    if not audio_bytes:
                        continue

                    raw_audio, orig_sr = sf.read(io.BytesIO(audio_bytes), dtype="float32")
                    ref_text = item.get("transcription", item.get("raw_transcription", "")).strip()

                    # Convert multi-channel to mono
                    if len(raw_audio.shape) > 1:
                        raw_audio = raw_audio.mean(axis=1)

                    wav_filename = f"{count:03d}.wav"
                    wav_path = lang_dir / wav_filename
                    sf.write(str(wav_path), raw_audio, SAMPLE_RATE, subtype="PCM_16")

                    duration = len(raw_audio) / SAMPLE_RATE

                    records.append({
                        "id": count,
                        "language": lang_name,
                        "fleurs_config": config,
                        "whisper_code": token,
                        "script": script,
                        "audio_path": str(wav_path.relative_to(SCRIPT_DIR)),
                        "duration": duration,
                        "reference": ref_text,
                    })
                    count += 1

                if count >= SAMPLES_PER_LANGUAGE:
                    with open(manifest_path, "w", encoding="utf-8") as f:
                        json.dump(records, f, ensure_ascii=False, indent=2)
                    print(f"[Saved] {lang_name} ({config}) -> {len(records)} samples.")
                    success = True
                    break
                else:
                    print(f"[Warning] Only got {count} samples for {lang_name}. Retrying...")

            except Exception as e:
                print(f"[Error] Attempt {attempt} failed for {lang_name}: {e}")
                time.sleep(3)

        if not success:
            print(f"[Failed] Could not complete download for {lang_name}.")


if __name__ == "__main__":
    download_all()
