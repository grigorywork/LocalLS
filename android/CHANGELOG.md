# Changelog

## 0.8.0
- Сборка переведена в release-candidate режим: новые функции больше не приоритетнее реальной APK-сборки.
- Основное тестовое устройство теперь Samsung Galaxy A01; minSdk 24 сохранён для совместимости с Android 7+.
- Добавлен `TransferForegroundService`: во время активной SFTP-очереди процесс поднимается в foreground priority, что уменьшает шанс убийства передачи One UI/Android при выключенном экране.
- Для foreground data-sync добавлены `FOREGROUND_SERVICE` и `FOREGROUND_SERVICE_DATA_SYNC`; сервис не управляет `sshd` и не меняет серверную схему Realme.
- `FilesActivity` не пересоздаётся при обычном повороте экрана (`configChanges`), чтобы активная очередь не обрывалась от rotation.
- Диагностика показывает модель клиентского телефона, приблизительный объём RAM и системный low-RAM профиль.
- Wake/Wi-Fi locks из v0.7 сохранены; foreground service включается только на время непустой очереди и выключается после последней передачи.
- Build/CI/artifact names обновлены под v0.8.

## 0.7.0
- SSH fingerprint теперь проверяется/закрепляется до password authentication; при mismatch пароль не отправляется.
- Первичный fingerprint считывается отдельным key-exchange probe без пароля.
- Добавлен `TransferPowerGuard`: CPU wake lock и high-performance Wi‑Fi lock только на время SFTP-очереди.
- Локи гарантированно освобождаются после последней передачи и в `FilesActivity.onDestroy()`.
- В историю успешной передачи добавлены средняя скорость и номер retry.
- Добавлен `LocalServerDiscovery` для поиска Termux/OpenSSH в текущей `/24` Wi‑Fi сети.
- Discovery проверяет SSH banner, а не только открытый TCP-порт.
- На экране «Сеть» добавлена кнопка «Найти сервер в Wi‑Fi» и безопасный выбор найденного Local host.
- Добавлены `WAKE_LOCK`, `ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE` permissions.
- Build gate/CI/version metadata обновлены под 0.7.0.

## 0.6.0
- Миграция настроек и отдельная диагностика.
- Local/Tailscale fallback и последний рабочий host.
- История SFTP-передач.

## 0.5.0
- SSH/SFTP keepalive, один retry, сглаженная скорость и SAF permission.

## 0.4.0
- Прогресс/ETA/отмена очереди, mkdir/rename/delete, экраны Network/Settings/History.
