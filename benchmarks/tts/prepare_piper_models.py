import os
import json
import onnx

BASE_DIR = os.path.dirname(__file__)
MODELS_DIR = os.path.join(BASE_DIR, "models")

def prepare_piper(model_name, lang_name="Hindi", voice="hi"):
    model_dir = os.path.join(MODELS_DIR, model_name)
    onnx_path = os.path.join(model_dir, "model.onnx")
    json_path = os.path.join(model_dir, "model.onnx.json")
    tokens_path = os.path.join(model_dir, "tokens.txt")

    if not os.path.exists(json_path):
        print(f"Missing {json_path}")
        return

    with open(json_path, "r", encoding="utf-8") as f:
        cfg = json.load(f)

    # 1. Generate tokens.txt from phoneme_id_map
    phoneme_id_map = cfg.get("phoneme_id_map", {})
    with open(tokens_path, "w", encoding="utf-8", newline="\n") as f:
        for s, ids in phoneme_id_map.items():
            if s == "\n":
                continue
            token_id = ids[0] if isinstance(ids, list) else ids
            f.write(f"{s} {token_id}\n")

    # 2. Add metadata properties to model.onnx
    model = onnx.load(onnx_path)
    keys = {"model_type", "comment", "language", "voice", "has_espeak", "n_speakers", "sample_rate"}
    del model.metadata_props[:]

    props = {
        "model_type": "vits",
        "comment": "piper",
        "language": lang_name,
        "voice": cfg.get("espeak", {}).get("voice", voice),
        "has_espeak": "1",
        "n_speakers": str(cfg.get("num_speakers", 1)),
        "sample_rate": str(cfg.get("audio", {}).get("sample_rate", 22050))
    }
    for k, v in props.items():
        p = model.metadata_props.add()
        p.key = k
        p.value = v

    onnx.save(model, onnx_path)
    print(f"[OK] Prepared {model_name}: sample_rate={props['sample_rate']}, voice={props['voice']}, tokens={len(phoneme_id_map)}")

if __name__ == "__main__":
    prepare_piper("piper_hi_priyamvada", "Hindi", "hi")
    prepare_piper("piper_hi_rohan", "Hindi", "hi")
