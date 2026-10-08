# Audit v0.4

## Выполнено статически в подготовленной среде
- [x] XML layouts/Manifest парсятся.
- [x] Manifest-компоненты соответствуют Java-классам.
- [x] Java `R.id` ссылки проверяются против XML id.
- [x] Java `R.layout` ссылки проверяются против layout-файлов.
- [x] Нет хардкодированного SSH password / agent token по audit-patterns.
- [x] Версия `0.4.0`, versionCode `4`.
- [x] minSdk 24 сохранён.
- [x] SFTP transfer progress/cancel hooks присутствуют.
- [x] mkdir/rename/delete hooks присутствуют.
- [x] Network/Settings/History screens добавлены.
- [x] Instrumented smoke tests добавлены.
- [x] Java main-source type-check выполнен против локальных Android/JSch stubs: ошибок исходного Java-кода не обнаружено.
- [x] `app/src/androidTest` также проходит локальную stub-компиляцию (JUnit/AndroidX stubs).
- [x] `realme-agent/agent.py` проходит `py_compile`; `/status` с верным токеном = 200, без токена = 401.

## Нельзя считать проверенным без Android SDK/устройства
- [ ] Gradle Sync.
- [ ] `:app:assembleDebug` = BUILD SUCCESSFUL.
- [ ] `connectedDebugAndroidTest`.
- [ ] Установка APK на Nokia 5.
- [ ] Fingerprint TOFU на реальном Realme.
- [ ] Upload/download 1–5+ ГБ.
- [ ] Скорость/ETA при передаче.
- [ ] Cancel active SFTP transfer.
- [ ] Clear queued transfers.
- [ ] Wi-Fi drop/recovery during transfer.
- [ ] mkdir/rename/delete на реальном SFTP.
- [ ] delete non-empty folder безопасно отклоняется.
- [ ] Tailscale host на реальной VPN-сети.
- [ ] Realme Agent status/start/restart.
- [ ] Background notifications.
- [ ] UI Nokia 5 portrait/landscape.

## Решение
Исходники v0.4 готовы к build-gate, но production-ready статус запрещён до реального APK и device test.
