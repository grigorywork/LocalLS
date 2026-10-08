# Audit v0.6

## Проверено в текущей среде
- [x] XML/Manifest парсятся.
- [x] Java R.id / layout references проверяются статическим аудитом.
- [x] VersionCode 6 / VersionName 0.6.0 / minSdk 24.
- [x] ConfigMigrator присутствует и удаляет известные экспериментальные plaintext secret keys.
- [x] Пароль SSH и agent token не выводятся новым Diagnostics screen.
- [x] Local/Tailscale fallback выключен по умолчанию и не перезаписывает выбранный режим.
- [x] Fingerprint остаётся привязан к конкретному host:port.
- [x] Background monitor умеет резервный host только при включённом fallback.
- [x] SFTP browser использует последний рабочий host только при включённом fallback.
- [x] Transfer log хранит только label/result, без password/token.
- [x] SSH/SFTP keepalive и единственный retry сохранены.
- [x] Рекурсивное удаление каталогов по-прежнему отсутствует.
- [x] Realme Agent не заменяет и не дублирует boot-механизм `sshd`.

## Известный build blocker этой среды
- [ ] Android SDK отсутствует.
- [ ] `gradle-wrapper.jar` не включён в исходный v0.5 архив; build scripts умеют восстановить его через системный Gradle/Android Studio в нормальной сборочной среде.
- [ ] Изолированная среда не может скачать Gradle/Android Maven зависимости, поэтому `assembleDebug` здесь не подтверждён.

## Обязательный device smoke test
- [ ] Nokia 5: запуск и ScrollView/клавиатура.
- [ ] Local `192.168.1.82:8022`.
- [ ] First trust + fingerprint mismatch.
- [ ] 2–5 GB upload/download.
- [ ] Wi‑Fi обрыв -> максимум один retry.
- [ ] Cancel during transfer/retry.
- [ ] Tailscale host + fallback Local -> VPN и VPN -> Local.
- [ ] Background monitor.
- [ ] Realme Agent status/start/restart.

## Вывод
v0.6 функционально подготовлен к build gate, но production/debug APK нельзя объявлять проверенным до реального `BUILD SUCCESSFUL`.
