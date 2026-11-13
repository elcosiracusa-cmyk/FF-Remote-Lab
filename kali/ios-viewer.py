#!/usr/bin/env python3
"""View consented iPhone ReplayKit frames relayed over private Tailscale.

Requires python3-tk and python3-pil.imagetk. Read-only; no touch control.
"""
import io
import json
from pathlib import Path
import threading
import tkinter as tk
from tkinter import ttk
import urllib.error
import urllib.request

from PIL import Image, ImageTk

PORT = 8765
CFG = Path.home() / ".config" / "ffremote-4" / "secrets.json"


class Viewer:
    def __init__(self, root, token):
        self.root = root
        self.token = token
        self.root.title("FF Remote Lab 4.0 - Schermo iPhone (autorizzato)")
        self.root.geometry("500x840")
        self.image = ttk.Label(root, text="In attesa di trasmissione iPhone autorizzata...")
        self.image.pack(fill="both", expand=True)
        self.note = ttk.Label(root, text="Solo visualizzazione. Stop dall'iPhone in qualsiasi momento.")
        self.note.pack(pady=6)
        self.active = True
        self.busy = False
        root.protocol("WM_DELETE_WINDOW", self.close)
        self.poll()

    def close(self):
        self.active = False
        self.root.destroy()

    def poll(self):
        if self.active and not self.busy:
            self.busy = True
            threading.Thread(target=self.fetch, daemon=True).start()
        if self.active:
            self.root.after(950, self.poll)

    def fetch(self):
        try:
            request = urllib.request.Request(
                "http://127.0.0.1:%d/v1/admin/ios-frame" % PORT,
                headers={"Authorization": "Bearer " + self.token})
            with urllib.request.urlopen(request, timeout=5) as response:
                frame = response.read(500000)
            image = Image.open(io.BytesIO(frame))
            image.thumbnail((460, 760))
            image.load()
            if self.active:
                self.root.after(0, self.show, image)
        except (urllib.error.URLError, OSError, ValueError):
            pass
        finally:
            self.busy = False

    def show(self, image):
        if not self.active:
            return
        photo = ImageTk.PhotoImage(image)
        self.image.configure(image=photo, text="")
        self.image.image = photo


def main():
    if not CFG.exists():
        raise SystemExit("Manca la configurazione: python3 kali/remote4-server.py init")
    config = json.loads(CFG.read_text(encoding="utf-8"))
    root = tk.Tk()
    Viewer(root, config["admin_token"])
    root.mainloop()


if __name__ == "__main__":
    main()
