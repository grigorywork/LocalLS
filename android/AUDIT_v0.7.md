# Audit v0.7

## Проверено в текущей среде
- [x] Первый SSH fingerprint считывается key-exchange probe без SSH password.
- [x] Доверенный fingerprint закрепляется в JSch `HostKeyRepository`; mismatch отклоняется до password authentication.
- [x] XML/Manifest парсятся без ошибок.
- [x] Java `R.id` / `R.layout` ссылки сверены со значениями ресурсов.
- [x] VersionCode 7 / VersionName 0.7.0 / minSdk 24 / target+compile 37.
- [x] Manifest содержит `WAKE_LOCK`, `ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE` для transfer power guard.
- [x] `TransferPowerGuard` удерживает CPU/Wi‑Fi только при непустой SFTP-очереди и освобождается при завершении последней задачи и в `onDestroy()`.
- [x] Success transfer log содержит объём и среднюю скорость, секреты туда не попадают.
- [x] `LocalServerDiscovery` сканирует только текущую `/24` сеть и не требует Wi‑Fi location scan API.
- [x] Discovery проверяет SSH banner `SSH-`, а не любой открытый TCP-порт.
- [x] Discovery не сохраняет адрес автоматически: пользователь сначала выбирает host.
- [x] `LocalServerDiscovery.java` отдельно компилируется `javac -Xlint:all`.
- [x] `PinnedHostKeyRepository.java` сверяется с актуальным JSch `HostKeyRepository` API через compile-stub.
- [x] `realme-agent/agent.py` и `tools/audit_project.py` проходят `python -m py_compile`.
- [x] Realme Agent localhost smoke test: `200` с правильным Bearer token и `401` без token.
- [x] Shell scripts проходят `bash -n`.
- [x] Старые гарантии v0.6 сохранены: fingerprint mismatch блокирует команды/SFTP, fallback выключен по умолчанию, один transfer retry, рекурсивного удаления папок нет.
- [x] Не добавлен новый механизм запуска `sshd`.

## Осознанные ограничения
- Discovery предполагает типичную домашнюю `/24` сеть. Для `/16`/`/23` он не сканирует весь диапазон, чтобы не превращать телефон в сетевой сканер и не ждать слишком долго.
- High-performance Wi‑Fi lock влияет только на клиент с приложением; он не увеличивает физический предел роутера/Realme и не исправляет медленную сеть сам по себе.
- Realme Agent по-прежнему HTTP + bearer token и предназначен только для доверенной LAN/Tailscale сети; порт 8787 нельзя пробрасывать напрямую в интернет.

## Build blocker этой среды
- [ ] Android SDK отсутствует.
- [ ] `gradle-wrapper.jar` отсутствует в исходном архиве.
- [ ] Shell/container не может скачать Gradle/Android/Maven dependencies из-за сетевого ограничения.
- [ ] `assembleDebug` здесь поэтому не подтверждён.

## Обязательный device smoke test
- [ ] Nokia 5: запуск, прокрутка, клавиатура.
- [ ] Local `192.168.1.82:8022` или текущий DHCP IP Realme.
- [ ] Кнопка «Найти сервер в Wi‑Fi» находит Realme.
- [ ] First trust + fingerprint mismatch.
- [ ] 2–5 GB upload/download.
- [ ] Выключение/затемнение экрана клиента не останавливает текущую передачу.
- [ ] Wi‑Fi обрыв -> максимум один retry.
- [ ] Cancel current / clear queue освобождают power guard после завершения задач.
- [ ] Tailscale/fallback.
- [ ] Background monitor.
- [ ] Realme Agent status/start/restart.

## Вывод
v0.7 усиливает именно стабильность реальных длинных передач и восстановление адреса Realme после DHCP. Исходники статически согласованы, но APK нельзя считать проверенным до настоящего Android `BUILD SUCCESSFUL` и smoke test на Nokia 5 + Realme.
