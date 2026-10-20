# LocalLS Android 0.9.7 (code 16)

Changes since 0.9.6:
- Connection settings can import ssh://user@host:port or sftp://user@host:port into the active Local/VPN endpoint. Bare host names use the current username and port. IPv6 literals use brackets in imported URLs.
- Public endpoint parser rejects passwords, paths, query parameters, fragments, invalid IPs and ports. Saving, checking and quick SSH commands validate hosts and usernames.
- Read-only commands show memory, network routes and Termux shared-storage availability. Android may restrict network diagnostics.
- Disk check falls back to HOME if Termux shared storage is unavailable.
- SSH remains key-only, with an explicit one-time password bootstrap to install the public key. SSH server password settings are not changed remotely.

Signing: this is a development APK signed with the existing local debug certificate. Package ID is unchanged and versionCode increases from 15 to 16. It can update the local 0.9.6 preview; it cannot update published 0.9.5 because that signing key is unavailable. The release guard continues to reject a different signer.

Named multi-server profiles are deferred until HTTPS agent credentials can be isolated per endpoint. Current Local/VPN settings remain available.

Validation results and APK SHA-256 are saved alongside the downloadable artifact. Physical-device installation, Wi-Fi connectivity and real Termux permissions are not verified in this workspace.
