# НАЧНИ ОТСЮДА — Home Server Control v0.8

1. Не меняй рабочую схему Realme `Termux + sshd :8022` без причины.
2. Запусти `python3 tools/audit_project.py`.
3. Собери `:app:assembleDebug` на Android SDK 37 / Build Tools 36.0.0 / Gradle 9.6.0.
4. Ошибки компиляции исправляй и сразу пересобирай.
5. Успех считается только при настоящем `artifacts/HomeServerControl-v0.8-debug.apk`.
6. После сборки проверь APK на Samsung Galaxy A01 и реальном Realme.

v0.8 добавляет временный CPU/Wi‑Fi high-performance lock только на время SFTP-передач и поиск Realme в локальной Wi‑Fi сети, если DHCP поменял IP.
