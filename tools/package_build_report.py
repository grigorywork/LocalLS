"""Write a report after the build, package validation and runtime gate succeed."""
from pathlib import Path
import argparse, hashlib, json, os, platform

parser = argparse.ArgumentParser()
parser.add_argument('client', choices=['desktop', 'linux'])
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
project = root / args.client
version = (root / 'VERSION').read_text(encoding='utf-8').strip()
name = f'LocalLS-{version}-Windows-x64-Setup.exe' if args.client == 'desktop' else f'LocalLS-{version}-Fedora-x86_64.rpm'
artifact = project / 'artifacts' / name
assert artifact.is_file() and artifact.stat().st_size > 1024 * 1024
metadata = {
    'version': version, 'platform': 'Windows x64' if args.client == 'desktop' else 'Fedora x86_64',
    'filename': name, 'bytes': artifact.stat().st_size,
    'sha256': hashlib.sha256(artifact.read_bytes()).hexdigest(),
    'sourceCommit': os.environ.get('GITHUB_SHA', 'local'),
    'buildHost': platform.platform(), 'publisherSigned': False,
    'checks': ['unit tests', 'package validation', 'actual Electron UI/SSH/SFTP fixture smoke'],
    'limitations': ['physical user devices and production network not tested', 'publisher signature absent'],
}
out = project / 'artifacts'
(out / 'BUILD_REPORT.json').write_text(json.dumps(metadata, indent=2) + '\n', encoding='utf-8')
(out / 'BUILD_REPORT.md').write_text(
    f'# LocalLS {version} — {metadata["platform"]}\n\n'
    f'Package: `{name}`\n\nBytes: {metadata["bytes"]}\n\nSHA-256: `{metadata["sha256"]}`\n\n'
    f'Source commit: `{metadata["sourceCommit"]}`\n\n'
    'Build, unit tests, package validation and actual Electron fixture smoke completed successfully.\n'
    'Windows runtime checks execute on the Windows CI runner; Fedora checks execute in Fedora 44.\n'
    'Production SSH/Termux, user devices and Tailscale were not tested. Packages have no publisher signature.\n',
    encoding='utf-8')
print('Verified release package:', name, metadata['sha256'])
