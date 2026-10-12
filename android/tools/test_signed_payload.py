import hashlib
from pathlib import Path
import tempfile
import unittest
import zipfile
from verify_signed_payload import EXPECTED_CERT, verify


class SigningProofTest(unittest.TestCase):
    def test_signature_entries_may_change_but_payload_must_match(self):
        self.check()

    def test_modified_payload_is_rejected(self):
        with self.assertRaisesRegex(ValueError, 'payload differs'):
            self.check(tamper=True)

    def test_wrong_certificate_is_rejected(self):
        with self.assertRaisesRegex(ValueError, 'certificate differs'):
            self.check(certificate='0' * 64)

    def test_report_for_different_bytes_is_rejected(self):
        with self.assertRaisesRegex(ValueError, 'these APK bytes'):
            self.check(wrong_hash=True)

    def check(self, tamper=False, certificate=EXPECTED_CERT, wrong_hash=False):
        with tempfile.TemporaryDirectory() as directory:
            preview, signed = Path(directory) / 'preview.apk', Path(directory) / 'signed.apk'
            for path, signature in ((preview, b'ci-key'), (signed, b'original-key')):
                with zipfile.ZipFile(path, 'w') as archive:
                    archive.writestr('classes.dex', b'changed' if tamper and path == signed else b'tested-code')
                    archive.writestr('assets/agent.py', b'tested-agent')
                    archive.writestr('META-INF/CERT.RSA', signature)
                    archive.writestr('META-INF/library.properties', b'preserved-library-metadata')
            def report(path):
                raw = path.read_bytes()
                return dict(sha256=hashlib.sha256(raw).hexdigest(), bytes=len(raw),
                            certificateSha256=certificate, package='com.bitpoint.homeservercontrol',
                            versionName='0.9.6', versionCode=15, minSdk=24, targetSdk=37,
                            manifest='valid', zipAlignment='valid', signature='verified for API 24–36')
            before, after = report(preview), report(signed)
            if wrong_hash:
                after['sha256'] = '0' * 64
            result = verify(preview, signed, before, after)
            self.assertTrue(result['payloadMatchesTestedPreview'])
            self.assertEqual(3, result['payloadEntries'])


if __name__ == '__main__':
    unittest.main(verbosity=2)
