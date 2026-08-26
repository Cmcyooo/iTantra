#!/usr/bin/env python3
"""
Phase 9: Automated Android Device Compatibility Profiler
Queries connected Android devices via ADB, extracts hardware specs,
monitors process PSS / CPU usage, and appends structured entries to results.csv.
"""

import sys
import os
import shutil
import subprocess
import argparse
import csv
import time
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
RESULTS_CSV_PATH = SCRIPT_DIR / "results.csv"


def find_adb():
    if shutil.which("adb"):
        return "adb"
    local_app_data = os.environ.get("LOCALAPPDATA", "")
    if local_app_data:
        candidate = Path(local_app_data) / "Android" / "Sdk" / "platform-tools" / "adb.exe"
        if candidate.exists():
            return str(candidate)
    return "adb"


ADB_BIN = find_adb()


def run_adb(cmd_list, device_id=None):
    base = [ADB_BIN]
    if device_id:
        base.extend(["-s", device_id])
    base.extend(cmd_list)
    try:
        res = subprocess.run(base, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, check=True, encoding="utf-8", errors="replace")
        return res.stdout.strip()
    except subprocess.CalledProcessError as e:
        print(f"ADB Error: {e.stderr}", file=sys.stderr)
        return ""


def get_device_info(device_id=None):
    model = run_adb(["shell", "getprop", "ro.product.model"], device_id)
    manufacturer = run_adb(["shell", "getprop", "ro.product.manufacturer"], device_id)
    android_ver = run_adb(["shell", "getprop", "ro.build.version.release"], device_id)
    sdk_ver = run_adb(["shell", "getprop", "ro.build.version.sdk"], device_id)
    abi = run_adb(["shell", "getprop", "ro.product.cpu.abi"], device_id)
    soc = run_adb(["shell", "getprop", "ro.soc.model"], device_id)
    if not soc:
        soc = run_adb(["shell", "getprop", "ro.board.platform"], device_id)

    mem_raw = run_adb(["shell", "cat", "/proc/meminfo"], device_id)
    mem_total_kb = 0
    for line in mem_raw.splitlines():
        if line.startswith("MemTotal:"):
            parts = line.split()
            mem_total_kb = int(parts[1])
            break

    mem_total_gb = mem_total_kb / (1024 * 1024)

    return {
        "model": f"{manufacturer} {model}".strip(),
        "android_ver": android_ver,
        "sdk_ver": sdk_ver,
        "abi": abi,
        "soc": soc,
        "ram_total_kb": mem_total_kb,
        "ram_total_gb": round(mem_total_gb, 2),
    }


def get_process_memory(pkg_name="com.itantra.app", device_id=None):
    raw = run_adb(["shell", "dumpsys", "meminfo", pkg_name], device_id)
    pss_total_kb = 0
    for line in raw.splitlines():
        if "TOTAL PSS:" in line:
            parts = line.split()
            try:
                pss_total_kb = int(parts[2])
            except (IndexError, ValueError):
                pass
            break
        elif "TOTAL" in line and "TOTAL SWAP" not in line:
            parts = line.split()
            try:
                pss_total_kb = int(parts[1])
            except (IndexError, ValueError):
                pass
            break

    return pss_total_kb / 1024.0  # MB


def main():
    parser = argparse.ArgumentParser(description="iTantra Android Device Compatibility Profiler")
    parser.add_argument("--device-id", "-s", type=str, default=None, help="ADB Device Serial")
    parser.add_argument("--pkg", type=str, default="com.itantra.app", help="Target Package Name")
    args = parser.parse_args()

    print("=" * 80)
    print("  iTantra Phase 9: Multi-Device Hardware Profiler")
    print("=" * 80)

    devices = run_adb(["devices"]).splitlines()
    active_devices = [d.split()[0] for d in devices[1:] if len(d.split()) > 1 and d.split()[1] == "device"]

    if not active_devices:
        print("Error: No active Android devices detected via ADB.")
        sys.exit(1)

    target_device = args.device_id if args.device_id else active_devices[0]
    print(f"Target ADB Device: {target_device}")

    info = get_device_info(target_device)
    print("\n--- System Specifications ---")
    print(f"Device Model:       {info['model']}")
    print(f"Android Version:    Android {info['android_ver']} (API {info['sdk_ver']})")
    print(f"CPU Architecture:   {info['abi']} (SoC: {info['soc']})")
    print(f"Total Physical RAM: {info['ram_total_gb']} GB ({info['ram_total_kb']:,} kB)")

    pss = get_process_memory(args.pkg, target_device)
    print(f"Current App PSS:    {pss:.2f} MB")
    print("=" * 80)


if __name__ == "__main__":
    main()
