#!/usr/bin/env python3
"""FF Remote Lab: auto-open scrcpy on previously paired and explicitly allowed Android devices."""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import time

CONFIG = Path.home() / ".config" / "ffremote"
ALLOWLIST = CONFIG / "approved-devices.json"


def load():
    try:
        data = json.loads(ALLOWLIST.read_text(encoding="utf-8"))
        return [x for x in data if isinstance(x, str)]
    except (FileNotFoundError, json.JSONDecodeError, TypeError):
        return []


def save(items):
    CONFIG.mkdir(parents=True, exist_ok=True)
    ALLOWLIST.write_text(json.dumps(sorted(set(items)), indent=2) + "\n", encoding="utf-8")


def allowed(serial, items):
    for item in items:
        if serial == item or serial.startswith(item + ":"):
            return True
    return False


def connected():
    result = subprocess.run(["adb", "devices"], capture_output=True, text=True, check=False, timeout=10)
    if result.returncode != 0:
        return []
    matches = []
    for line in result.stdout.splitlines()[1:]:
        parts = line.split()
        if len(parts) >= 2 and parts[1] == "device":
            matches.append(parts[0])
    return matches


def watch():
    if not shutil.which("adb") or not shutil.which("scrcpy"):
        raise SystemExit("Installa adb e scrcpy prima di usare il monitor.")
    if not (os.getenv("DISPLAY") or os.getenv("WAYLAND_DISPLAY")):
        raise SystemExit("Apri Kali in una sessione desktop grafica.")
    if not load():
        raise SystemExit("Nessun telefono autorizzato. Usa: ffremote-autoscreen allow IP_TELEFONO")
    active = {}
    print("FF Remote Lab: monitor ADB attivo (Ctrl+C per fermare).", flush=True)
    while True:
        try:
            serials = connected()
        except (subprocess.TimeoutExpired, OSError):
            serials = []
        approved = load()
        for serial, proc in list(active.items()):
            if serial not in serials or proc.poll() is not None:
                if serial not in serials and proc.poll() is None:
                    proc.terminate()
                del active[serial]
        for serial in serials:
            if not allowed(serial, approved):
                continue
            if serial not in active:
                print("Collegato: " + serial + "; apro schermo.", flush=True)
                active[serial] = subprocess.Popen(
                    ["scrcpy", "-s", serial, "-m", "1024"],
                    stdout=subprocess.DEVNULL,
                    stderr=subprocess.DEVNULL,
                    start_new_session=True
                )
        time.sleep(3)


def main():
    parser = argparse.ArgumentParser(description="FF Remote Lab - apertura automatica scrcpy")
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("list")
    commands.add_parser("watch")
    allow = commands.add_parser("allow")
    allow.add_argument("ip_or_serial")
    forget = commands.add_parser("forget")
    forget.add_argument("ip_or_serial")
    args = parser.parse_args()
    if args.command == "list":
        print("Dispositivi autorizzati:", *load(), sep="\n")
    elif args.command == "allow":
        item = args.ip_or_serial.strip()
        if not item or any(c.isspace() for c in item) or item.startswith("-"):
            parser.error("IP o seriale non valido")
        save(load() + [item])
        print("Autorizzato per auto-apertura: " + item)
        print("Serve comunque pairing e connessione ADB autorizzati.")
    elif args.command == "forget":
        save([x for x in load() if x != args.ip_or_serial.strip()])
        print("Dispositivo rimosso dall'auto-apertura.")
    elif args.command == "watch":
        watch()


if __name__ == "__main__":
    main()
