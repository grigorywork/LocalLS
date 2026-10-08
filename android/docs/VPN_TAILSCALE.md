# VPN integration

LocalLS uses the public exported Tailscale Android IPNReceiver actions:

- `com.tailscale.ipn.CONNECT_VPN`
- `com.tailscale.ipn.DISCONNECT_VPN`

Broadcasts are restricted to package `com.tailscale.ipn`. Installed package and
receiver availability are checked before sending; no auth keys or account tokens
are passed. Android ConnectivityManager drives switch state. Request timeout is
20 seconds; no success is claimed just because a broadcast was sent.

API 30+: VPN owner UID identifies Tailscale when exposed by Android. API 24–29
(or hidden owner UID): installed Tailscale plus VPN link addresses in documented
100.64.0.0/10 or fd7a:115c:a1e0::/48 classify the interface. This fallback is not
proof of peer reachability and could overlap with another VPN using those ranges.
SSH/SFTP reachability and fingerprint remain separate checks.

First login, tailnet membership and Android VPN consent take place in the client.
A single Android VPN slot can replace another provider; UI asks before switching.
Desktop and Realme clients must be configured separately. Local profile is not
changed by the toggle. With an exit node, enable Allow LAN access in Tailscale;
LocalLS does not silently change the user's exit-node settings or server routing.

Official source consulted on 2026-10-06:
https://github.com/tailscale/tailscale-android/blob/main/android/src/main/AndroidManifest.xml
https://github.com/tailscale/tailscale-android/blob/main/android/src/main/java/com/tailscale/ipn/IPNReceiver.java
