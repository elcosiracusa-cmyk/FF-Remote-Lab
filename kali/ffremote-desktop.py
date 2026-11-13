#!/usr/bin/env python3
"""FF Remote Lab 4.0 desktop console. All remote actions require paired ADB."""
import base64
from datetime import datetime
import os
from pathlib import Path
import shutil
import subprocess
import threading
import tkinter as tk
from tkinter import messagebox, ttk

APP_PACKAGE = "it.fflab.remote"
ACTION = "it.fflab.remote.COMMAND"
COMMANDS = ("PING", "STATUS", "VIBRATE", "BEEP", "STOP")


def run_adb(args, timeout=12):
    if not shutil.which("adb"):
        raise RuntimeError("ADB non trovato. Installa Android Platform Tools.")
    result = subprocess.run(
        ["adb", *args], capture_output=True, text=True, timeout=timeout,
        check=False)
    if result.returncode:
        raise RuntimeError((result.stderr or result.stdout).strip()
                           or "ADB ha restituito un errore.")
    return result.stdout.strip()


def parse_devices(adb_output):
    devices = []
    for line in adb_output.splitlines()[1:]:
        parts = line.strip().split()
        if len(parts) >= 2 and parts[1] == "device":
            devices.append(parts[0])
    return devices


class LabConsole:
    def __init__(self, root):
        self.root = root
        self.root.title("FF Remote Lab 4.0 - Console locale")
        self.root.geometry("700x560")
        self.root.minsize(610, 470)
        self.devices = []
        self.selected = tk.StringVar()
        self.address = tk.StringVar(value="")
        self.message = tk.StringVar(value="")
        self.status = tk.StringVar(value="Solo dispositivi Android gia' autorizzati tramite ADB.")
        self.build()
        self.refresh()

    def build(self):
        container = ttk.Frame(self.root, padding=16)
        container.pack(fill="both", expand=True)
        ttk.Label(container, text="FF REMOTE LAB 4.0",
                  font=("Sans", 20, "bold")).pack(anchor="w")
        ttk.Label(container,
                  text="Controlli di laboratorio su telefoni autorizzati. Nessuna modalita' invisibile.",
                  wraplength=650).pack(anchor="w", pady=(2, 14))

        device_box = ttk.LabelFrame(container, text="Dispositivi Android collegati tramite ADB", padding=10)
        device_box.pack(fill="x")
        line = ttk.Frame(device_box)
        line.pack(fill="x")
        ttk.Label(line, text="Dispositivo:").pack(side="left")
        self.choice = ttk.Combobox(line, textvariable=self.selected, state="readonly", width=32)
        self.choice.pack(side="left", padx=8, fill="x", expand=True)
        ttk.Button(line, text="Aggiorna", command=self.refresh).pack(side="left")

        line2 = ttk.Frame(device_box)
        line2.pack(fill="x", pady=(10, 0))
        ttk.Label(line2, text="IP:porta ADB:").pack(side="left")
        ttk.Entry(line2, textvariable=self.address).pack(side="left", fill="x", expand=True, padx=8)
        ttk.Button(line2, text="Connetti", command=self.connect).pack(side="left")

        control_box = ttk.LabelFrame(container, text="Controlli autorizzati", padding=10)
        control_box.pack(fill="x", pady=10)
        bar = ttk.Frame(control_box)
        bar.pack(fill="x")
        ttk.Button(bar, text="Apri schermo", command=self.screen).pack(side="left", padx=(0, 8))
        for cmd in COMMANDS:
            ttk.Button(bar, text=cmd, command=lambda c=cmd: self.command(c)).pack(side="left", padx=3)

        message_row = ttk.Frame(control_box)
        message_row.pack(fill="x", pady=(12, 0))
        ttk.Label(message_row, text="Messaggio:").pack(side="left")
        ttk.Entry(message_row, textvariable=self.message).pack(side="left", fill="x", expand=True, padx=8)
        ttk.Button(message_row, text="Invia", command=self.send_message).pack(side="left")

        ttk.Label(container, textvariable=self.status, wraplength=640).pack(anchor="w", pady=(4, 5))
        log_box = ttk.LabelFrame(container, text="Registro locale della sessione", padding=8)
        log_box.pack(fill="both", expand=True)
        self.logview = tk.Text(log_box, height=9, state="disabled", wrap="word")
        self.logview.pack(fill="both", expand=True)
        ttk.Label(container,
                  text="REVOCA: usa REVOCA nell'app o INTERROMPI nella notifica. "
                       "Per revocare anche scrcpy/ADB, disattiva Debug wireless sul telefono.",
                  wraplength=650).pack(anchor="w", pady=(10, 0))
        ttk.Label(container,
                  text="iPhone: duplicazione schermo AirPlay con consenso dal Centro di Controllo; "
                       "nessun controllo touch tramite ADB.",
                  wraplength=650).pack(anchor="w", pady=(5, 0))

    def log(self, info):
        when = datetime.now().strftime("%H:%M:%S")
        self.logview.configure(state="normal")
        self.logview.insert("end", "[" + when + "] " + info + "\n")
        self.logview.see("end")
        self.logview.configure(state="disabled")

    def work(self, description, func, on_success=None):
        self.status.set(description + " ...")
        def worker():
            try:
                result = func()
            except Exception as error:
                self.root.after(0, self.failed, str(error))
                return
            self.root.after(0, self.succeeded, description, result, on_success)
        threading.Thread(target=worker, daemon=True).start()

    def failed(self, reason):
        self.status.set("ERRORE: " + reason)
        self.log("ERRORE: " + reason)

    def succeeded(self, description, result, callback):
        self.status.set(description + ": completato")
        self.log(description + (" - " + result[:240] if result else ""))
        if callback:
            callback(result)

    def refresh(self):
        def done(output):
            previous = self.selected.get()
            self.devices = parse_devices(output)
            self.choice["values"] = self.devices
            if previous in self.devices:
                self.selected.set(previous)
            else:
                self.selected.set(self.devices[0] if self.devices else "")
            self.status.set(str(len(self.devices)) + " telefono/i ADB online.")
        self.work("Ricerca dispositivi ADB", lambda: run_adb(["devices", "-l"]), done)

    def require_device(self):
        serial = self.selected.get()
        if serial not in self.devices:
            messagebox.showwarning("Dispositivo non selezionato",
                                   "Seleziona un telefono gia' associato ad ADB.")
            return None
        return serial

    def connect(self):
        address = self.address.get().strip()
        if not address or " " in address or ":" not in address:
            messagebox.showwarning("Indirizzo", "Inserisci l'indirizzo IP:PORTA da Debug wireless.")
            return
        def done(_):
            self.refresh()
        self.work("Connessione ADB", lambda: run_adb(["connect", address]), done)

    def command(self, cmd):
        serial = self.require_device()
        if not serial:
            return
        if cmd == "STOP":
            if not messagebox.askyesno("Interrompi sessione",
                                      "Inviare STOP al servizio FF Remote Lab sul telefono? "
                                      "Questo non revoca il pairing ADB."):
                return
        args = ["-s", serial, "shell", "am", "broadcast", "-a", ACTION,
                "-p", APP_PACKAGE, "--es", "ff.command", cmd]
        self.work("Comando " + cmd + " a " + serial, lambda: run_adb(args))

    def send_message(self):
        serial = self.require_device()
        content = self.message.get().strip()
        if not serial or not content:
            return
        if len(content) > 120:
            messagebox.showwarning("Messaggio", "Massimo 120 caratteri.")
            return
        encoded = base64.b64encode(content.encode("utf-8")).decode("ascii")
        args = ["-s", serial, "shell", "am", "broadcast", "-a", ACTION,
                "-p", APP_PACKAGE, "--es", "ff.command", "MESSAGE",
                "--es", "ff.text64", encoded]
        # Do not log the private message content.
        self.work("Invio messaggio a " + serial, lambda: run_adb(args))

    def screen(self):
        serial = self.require_device()
        if not serial:
            return
        if not shutil.which("scrcpy"):
            messagebox.showerror("scrcpy mancante", "Installa scrcpy sul computer.")
            return
        if not messagebox.askyesno(
                "Avvia visualizzazione schermo",
                "Il telefono deve essere tuo o esplicitamente autorizzato. "
                "Aprire lo schermo su Kali tramite ADB di " + serial + "?"):
            return
        try:
            subprocess.Popen(["scrcpy", "-s", serial, "-m", "1024"],
                             stdout=subprocess.DEVNULL,
                             stderr=subprocess.DEVNULL)
            self.status.set("Apertura schermo di " + serial)
            self.log("Avvio scrcpy: " + serial)
        except OSError as error:
            self.failed(str(error))


if __name__ == "__main__":
    root = tk.Tk()
    LabConsole(root)
    root.mainloop()
