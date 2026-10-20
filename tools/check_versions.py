"""Verify desktop versions and independent Android release metadata."""
from pathlib import Path
import json, re

root = Path(__file__).resolve().parents[1]
version = (root / 'VERSION').read_text(encoding='utf-8').strip()
assert re.fullmatch(r'\d+\.\d+\.\d+', version), 'Invalid release version'
for platform in ('desktop', 'linux'):
    package = json.loads((root / platform / 'package.json').read_text(encoding='utf-8'))
    lock = json.loads((root / platform / 'package-lock.json').read_text(encoding='utf-8'))
    assert package['version'] == lock['version'] == lock['packages']['']['version'] == version, platform
    assert version in (root / platform / 'src/index.html').read_text(encoding='utf-8'), platform + ' UI'
import sys
sys.path.insert(0, str(root / 'android/tools'))
from release_metadata import load_metadata
android = load_metadata()
spec = (root / 'linux/tools/localls.spec').read_text(encoding='utf-8')
assert re.search(r'^Version:\s*' + re.escape(version) + r'$', spec, re.M), 'RPM version mismatch'
print('Windows/Fedora:', version, '| Android:', android['versionName'], 'code', android['versionCode'])
