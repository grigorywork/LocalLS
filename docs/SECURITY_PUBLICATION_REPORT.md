# LocalLS — публикация обновления безопасности, 9 октября 2026

Готовые исходники и сборки опубликованы в grigorywork/LocalLS. GitHub main перед
публикацией совпала с e088520; проверенные изменения добавлены fast-forward без
force push. Параллельных изменений в рабочей копии с момента контрольного снимка
не обнаружено. Для сохранения общей рабочей папки публикация выполнялась из
отдельного worktree и ветки security-ssh-https-20261009.

Теги android-v0.9.4, windows-v0.1.1, fedora-v0.1.1 указывают на исходники сборок:
`0332797ca6117b29c73a4b589e5f0bd37be9d08f`.

Выполнено 21 полное анонимное скачивание release assets. Размеры и SHA-256
совпали с оригиналами. ZIP CRC проверены; установщики внутри скачанных комплектов
совпали с отдельно опубликованными бинарными файлами. TXT — UTF-8 BOM / CRLF.
Подробные отчёты: android/BUILD_REPORT.md, desktop/BUILD_REPORT.md, linux/BUILD_REPORT.md.

| Платформа | Настоящий установщик | Байт | SHA-256 |
|---|---|---:|---|
| Android | [LocalLS-v0.9.4-debug.apk](https://github.com/grigorywork/LocalLS/releases/download/android-v0.9.4/LocalLS-v0.9.4-debug.apk) | 1376878 | `473aeade948671d24b696843e2f61d6b36d1eda70f511dcf6fecc3b5d6b62291` |
| Windows | [LocalLS-0.1.1-Windows-x64-Setup.exe](https://github.com/grigorywork/LocalLS/releases/download/windows-v0.1.1/LocalLS-0.1.1-Windows-x64-Setup.exe) | 158252552 | `e52853a1ffe55c692544a442cdc7610fde5ce1c1d9c0f4a65771a6a6aace1403` |
| Fedora | [LocalLS-0.1.1-Fedora-x86_64.rpm](https://github.com/grigorywork/LocalLS/releases/download/fedora-v0.1.1/LocalLS-0.1.1-Fedora-x86_64.rpm) | 110665067 | `e5fa871ed48be3a43f4c99d8a80a85f6c003a46a3e27635991335fde407fa50b` |

В каждом релизе: отдельный APK/EXE/RPM, ZIP с папкой платформы и обеими инструкциями,
BUILD_REPORT, SHA256SUMS, обновлённый HTTPS-агент LocalLS-Agent-0.3.zip без токена,
TLS-приватного ключа или пользовательской конфигурации.

Все Git blobs, достижимые из веток/тегов, проверены: секретов не найдено.
Прежние установщики Android 0.9.3, Windows/Fedora 0.1.0 сохранены побайтно;
предыдущие релизы и теги не удалялись и не перезаписывались.

SSH key mode не использует парольный fallback. Миграция сначала устанавливает
публичный ключ и проверяет новый вход. Парольный вход сервера автоматически
не отключён. Нужны проверенные ключи всех устройств и резервный доступ.
HTTPS требует обновления агента и независимой проверки его сертификата.
Полный logcat после Android-тестов получить не удалось из-за отключения эмулятора;
это отдельно записано в Android BUILD_REPORT, не выдано за пройденную проверку.
