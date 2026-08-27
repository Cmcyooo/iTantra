import json
import sys
import numpy as np
import onnxruntime as ort
import soundfile as sf
from pathlib import Path

if sys.platform == "win32": sys.stdout.reconfigure(encoding="utf-8")

ROOT_DIR = Path("d:/iTantra")
mr_model = ROOT_DIR / "benchmarks/indic_stt/models/vakyansh_marathi_base.int8.onnx"
mr_vocab_file = ROOT_DIR / "app/src/main/assets/indic_stt/marathi_vocab.json"
audio_file = ROOT_DIR / "benchmarks/stt_accuracy/audio/mr_02_short.wav"

with open(mr_vocab_file, "r", encoding="utf-8") as f:
    vocab = json.load(f)
inv_vocab = {v: k for k, v in vocab.items()}

session = ort.InferenceSession(str(mr_model), providers=["CPUExecutionProvider"])
audio, sr = sf.read(str(audio_file), dtype="float32")

inp = np.expand_dims(audio, axis=0).astype(np.float32)
logits = session.run(None, {"input_values": inp})[0][0] # shape: (T, vocab_size)

print(f"Total frames: {len(logits)}")
for t in range(len(logits)):
    top_id = np.argmax(logits[t])
    top_tok = inv_vocab.get(top_id, "?")
    if top_id != 1: # not pad
        print(f"Frame {t:3d}: ID={top_id:2d} ({top_tok}) logit={logits[t][top_id]:.2f}")
