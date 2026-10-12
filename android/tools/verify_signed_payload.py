#!/usr/bin/env python3
"""Compare a signed release with the exact tested CI payload; never handle keys."""
import hashlib
import json
from pathlib import Path
import sys
import zipfile

EXPECTED_CERT = "3b4c5a568105f73bf999f288a041255a57ee5cfc83253b99e5f2c07789f1f9ba"


def payload(path):
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)) or archive.testzip() is not None:
            raise ValueError("Invalid or ambiguous ZIP entries")
        def signature(name):
            return name == "META-INF/MANIFEST.MF" or (
                name.startswith("META-INF/") and name.count("/") == 1
                and name.endswith((".SF", ".RSA", ".DSA", ".EC")))
        return {name: archive.read(name) for name in names if not signature(name)}


def verify(preview, signed, preview_report, signed_report):
    for path, report in ((preview, preview_report), (signed, signed_report)):
        raw = Path(path).read_bytes()
        if report["sha256"] != hashlib.sha256(raw).hexdigest() or report["bytes"] != len(raw):
            raise ValueError("Verification report does not describe these APK bytes")
        for key, value in {"package": "com.bitpoint.homeservercontrol", "versionName": "0.9.6",
                           "versionCode": 15, "minSdk": 24, "targetSdk": 37,
                           "manifest": "valid", "zipAlignment": "valid",
                           "signature": "verified for API 24–36"}.items():
            if report.get(key) != value:
                raise ValueError("Unexpected APK metadata: " + key)
    if signed_report.get("certificateSha256") != EXPECTED_CERT:
        raise ValueError("Release certificate differs from the installed 0.9.4/0.9.5 certificate")
    before, after = payload(preview), payload(signed)
    if before != after:
        raise ValueError("Release payload differs from the tested preview")
    return {"status": "verified", "payloadEntries": len(before),
            "payloadMatchesTestedPreview": True, "signingCompatibleWith095": True,
            "previewSha256": preview_report["sha256"], "release": signed_report,
            "physicalDeviceUpdateTested": False}


if __name__ == "__main__":
    preview, signed, preview_json, signed_json = sys.argv[1:]
    print(json.dumps(verify(preview, signed, json.loads(Path(preview_json).read_text()),
                            json.loads(Path(signed_json).read_text())), indent=2))
