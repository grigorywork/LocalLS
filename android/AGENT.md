# AGENT.md — LocalLS v0.9.1

## Цель
Довести приложение-пульт для Realme GT Neo 2 + Termux + OpenSSH/SFTP до реально собираемого и проверенного APK для Samsung Galaxy A01 / другие Android 7+ устройства, не ломая уже рабочую локальную схему сервера.

Типовые параметры пользователя:
- Local: `192.168.1.82:8022`;
- user: `u0_a606`;
- Tailscale позже/опционально;
- Realme Agent: `8787`.

## Жёсткие правила
1. Перед существенным этапом фиксировать процент готовности.
2. После этапа проводить аудит и сразу исправлять ошибки.
3. Не добавлять конкурирующие механизмы автозапуска `sshd`.
4. Не хранить SSH password или agent token открытым текстом.
5. Stop/restart `sshd` выполняются только независимым Realme Agent, а не через ту же SSH-сессию.
6. Local/Tailscale fallback выключен по умолчанию и не должен сам переписывать профиль пользователя.
7. Fingerprint mismatch блокирует команды и SFTP.
8. Рекурсивное удаление папок не включать без отдельного UX подтверждения.
9. UI обязан оставаться удобным на небольшом/частично повреждённом экране Samsung Galaxy A01.
10. Не объявлять APK готовым без реального `BUILD SUCCESSFUL`.

## Реализовано к v0.8
- Dashboard/Network/Files/Settings/History/Diagnostics.
- SSH/SFTP fingerprint trust + pre-auth host-key pinning.
- Keystore secrets.
- Local + Tailscale profile/fallback.
- SFTP queue, progress, speed, ETA, cancel, one retry.
- SFTP mkdir/rename/delete-file/empty-dir.
- Background reachability monitor.
- iperf3 integration.
- Realme Agent.
- Config migration and transfer/metric history.
- Temporary CPU + Wi‑Fi high-performance locks while SFTP queue is active.
- Local `/24` SSH discovery when Realme DHCP address changes.

## Следующий build gate
1. `python3 tools/audit_project.py` — 0 errors.
2. Restore official Gradle wrapper if needed.
3. `:app:assembleDebug` and `:app:assembleDebugAndroidTest`.
4. Fix every compile/resource/manifest error immediately.
5. Copy real APK to `artifacts/LocalLS-v0.9.1-debug.apk`.
6. Create `BUILD_REPORT.md` with size + SHA-256.
7. Install on Samsung Galaxy A01.
8. Smoke test against real Realme.

## Device smoke test
- Launch/scroll/keyboard on Samsung Galaxy A01.
- Local connection to Realme.
- Fingerprint first trust + mismatch block.
- Discovery finds Realme when it is in same /24 Wi‑Fi.
- 2–5 GB upload and download with screen dim/off; transfer should not stall because client Wi‑Fi slept.
- Wi‑Fi interruption -> at most one retry.
- Cancel current and clear queue.
- Tailscale host/fallback.
- Background monitor/notifications.
- Agent status/start/restart.
- No password/token in logs or diagnostics.

## LocalLS v0.9.1

Рабочая v0.8 сохранена отдельно. Новые требования: вложения с подтверждённой отправкой,
открытие папки сервера по пути, правая шторка шириной 50%, пять цветовых тем,
название LocalLS, облако с молнией, официальный Android API Tailscale.
applicationId остаётся прежним для обновления и сохранения настроек.
Не имитировать VPN: статус основан на сети Android, первый вход и VPN consent — в Tailscale.

Последний этап: графический мастер на Realme через официальный Termux SAF/RUN_COMMAND,
без ручного ввода команд. Сохранять sshd/config/host keys/autostart; новый пароль
задаётся только явно. Проверять реальный SSH-вход до экспорта публичного профиля.
