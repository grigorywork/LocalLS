"""Verify desktop versions and independent Android release metadata."""
from pathlib import Path
import json, re

def verify_pc_versions(root):
    version = (root / 'VERSION').read_text(encoding='utf-8').strip()
    assert re.fullmatch(r'\d+\.\d+\.\d+', version), 'Invalid release version'
    releases = {}
    for platform in ('desktop', 'linux'):
        release = root / platform / 'release.json'
        expected = version
        if release.is_file():
            metadata = json.loads(release.read_text(encoding='utf-8'))
            assert metadata['platform'] == {'desktop': 'windows', 'linux': 'fedora'}[platform], platform + ' release platform'
            expected = metadata['version']
            assert isinstance(expected, str) and re.fullmatch(r'\d+\.\d+\.\d+', expected), platform + ' invalid release version'
        package = json.loads((root / platform / 'package.json').read_text(encoding='utf-8'))
        lock = json.loads((root / platform / 'package-lock.json').read_text(encoding='utf-8'))
        assert package['version'] == lock['version'] == lock['packages']['']['version'] == expected, platform
        assert expected in (root / platform / 'src/index.html').read_text(encoding='utf-8'), platform + ' UI'
        releases[platform] = expected
    spec = (root / 'linux/tools/localls.spec').read_text(encoding='utf-8')
    assert re.search(r'^Version:\s*' + re.escape(releases['linux']) + r'$', spec, re.M), 'RPM version mismatch'
    return releases

if __name__ == '__main__':
    root = Path(__file__).resolve().parents[1]
    releases = verify_pc_versions(root)
    import sys
    sys.path.insert(0, str(root / 'android/tools'))
    from release_metadata import load_metadata
    android = load_metadata()
    print('Windows:', releases['desktop'], '| Fedora:', releases['linux'], '| Android:', android['versionName'], 'code', android['versionCode'])
