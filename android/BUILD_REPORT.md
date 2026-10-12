# LocalLS Android 0.9.6 — cloud build verification

Status: preview successfully built and verified in GitHub Actions. Publication awaits the retained original signing certificate and release payload verification.

- Tested application source: 2021449b3ccdfe2dd46096ac0b876345678333d1.
- Pull request: https://github.com/grigorywork/LocalLS/pull/7 .
- Successful cloud build: https://github.com/grigorywork/LocalLS/actions/runs/38210324771 .
- versionName 0.9.6; versionCode 15; package com.bitpoint.homeservercontrol.
- minSdk 24; targetSdk 37; Gradle 9.6.0; AGP 9.4.1; JDK 17.
- Project audit: 0 errors, 0 warnings. Architecture audit: 59 classes, 5 modules, 0 errors.
- 14 passing JVM/Robolectric tests: core 4, transport 3, app 7; zero failures/skips.
- Four actual HTTPS Agent security tests and five isolated Linux wizard provisioning tests passed.
- 27 UI render images, including the wizard at three themes and three font scales.
- Android Lint: 16 warnings, zero errors. Warnings include dependency updates, existing layout/i18n/RTL issues and intentional Termux paths.
- Real debug application and instrumentation APKs built. Device instrumentation was not executed.
- APK manifest, ZIP integrity, signature on API 24–36 and 16 KiB alignment passed SDK verification.

Preview: LocalLS-v0.9.6-preview-debug.apk; 1443419 bytes.
SHA-256: 10d51e0a46ab501473e3ce68692b7b0528a16c81aebd2fe9f8811e65323014f5.
Preview certificate SHA-256: a678ef20d90050309c211cbdd2b8a920dce664863a1f1565353dd977a2da8cd1.
The CI certificate is not the original installed certificate. Do not use this preview to update 0.9.5.

The compatible release must retain certificate SHA-256
3b4c5a568105f73bf999f288a041255a57ee5cfc83253b99e5f2c07789f1f9ba.
Signing occurs only in the cloud environment holding that key. No private signing key or password is exported to CI or GitHub. A separate release verification workflow verifies metadata, original certificate and every payload entry against the tested preview, then publishes SIGNING_VERIFICATION_0.9.6.json in android-v0.9.6.

What the new wizard verifies: Termux installation/signature/provider, persisted SAF access, RUN_COMMAND permission, host-key pinning before credentials, actual SSH authentication, a separate key-only connection, HTTPS certificate pinning and authenticated agent status. Repeated provisioning preserves active TLS processes, configuration, tokens/certificates and existing SSH files. Password authentication is not automatically disabled. Existing HTTP agents require a separate migration.

Limitations: no physical-phone installation/update, actual Termux/OpenSSH wizard run, reboot/battery restrictions or real-network smoke test was performed for 0.9.6. Robolectric screenshots are not emulator/device tests. Linux provisioning fixtures do not prove Android permissions or vendor background behavior. Nonstandard ListenAddress/sshd command-line overrides require device validation. File transfer improvements remain issue #4; Windows and Fedora remain 0.9.5.

Historical 0.9.5 report: docs/BUILD_REPORT_v0.9.5_BASELINE.md. Old releases, tags and installer bytes remain preserved.