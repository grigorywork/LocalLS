"""Idempotent LocalLS wizard provisioning. Never stops/restarts SSH or a live agent."""
import fcntl
import hashlib
import http.client
import json
import os
from pathlib import Path
import secrets
import socket
import ssl
import subprocess
import sys
import tempfile
import time


class SetupFailure(Exception):
    pass


def atomic_write(path, data, mode):
    if path.is_symlink():
        raise SetupFailure('UNSAFE_FILE')
    fd, name = tempfile.mkstemp(prefix='.localls-', dir=path.parent)
    try:
        with os.fdopen(fd, 'wb') as stream:
            stream.write(data)
            stream.flush()
            os.fsync(stream.fileno())
        os.chmod(name, mode)
        os.replace(name, path)
    finally:
        Path(name).unlink(missing_ok=True)


def agent_ready(host, port, token, cert):
    connection = None
    try:
        context = ssl.create_default_context(cafile=str(cert))
        context.check_hostname = False
        expected = hashlib.sha256(ssl.PEM_cert_to_DER_cert(cert.read_text())).digest()
        connection = http.client.HTTPSConnection(host, port, context=context, timeout=1)
        connection.connect()
        actual = hashlib.sha256(connection.sock.getpeercert(binary_form=True)).digest()
        if not secrets.compare_digest(expected, actual):
            return False
        connection.request('GET', '/status', headers={'Authorization': 'Bearer ' + token})
        response = connection.getresponse()
        body = response.read(65537)
        result = json.loads(body) if len(body) <= 65536 else {}
        return response.status == 200 and result.get('ok') is True and result.get('agent') == '0.3'
    except (OSError, ValueError, http.client.HTTPException):
        return False
    finally:
        if connection:
            connection.close()


def port_busy(host, port):
    with socket.socket() as sock:
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        try:
            sock.bind((host, port))
            return False
        except OSError:
            return True


def prepare(base, source, default_host='0.0.0.0', default_port=8787):
    base = Path(base)
    if base.is_symlink():
        raise SetupFailure('UNSAFE_FILE')
    base.mkdir(mode=0o700, parents=True, exist_ok=True)
    lock_path = base / '.wizard.lock'
    fd = os.open(lock_path, os.O_CREAT | os.O_RDWR | os.O_NOFOLLOW, 0o600)
    with os.fdopen(fd, 'w') as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise SetupFailure('BUSY') from None
        config = base / 'config.json'
        if config.is_symlink():
            raise SetupFailure('UNSAFE_FILE')
        if config.exists():
            if not config.is_file() or config.stat().st_size > 16384:
                raise SetupFailure('CONFIG')
            try:
                cfg = json.loads(config.read_text())
                host, port, token = cfg.get('host', '0.0.0.0'), cfg.get('port', 8787), cfg['token']
            except (ValueError, KeyError, TypeError, AttributeError):
                raise SetupFailure('CONFIG') from None
        else:
            cfg = {'host': default_host, 'port': default_port, 'token': secrets.token_urlsafe(32), 'tls': True,
                   'tls_cert': str(base / 'agent-cert.pem'), 'tls_key': str(base / 'agent-key.pem')}
            host, port, token = cfg['host'], cfg['port'], cfg['token']
        if host not in ('0.0.0.0', '127.0.0.1', 'localhost') or type(port) is not int or not 1 <= port <= 65535:
            raise SetupFailure('CONFIG')
        if not isinstance(token, str) or not 1 <= len(token) <= 4096 or any(ord(c) < 33 or ord(c) > 126 for c in token):
            raise SetupFailure('CONFIG')
        if cfg.get('tls', True) is not True:
            raise SetupFailure('LEGACY')
        cert = Path(cfg.get('tls_cert', str(base / 'agent-cert.pem')))
        key = Path(cfg.get('tls_key', str(base / 'agent-key.pem')))
        # Wizard verification reads these files through Termux HOME SAF too.
        if not cert.is_absolute() or not key.is_absolute() or cert == key:
            raise SetupFailure('TLS')
        home = base.parent.resolve()
        for path in (cert, key, base / 'agent.py'):
            if path.is_symlink() or not path.resolve().is_relative_to(home):
                raise SetupFailure('UNSAFE_FILE')
        if cert.exists() != key.exists():
            raise SetupFailure('TLS_INCOMPLETE')
        if cert.exists():
            try:
                if cert.stat().st_size > 16384 or key.stat().st_size > 16384:
                    raise ValueError()
                check = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
                check.load_cert_chain(str(cert), str(key))
                valid = subprocess.run(['openssl', 'x509', '-in', str(cert), '-noout', '-checkend', '0'],
                                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode == 0
                if not valid:
                    raise ValueError()
            except (OSError, ValueError):
                raise SetupFailure('TLS') from None
            if config.exists():
                os.chmod(config, 0o600)
            os.chmod(key, 0o600)
            if agent_ready('127.0.0.1', port, token, cert):
                return 'existing'
        if port_busy(host, port):
            raise SetupFailure('PORT_BUSY')
        # No existing config bytes or token are rewritten, including unknown fields.
        if not cert.exists():
            if cert.parent != base or key.parent != base:
                raise SetupFailure('TLS')
            with tempfile.TemporaryDirectory(prefix='.tls-', dir=base) as temporary:
                directory = Path(temporary)
                result = subprocess.run(['openssl', 'req', '-x509', '-newkey', 'rsa:3072', '-sha256', '-nodes',
                                         '-days', '365', '-subj', '/CN=LocalLS Agent',
                                         '-addext', 'subjectAltName=DNS:localhost,IP:127.0.0.1',
                                         '-keyout', str(directory / 'key.pem'), '-out', str(directory / 'cert.pem')],
                                        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
                if result.returncode:
                    raise SetupFailure('TLS')
                atomic_write(key, (directory / 'key.pem').read_bytes(), 0o600)
                atomic_write(cert, (directory / 'cert.pem').read_bytes(), 0o600)
        if not config.exists():
            atomic_write(config, json.dumps(cfg, indent=2).encode(), 0o600)
        os.chmod(config, 0o600)
        os.chmod(key, 0o600)
        # A stopped agent is updated atomically; a verified live process is left alone.
        compile(source, 'agent.py', 'exec')
        atomic_write(base / 'agent.py', source, 0o700)
        process = subprocess.Popen([sys.executable, str(base / 'agent.py')],
                                   env={**os.environ, 'LOCALLS_AGENT_CONFIG_DIR': str(base)},
                                   stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                                   start_new_session=True, close_fds=True)
        for _ in range(40):
            if agent_ready('127.0.0.1', port, token, cert):
                return 'started'
            if process.poll() is not None:
                raise SetupFailure('START')
            time.sleep(.1)
        # Terminate only the new child created here. No PID search or SSH action.
        process.terminate()
        try:
            process.wait(timeout=3)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait()
        raise SetupFailure('START')


if __name__ == '__main__':
    try:
        prepare(Path.home() / 'home-server-agent', AGENT_SOURCE)
        print('AGENT_READY=1', flush=True)
    except SetupFailure as error:
        print('AGENT_SETUP_' + str(error), flush=True)
        sys.exit(1)
    except Exception:
        print('AGENT_SETUP_FAILED', flush=True)
        sys.exit(1)
