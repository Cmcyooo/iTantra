#!/usr/bin/env python3
import subprocess
import sys
import time

ADB = r"C:\Users\CMCY\AppData\Local\Android\Sdk\platform-tools\adb.exe"
DEVICE = "954bd222"

METHODS = [
    "test04_gujarati_clean",
    "test05_telugu_clean",
    "test06_tamil_clean",
    "test07_bengali_clean",
    "test08_kannada_clean",
    "test09_malayalam_clean",
    "test10_odia_clean",
    "test11_moderateNoise_hindi",
    "test12_moderateNoise_english",
    "test13_moderateNoise_marathi",
    "test14_moderateNoise_telugu",
    "test15_benchmarkMarathiModelComparison",
    "test16_benchmarkOdiaModelComparison"
]

def main():
    for method in METHODS:
        print(f"\n=======================================================")
        print(f"RUNNING STT BENCHMARK METHOD: {method}")
        print(f"=======================================================")
        cmd = [
            ADB, "-s", DEVICE, "shell", "am", "instrument",
            "-w", "-r",
            "-e", "class", f"com.itantra.app.benchmark.SttComprehensiveBenchmarkTest#{method}",
            "com.itantra.app.test/androidx.test.runner.AndroidJUnitRunner"
        ]
        start_t = time.time()
        proc = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, encoding="utf-8", errors="replace")
        elapsed = time.time() - start_t
        print(proc.stdout)
        print(f"Finished {method} in {elapsed:.1f}s (Exit code: {proc.returncode})")
        time.sleep(2)

if __name__ == "__main__":
    main()
