# LocalLS Fedora

Debian support was explicitly authorized on2026-11-02. Add a separately verified Debian13/amd64 DEB using the common Linux1.42 application; preserve existing Fedora/Android/Windows artifacts and identities. Keep Debian packaging in linux/tools and document its actual runtime checks. Do not claim Debian12/Ubuntu support or a publisher signature without verification.

The user explicitly chose a built-in LocalLS server with QR and device approval on2026-11-02. This supersedes the previous system-OpenSSH wizard design. Do not alter sshd.service, system SSH configuration, user keys, firewall or automatic startup.

Build and verify a genuine Fedora x86_64 RPM. Work in linux/ only; preserve Android and Windows sources, signatures, settings and existing artifacts. Android count fix1.40/code62 was separately authorized and is frozen during Fedora work.

Use the current portable LocalLS client/server protocol and chosen interface. No VPN, old Agent UI, button-shape chooser or easter egg. Keep all original32 Linux Node test IDs/assertions. Legacy platform/helper checks remain development-only, excluded from the installed app.

Use encrypted OS key storage; never fall back to plaintext or regenerate unreadable existing identities. Pin SSH keys before authentication. Renderer stays isolated, sandboxed and without Node.

GNOME without a tray extension must retain a recoverable minimized window. Do not ship --no-sandbox. Record actual Fedora RPM install/runtime checks and physical/Wayland limitations. Never replace prior artifacts or the release branch/tag/index. Use unique build outputs and the global validation lock; no parallel builds.
