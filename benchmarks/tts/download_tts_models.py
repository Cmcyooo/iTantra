import os
import urllib.request
import sys

MODELS_DIR = os.path.join(os.path.dirname(__file__), "models")
os.makedirs(MODELS_DIR, exist_ok=True)

DOWNLOADS = [
    # 1. Piper Hindi Priyamvada
    {
        "name": "piper_hindi_priyamvada",
        "dir": os.path.join(MODELS_DIR, "piper_hi_priyamvada"),
        "files": {
            "model.onnx": "https://huggingface.co/rhasspy/piper-voices/resolve/main/hi/hi_IN/priyamvada/medium/hi_IN-priyamvada-medium.onnx",
            "model.onnx.json": "https://huggingface.co/rhasspy/piper-voices/resolve/main/hi/hi_IN/priyamvada/medium/hi_IN-priyamvada-medium.onnx.json"
        }
    },
    # 2. Piper Hindi Rohan
    {
        "name": "piper_hindi_rohan",
        "dir": os.path.join(MODELS_DIR, "piper_hi_rohan"),
        "files": {
            "model.onnx": "https://huggingface.co/rhasspy/piper-voices/resolve/main/hi/hi_IN/rohan/medium/hi_IN-rohan-medium.onnx",
            "model.onnx.json": "https://huggingface.co/rhasspy/piper-voices/resolve/main/hi/hi_IN/rohan/medium/hi_IN-rohan-medium.onnx.json"
        }
    },
    # 3. Meta MMS Hindi (willwade onnx)
    {
        "name": "mms_hindi",
        "dir": os.path.join(MODELS_DIR, "mms_hin"),
        "files": {
            "model.onnx": "https://huggingface.co/willwade/mms-tts-multilingual-models-onnx/resolve/main/hin/model.onnx",
            "tokens.txt": "https://huggingface.co/willwade/mms-tts-multilingual-models-onnx/resolve/main/hin/tokens.txt"
        }
    },
    # 4. Meta MMS Gujarati (willwade onnx)
    {
        "name": "mms_gujarati",
        "dir": os.path.join(MODELS_DIR, "mms_guj"),
        "files": {
            "model.onnx": "https://huggingface.co/willwade/mms-tts-multilingual-models-onnx/resolve/main/guj/model.onnx",
            "tokens.txt": "https://huggingface.co/willwade/mms-tts-multilingual-models-onnx/resolve/main/guj/tokens.txt"
        }
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
            chunk = resp.read(1024 * 1024) # 1MB chunk
            if not chunk:
                break
            downloaded += len(chunk)
            f.write(chunk)
            if total > 0:
                percent = downloaded / total * 100
                sys.stdout.write(f"\r    Progress: {downloaded / (1024*1024):.1f}MB / {total / (1024*1024):.1f}MB ({percent:.1f}%)")
                sys.stdout.flush()
    print(f"\n  Done! {dest_path} ({os.path.getsize(dest_path) / (1024*1024):.2f} MB)")

def main():
    print("=== Downloading Hindi and Gujarati Candidate TTS Models ===")
    for m in DOWNLOADS:
        print(f"\nModel: {m['name']} in {m['dir']}")
        os.makedirs(m["dir"], exist_ok=True)
        for fname, url in m["files"].items():
            dest = os.path.join(m["dir"], fname)
            download_file(url, dest)
    print("\n✓ All models downloaded successfully.")

if __name__ == "__main__":
    main()
