#!/data/data/com.termux/files/usr/bin/python
import json
import os
import secrets
import shutil
import signal
import ssl
import subprocess
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

BASE = Path(os.environ.get("LOCALLS_AGENT_CONFIG_DIR", str(Path.home() / "home-server-agent")))
CONFIG_PATH = BASE / "config.json"


def load_config():
    BASE.mkdir(parents=True, exist_ok=True)
    if not CONFIG_PATH.exists():
        cfg = {"host": "0.0.0.0", "port": 8787, "token": secrets.token_urlsafe(32), "tls": True, "tls_cert": str(BASE / "agent-cert.pem"), "tls_key": str(BASE / "agent-key.pem")}
        CONFIG_PATH.write_text(json.dumps(cfg, indent=2), encoding="utf-8")
        os.chmod(CONFIG_PATH, 0o600)
        return cfg
    return json.loads(CONFIG_PATH.read_text(encoding="utf-8"))


CFG = load_config()
TOKEN = str(CFG.get("token", ""))
HOST = str(CFG.get("host", "0.0.0.0"))
PORT = int(CFG.get("port", 8787))


def sshd_pids():
    try:
        out = subprocess.check_output(["pgrep", "-x", "sshd"], text=True, stderr=subprocess.DEVNULL)
        return [int(x) for x in out.split() if x.isdigit()]
    except Exception:
        return []


def start_sshd():
    pids = sshd_pids()
    if pids:
        return {"ok": True, "state": "already_running", "pids": pids}
    binary = shutil.which("sshd")
    if not binary:
        return {"ok": False, "error": "sshd not found"}
    subprocess.Popen([binary], stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, start_new_session=True)
    time.sleep(0.4)
    pids = sshd_pids()
    return {"ok": bool(pids), "state": "running" if pids else "failed_to_start", "pids": pids}


def stop_sshd():
    pids = sshd_pids()
    if not pids:
        return {"ok": True, "state": "already_stopped", "pids": []}
    errors = []
    for pid in pids:
        try:
            os.kill(pid, signal.SIGTERM)
        except ProcessLookupError:
            pass
        except Exception as exc:
            errors.append(f"{pid}: {exc}")
    time.sleep(0.7)
    remaining = sshd_pids()
    for pid in remaining:
        try:
            os.kill(pid, signal.SIGKILL)
        except ProcessLookupError:
            pass
        except Exception as exc:
            errors.append(f"{pid}: {exc}")
    time.sleep(0.2)
    remaining = sshd_pids()
    return {"ok": not remaining and not errors, "state": "stopped" if not remaining else "still_running", "pids": remaining, "errors": errors}


def restart_sshd():
    stopped = stop_sshd()
    started = start_sshd()
    return {"ok": bool(started.get("ok")), "state": "running" if started.get("ok") else "failed", "stop": stopped, "start": started}


class Handler(BaseHTTPRequestHandler):
    server_version = "LocalLSAgent/0.3"

    def setup(self):
        self.request.settimeout(8)
        super().setup()

    def log_message(self, fmt, *args):
        print("%s - %s" % (self.address_string(), fmt % args), flush=True)

    def authorized(self):
        header = self.headers.get("Authorization", "")
        if not header.startswith("Bearer "):
            return False
        supplied = header[7:]
        return bool(TOKEN) and secrets.compare_digest(supplied, TOKEN)

    def send_json(self, code, obj):
        data = json.dumps(obj, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        if not self.authorized():
            self.send_json(401, {"ok": False, "error": "unauthorized"})
            return
        if self.path == "/status":
            pids = sshd_pids()
            self.send_json(200, {"ok": True, "sshd": "running" if pids else "stopped", "pids": pids, "agent": "0.3"})
        else:
            self.send_json(404, {"ok": False, "error": "not_found"})

    def do_POST(self):
        if not self.authorized():
            self.send_json(401, {"ok": False, "error": "unauthorized"})
            return
        if self.path == "/start":
            result = start_sshd()
        elif self.path == "/stop":
            result = stop_sshd()
        elif self.path == "/restart":
            result = restart_sshd()
        else:
            self.send_json(404, {"ok": False, "error": "not_found"})
            return
        self.send_json(200 if result.get("ok") else 500, result)


if __name__ == "__main__":
    if not TOKEN:
        raise SystemExit("Token is empty in config.json")
    server = ThreadingHTTPServer((HOST, PORT), Handler)
    if CFG.get("tls", True):
        context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        context.minimum_version = ssl.TLSVersion.TLSv1_2
        certificate = Path(CFG.get("tls_cert", str(BASE / "agent-cert.pem")))
        private_key = Path(CFG.get("tls_key", str(BASE / "agent-key.pem")))
        if not certificate.is_file() or not private_key.is_file():
            raise SystemExit("TLS certificate is missing; run the updated installer. No HTTP fallback.")
        if private_key.stat().st_mode & 0o077:
            raise SystemExit("TLS private key requires permissions 600")
        context.load_cert_chain(str(certificate), str(private_key))
        server.socket = context.wrap_socket(server.socket, server_side=True, do_handshake_on_connect=False)
    elif not CFG.get("allow_legacy_http", False):
        raise SystemExit("HTTP requires explicit allow_legacy_http=true. HTTPS is recommended.")
    print(f"LocalLS Agent 0.3 listening on {HOST}:{PORT} ({'HTTPS' if CFG.get('tls',True) else 'legacy HTTP'})", flush=True)
    server.serve_forever()
