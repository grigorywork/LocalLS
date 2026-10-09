#!/usr/bin/env python3
"""Isolated real-agent HTTPS smoke tests; never operate a user's SSH service."""
import http.client, json, os, pathlib, secrets, socket, ssl, subprocess, tempfile, time, unittest
AGENT = pathlib.Path(__file__).resolve().parents[1] / 'realme-agent/agent.py'
class AgentSecurityTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(prefix='localls-agent-test-')
        self.root = pathlib.Path(self.tmp.name)
        self.token = secrets.token_urlsafe(32)
        with socket.socket() as s:
            s.bind(('127.0.0.1',0)); self.port = s.getsockname()[1]
        self.cfg = {'host':'127.0.0.1','port':self.port,'token':self.token}
        self.proc = None
    def tearDown(self):
        if self.proc:
            self.proc.terminate()
            try: self.proc.communicate(timeout=5)
            except subprocess.TimeoutExpired: self.proc.kill(); self.proc.communicate()
        self.tmp.cleanup()
    def write(self):
        p=self.root/'config.json';p.write_text(json.dumps(self.cfg));p.chmod(0o600)
    def run_failure(self):
        self.write();p=subprocess.run(['python3',str(AGENT)],env={**os.environ,'LOCALLS_AGENT_CONFIG_DIR':str(self.root)},capture_output=True,text=True,timeout=5)
        self.assertNotEqual(p.returncode,0);self.assertNotIn(self.token,p.stdout+p.stderr)
        return p.stderr
    def test_missing_certificate_has_no_http_fallback(self):
        self.assertIn('No HTTP fallback',self.run_failure())
    def test_legacy_http_requires_explicit_opt_in(self):
        self.cfg['tls']=False;self.assertIn('explicit allow_legacy_http',self.run_failure())
    def test_weak_private_file_permissions_are_rejected(self):
        (self.root/'agent-cert.pem').write_text('not-a-cert');(self.root/'agent-key.pem').write_text('not-a-key');(self.root/'agent-key.pem').chmod(0o644)
        self.assertIn('permissions 600',self.run_failure())
    def test_actual_https_auth_routes_and_token_preservation(self):
        subprocess.run(['openssl','req','-x509','-newkey','rsa:3072','-nodes','-keyout',str(self.root/'agent-key.pem'),'-out',str(self.root/'agent-cert.pem'),'-days','2','-subj','/CN=localhost','-addext','subjectAltName=DNS:localhost'],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
        (self.root/'agent-key.pem').chmod(0o600);self.cfg['tls']=True;self.write();before=(self.root/'config.json').read_bytes()
        self.proc=subprocess.Popen(['python3',str(AGENT)],env={**os.environ,'LOCALLS_AGENT_CONFIG_DIR':str(self.root)},stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
        context=ssl.create_default_context(cafile=str(self.root/'agent-cert.pem'))
        def request(path,token=None,method='GET'):
            c=http.client.HTTPSConnection('localhost',self.port,context=context,timeout=3)
            c.request(method,path,headers={'Authorization':'Bearer '+token} if token else {});r=c.getresponse();body=r.read();code=r.status;c.close();self.assertNotIn(self.token.encode(),body);return code,body
        for _ in range(30):
            try: code,body=request('/status',self.token);break
            except ConnectionRefusedError:time.sleep(.1)
        else:self.fail('Agent did not start')
        self.assertEqual(code,200);self.assertEqual(json.loads(body)['agent'],'0.3')
        self.assertEqual(request('/status')[0],401);self.assertEqual(request('/status',secrets.token_urlsafe(32))[0],401)
        self.assertEqual(request('/not-an-action',self.token,'POST')[0],404)
        self.assertEqual((self.root/'config.json').read_bytes(),before)
        self.proc.terminate();stdout,stderr=self.proc.communicate(timeout=5);self.proc=None;self.assertNotIn(self.token,stdout+stderr)
if __name__=='__main__':unittest.main(verbosity=2)
