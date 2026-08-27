#!/usr/bin/env python3
"""
Step 5: Verify Input/Output Contracts & Tokenizer Alignment for New Odia and Marathi Models.
Fails if: model output dimension != vocabulary size.
"""

import sys
import json
import onnx
import onnxruntime as ort
import numpy as np
from pathlib import Path

if sys.platform == "win32":
    sys.stdout.reconfigure(encoding="utf-8")

ROOT_DIR = Path(__file__).resolve().parent.parent.parent.parent
MODELS_DIR = ROOT_DIR / "benchmarks" / "indic_stt" / "models"

MODELS = [
    {
        "language": "Marathi",
        "code": "mr",
        "model_path": MODELS_DIR / "marathi_xlsr_large.int8.onnx",
        "vocab_path": MODELS_DIR / "marathi_xlsr_vocab.json",
        "expected_pad": "[PAD]"
    },
    {
        "language": "Odia",
        "code": "or",
        "model_path": MODELS_DIR / "odia_large.int8.onnx",
        "vocab_path": MODELS_DIR / "odia_large_vocab.json",
        "expected_pad": "<pad>"
    }
]

def verify_contracts():
    print("=" * 80)
    print("STEP 5: VERIFY INPUT/OUTPUT CONTRACTS & TOKENIZER ALIGNMENT")
    print("=" * 80)

    all_passed = True

    for item in MODELS:
        lang = item["language"]
        code = item["code"]
        mpath = item["model_path"]
        vpath = item["vocab_path"]

        print(f"\n--- Verifying {lang} ({code}) ---")
        print(f"Model Path: {mpath}")
        print(f"Vocab Path: {vpath}")

        if not mpath.exists():
            print(f"[FAIL] Model file does not exist: {mpath}")
            all_passed = False
            continue

        if not vpath.exists():
            print(f"[FAIL] Vocab file does not exist: {vpath}")
            all_passed = False
            continue

        # Check vocabulary
        with open(vpath, "r", encoding="utf-8") as f:
            vocab = json.load(f)
        vocab_size = len(vocab)
        max_idx = max(vocab.values())
        print(f"Vocab items: {vocab_size} | Max token ID: {max_idx}")

        if max_idx != vocab_size - 1:
            print(f"[FAIL] Vocab IDs are not contiguous (0 to {vocab_size - 1})!")
            all_passed = False

        # Check ONNX session
        sess = ort.InferenceSession(str(mpath), providers=["CPUExecutionProvider"])
        inputs = sess.get_inputs()
        outputs = sess.get_outputs()

        print(f"Input count: {len(inputs)}")
        for inp in inputs:
            print(f"  Input Name: '{inp.name}' | Type: {inp.type} | Shape: {inp.shape}")

        print(f"Output count: {len(outputs)}")
        for out in outputs:
            print(f"  Output Name: '{out.name}' | Type: {out.type} | Shape: {out.shape}")

        # Run dummy 16 kHz inference
        dummy_audio = np.random.randn(1, 16000).astype(np.float32)
        res = sess.run(None, {inputs[0].name: dummy_audio})
        logits = res[0]
        output_dim = logits.shape[-1]
        print(f"Inference output shape on 1s audio: {logits.shape} (Time steps: {logits.shape[1]}, Vocab dim: {output_dim})")

        if output_dim != vocab_size:
            print(f"[CRITICAL FAIL] Model output dim ({output_dim}) != Vocab size ({vocab_size})!")
            all_passed = False
        else:
            print(f"[PASS] Model output dimension ({output_dim}) exactly matches vocabulary size ({vocab_size})!")

    if all_passed:
        print("\nALL CONTRACT VERIFICATIONS PASSED SUCCESSFULLY!")
    else:
        print("\nCONTRACT VERIFICATION FAILED!")
        sys.exit(1)

if __name__ == "__main__":
    verify_contracts()
