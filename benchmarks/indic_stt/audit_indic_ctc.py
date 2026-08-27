import os
import sys
import json
import onnx
import onnxruntime as ort

if sys.platform == "win32":
    sys.stdout.reconfigure(encoding="utf-8")

BASE_DIR = os.path.dirname(__file__)
MODELS_DIR = os.path.join(BASE_DIR, "models")

LANGS = [
    ("Hindi", "vakyansh_hindi_base.int8.onnx", "hindi_vocab.json"),
    ("Gujarati", "vakyansh_gujarati_base.int8.onnx", "gujarati_vocab.json"),
    ("Marathi", "vakyansh_marathi_base.int8.onnx", "marathi_vocab.json"),
    ("Kannada", "vakyansh_kannada_base.int8.onnx", "kannada_vocab.json"),
    ("Malayalam", "vakyansh_malayalam_base.int8.onnx", "malayalam_vocab.json"),
    ("Tamil", "vakyansh_tamil_base.int8.onnx", "tamil_vocab.json"),
    ("Telugu", "vakyansh_telugu_base.int8.onnx", "telugu_vocab.json"),
    ("Odia", "vakyansh_odia_base.int8.onnx", "odia_vocab.json"),
    ("Bengali", "vakyansh_bengali_base.int8.onnx", "bengali_vocab.json"),
]

print("=" * 80)
print("AUDITING INDIC WAV2VEC2 ONNX TENSOR SIGNATURES & VOCABULARIES")
print("=" * 80)

for lang, model_name, vocab_name in LANGS:
    model_path = os.path.join(MODELS_DIR, model_name)
    vocab_path = os.path.join(MODELS_DIR, vocab_name)

    print(f"\n--- {lang} ---")
    if not os.path.exists(model_path):
        print(f"  [ERROR] Model not found: {model_path}")
        continue
    if not os.path.exists(vocab_path):
        print(f"  [ERROR] Vocab not found: {vocab_path}")
        continue

    # Load vocab
    with open(vocab_path, "r", encoding="utf-8") as f:
        vocab = json.load(f)

    vocab_size = len(vocab)
    max_id = max(vocab.values())
    min_id = min(vocab.values())
    pad_id = vocab.get("<pad>", -1)
    unk_id = vocab.get("<unk>", -1)
    bos_id = vocab.get("<s>", -1)
    eos_id = vocab.get("</s>", -1)
    pipe_id = vocab.get("|", -1)

    print(f"  Vocab: {vocab_name} | Entries: {vocab_size} | Min ID: {min_id} | Max ID: {max_id}")
    print(f"  Special Tokens: <pad>={pad_id}, <unk>={unk_id}, <s>={bos_id}, </s>={eos_id}, '|'={pipe_id}")

    # Inspect ONNX
    sess_opts = ort.SessionOptions()
    sess_opts.intra_op_num_threads = 1
    session = ort.InferenceSession(model_path, sess_opts, providers=["CPUExecutionProvider"])
    
    inputs = session.get_inputs()
    outputs = session.get_outputs()

    inp_info = [(inp.name, inp.type, inp.shape) for inp in inputs]
    out_info = [(out.name, out.type, out.shape) for out in outputs]

    print(f"  Inputs: {inp_info}")
    print(f"  Outputs: {out_info}")

    out_vocab_dim = out_info[0][2][-1]
    print(f"  Model output vocab dim: {out_vocab_dim}")
    
    if out_vocab_dim != vocab_size and out_vocab_dim != (max_id + 1):
        print(f"  🚨 MISMATCH DETECTED: model output dim ({out_vocab_dim}) != vocab size ({vocab_size}) or max_id+1 ({max_id + 1})")
    else:
        print(f"  ✓ Model output dim matches vocabulary max ID ({max_id + 1})")
