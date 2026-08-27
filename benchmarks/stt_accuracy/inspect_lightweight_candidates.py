import sys
from huggingface_hub import HfApi

if sys.platform == "win32":
    sys.stdout.reconfigure(encoding="utf-8")

api = HfApi()

models = [
    # Bengali
    "bangla-speech-processing/BanglaASR",
    "ai4bharat/indicwav2vec_v1_bengali",
    "addy88/wav2vec2-bengali-stt",
    "Harveenchadha/vakyansh-wav2vec2-bengali-bnm-200",
    # Malayalam
    "addy88/wav2vec2-malayalam-stt",
    "Bluecast/wav2vec2-Malayalam",
    "mohamed-illiyas/wav2vec-malayalam-new",
    "Harveenchadha/vakyansh-wav2vec2-malayalam-mlm-8",
    # Marathi
    "addy88/wav2vec2-marathi-stt",
    "StephennFernandes/XLS-R-marathi",
    "Harveenchadha/vakyansh-wav2vec2-marathi-mrm-100",
    # Odia
    "addy88/wav2vec-odia-stt",
    "ai4bharat/indicwav2vec-odia",
    "Harveenchadha/vakyansh-wav2vec2-odia-orm-100",
]

print("=" * 80)
print("INSPECTING LIGHTWEIGHT CANDIDATES (50M - 150M)")
print("=" * 80)

for mid in models:
    try:
        info = api.model_info(mid)
        # Check files to deduce param size
        siblings = [s.rfilename for s in info.siblings]
        weight_files = [s for s in siblings if s.endswith(".bin") or s.endswith(".safetensors")]
        print(f"\nModel: {mid}")
        print(f"  Downloads: {info.downloads} | Likes: {info.likes}")
        print(f"  Files: {weight_files}")
    except Exception as e:
        print(f"\nModel: {mid} -> Error: {e}")
