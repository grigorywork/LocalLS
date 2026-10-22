# LocalLS 0.11.21 — compact home screen

Signed test rebuild: the original home screen with both live charts and action grid is restored, with smaller padding and controls, without vertical scrolling or page switching. Key-only SSH and permanent QR binding are retained. There is no SSH-password input.

Version 0.11.21 / versionCode 41. Package: com.bitpoint.homeservercontrol.

This branch preserves the original release source tree and adds only download artifacts. The APK is built from the working project with local feature/audit/UI changes; that source is not claimed to be committed here. The release branch and published release assets are unchanged.

APK SHA-256: 0aeeb19dc1b0f4a6728e0b492be50ba25905d1115972cd106124106d6e7c07eb

Signing certificate SHA-256: a2230c3032135def93cd5e8224ca3e26ad8e98f294b5e372a33b1e468caa221a

Validation: 285 JVM and 19 Python tests; no failures/errors/skipped tests. All 282 previous scenarios passed. Project/architecture audits and debug/release lint passed (warnings retained in the full local report). Release and instrumentation APKs built. Physical phone/camera tests have not been performed. The PNG is a generated UI preview with synthetic status, not a phone photograph.

Install over the current application without clearing its data. Existing keys and settings are reused. No new release/version or VPN functionality.
