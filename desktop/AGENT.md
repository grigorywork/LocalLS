# LocalLS Desktop — Windows client

First release: Windows 10/11 x64, SSH/SFTP client. Work in this separate directory.
Preserve all Android projects and their APKs. Keep the existing Termux/server agent protocol.
Do not install or start another server or competing sshd boot mechanism.

Validate host keys before sending passwords. A changed key must block authentication.
Public profiles must be compatible with Android localls-server-v1 and contain no secrets.
Passwords and agent tokens may be persisted only using OS-backed Electron safeStorage;
otherwise keep them in memory. Never print actual credentials or raw credential-bearing errors.

Renderer has no Node integration. Use context isolation, sandbox, CSP, validated IPC,
textContent for filenames, and main-process native confirmations for destructive actions.
Transfers are streamed, cancellable and remain active in the tray. No fake speed/VPN status.

Ship a genuine Electron Builder Windows installer, verify PE/artifacts/hashes and report
exactly which runtime tests were executed. Do not claim a Windows runtime test from Linux alone.
