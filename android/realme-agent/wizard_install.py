#!/usr/bin/env python3
"""Prepare the existing LocalLS HTTPS agent, preserving SSH and live processes.

Invoked by the existing Termux RUN_COMMAND bridge. Only public status reaches stdout.
"""
import argparse
import fcntl
import json
import os
from pathlib import Path
import secrets
import shutil
import socket
import ssl
import subprocess
import tempfile
import time


class Refused(Exception):
    def __init__(self, code):
        self.code = code


def private_file(path):
    if path.is_symlink() or not path.is_file() or path.stat().st_mode & 0o077:
        raise Refused(54)


def atomic_write(path, data, mode):
    if path.is_symlink():
        raise Refused(54)
    fd, temporary = tempfile.mkstemp(prefix='.localls-', dir=path.parent)
    try:
        os.fchmod(fd, mode)
        with os.fdopen(fd, 'wb') as stream:
            stream.write(data)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


def endpoint(cfg):
    host = cfg.get('host', '0.0.0.0')
    port = cfg.get('port', 8787)
    if not isinstance(host, str) or not isinstance(port, int) or isinstance(port, bool) or not 1 <= port <= 65535:
        raise Refused(52)
    return ('127.0.0.1' if host in ('', '0.0.0.0', '::') else host), port


def listening(host, port):
    try:
        with socket.create_connection((host, port), timeout=1):
            return True
    except OSError:
        return False


def tls_matches(host, port, certificate):
    expected = ssl.PEM_cert_to_DER_cert(certificate.read_text(encoding='utf-8'))
    context = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
    context.check_hostname = False
    context.verify_mode = ssl.CERT_NONE  # Exact DER comparison below, no token/HTTP sent.
    try:
        with socket.create_connection((host, port), timeout=2) as raw:
            with context.wrap_socket(raw, server_hostname='localhost') as secure:
                return secure.getpeercert(binary_form=True) == expected
    except (OSError, ssl.SSLError):
        return False


def prepare(home, source, port=8787, start=True):
    home = Path(home).resolve()
    base = home / 'home-server-agent'
    if base.is_symlink():
        raise Refused(54)
    base.mkdir(mode=0o700, exist_ok=True)
    lock = base / '.localls-wizard.lock'
    if lock.is_symlink():
        raise Refused(54)
    with lock.open('a') as stream:
        os.chmod(lock, 0o600)
        fcntl.flock(stream, fcntl.LOCK_EX)
        config = base / 'config.json'
        existed = config.exists() or config.is_symlink()
        if existed:
            private_file(config)
            if config.stat().st_size > 16384:
                raise Refused(52)
            cfg = json.loads(config.read_text(encoding='utf-8'))
            # Migration of an existing HTTP agent requires a separate user action.
            if cfg.get('tls') is not True:
                raise Refused(51)
        else:
            cfg = {'host': '0.0.0.0', 'port': port, 'token': secrets.token_urlsafe(32), 'tls': True,
                   'tls_cert': str(base / 'agent-cert.pem'), 'tls_key': str(base / 'agent-key.pem')}
        host, actual_port = endpoint(cfg)
        token = cfg.get('token')
        if not isinstance(token, str) or not token or len(token) > 4096 or '\r' in token or '\n' in token:
            raise Refused(52)
        cert = Path(cfg.get('tls_cert', ''))
        key = Path(cfg.get('tls_key', ''))
        # The SAF grant is the Termux home, so keep managed TLS material inside it.
        for path in (cert, key):
            if not path.is_absolute() or not path.resolve().is_relative_to(home) or path.is_symlink():
                raise Refused(54)
        active = listening(host, actual_port)
        if active:
            private_file(key)
            if not cert.is_file() or not tls_matches(host, actual_port, cert):
                raise Refused(53)
            # Never overwrite a running agent, its token, certificate or configuration.
            return actual_port, True
        if cert.exists() != key.exists():
            raise Refused(54)
        if not cert.exists():
            if existed:
                raise Refused(54)
            with tempfile.TemporaryDirectory(prefix='.localls-tls-', dir=base) as temporary:
                generated_cert = Path(temporary) / 'cert.pem'
                generated_key = Path(temporary) / 'key.pem'
                subprocess.run(['openssl', 'req', '-x509', '-newkey', 'rsa:3072', '-sha256', '-nodes',
                                '-days', '365', '-keyout', str(generated_key), '-out', str(generated_cert),
                                '-subj', '/CN=LocalLS Agent', '-addext', 'subjectAltName=DNS:localhost,IP:127.0.0.1'],
                               check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
                atomic_write(key, generated_key.read_bytes(), 0o600)
                atomic_write(cert, generated_cert.read_bytes(), 0o600)
        private_file(key)
        # Validate TLS material before changing an agent executable/configuration.
        context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        context.load_cert_chain(str(cert), str(key))
        target = base / 'agent.py'
        if target.is_symlink():
            raise Refused(54)
        if not existed:
            atomic_write(config, json.dumps(cfg, indent=2).encode('utf-8'), 0o600)
        source_data = Path(source).read_bytes()
        if not target.is_file() or target.read_bytes() != source_data:
            atomic_write(target, source_data, 0o700)
        if start:
            env = {**os.environ, 'LOCALLS_AGENT_CONFIG_DIR': str(base)}
            process = subprocess.Popen([shutil.which('python') or shutil.which('python3'), str(target)],
                                       env=env, stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL,
                                       stderr=subprocess.DEVNULL, start_new_session=True)
            for _ in range(30):
                if process.poll() is not None:
                    raise Refused(55)
                if listening(host, actual_port) and tls_matches(host, actual_port, cert):
                    break
                time.sleep(.1)
            else:
                # Stop only the process started by this attempt, never SSH or another agent.
                process.terminate()
                process.wait(timeout=5)
                raise Refused(55)
        return actual_port, False


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--home', default=str(Path.home()))
    parser.add_argument('--source', required=True)
    parser.add_argument('--port', type=int, default=8787)
    args = parser.parse_args()
    try:
        port, running = prepare(args.home, args.source, args.port)
        print('AGENT_PORT=' + str(port))
        print('AGENT_READY=1')
    except Refused as error:
        return error.code
    except Exception:
        # Do not print configuration, tokens, file contents or raw exceptions.
        return 56
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
