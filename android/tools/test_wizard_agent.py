#!/usr/bin/env python3
"""Real isolated provisioning, TLS and recovery tests; never operate system sshd."""
import hashlib
import http.client
import importlib.util
import json
import os
from pathlib import Path
import secrets
import socket
import ssl
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location('wizard', ROOT / 'realme-agent/wizard_install.py')
WIZARD = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(WIZARD)
SOURCE = ROOT / 'realme-agent/agent.py'


class ProvisioningTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(prefix='localls-wizard-test-')
        self.home = Path(self.tmp.name)
        with socket.socket() as listener:
            listener.bind(('127.0.0.1', 0))
            self.port = listener.getsockname()[1]
        self.base = self.home / 'home-server-agent'
        self.process = None

    def tearDown(self):
        if self.process:
            self.process.terminate()
            self.process.wait(timeout=5)
        self.tmp.cleanup()

    def config(self, tls=True):
        self.base.mkdir(exist_ok=True)
        cfg = {'host': '127.0.0.1', 'port': self.port, 'token': secrets.token_urlsafe(32), 'tls': tls,
               'tls_cert': str(self.base / 'agent-cert.pem'), 'tls_key': str(self.base / 'agent-key.pem')}
        file = self.base / 'config.json'
        file.write_text(json.dumps(cfg))
        file.chmod(0o600)
        return cfg

    def prepare(self):
        return WIZARD.prepare(self.home, SOURCE, self.port, start=False)

    def test_repeat_preserves_configuration_certificate_token_source_and_ssh_files(self):
        ssh = self.home / '.ssh'
        ssh.mkdir()
        (ssh / 'authorized_keys').write_text('preserved existing keys\n')
        (self.home / 'sshd_config').write_text('Port 8022\nPasswordAuthentication yes\n')
        self.prepare()
        paths = [self.base / name for name in ('config.json', 'agent-key.pem', 'agent-cert.pem', 'agent.py')]
        paths += [ssh / 'authorized_keys', self.home / 'sshd_config']
        before = {p: p.read_bytes() for p in paths}
        self.prepare()
        self.assertEqual(before, {p: p.read_bytes() for p in paths})
        self.assertEqual((self.base / 'config.json').stat().st_mode & 0o777, 0o600)
        self.assertEqual((self.base / 'agent-key.pem').stat().st_mode & 0o777, 0o600)

    def test_existing_http_agent_is_refused_without_rewriting_any_configuration(self):
        self.config(tls=False)
        previous = (self.base / 'config.json').read_bytes()
        with self.assertRaises(WIZARD.Refused) as error:
            self.prepare()
        self.assertEqual(error.exception.code, 51)
        self.assertEqual(previous, (self.base / 'config.json').read_bytes())
        self.assertFalse((self.base / 'agent.py').exists())

    def test_symlink_and_partial_certificate_are_refused_and_target_preserved(self):
        outside = self.home / 'preserved'
        outside.write_text('preserved')
        self.base.mkdir()
        (self.base / 'config.json').symlink_to(outside)
        with self.assertRaises(WIZARD.Refused):
            self.prepare()
        self.assertEqual(outside.read_text(), 'preserved')
        (self.base / 'config.json').unlink()
        (self.base / 'agent-key.pem').write_text('preserved partial key')
        with self.assertRaises(WIZARD.Refused) as error:
            self.prepare()
        self.assertEqual(error.exception.code, 54)
        self.assertEqual((self.base / 'agent-key.pem').read_text(), 'preserved partial key')

    def test_running_tls_agent_is_reused_without_replacing_process_or_files(self):
        self.prepare()
        cfg = json.loads((self.base / 'config.json').read_text())
        cfg['host'] = '127.0.0.1'
        (self.base / 'config.json').write_text(json.dumps(cfg))
        self.process = subprocess.Popen(['python3', str(self.base / 'agent.py')],
                                        env={**os.environ, 'LOCALLS_AGENT_CONFIG_DIR': str(self.base)},
                                        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        import time
        for _ in range(50):
            if WIZARD.listening('127.0.0.1', self.port):
                break
            time.sleep(.1)
        before = {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in self.base.iterdir() if p.is_file()}
        port, reused = WIZARD.prepare(self.home, SOURCE, self.port)
        self.assertTrue(reused)
        self.assertEqual(port, self.port)
        self.assertIsNone(self.process.poll())
        self.assertEqual(before, {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in self.base.iterdir() if p.is_file()})
        context = ssl.create_default_context(cafile=str(self.base / 'agent-cert.pem'))
        client = http.client.HTTPSConnection('localhost', self.port, context=context, timeout=3)
        client.request('GET', '/status', headers={'Authorization': 'Bearer ' + cfg['token']})
        response = client.getresponse()
        self.assertEqual(response.status, 200)
        body = response.read()
        self.assertNotIn(cfg['token'].encode(), body)
        self.assertEqual(json.loads(body)['agent'], '0.3')
        client.close()

    def test_invalid_port_and_weak_private_file_permissions_fail_closed(self):
        cfg = self.config()
        cfg['port'] = True
        (self.base / 'config.json').write_text(json.dumps(cfg))
        with self.assertRaises(WIZARD.Refused) as error:
            self.prepare()
        self.assertEqual(error.exception.code, 52)
        (self.base / 'config.json').chmod(0o644)
        with self.assertRaises(WIZARD.Refused) as error:
            self.prepare()
        self.assertEqual(error.exception.code, 54)


if __name__ == '__main__':
    unittest.main(verbosity=2)
