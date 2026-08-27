#!/usr/bin/env python3
"""
Step 1 & Step 2 — Establish Real Baseline STT Accuracy & Audio Pipeline Audit across all 10 languages.
Generates audio from standardized tactical test set using native TTS voices, audits audio characteristics
(sample rate, RMS, peak amplitude, clipping rate, DC offset), and benchmarks the baseline STT pipeline.
"""

import os
import sys
import json
import time
import csv
import numpy as np
import soundfile as sf
from pathlib import Path
import jiwer
import onnxruntime as ort

if sys.platform == "win32":
    sys.stdout.reconfigure(encoding="utf-8")

ROOT_DIR = Path(__file__).resolve().parent.parent.parent
DATASET_PATH = ROOT_DIR / "benchmarks" / "stt_accuracy" / "test_dataset_all_10_languages.json"
AUDIO_DIR = ROOT_DIR / "benchmarks" / "stt_accuracy" / "audio"
AUDIO_DIR.mkdir(parents=True, exist_ok=True)

MODELS_DIR = ROOT_DIR / "benchmarks" / "indic_stt" / "models"
TTS_MODELS_DIR = ROOT_DIR / "benchmarks" / "tts" / "models"
APP_ASSETS_DIR = ROOT_DIR / "app" / "src" / "main" / "assets" / "indic_stt"

CSV_PATH = ROOT_DIR / "benchmarks" / "stt_accuracy" / "baseline_results.csv"
MD_PATH = ROOT_DIR / "benchmarks" / "stt_accuracy" / "baseline_report.md"
AUDIO_REPORT_MD = ROOT_DIR / "benchmarks" / "stt_accuracy" / "audio_pipeline_report.md"

LANG_CONFIGS = [
    ("hi", "Hindi", "vakyansh_hindi_base.int8.onnx", "hindi_vocab.json", "piper_hi_priyamvada", True),
    ("mr", "Marathi", "vakyansh_marathi_base.int8.onnx", "marathi_vocab.json", "piper_mr_google", True),
    ("bn", "Bengali", "vakyansh_bengali_base.int8.onnx", "bengali_vocab.json", "piper_bn_google", True),
    ("te", "Telugu", "vakyansh_telugu_base.int8.onnx", "telugu_vocab.json", "piper_te_maya", True),
    ("ml", "Malayalam", "vakyansh_malayalam_base.int8.onnx", "malayalam_vocab.json", "piper_ml_meera", True),
    ("ta", "Tamil", "vakyansh_tamil_base.int8.onnx", "tamil_vocab.json", "piper_ta_rasa_female", True),
    ("gu", "Gujarati", "vakyansh_gujarati_base.int8.onnx", "gujarati_vocab.json", "mms_guj", False),
    ("kn", "Kannada", "vakyansh_kannada_base.int8.onnx", "kannada_vocab.json", "mms_kan", False),
    ("or", "Odia", "vakyansh_odia_base.int8.onnx", "odia_vocab.json", "mms_ory", False),
    ("en", "English", "whisper", "tokens.txt", "tts-en-amy", True),
]

def analyze_audio(samples: np.ndarray, sr: int):
    rms = float(np.sqrt(np.mean(samples ** 2)))
    peak = float(np.max(np.abs(samples)))
    clipping_rate = float(np.mean(np.abs(samples) >= 0.99)) * 100.0
    dc_offset = float(np.mean(samples))
    duration_sec = len(samples) / sr
    return {
        "duration_sec": duration_sec,
        "sample_rate": sr,
        "rms": rms,
        "peak": peak,
        "clipping_rate_pct": clipping_rate,
        "dc_offset": dc_offset,
        "num_samples": len(samples)
    }

