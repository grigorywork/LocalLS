#!/usr/bin/env python3
from pathlib import Path
import re, sys, xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
errors=[]
warnings=[]

# XML parse and collect resources/ids.
xml_files=list((ROOT/'app'/'src'/'main'/'res').rglob('*.xml'))
manifest=ROOT/'app'/'src'/'main'/'AndroidManifest.xml'
all_xml=xml_files+[manifest]
xml_text=''
for p in all_xml:
    try:
        ET.parse(p)
        xml_text += '\n' + p.read_text(encoding='utf-8', errors='ignore')
    except Exception as e:
        errors.append(f'XML parse: {p.relative_to(ROOT)}: {e}')

# Build config checks.
app_gradle=(ROOT/'app'/'build.gradle.kts').read_text(encoding='utf-8')
for needle in ['minSdk = 24','compileSdk = 37','targetSdk = 37','versionCode = 9','versionName = "0.9.0"']:
    if needle not in app_gradle:
        warnings.append(f'Expected build setting not found: {needle}')


# v0.8 transfer/background permissions.
manifest_text=manifest.read_text(encoding='utf-8', errors='ignore')
for permission in [
    'android.permission.WAKE_LOCK',
    'android.permission.ACCESS_WIFI_STATE',
    'android.permission.CHANGE_WIFI_STATE',
    'android.permission.FOREGROUND_SERVICE',
    'android.permission.FOREGROUND_SERVICE_DATA_SYNC',
]:
    if permission not in manifest_text:
        errors.append(f'Manifest permission missing: {permission}')

# Manifest class existence.
try:
    tree=ET.parse(manifest)
    ns='{http://schemas.android.com/apk/res/android}'
    pkg='com.bitpoint.homeservercontrol'
    for tag in ('activity','service','receiver','provider'):
        for el in tree.findall(f'.//{tag}'):
            n=el.get(ns+'name')
            if not n: continue
            fq=(pkg+n) if n.startswith('.') else n
            if fq.startswith(pkg+'.'):
                rel=Path(*fq.split('.')).with_suffix('.java')
                candidate=ROOT/'app'/'src'/'main'/'java'/rel
                if not candidate.exists():
                    errors.append(f'Manifest {tag} class missing: {fq}')
except Exception as e:
    errors.append(f'Manifest audit failed: {e}')

# Java -> XML id/layout reference checks.
java_files=list((ROOT/'app'/'src'/'main'/'java').rglob('*.java'))
java_text='\n'.join(p.read_text(encoding='utf-8',errors='ignore') for p in java_files)
xml_ids=set(re.findall(r'@\+id/([A-Za-z0-9_]+)', xml_text))
java_ids=set(re.findall(r'(?<!android\.)\bR\.id\.([A-Za-z0-9_]+)', java_text))
for rid in sorted(java_ids - xml_ids):
    errors.append(f'Java references missing R.id.{rid}')

layout_names={p.stem for p in (ROOT/'app'/'src'/'main'/'res'/'layout').glob('*.xml')}
java_layouts=set(re.findall(r'R\.layout\.([A-Za-z0-9_]+)', java_text))
for layout in sorted(java_layouts - layout_names):
    errors.append(f'Java references missing layout: {layout}.xml')

# Very small structural sanity checks for Java sources. This is not a compiler.
for p in java_files:
    s=p.read_text(encoding='utf-8', errors='ignore')
    if s.count('{') != s.count('}'):
        errors.append(f'Brace mismatch: {p.relative_to(ROOT)}')
    if '<<<<<<<' in s or '>>>>>>>' in s or '=======' in s:
        errors.append(f'Merge-conflict marker: {p.relative_to(ROOT)}')

# Secrets / risky strings.
text_files=[]
for base in [ROOT/'app'/'src', ROOT/'realme-agent']:
    if base.exists():
        text_files += [p for p in base.rglob('*') if p.is_file() and p.suffix.lower() in {'.java','.kt','.xml','.py','.sh','.md','.txt','.properties'}]
secret_patterns=[
    re.compile(r'(?i)\b(default_)?password\s*=\s*["\'][^"\']{4,}["\']'),
    re.compile(r'(?i)\b(default_)?agent[_ -]?token\s*=\s*["\'][A-Za-z0-9_\-]{8,}["\']'),
]
for p in text_files:
    try: s=p.read_text(encoding='utf-8',errors='ignore')
    except Exception: continue
    for rx in secret_patterns:
        if rx.search(s): warnings.append(f'Possible hardcoded secret: {p.relative_to(ROOT)}')

# Required v0.8 features should have concrete source hooks.
feature_needles={
    'transfer progress': 'transferProgressBar',
    'transfer cancellation': 'cancelCurrentTransfer',
    'mkdir': 'SftpClient.mkdir',
    'rename': 'SftpClient.rename',
    'delete': 'SftpClient.delete',
    'metric history': 'MetricHistoryStore',
    'network screen': 'NetworkActivity',
    'settings screen': 'SettingsActivity',
    'ssh keepalive': 'setServerAliveInterval',
    'transfer retry': 'MAX_TRANSFER_ATTEMPTS',
    'smoothed transfer speed': 'smoothedBytesPerSecond',
    'persistable SAF permission': 'takePersistableUriPermission',
    'config migration': 'ConfigMigrator',
    'connection fallback': 'KEY_AUTO_FALLBACK',
    'diagnostics screen': 'DiagnosticsActivity',
    'transfer log': 'TransferLogStore',
    'transfer wake guard': 'TransferPowerGuard',
    'local ssh discovery': 'LocalServerDiscovery',
    'wifi discovery UI': 'networkDiscoverButton',
    'pre-auth host key pinning': 'PinnedHostKeyRepository',
    'foreground transfer guard': 'TransferForegroundService',
    'low-ram device profile': 'DeviceProfile',
}
for feature, needle in feature_needles.items():
    if needle not in java_text and needle not in xml_text:
        errors.append(f'v0.8 feature hook missing: {feature}')


# Gradle wrapper completeness.
wrapper_jar=ROOT/'gradle'/'wrapper'/'gradle-wrapper.jar'
if not wrapper_jar.exists():
    warnings.append('gradle-wrapper.jar missing: Android Studio can sync, but ./gradlew cannot bootstrap offline')

print('=== LocalLS static audit ===')
for w in warnings: print('WARN:',w)
for e in errors: print('ERROR:',e)
print(f'Result: {len(errors)} errors, {len(warnings)} warnings')
sys.exit(1 if errors else 0)
