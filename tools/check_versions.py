"""Verify all client version metadata against the unified release version."""
from pathlib import Path
import json, re

root = Path(__file__).resolve().parents[1]
version = (root / 'VERSION').read_text(encoding='utf-8').strip()
assert re.fullmatch(r'\d+\.\d+\.\d+', version), 'Invalid release version'
manifest = json.loads((root / 'versions.json').read_text(encoding='utf-8')) if (root / 'versions.json').exists() else {}
for platform in ('desktop', 'linux'):
    expected = manifest.get('windows' if platform == 'desktop' else 'fedora', {}).get('version', version)
    package = json.loads((root / platform / 'package.json').read_text(encoding='utf-8'))
    lock = json.loads((root / platform / 'package-lock.json').read_text(encoding='utf-8'))
    assert package['version'] == lock['version'] == lock['packages']['']['version'] == expected, platform
    assert expected in (root / platform / 'src/index.html').read_text(encoding='utf-8'), platform + ' UI'
android = (root / 'android/app/build.gradle.kts').read_text(encoding='utf-8')
android_version = manifest.get("android", {}).get("version", version)
assert f'versionName = "{android_version}"' in android, 'Android version mismatch'
assert int(re.search(r'versionCode\s*=\s*(\d+)', android)[1]) > 13, 'Android update code must increase'
spec = (root / 'linux/tools/localls.spec').read_text(encoding='utf-8')
assert re.search(r'^Version:\s*' + re.escape(manifest.get('fedora', {}).get('version', version)) + r'$', spec, re.M), 'RPM version mismatch'
if manifest:
    assert int(re.search(r'versionCode\s*=\s*(\d+)', android)[1]) == manifest['android']['versionCode']
print('Client versions verified:', manifest or version)