def main():
    print("=" * 80)
    print("STEP 1: ESTABLISHING MULTILINGUAL STT BASELINE (10 LANGUAGES)")
    print("=" * 80)

    import sherpa_onnx

    with open(DATASET_PATH, "r", encoding="utf-8") as f:
        dataset = json.load(f)

    # Initialize sherpa-onnx OfflineRecognizer for English Whisper
    whisper_dir = ROOT_DIR / "app" / "src" / "main" / "assets" / "whisper-tiny-en"
    whisper_recognizer = sherpa_onnx.OfflineRecognizer.from_whisper(
        encoder=str(whisper_dir / "encoder.onnx"),
        decoder=str(whisper_dir / "decoder.onnx"),
        tokens=str(whisper_dir / "tokens.txt"),
        language="en",
        task="transcribe",
        num_threads=2
    )

    all_csv_rows = []
    summary_report = []
    audio_report_rows = []

    for code, lang_name, model_file, vocab_file, tts_dir, is_piper in LANG_CONFIGS:
        print(f"\nEvaluating Baseline: {lang_name} ({code})...")
        
        # Load STT model if Indic
        session = None
        inv_vocab = {}
        pad_id = 1
        if code != "en":
            stt_model_path = MODELS_DIR / model_file
            vocab_path = APP_ASSETS_DIR / vocab_file
            if not vocab_path.exists():
                vocab_path = MODELS_DIR / vocab_file

            with open(vocab_path, "r", encoding="utf-8") as f:
                vocab = json.load(f)
            inv_vocab = {v: k for k, v in vocab.items()}
            pad_id = vocab.get("<pad>", 1)

            sess_opts = ort.SessionOptions()
            sess_opts.intra_op_num_threads = 2
            session = ort.InferenceSession(str(stt_model_path), sess_opts, providers=["CPUExecutionProvider"])

        # Load TTS model to generate real offline speech samples
        if code == "en":
            tts_path = ROOT_DIR / "app" / "src" / "main" / "assets" / "tts-en-amy"
        else:
            tts_path = TTS_MODELS_DIR / tts_dir
        tts = None
        if is_piper:
            model_onnx = list(tts_path.glob("*.onnx"))[0]
            tokens_txt = list(tts_path.glob("tokens.txt"))[0]
            data_dir = ROOT_DIR / "app" / "src" / "main" / "assets" / "tts-en-amy" / "espeak-ng-data"
            tts_config = sherpa_onnx.OfflineTtsConfig(
                model=sherpa_onnx.OfflineTtsModelConfig(
                    vits=sherpa_onnx.OfflineTtsVitsModelConfig(
                        model=str(model_onnx),
                        tokens=str(tokens_txt),
                        data_dir=str(data_dir)
                    ),
                    num_threads=2
                )
            )
            tts = sherpa_onnx.OfflineTts(tts_config)
        else:
            model_onnx = list(tts_path.glob("*.onnx"))[0]
            tokens_txt = list(tts_path.glob("tokens.txt"))[0]
            tts_config = sherpa_onnx.OfflineTtsConfig(
                model=sherpa_onnx.OfflineTtsModelConfig(
                    vits=sherpa_onnx.OfflineTtsVitsModelConfig(
                        model=str(model_onnx),
                        tokens=str(tokens_txt),
                        data_dir=""
                    ),
                    num_threads=2
                )
            )
            tts = sherpa_onnx.OfflineTts(tts_config)

        lang_samples = dataset[code]["samples"]
        refs = []
        hyps = []
        latencies = []
        durations = []
        rms_list = []
        peak_list = []
        clip_list = []

        for sample in lang_samples:
            ref_text = sample["text"]
            audio_out_path = AUDIO_DIR / f"{sample['id']}.wav"

            # Synthesize audio if not already generated
            if not audio_out_path.exists():
                audio = tts.generate(ref_text, sid=0, speed=1.0)
                # Resample or convert to 16 kHz mono float32
                samples_16k = audio.samples
                sr = audio.sample_rate
                if sr != 16000:
                    import scipy.signal
                    num_out = int(len(samples_16k) * 16000 / sr)
                    samples_16k = scipy.signal.resample(samples_16k, num_out).astype(np.float32)
                    sr = 16000
                sf.write(str(audio_out_path), samples_16k, 16000, subtype="PCM_16")

            # Load audio for STT test
            audio_data, sr = sf.read(str(audio_out_path), dtype="float32")
            if len(audio_data.shape) > 1:
                audio_data = audio_data.mean(axis=1)

            # Audio pipeline metrics
            metrics = analyze_audio(audio_data, sr)
            rms_list.append(metrics["rms"])
            peak_list.append(metrics["peak"])
            clip_list.append(metrics["clipping_rate_pct"])
            durations.append(metrics["duration_sec"])

            # Baseline STT Inference (unnormalized raw float samples, as in original Android code)
            t0 = time.perf_counter()
            hyp_text = ""
            if code == "en":
                stream = whisper_recognizer.create_stream()
                stream.accept_waveform(sr, audio_data)
                whisper_recognizer.decode_stream(stream)
                hyp_text = stream.result.text.strip().lower()
            else:
                inp = np.expand_dims(audio_data, axis=0).astype(np.float32)
                logits = session.run(None, {"input_values": inp})[0]
                pred_ids = np.argmax(logits, axis=-1)[0]

                # Original CTC decoder
                collapsed = []
                prev = -1
                for tid in pred_ids:
                    if tid != prev:
                        collapsed.append(tid)
                        prev = tid

                tokens = [inv_vocab.get(tid, "") for tid in collapsed if tid not in (0, 1, 2, 3)]
                hyp_text = "".join(tokens).replace("|", " ").strip()
                hyp_text = " ".join(hyp_text.split())

            t1 = time.perf_counter()
            infer_ms = (t1 - t0) * 1000.0
            latencies.append(infer_ms)

            refs.append(ref_text)
            hyps.append(hyp_text)

            rtf_sample = (infer_ms / 1000.0) / metrics["duration_sec"]
            all_csv_rows.append({
                "sample_id": sample["id"],
                "language": lang_name,
                "code": code,
                "category": sample["category"],
                "audio_duration_s": f"{metrics['duration_sec']:.2f}",
                "infer_latency_ms": f"{infer_ms:.1f}",
                "rtf": f"{rtf_sample:.3f}",
                "rms": f"{metrics['rms']:.4f}",
                "peak": f"{metrics['peak']:.4f}",
                "clipping_pct": f"{metrics['clipping_rate_pct']:.2f}%",
                "reference": ref_text,
                "hypothesis": hyp_text
            })

        wer = jiwer.wer(refs, hyps) * 100.0
        cer = jiwer.cer(refs, hyps) * 100.0
        avg_dur = float(np.mean(durations))
        avg_lat = float(np.mean(latencies))
        avg_rtf = avg_lat / (avg_dur * 1000.0)
        avg_rms = float(np.mean(rms_list))
        avg_peak = float(np.mean(peak_list))
        avg_clip = float(np.mean(clip_list))

        print(f"  --> {lang_name:<10}: WER={wer:.2f}% | CER={cer:.2f}% | Latency={avg_lat:.1f}ms | RTF={avg_rtf:.3f}")

        summary_report.append({
            "language": lang_name,
            "code": code,
            "wer": wer,
            "cer": cer,
            "avg_latency_ms": avg_lat,
            "avg_rtf": avg_rtf,
            "samples_count": len(refs)
        })

        audio_report_rows.append({
            "language": lang_name,
            "code": code,
            "sample_rate": 16000,
            "avg_duration": f"{avg_dur:.2f}s",
            "avg_rms": f"{avg_rms:.4f}",
            "avg_peak": f"{avg_peak:.4f}",
            "clipping_pct": f"{avg_clip:.2f}%",
            "status": "HEALTHY" if avg_clip < 0.1 and avg_peak < 0.98 else "CHECK_GAIN"
        })

    # Write Baseline CSV
    with open(CSV_PATH, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=list(all_csv_rows[0].keys()))
        writer.writeheader()
        writer.writerows(all_csv_rows)

    # Write Baseline Markdown Report
    with open(MD_PATH, "w", encoding="utf-8") as f:
        f.write("# Step 1 — Multilingual STT Baseline Accuracy Report\n\n")
        f.write("Evaluation on 200 standardized tactical & conversational speech samples across all 10 target languages (unnormalized raw audio baseline).\n\n")
        f.write("| Language | Code | Samples | Baseline WER (%) | Baseline CER (%) | Avg Latency (ms) | Avg RTF | Initial Status |\n")
        f.write("| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |\n")
        for row in summary_report:
            status = "STRONG" if row["wer"] < 15.0 else ("FAIR" if row["wer"] < 35.0 else "POOR")
            f.write(f"| {row['language']} | `{row['code']}` | {row['samples_count']} | **{row['wer']:.2f}%** | **{row['cer']:.2f}%** | {row['avg_latency_ms']:.1f} ms | {row['avg_rtf']:.3f} | {status} |\n")

    # Write Audio Pipeline Report
    with open(AUDIO_REPORT_MD, "w", encoding="utf-8") as f:
        f.write("# Step 2 — Audio Pipeline Validation Report\n\n")
        f.write("Inspection of acoustic properties, dynamic range, clipping rates, and PCM conversions across all language test sets.\n\n")
        f.write("| Language | Code | Sample Rate | Avg Duration | Avg RMS | Avg Peak | Clipping Rate | Pipeline Status |\n")
        f.write("| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |\n")
        for row in audio_report_rows:
            f.write(f"| {row['language']} | `{row['code']}` | {row['sample_rate']} Hz | {row['avg_duration']} | {row['avg_rms']} | {row['avg_peak']} | {row['clipping_pct']} | **{row['status']}** |\n")

    print(f"\nBaseline CSV saved to: {CSV_PATH}")
    print(f"Baseline Report saved to: {MD_PATH}")
    print(f"Audio Report saved to: {AUDIO_REPORT_MD}")

if __name__ == "__main__":
    main()
