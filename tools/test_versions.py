"""Platform releases may differ, but each package, lock, UI and spec stays pinned."""
from pathlib import Path
import json, tempfile, unittest
from check_versions import verify_pc_versions

class ReleaseVersionsTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root/'VERSION').write_text('0.9.5\n')
        for platform, version in [('desktop', '1.33.0'), ('linux', '0.9.5')]:
            base = self.root/platform
            (base/'src').mkdir(parents=True)
            (base/'package.json').write_text(json.dumps({'version': version}))
            (base/'package-lock.json').write_text(json.dumps({'version': version, 'packages': {'': {'version': version}}}))
            (base/'src/index.html').write_text('LocalLS ' + version)
        (self.root/'desktop/release.json').write_text(json.dumps({'platform': 'windows', 'version': '1.33.0'}))
        (self.root/'linux/tools').mkdir()
        (self.root/'linux/tools/localls.spec').write_text('Version: 0.9.5\n')

    def test_sequential_windows_then_fedora_releases_keep_independent_versions(self):
        self.assertEqual({'desktop': '1.33.0', 'linux': '0.9.5'}, verify_pc_versions(self.root))

    def test_windows_lock_or_ui_mismatch_is_rejected(self):
        base = self.root/'desktop'
        (base/'package-lock.json').write_text(json.dumps({'version': '1.32.0', 'packages': {'': {'version': '1.33.0'}}}))
        with self.assertRaises(AssertionError): verify_pc_versions(self.root)
        (base/'package-lock.json').write_text(json.dumps({'version': '1.33.0', 'packages': {'': {'version': '1.33.0'}}}))
        (base/'src/index.html').write_text('LocalLS 0.9.5')
        with self.assertRaises(AssertionError): verify_pc_versions(self.root)

    def test_fedora_spec_mismatch_is_still_rejected(self):
        (self.root/'linux/tools/localls.spec').write_text('Version: 1.33.0\n')
        with self.assertRaises(AssertionError): verify_pc_versions(self.root)

    def test_wrong_platform_or_invalid_release_version_is_rejected(self):
        p = self.root/'desktop/release.json'
        for value in [{'platform': 'fedora', 'version': '1.33.0'}, {'platform': 'windows', 'version': '../1.33'}]:
            p.write_text(json.dumps(value))
            with self.assertRaises(AssertionError): verify_pc_versions(self.root)

if __name__ == '__main__': unittest.main()
