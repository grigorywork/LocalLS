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


def verify(apk, sdk):
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
    package = re.search(r"package: name='([^']+)'", badging).group(1)
    version_name = re.search(r"versionName='([^']+)'", badging).group(1)
    version_code = int(re.search(r"versionCode='(\d+)'", badging).group(1))
    if version_name != "0.9.5" or version_code != 14:
        raise ValueError("Unexpected Android release version")
    minimum = int(re.search(r"sdkVersion:'(\d+)'", badging).group(1))
    target = int(re.search(r"targetSdkVersion:'(\d+)'", badging).group(1))
    if package != "com.bitpoint.homeservercontrol" or minimum != 24 or target != 37:
        raise ValueError("Unexpected package/SDK configuration")
    if "launchable-activity: name='com.bitpoint.homeservercontrol.MainActivity'" not in badging:
        raise ValueError("Launcher missing")
    run(aapt, "dump", "xmltree", apk, "AndroidManifest.xml")
    signature = run(signer, "verify", "--verbose", "--min-sdk-version", "24", "--max-sdk-version", "36", apk)
    run(align, "-c", "-P", "16", "4", apk)
    return {"apk": str(apk), "bytes": apk.stat().st_size,
            "sha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
            "package": package, "versionName": version_name, "versionCode": version_code,
            "minSdk": minimum, "targetSdk": target,
            "manifest": "valid", "signature": "verified for API 24–36",
            "zipAlignment": "valid", "signatureDetails": signature.strip()}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("apk", nargs="?", default="artifacts/LocalLS-v0.9.5-debug.apk")
    parser.add_argument("--sdk", default=os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT"))
    args = parser.parse_args()
    if not args.sdk:
        parser.error("Set ANDROID_HOME or pass --sdk")
    print(json.dumps(verify(args.apk, args.sdk), indent=2, ensure_ascii=False))
