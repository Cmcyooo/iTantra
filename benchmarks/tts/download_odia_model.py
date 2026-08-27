import os
import urllib.request
import sys

MODELS_DIR = os.path.join(os.path.dirname(__file__), "models", "mms_ory")
os.makedirs(MODELS_DIR, exist_ok=True)

FILES = {
    "model.onnx": "https://huggingface.co/willwade/mms-tts-multilingual-models-onnx/resolve/main/ory/model.onnx",
    "tokens.txt": "https://huggingface.co/willwade/mms-tts-multilingual-models-onnx/resolve/main/ory/tokens.txt"
}

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

def clean_tokens_lf(tokens_path):
    if os.path.exists(tokens_path):
        with open(tokens_path, "rb") as f:
            c = f.read().replace(b"\r\n", b"\n")
        with open(tokens_path, "wb") as f:
            f.write(c)

def main():
    if sys.platform == "win32":
        sys.stdout.reconfigure(encoding="utf-8")
    print("=== Downloading Meta MMS Odia TTS Model ===")
    for fname, url in FILES.items():
        dest = os.path.join(MODELS_DIR, fname)
        download_file(url, dest)
    clean_tokens_lf(os.path.join(MODELS_DIR, "tokens.txt"))
    print("\n[OK] Meta MMS Odia model prepared successfully.")

if __name__ == "__main__":
    main()
