"""Non-secret metadata shared by the APK verifier and repository version check."""
from pathlib import Path
import re


def load_metadata(path=None):
    path = Path(path) if path else Path(__file__).resolve().parents[1] / 'release.properties'
    values = {}
    for line in path.read_text(encoding='utf-8').splitlines():
        if not line.strip() or line.startswith('#'):
            continue
        key, value = line.split('=', 1)
        if key in values:
            raise ValueError('Duplicate release metadata')
        values[key] = value
    if not re.fullmatch(r'\d+\.\d+(?:\.\d+)?', values['versionName']):
        raise ValueError('Invalid Android version')
    values['versionCode'] = int(values['versionCode'])
    values['previousVersionCode'] = int(values['previousVersionCode'])
    if not 0 < values['previousVersionCode'] < values['versionCode'] <= 2100000000:
        raise ValueError('Android update code must increase')
    if not re.fullmatch(r'[a-f0-9]{64}', values['releaseCertificateSha256']):
        raise ValueError('Invalid release certificate fingerprint')
    return values
