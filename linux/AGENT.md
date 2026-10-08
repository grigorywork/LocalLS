# LocalLS Fedora

Build a genuine Fedora x86_64 RPM and verify it in Fedora. Work in linux/ only;
preserve Android and Windows sources and their published artifacts.
Include SSH/SFTP client and graphical setup of the system OpenSSH server.
Use existing sshd.service and configuration; never add a competing startup mechanism.
Privileged actions require a native confirmation and OS polkit authentication.
Use fixed arguments and a root-owned installed helper; never accept arbitrary commands.
Keep passwords/tokens out of argv, logs, profiles and plaintext persistent storage.
Validate SSH host keys before sending passwords. Renderer remains isolated and sandboxed.
Handle GNOME without a tray extension: minimizing must keep the window recoverable.
Do not ship --no-sandbox. Record actual RPM install/runtime checks and limitations.
