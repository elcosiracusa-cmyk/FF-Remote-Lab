#!/usr/bin/env python3
"""FF Remote Lab 4.0: private HTTPS (via Tailscale Serve) transport.

Run only on Kali on 127.0.0.1. Never publish this service with Funnel.
Each phone gets a distinct token; local administration requires an admin token.
No remote shell, arbitrary command execution, microphone, camera or hidden access.
"""
import argparse
from collections import deque
from datetime import datetime, timezone
import hmac
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import io
import json
import os
from pathlib import Path
import secrets
import sys
import threading
import time
import urllib.error
import urllib.request

CONFIG_PATH = Path.home() / ".config" / "ffremote-4" / "secrets.json"
PORT = 8765
COMMANDS = {"PING", "STATUS", "VIBRATE", "BEEP", "MESSAGE", "STOP"}
lock = threading.RLock()
pending = deque(maxlen=12)
last_seen = {"android": None, "ios": None}
last_ack = None
latest_frame = None
latest_frame_time = None
seq = 0


def load_config(create=False):
    if not CONFIG_PATH.exists():
        if not create:
            raise SystemExit("Configurazione assente: esegui 'python3 remote4-server.py init'")
        CONFIG_PATH.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
        config = {
            "admin_token": secrets.token_urlsafe(32),
            "android_token": secrets.token_urlsafe(32),
            "ios_token": secrets.token_urlsafe(32),
        }
        fd = os.open(str(CONFIG_PATH), os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(fd, "w", encoding="utf-8") as out:
            json.dump(config, out, indent=2)
        print("Credenziali generate localmente in", CONFIG_PATH)
    if os.stat(CONFIG_PATH).st_mode & 0o077:
        raise SystemExit("Permessi insicuri: esegui chmod 600 " + str(CONFIG_PATH))
    return json.loads(CONFIG_PATH.read_text(encoding="utf-8"))


def timestamp():
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


class Handler(BaseHTTPRequestHandler):
    server_version = "FFRemoteLab/4"

    def log_message(self, fmt, *args):
        # Do not log tokens, URL paths with secrets or frame bodies.
        sys.stderr.write("[%s] %s\n" % (timestamp(), fmt % args))

    def send_data(self, status, content, kind="application/json"):
        payload = content if isinstance(content, bytes) else json.dumps(
            content, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", kind)
        self.send_header("Content-Length", str(len(payload)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.end_headers()
        self.wfile.write(payload)

    def authenticated(self, role):
        authorization = self.headers.get("Authorization", "")
        if not authorization.startswith("Bearer "):
            self.send_data(401, {"error": "Token richiesto"})
            return False
        token = authorization[7:].strip()
        expected = self.server.config[role + "_token"]
        if len(token) < 32 or not hmac.compare_digest(token, expected):
            self.send_data(403, {"error": "Token non valido"})
            return False
        return True

    def read_body(self, max_bytes):
        try:
            size = int(self.headers.get("Content-Length", "-1"))
        except ValueError:
            size = -1
        if size < 0 or size > max_bytes:
            self.send_data(413, {"error": "Dimensione non valida"})
            return None
        return self.rfile.read(size)

    def read_json(self, max_bytes=2048):
        payload = self.read_body(max_bytes)
        if payload is None:
            return None
        try:
            decoded = json.loads(payload)
            if not isinstance(decoded, dict):
                raise ValueError("Oggetto atteso")
            return decoded
        except (ValueError, UnicodeDecodeError):
            self.send_data(400, {"error": "JSON non valido"})
            return None

    def do_GET(self):
        global latest_frame
        path = self.path.split("?")[0]
        if path == "/health":
            self.send_data(200, {"ok": True, "service": "FF Remote Lab 4.0"})
        elif path == "/v1/android/poll":
            if not self.authenticated("android"):
                return
            with lock:
                last_seen["android"] = timestamp()
                cmd = pending.popleft() if pending else {"command": "NONE"}
            self.send_data(200, cmd)
        elif path == "/v1/admin/status":
            if not self.authenticated("admin"):
                return
            with lock:
                self.send_data(200, {
                    "last_seen": dict(last_seen),
                    "last_ack": last_ack,
                    "pending": len(pending),
                    "ios_frame_at": latest_frame_time,
                })
        elif path == "/v1/admin/ios-frame":
            if not self.authenticated("admin"):
                return
            with lock:
                frame = latest_frame
                frame_when = latest_frame_time
            # A disconnected or revoked broadcast must not leave a stale
            # picture frozen on the observer screen.
            if frame_when is not None:
                try:
                    age = (datetime.now(timezone.utc)
                           - datetime.fromisoformat(frame_when)).total_seconds()
                    if age > 8:
                        frame = None
                except ValueError:
                    frame = None
            if frame is None:
                self.send_data(404, {"error": "Nessun frame disponibile"})
                return
            self.send_data(200, frame, "image/jpeg")
        else:
            self.send_data(404, {"error": "Percorso sconosciuto"})

    def do_POST(self):
        global last_ack, latest_frame, latest_frame_time, seq
        path = self.path.split("?")[0]
        if path == "/v1/android/ack":
            if not self.authenticated("android"):
                return
            data = self.read_json()
            if data is None:
                return
            with lock:
                last_seen["android"] = timestamp()
                last_ack = {
                    "id": data.get("id"),
                    "command": str(data.get("command", ""))[:20],
                    "result": str(data.get("result", ""))[:240],
                    "when": timestamp(),
                }
            self.send_data(200, {"ok": True})
        elif path == "/v1/ios/frame":
            if not self.authenticated("ios"):
                return
            if self.headers.get("Content-Type", "") != "image/jpeg":
                self.send_data(415, {"error": "Solo image/jpeg"})
                return
            data = self.read_body(450000)
            if data is None:
                return
            if len(data) < 100 or data[:3] != b"\xff\xd8\xff" or data[-2:] != b"\xff\xd9":
                self.send_data(400, {"error": "JPEG non valido"})
                return
            with lock:
                latest_frame = data
                latest_frame_time = timestamp()
                last_seen["ios"] = latest_frame_time
            self.send_data(200, {"ok": True})
        elif path == "/v1/admin/command":
            if not self.authenticated("admin"):
                return
            data = self.read_json()
            if data is None:
                return
            command = str(data.get("command", "")).upper()
            message = data.get("message", "")
            if command not in COMMANDS or (command == "MESSAGE" and
                                           (not isinstance(message, str) or
                                            len(message) > 120 or not message.strip())):
                self.send_data(400, {"error": "Comando o messaggio non consentito"})
                return
            if command != "MESSAGE":
                message = ""
            with lock:
                if len(pending) >= pending.maxlen:
                    self.send_data(429, {"error": "Coda piena"})
                    return
                seq += 1
                entry = {"id": seq, "command": command, "text": message}
                pending.append(entry)
            self.send_data(200, {"ok": True, "id": seq})
        else:
            self.send_data(404, {"error": "Percorso sconosciuto"})


def admin_request(config, method, path, data=None):
    body = None if data is None else json.dumps(data).encode("utf-8")
    request = urllib.request.Request(
        "http://127.0.0.1:%d%s" % (PORT, path), data=body, method=method,
        headers={
            "Authorization": "Bearer " + config["admin_token"],
            "Content-Type": "application/json",
        })
    with urllib.request.urlopen(request, timeout=10) as response:
        return response.read()


def main():
    parser = argparse.ArgumentParser(description="FF Remote Lab 4.0 private transport")
    sub = parser.add_subparsers(dest="action", required=True)
    for name in ("init", "show", "serve", "status"):
        sub.add_parser(name)
    send = sub.add_parser("send")
    send.add_argument("command", choices=sorted(COMMANDS))
    send.add_argument("message", nargs="?", default="")
    args = parser.parse_args()

    if args.action == "init":
        load_config(create=True)
        return
    config = load_config()
    if args.action == "show":
        print("CONFIGURAZIONE RISERVATA. Non condividere screenshot o token.")
        print("Admin (solo Kali):", config["admin_token"])
        print("Pixel Android:", config["android_token"])
        print("iPhone:", config["ios_token"])
        return
    if args.action == "serve":
        httpd = ThreadingHTTPServer(("127.0.0.1", PORT), Handler)
        httpd.config = config
        print("Server in ascolto SOLO su 127.0.0.1:%s" % PORT, flush=True)
        print("Esponi ai telefoni esclusivamente con Tailscale Serve, NON Funnel.",
              flush=True)
        try:
            httpd.serve_forever(poll_interval=0.5)
        except KeyboardInterrupt:
            pass
        finally:
            httpd.server_close()
        return
    try:
        if args.action == "status":
            print(admin_request(config, "GET", "/v1/admin/status").decode("utf-8"))
        if args.action == "send":
            payload = {"command": args.command, "message": args.message}
            print(admin_request(config, "POST", "/v1/admin/command", payload)
                  .decode("utf-8"))
    except urllib.error.URLError as exc:
        raise SystemExit("Server non raggiungibile: " + str(exc))


if __name__ == "__main__":
    main()
