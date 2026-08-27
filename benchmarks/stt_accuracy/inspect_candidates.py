import sys
from huggingface_hub import HfApi

if sys.platform == "win32":
    sys.stdout.reconfigure(encoding="utf-8")

api = HfApi()

candidates = [
    # Marathi
    "addy88/wav2vec2-marathi-stt",
    "ravirajoshi/wav2vec2-large-xls-r-300m-marathi",
    "sumedh/wav2vec2-large-xlsr-marathi",
    "shivam/xls-r-300m-marathi",
    "Harveenchadha/vakyansh-wav2vec2-marathi-mrm-100",
    # Odia
    "Harveenchadha/odia_large_wav2vec2",
    "anuragshas/wav2vec2-large-xlsr-53-odia",
    "infinitejoy/wav2vec2-large-xls-r-300m-odia",
    "addy88/wav2vec-odia-stt",
    "Harveenchadha/vakyansh-wav2vec2-odia-orm-100",
]

print("=" * 80)
print("INSPECTING CANDIDATE MODEL METADATA")
print("=" * 80)

for cid in candidates:
    try:
        info = api.model_info(cid)
        tags = info.tags
        card = info.card_data or {}
        license_str = getattr(card, "license", None) or [t for t in tags if "license:" in t]
        downloads = info.downloads
        print(f"\nModel: {cid}")
        print(f"  Downloads: {downloads}")
        print(f"  License: {license_str}")
        print(f"  Tags: {[t for t in tags if t in ('pytorch', 'jax', 'onnx', 'wav2vec2', 'whisper', 'ctc')]}")
    except Exception as e:
        print(f"  Error loading {cid}: {e}")
