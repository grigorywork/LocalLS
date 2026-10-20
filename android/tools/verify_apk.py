#!/usr/bin/env python3
"""Verify a real Android artifact with SDK tools; emit machine-readable metadata."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import zipfile
from release_metadata import load_metadata


def check_update(previous, current):
    if previous['package'] != current['package']:
        raise ValueError("Update changes applicationId")
    if len(current['certificates']) != 1 or previous['certificates'] != current['certificates']:
        raise ValueError("Update changes signing certificate")
    if current['versionCode'] <= previous['versionCode']:
        raise ValueError("Update versionCode must exceed previous APK")


def check_release_baseline(metadata, require_release_signer, previous_apk, initial_release):
    if initial_release and (not require_release_signer or previous_apk is not None
                            or metadata.get('releaseBaselineVersionCode') != str(metadata['versionCode'])):
        raise ValueError("Initial release is allowed only for the pinned baseline version")
    if require_release_signer and previous_apk is None and not initial_release:
        raise ValueError("Release verification requires --previous-apk")


def verify(apk, sdk, require_release_signer=False, previous_apk=None, initial_release=False):
    metadata = load_metadata()
    apk = Path(apk).resolve()
    if not apk.is_file() or apk.stat().st_size == 0:
        raise ValueError("APK missing or empty")
    with zipfile.ZipFile(apk) as archive:
        required = {"AndroidManifest.xml", "classes.dex", "resources.arsc"}
        if not required.issubset(archive.namelist()):
            raise ValueError("Not an Android application package")
        if archive.testzip() is not None:
            raise ValueError("Corrupt APK entry")
    tools = Path(sdk) / "build-tools" / "36.0.0"
    suffix = ".exe" if os.name == "nt" else ""
    aapt = tools / ("aapt" + suffix)
    signer = tools / ("apksigner.bat" if os.name == "nt" else "apksigner")
    align = tools / ("zipalign" + suffix)
    def run(*args):
        return subprocess.run([str(a) for a in args], check=True, capture_output=True, text=True).stdout
    badging = run(aapt, "dump", "badging", apk)
    if require_release_signer and "application-debuggable" in badging:
        raise ValueError("Release APK must not be debuggable")
    package = re.search(r"package: name='([^']+)'", badging).group(1)
    version_name = re.search(r"versionName='([^']+)'", badging).group(1)
    version_code = int(re.search(r"versionCode='(\d+)'", badging).group(1))
    if version_name != metadata["versionName"] or version_code != metadata["versionCode"]:
        raise ValueError("Unexpected Android release version")
    minimum = int(re.search(r"sdkVersion:'(\d+)'", badging).group(1))
    target = int(re.search(r"targetSdkVersion:'(\d+)'", badging).group(1))
    if package != "com.bitpoint.homeservercontrol" or minimum != 24 or target != 37:
        raise ValueError("Unexpected package/SDK configuration")
    if "launchable-activity: name='com.bitpoint.homeservercontrol.MainActivity'" not in badging:
        raise ValueError("Launcher missing")
    run(aapt, "dump", "xmltree", apk, "AndroidManifest.xml")
    signature = run(signer, "verify", "--verbose", "--print-certs", "--min-sdk-version", "24", "--max-sdk-version", "36", apk)
    certificates = re.findall(r"Signer #\d+ certificate SHA-256 digest: ([a-fA-F0-9]+)", signature)
    compatible = len(certificates) == 1 and certificates[0].lower() == metadata['releaseCertificateSha256']
    if require_release_signer and not compatible:
        raise ValueError("APK signature is incompatible with published LocalLS")
    check_release_baseline(metadata, require_release_signer, previous_apk, initial_release)
    if previous_apk is not None:
        old_badging = run(aapt, "dump", "badging", Path(previous_apk).resolve())
        old_signature = run(signer, "verify", "--print-certs", Path(previous_apk).resolve())
        old = {
            'package': re.search(r"package: name='([^']+)'", old_badging).group(1),
            'versionCode': int(re.search(r"versionCode='(\d+)'", old_badging).group(1)),
            'certificates': [x.lower() for x in re.findall(r"Signer #\d+ certificate SHA-256 digest: ([a-fA-F0-9]+)", old_signature)]
        }
        check_update(old, {'package': package, 'versionCode': version_code,
                          'certificates': [x.lower() for x in certificates]})
    run(align, "-c", "-P", "16", "4", apk)
    return {"apk": str(apk), "bytes": apk.stat().st_size,
            "sha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
            "package": package, "versionName": version_name, "versionCode": version_code,
            "minSdk": minimum, "targetSdk": target,
            "certificateSha256": certificates, "compatibleWithPublishedSigner": compatible,
            "updateFrom": str(previous_apk) if previous_apk is not None else None,
            "updateCompatibilityVerified": previous_apk is not None,
            "initialRelease": initial_release,
            "manifest": "valid", "signature": "verified for API 24–36",
            "zipAlignment": "valid", "signatureDetails": signature.strip()}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("apk", nargs="?", default="app/build/outputs/apk/debug/app-debug.apk")
    parser.add_argument("--sdk", default=os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT"))
    parser.add_argument("--require-release-signer", action="store_true")
    parser.add_argument("--previous-apk")
    parser.add_argument("--initial-release", action="store_true")
    args = parser.parse_args()
    if not args.sdk:
        parser.error("Set ANDROID_HOME or pass --sdk")
    print(json.dumps(verify(args.apk, args.sdk, args.require_release_signer, args.previous_apk, args.initial_release), indent=2, ensure_ascii=False))
