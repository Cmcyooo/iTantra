import sys
from huggingface_hub import HfApi

if sys.platform == "win32":
    sys.stdout.reconfigure(encoding="utf-8")

api = HfApi()

print("=== MARATHI ASR MODELS ON HUGGING FACE ===")
mr_models = api.list_models(filter="automatic-speech-recognition", search="marathi", limit=25)
for m in mr_models:
    print(f"- {m.id}")

print("\n=== ODIA / ORIYA ASR MODELS ON HUGGING FACE ===")
or_models = api.list_models(filter="automatic-speech-recognition", search="odia", limit=25)
for m in or_models:
    print(f"- {m.id}")

or_models2 = api.list_models(filter="automatic-speech-recognition", search="oriya", limit=25)
for m in or_models2:
    print(f"- {m.id}")
