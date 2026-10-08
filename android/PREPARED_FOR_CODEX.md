> Исторический документ исходной v0.8. Блокер снят; актуальные APK, сборка и проверки LocalLS 0.9.1 — в BUILD_REPORT.md.

# Prepared for Codex — v0.8

Главная задача теперь не расширять scope, а пройти настоящий Android build gate.

Особенно проверить новые участки v0.8:
- `PinnedHostKeyRepository.java` + first-trust probe без password auth;
- `TransferPowerGuard.java` + manifest permissions;
- `LocalServerDiscovery.java`;
- кнопку `networkDiscoverButton` и NetworkActivity;
- release всех wake/Wi-Fi locks после окончания очереди и при уничтожении Activity.

Финальный артефакт: `artifacts/HomeServerControl-v0.8-debug.apk`.
