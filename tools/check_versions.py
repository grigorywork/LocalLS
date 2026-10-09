"""Verify all client version metadata against the unified release version."""
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
android = (root / 'android/app/build.gradle.kts').read_text(encoding='utf-8')
assert f'versionName = "{version}"' in android, 'Android version mismatch'
assert int(re.search(r'versionCode\s*=\s*(\d+)', android)[1]) > 13, 'Android update code must increase'
spec = (root / 'linux/tools/localls.spec').read_text(encoding='utf-8')
assert re.search(r'^Version:\s*' + re.escape(version) + r'$', spec, re.M), 'RPM version mismatch'
print('Unified client version:', version)
