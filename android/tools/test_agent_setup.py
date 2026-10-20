#!/usr/bin/env python3
"""Real isolated wizard provisioning tests. Never stop or modify a user's SSH service."""
import fcntl
import base64
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import secrets
import socket
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('setup_agent', ROOT / 'realme-agent/setup_agent.py')
setup = importlib.util.module_from_spec(spec)
spec.loader.exec_module(setup)
SOURCE = (ROOT / 'realme-agent/agent.py').read_bytes()


class AgentSetupTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='localls-setup-')
        self.base = Path(self.temp.name) / 'home-server-agent'
        self.base.mkdir()
        with socket.socket() as sock:
            sock.bind(('127.0.0.1', 0))
            self.port = sock.getsockname()[1]
        self.token = secrets.token_urlsafe(32)
        self.processes = []
        original = subprocess.Popen

        def start(*args, **kwargs):
            process = original(*args, **kwargs)
            self.processes.append(process)
            return process
        self.patch = patch.object(setup.subprocess, 'Popen', side_effect=start)
        self.patch.start()

    def tearDown(self):
        self.patch.stop()
        for process in self.processes:
            if process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait()
        self.temp.cleanup()

    def config(self, **values):
        cfg = {'host': '127.0.0.1', 'port': self.port, 'token': self.token, 'tls': True,
               'unknownField': 'preserve'}
        cfg.update(values)
        path = self.base / 'config.json'
        path.write_text(json.dumps(cfg, indent=2))
        path.chmod(0o600)
        return self.digest(path)

    def digest(self, path):
        return hashlib.sha256(path.read_bytes()).hexdigest()

    def prepare(self):
        return setup.prepare(self.base, SOURCE, default_host='127.0.0.1', default_port=self.port)

    def test_new_install_and_repeat_preserve_secrets_and_do_not_start_duplicate(self):
        self.assertEqual('started', self.prepare())
        paths = [self.base / name for name in ('config.json', 'agent-key.pem', 'agent-cert.pem', 'agent.py')]
        snapshots = [self.digest(path) for path in paths]
        process = next(p for p in self.processes if p.poll() is None)
        count = sum(p.poll() is None for p in self.processes)
        self.assertEqual('existing', self.prepare())
        self.assertEqual(count, sum(p.poll() is None for p in self.processes))
        self.assertIsNone(process.poll())
        self.assertEqual(snapshots, [self.digest(path) for path in paths])
        self.assertEqual(0o600, (paths[0].stat().st_mode & 0o777))
        self.assertEqual(0o600, (paths[1].stat().st_mode & 0o777))

    def test_stopped_agent_upgrade_keeps_existing_token_config_and_tls_pair(self):
        old_config = self.config()
        self.prepare()
        cert, key = self.digest(self.base / 'agent-cert.pem'), self.digest(self.base / 'agent-key.pem')
        agent = next(p for p in self.processes if p.poll() is None)
        agent.terminate(); agent.wait(timeout=5)
        (self.base / 'agent.py').write_bytes(b'# old stopped implementation\n')
        self.assertEqual('started', self.prepare())
        self.assertEqual(old_config, self.digest(self.base / 'config.json'))
        self.assertEqual(cert, self.digest(self.base / 'agent-cert.pem'))
        self.assertEqual(key, self.digest(self.base / 'agent-key.pem'))
        self.assertEqual(hashlib.sha256(SOURCE).hexdigest(), self.digest(self.base / 'agent.py'))

    def test_incomplete_pair_is_never_replaced(self):
        previous = self.config()
        key = self.base / 'agent-key.pem'
        key.write_bytes(secrets.token_bytes(64))
        snapshot = self.digest(key)
        with self.assertRaisesRegex(setup.SetupFailure, 'TLS_INCOMPLETE'):
            self.prepare()
        self.assertEqual(previous, self.digest(self.base / 'config.json'))
        self.assertEqual(snapshot, self.digest(key))
        self.assertFalse((self.base / 'agent-cert.pem').exists())

    def test_busy_port_fails_without_creating_certificate_or_replacing_config(self):
        previous = self.config()
        with socket.socket() as sock:
            sock.bind(('127.0.0.1', self.port)); sock.listen()
            with self.assertRaisesRegex(setup.SetupFailure, 'PORT_BUSY'):
                self.prepare()
        self.assertEqual(previous, self.digest(self.base / 'config.json'))
        self.assertFalse((self.base / 'agent-key.pem').exists())
        self.assertFalse((self.base / 'agent.py').exists())

    def test_legacy_http_is_not_silently_migrated(self):
        previous = self.config(tls=False, allow_legacy_http=True)
        with self.assertRaisesRegex(setup.SetupFailure, 'LEGACY'):
            self.prepare()
        self.assertEqual(previous, self.digest(self.base / 'config.json'))
        self.assertFalse((self.base / 'agent-cert.pem').exists())

    def test_concurrent_prepare_is_refused_before_configuration_changes(self):
        previous = self.config()
        with (self.base / '.wizard.lock').open('w') as lock:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            with self.assertRaisesRegex(setup.SetupFailure, 'BUSY'):
                self.prepare()
        self.assertEqual(previous, self.digest(self.base / 'config.json'))
        self.assertFalse((self.base / 'agent-cert.pem').exists())

    def test_symlink_configuration_is_refused(self):
        other = Path(self.temp.name) / 'private.json'
        other.write_text('{}')
        (self.base / 'config.json').symlink_to(other)
        with self.assertRaisesRegex(setup.SetupFailure, 'UNSAFE_FILE'):
            self.prepare()
        self.assertEqual(hashlib.sha256(b'{}').hexdigest(), self.digest(other))

    def test_cli_repeat_returns_only_the_public_ready_marker(self):
        self.config()
        self.prepare()
        command = "import base64\nAGENT_SOURCE=base64.b64decode('" + base64.b64encode(SOURCE).decode() + "')\n"
        command += (ROOT / 'realme-agent/setup_agent.py').read_text()
        result = subprocess.run([sys.executable, '-c', command], env={**os.environ, 'HOME': self.temp.name},
                                stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, timeout=10)
        self.assertEqual(0, result.returncode)
        self.assertEqual('AGENT_READY=1', result.stdout.strip())
        self.assertEqual('', result.stderr)
        self.assertNotIn(self.token, result.stdout + result.stderr)


if __name__ == '__main__':
    unittest.main(verbosity=2)
