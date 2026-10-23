# LocalLS 0.11.21 — security, persistent QR and folder selection

[Download signed APK](LocalLS-0.11.21-security-qr.apk) · [Security report in Russian](REPORT-security-qr-20261023.md) · [Native interface screenshots](interface-security-20261023/README.md)

Successful key binding survives QR screen recreation while the server is offline. Pending approval resumes after closing the client without another scan or identity generation. First owner approval, server pinning and proof of private-key possession remain mandatory. QR expiry never revokes the approved client key. Public progress metadata cannot grant backend access. Nested QR JSON is rejected before recursive parsing.

Tap the phone folder path, or Up at its root, to choose any Android-accessible document tree. Read-only folders can be browsed and sent. The saved writable destination stays protected; read-only browsing cannot replace it. Selection/grants survive restart; picker cancellation preserves the previous folder. Android system restrictions and the dedicated SFTP sharing boundary remain enforced.

The server phone home displays a compact live status, actual port and saved-key access status. Client controls remain hidden; both client graphs remain available in client mode.

Validation: 316 JVM + 19 Python checks, zero failures/errors/skips. All previous 306 test references and 102 function references passed. Real loopback SSH attacks included forged signatures with a copied public key, unknown keys/passwords, changed server pin, stale/replayed pairing invitations, unauthorized key replacement, repeated unauthorized connections, filesystem escape/write/delete/rename attempts, forbidden channels and forwarding. The tested attacks did not bypass authorization. This is a bounded automated audit, not a guarantee against all attacks or a DDoS benchmark. Physical phones/camera were unavailable. Remaining lint warnings were not suppressed.

Five native 360×640 Android view renders use isolated demonstration data and a closed local fixture, not a physical phone.

Version 0.11.21 / code 41; package com.bitpoint.homeservercontrol. Install over the existing app without clearing data. Same-version rebuild, not a new release.

APK SHA-256: 350c1cb1c5892197f698151efebd70664edc0a7d7ae1c56086303d8f50686535

Original signing certificate SHA-256: a2230c3032135def93cd5e8224ca3e26ad8e98f294b5e372a33b1e468caa221a

Signing/access keys and settings are preserved. No VPN added. This artifact-only branch adds APK/checksum/screenshots/report/documentation to the unchanged release source tree. Current functional source changes remain in the local worktree and a private source snapshot; they are not claimed to be committed here. Release branch and published release assets are unchanged.
