# Audit v0.5

## Проверено в текущей среде
- [x] XML/Manifest парсятся.
- [x] R.id / layout references: static audit = 0 errors, 0 warnings.
- [x] Нет хардкодированного SSH password / agent token по audit-patterns.
- [x] Версия `0.5.0`, versionCode `5`, minSdk 24.
- [x] SSH/SFTP keepalive hooks присутствуют.
- [x] Upload/download retry hook присутствует (максимум 2 попытки всего).
- [x] Сглаженная скорость передачи присутствует.
- [x] SAF persistable permission hook присутствует.
- [x] Main Java sources проходят локальный stub type-check; ошибок исходного Java-кода не выявлено.
- [x] androidTest sources проходят локальный stub type-check.
- [x] `realme-agent/agent.py` проходит `py_compile` и импорт.
- [x] Build gate запущен; static audit проходит, затем честно останавливается из-за отсутствия Android SDK.

## Не проверено без Android SDK/реального устройства
- [ ] Gradle Sync.
- [ ] `:app:assembleDebug` = BUILD SUCCESSFUL.
- [ ] APK install on Nokia 5.
- [ ] fingerprint first trust + mismatch.
- [ ] 2–5 GB upload/download.
- [ ] Wi‑Fi interruption -> one automatic retry.
- [ ] cancel transfer during retry.
- [ ] Local / Tailscale host.
- [ ] Agent status/start/restart on Realme.
- [ ] portrait UI with keyboard open on Nokia 5.

## Вывод
Исходники v0.5 подготовлены к реальному Android build gate. Production APK не заявлять до BUILD SUCCESSFUL.
