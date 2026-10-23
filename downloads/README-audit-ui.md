# LocalLS 0.11.21 — audit fixes and interface screenshots

Five reproduced defects fixed:

1. Reapplying the icon style no longer expands controls until the file list collapses. Native tests retain at least 80 dp for lists in short windows and 48 dp touch targets.
2. A late saved-identity callback cannot reopen an old server's Files button while a new scan is in progress. QR decode/identity callbacks are bound to the current flow; stale results cannot change the selected profile.
3. A lost foreground-service dispatch no longer disables QR startup permanently. The shared timeout rule permits retry only while the service is neither running nor starting. Recent and active dispatches remain protected from duplicate launches.
4. A dense reply QR that decoded at 720 px could fail after ImageView downscaling. It is now encoded for the actual displayed pixel size, with exact round-trip verification by both original decoders. Native tests decode the original complete payload at 320 and 360 dp window widths. Pin validation, owner approval, matching codes and permanent key binding are unchanged.

5. Small nonempty files were shown as 0.0 MB. File rows now use appropriate Android byte/KB/MB units and distinguish unknown from empty sizes. File data is unchanged.

QR actions also have the appropriate scan/key/refresh/power/back icons. Existing client graphs, server-only home, selection, transfer recovery, integrity and collision handling remain available. No VPN added.

Validation: 306 JVM + 19 Python checks, zero failures/errors/skips; all previous 299 test references and 102/102 function references passed. Full audits, debug/release lint, signed release and instrumentation APK builds passed. Remaining lint warnings are documented locally and were not suppressed. Hardware camera and physical phones were not tested.

[Five native interface screenshots](interface-audit-20261023/README.md) show actual Android views with demonstration data: client home, server home, selected phone files, selected server files, server QR. The QR belongs to a closed local test fixture.

Version 0.11.21 / code 41; package com.bitpoint.homeservercontrol. Install over the existing app without clearing data. Same-version rebuild, not a new release. APK SHA-256: 4d5965a9893b7100e26e582af6c0f8de81123f29eeebd2693918a89c8ed9e13c

Original signing certificate SHA-256: a2230c3032135def93cd5e8224ca3e26ad8e98f294b5e372a33b1e468caa221a

Existing signing/access keys and settings are preserved. This artifact-only branch adds APK/checksum/screenshots/documentation to the unchanged release source tree. Functional source changes remain in the local worktree and a private source snapshot; they are not claimed to be committed here. Release branch and published release assets are unchanged.
