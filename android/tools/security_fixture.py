#!/usr/bin/env python3
"""Disposable loopback SSH/SFTP and real HTTPS agent for Android JVM integration tests.

Requires asyncssh 2.21.1 and openssl. Generated identities stay in the supplied new
directory outside the repository. Never exposes or operates a user's SSH service.
"""
import argparse
import asyncio
import json
import os
from pathlib import Path
import secrets
import signal
import socket
import subprocess
import asyncssh


async def serve(root):
    home = root / 'home'
    (home / '.ssh').mkdir(parents=True)
    (home / '.ssh/authorized_keys').write_bytes(b'# preserved fixture key list\n')
    host_key = asyncssh.generate_private_key('ssh-rsa', key_size=3072)
    password = secrets.token_urlsafe(32)
    metrics = {'passwordAuths': 0, 'keyAuths': 0, 'rejectKeys': False}
    metrics_path = root / 'metrics.json'
    metrics_path.write_text(json.dumps(metrics))

    class Server(asyncssh.SSHServer):
        def begin_auth(self, username):
            return True

        def password_auth_supported(self):
            return True

        def public_key_auth_supported(self):
            return True

        def validate_password(self, username, value):
            current = json.loads(metrics_path.read_text())
            current['passwordAuths'] += 1
            metrics_path.write_text(json.dumps(current))
            return username == 'fixture' and value == password

        def validate_public_key(self, username, key):
            current = json.loads(metrics_path.read_text())
            current['keyAuths'] += 1
            metrics_path.write_text(json.dumps(current))
            authorized = (home / '.ssh/authorized_keys').read_text()
            return username == 'fixture' and not current['rejectKeys'] and key.export_public_key().decode().split()[1] in authorized

    async def command(process):
        # Deterministic test protocol: never execute arbitrary shell commands.
        try:
            if process.command == 'fixture-ok':
                process.stdout.write('ready\n')
                process.exit(0)
            elif process.command == 'fixture-fail':
                process.stdout.write('not-ready\n')
                process.stderr.write('private diagnostic ' + password)
                process.exit(41)
            elif process.command == 'fixture-stderr':
                process.stderr.write('x' * 32000)
                process.stdout.write('ready\n')
                process.exit(0)
            elif process.command == 'fixture-oversize':
                process.stdout.write('x' * 70000)
                process.exit(0)
            elif process.command == 'fixture-timeout':
                await asyncio.sleep(3)
                process.exit(0)
            else:
                process.exit(127)
        except (BrokenPipeError, ConnectionError):
            pass

    listener = await asyncssh.create_server(Server, '127.0.0.1', 0, server_host_keys=[host_key],
                                           sftp_factory=lambda chan: asyncssh.SFTPServer(chan, chroot=str(home)),
                                           process_factory=command)
    for name in ('agent', 'other'):
        subprocess.run(['openssl', 'req', '-x509', '-newkey', 'rsa:3072', '-nodes', '-days', '2',
                        '-keyout', str(root / (name + '-key.pem')), '-out', str(root / (name + '-cert.pem')),
                        '-subj', '/CN=localhost'], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        (root / (name + '-key.pem')).chmod(0o600)
    with socket.socket() as sock:
        sock.bind(('127.0.0.1', 0))
        agent_port = sock.getsockname()[1]
    token = secrets.token_urlsafe(32)
    config = root / 'config.json'
    config.write_text(json.dumps({'host': '127.0.0.1', 'port': agent_port, 'token': token, 'tls': True,
                                 'tls_cert': str(root / 'agent-cert.pem'), 'tls_key': str(root / 'agent-key.pem')}))
    config.chmod(0o600)
    log = (root / 'agent.log').open('w')
    agent = subprocess.Popen(['python3', str(Path(__file__).resolve().parents[1] / 'realme-agent/agent.py')],
                             env={**os.environ, 'LOCALLS_AGENT_CONFIG_DIR': str(root)}, stdout=log, stderr=log)
    ready = root / 'fixture.json'
    try:
        for _ in range(100):
            if agent.poll() is not None:
                raise RuntimeError('Fixture agent did not start')
            try:
                with socket.create_connection(('127.0.0.1', agent_port), timeout=.2):
                    break
            except OSError:
                await asyncio.sleep(.05)
        else:
            raise RuntimeError('Fixture agent timed out')
        ready.write_text(json.dumps({'sshPort': listener.get_port(), 'password': password,
                                    'hostPin': host_key.get_fingerprint('sha256'), 'agentPort': agent_port, 'token': token}))
        ready.chmod(0o600)
        print('Isolated SSH/HTTPS fixture ready', flush=True)
        stop = asyncio.Event()
        loop = asyncio.get_running_loop()
        for sig in (signal.SIGINT, signal.SIGTERM):
            loop.add_signal_handler(sig, stop.set)
        await stop.wait()
    finally:
        ready.unlink(missing_ok=True)
        agent.terminate()
        try:
            agent.wait(timeout=5)
        except subprocess.TimeoutExpired:
            agent.kill()
            agent.wait()
        log.close()
        listener.close()
        await listener.wait_closed()


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    args.output.mkdir(mode=0o700, parents=True, exist_ok=False)
    asyncio.run(serve(args.output.resolve()))
