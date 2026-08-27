import sys
from huggingface_hub import HfApi

if sys.platform == "win32":
    sys.stdout.reconfigure(encoding="utf-8")

api = HfApi()

languages = {
    "Bengali": ["bengali", "bangla"],
    "Malayalam": ["malayalam"],
    "Marathi": ["marathi"],
    "Odia": ["odia", "oriya"]
}

for lang, queries in languages.items():
    print(f"\n=== {lang} Lightweight ASR Candidates ===")
    found = set()
    for q in queries:
        models = api.list_models(filter="automatic-speech-recognition", search=q, limit=40)
        for m in models:
            if m.id not in found:
                found.add(m.id)
                name = m.id.lower()
                # filter out explicitly large 300M, 1B, 2B models
                if any(x in name for x in ["300m", "large", "1b", "2b", "medium"]):
                    continue
                dls = getattr(m, "downloads", 0)
                likes = getattr(m, "likes", 0)
                print(f"  - {m.id} | dls: {dls} | likes: {likes}")
