import os
import urllib.request
import sys
import json
import onnx

MODELS_DIR = os.path.join(os.path.dirname(__file__), "models")
os.makedirs(MODELS_DIR, exist_ok=True)

DOWNLOADS = [
    # 1. Piper Malayalam Meera (Female)
    {
        "name": "piper_ml_meera",
        "dir": os.path.join(MODELS_DIR, "piper_ml_meera"),
        "files": {
            "model.onnx": "https://huggingface.co/rhasspy/piper-voices/resolve/main/ml/ml_IN/meera/medium/ml_IN-meera-medium.onnx",
            "model.onnx.json": "https://huggingface.co/rhasspy/piper-voices/resolve/main/ml/ml_IN/meera/medium/ml_IN-meera-medium.onnx.json"
        },
        "is_piper": True,
        "lang": "Malayalam",
        "voice": "ml"
    },
    # 2. Piper Malayalam Arjun (Male)
    {
        "name": "piper_ml_arjun",
        "dir": os.path.join(MODELS_DIR, "piper_ml_arjun"),
        "files": {
            "model.onnx": "https://huggingface.co/rhasspy/piper-voices/resolve/main/ml/ml_IN/arjun/medium/ml_IN-arjun-medium.onnx",
            "model.onnx.json": "https://huggingface.co/rhasspy/piper-voices/resolve/main/ml/ml_IN/arjun/medium/ml_IN-arjun-medium.onnx.json"
        },
        "is_piper": True,
        "lang": "Malayalam",
        "voice": "ml"
    },
    # 3. Piper Tamil Roja (Female)
    {
        "name": "piper_ta_roja",
        "dir": os.path.join(MODELS_DIR, "piper_ta_roja"),
        "files": {
            "model.onnx": "https://huggingface.co/ezhilkumaran/piper-tamil/resolve/main/ta_IN-roja-medium.onnx",
            "model.onnx.json": "https://huggingface.co/ezhilkumaran/piper-tamil/resolve/main/ta_IN-roja-medium.onnx.json"
        },
        "is_piper": True,
        "lang": "Tamil",
        "voice": "ta"
    },
    # 4. Piper Tamil Rasa (Male)
    {
        "name": "piper_ta_rasa_male",
        "dir": os.path.join(MODELS_DIR, "piper_ta_rasa_male"),
        "files": {
            "model.onnx": "https://huggingface.co/tinisoft/piper-ta_IN-rasa_male-medium/resolve/main/ta_IN-rasa_male-medium.onnx",
            "model.onnx.json": "https://huggingface.co/tinisoft/piper-ta_IN-rasa_male-medium/resolve/main/ta_IN-rasa_male-medium.onnx.json"
        },
        "is_piper": True,
        "lang": "Tamil",
        "voice": "ta"
    },
    # 5. Meta MMS Malayalam
    {
        "name": "mms_mal",
        "dir": os.path.join(MODELS_DIR, "mms_mal"),
        "files": {
            "model.onnx": "https://huggingface.co/willwade/mms-tts-multilingual-models-onnx/resolve/main/mal/model.onnx",
            "tokens.txt": "https://huggingface.co/willwade/mms-tts-multilingual-models-onnx/resolve/main/mal/tokens.txt"
        },
        "is_piper": False
    },
    # 6. Meta MMS Tamil
    {
        "name": "mms_tam",
        "dir": os.path.join(MODELS_DIR, "mms_tam"),
        "files": {
            "model.onnx": "https://huggingface.co/willwade/mms-tts-multilingual-models-onnx/resolve/main/tam/model.onnx",
            "tokens.txt": "https://huggingface.co/willwade/mms-tts-multilingual-models-onnx/resolve/main/tam/tokens.txt"
        },
        "is_piper": False
    }
]

def download_file(url, dest_path):
    if os.path.exists(dest_path) and os.path.getsize(dest_path) > 1000:
        print(f"  [Already exists] {dest_path} ({os.path.getsize(dest_path) / (1024*1024):.2f} MB)")
        return
    print(f"  Downloading: {url} -> {dest_path}...")
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(req) as resp, open(dest_path, "wb") as f:
        total = int(resp.headers.get("Content-Length", 0))
        downloaded = 0
        while True:
            chunk = resp.read(1024 * 1024)
            if not chunk:
                break
            downloaded += len(chunk)
            f.write(chunk)
            if total > 0:
                percent = downloaded / total * 100
                sys.stdout.write(f"\r    Progress: {downloaded / (1024*1024):.1f}MB / {total / (1024*1024):.1f}MB ({percent:.1f}%)")
                sys.stdout.flush()
    print(f"\n  Done! {dest_path} ({os.path.getsize(dest_path) / (1024*1024):.2f} MB)")

def prepare_piper(model_dir, lang_name="Malayalam", voice="ml"):
    onnx_path = os.path.join(model_dir, "model.onnx")
    json_path = os.path.join(model_dir, "model.onnx.json")
    tokens_path = os.path.join(model_dir, "tokens.txt")

    if not os.path.exists(json_path):
        return

    with open(json_path, "r", encoding="utf-8") as f:
        cfg = json.load(f)

    # 1. Generate clean LF tokens.txt from phoneme_id_map
    phoneme_id_map = cfg.get("phoneme_id_map", {})
    with open(tokens_path, "w", encoding="utf-8", newline="\n") as f:
        for s, ids in phoneme_id_map.items():
            if s == "\n":
                continue
            token_id = ids[0] if isinstance(ids, list) else ids
            f.write(f"{s} {token_id}\n")

    # 2. Add metadata properties to model.onnx
    model = onnx.load(onnx_path)
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
    print(f"  [OK] Prepared Piper model: {os.path.basename(model_dir)} (sample_rate={props['sample_rate']}, tokens={len(phoneme_id_map)})")

def clean_tokens_lf(tokens_path):
    if os.path.exists(tokens_path):
        with open(tokens_path, "rb") as f:
            c = f.read().replace(b"\r\n", b"\n")
        with open(tokens_path, "wb") as f:
            f.write(c)

def main():
    if sys.platform == "win32":
        sys.stdout.reconfigure(encoding="utf-8")
    print("=== Sourcing and Preparing Malayalam and Tamil TTS Models ===")
    for m in DOWNLOADS:
        print(f"\nModel: {m['name']} in {m['dir']}")
        os.makedirs(m["dir"], exist_ok=True)
        for fname, url in m["files"].items():
            dest = os.path.join(m["dir"], fname)
            download_file(url, dest)
        if m.get("is_piper", False):
            prepare_piper(m["dir"], m.get("lang", "Malayalam"), m.get("voice", "ml"))
        else:
            clean_tokens_lf(os.path.join(m["dir"], "tokens.txt"))
    print("\n[OK] All Malayalam and Tamil models ready for validation.")

if __name__ == "__main__":
    main()
