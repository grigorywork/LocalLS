# LocalLS Android 0.9.5 — build verification

Status: merged application builds successfully. Original 0.9.4 signing certificate independently verified; the final APK payload matches the tested preview.

- versionName: 0.9.5; versionCode: 14.
- Package: com.bitpoint.homeservercontrol; minSdk 24; targetSdk 37.
- Gradle 9.6.0, Android Gradle Plugin 9.4.1, JDK 17.
- Local Gradle build: BUILD SUCCESSFUL; 8 passing tests; 18 rendered screenshots.
- Architecture: 56 production classes in 5 modules; zero errors.
- Lint: 15 warnings, zero errors.
- GitHub Android run 37926821271: successful build, audits, all four real Agent HTTPS security tests and APK verification.
- Merged Android device instrumentation APK builds; device instrumentation was not executed for 0.9.5.

Preview artifact: artifacts/LocalLS-v0.9.5-preview-debug.apk.
Preview SHA-256: 22d8892b725232835cd9f3037f2701386547309318c99bbad7d73c95132e1b07.
APK Manifest, ZIP integrity, signature API 24–36 and 16 KiB alignment verified with SDK tools.

Final artifact: LocalLS-v0.9.5-debug.apk; 1418730 bytes.
Final SHA-256: 4cf2a043914dba45658fd372ca23210d609eedbb9a0e4d90306f382dfd88311d.
Certificate SHA-256: 3b4c5a568105f73bf999f288a041255a57ee5cfc83253b99e5f2c07789f1f9ba.
All 72 payload entries match the tested preview byte-for-byte. APK signature API 24–36, Manifest and alignment pass. Signing compatibility with published 0.9.4 is established; installation on a physical phone was not tested.

See docs/BUILD_REPORT_v0.9.4_BASELINE.md for historical device tests of 0.9.4.
