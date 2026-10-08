# CODEX TASK — LocalLS v0.9.1 build gate

Работай непрерывно до настоящего APK.

1. Прочитай `AGENT.md`, `README.md`, `AUDIT_v0.8.md`.
2. Запусти `python3 tools/audit_project.py`.
3. Установи/используй JDK 17, Gradle 9.6.0, Android SDK 37, Build Tools 36.0.0.
4. Если отсутствует `gradle-wrapper.jar`, восстанови официальный wrapper через `gradle wrapper --gradle-version 9.6.0`.
5. Выполни `:app:assembleDebug :app:assembleDebugAndroidTest`.
6. При любой ошибке найди причину, исправь исходники и сразу повтори build.
7. Не удаляй функции ради прохождения компиляции без объяснения.
8. Не меняй рабочий протокол Realme/Termux/sshd и не добавляй второй boot-механизм sshd.
9. После успеха скопируй APK в `artifacts/LocalLS-v0.9.1-debug.apk`.
10. Создай `BUILD_REPORT.md` с `BUILD SUCCESSFUL`, размером APK и SHA-256.

## LocalLS v0.9.1

Рабочая v0.8 сохранена отдельно. Новые требования: вложения с подтверждённой отправкой,
открытие папки сервера по пути, правая шторка шириной 50%, пять цветовых тем,
название LocalLS, облако с молнией, официальный Android API Tailscale.
applicationId остаётся прежним для обновления и сохранения настроек.
Не имитировать VPN: статус основан на сети Android, первый вход и VPN consent — в Tailscale.

Последний этап: графический мастер на Realme через официальный Termux SAF/RUN_COMMAND,
без ручного ввода команд. Сохранять sshd/config/host keys/autostart; новый пароль
задаётся только явно. Проверять реальный SSH-вход до экспорта публичного профиля.
