# BUILD_REPORT — LocalLS 0.9.2

Дата: 7 октября 2026. APK реально получен успешной Android Gradle-сборкой.

## Итог сборки

`BUILD SUCCESSFUL in 40s`, 73 задачи выполнены. Команда:

```bash
./gradlew clean assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
```

Лог: `artifacts/logs/build-v0.9.2-final.txt`.
Статический аудит: **0 errors, 0 warnings**.
Android Lint: **0 errors/fatal, 316 warnings**. Предупреждения о строках/localization,
Autofill и структуре/виде layout не скрывались baseline или отключением lint.

## Конфигурация

| Параметр | Значение |
|---|---|
| Название | LocalLS |
| applicationId / package | com.bitpoint.homeservercontrol |
| versionCode / versionName | 11 / 0.9.2 |
| Gradle / официальный Wrapper | 9.6.0 |
| Android Gradle Plugin | 9.4.1 |
| compileSdk / minSdk / targetSdk | 37 / 24 / 37 |
| JDK | Eclipse Temurin 17.0.20.1+1 |
| Android Platform | android-37.0, revision 2 |
| Build Tools / Platform Tools | 36.0.0 / 37.0.1 |
| SSH/SFTP dependency | com.github.mwiede:jsch:2.28.7 |

Проверены settings.gradle.kts, build.gradle.kts, app/build.gradle.kts, официальный
Gradle Wrapper с закреплённым SHA-256 distribution, зависимости и SDK-конфигурация.
SDK: `/workspace/toolchains/android-sdk`; JDK: `/workspace/toolchains/jdk17`.
`local.properties` и приватный debug keystore в исходный архив не включены.
minSdk 24 означает Android 7.0+: Galaxy A01 на Android 10/11 совместим.
targetSdk 37 не повышает минимальную версию Android для установки.

## Настоящий APK

- Путь: `artifacts/LocalLS-v0.9.2-debug.apk`.
- Исходный результат: `app/build/outputs/apk/debug/app-debug.apk`; файлы побайтно идентичны.
- Размер: **1,354,778 байт**.
- SHA-256: **`11cec2907ef8f2c44900a2bfd43a10218ec5b7530bc67455f1888ec9e424bc64`**.
- aapt читает package, launcher, minSdk, targetSdk и бинарный Manifest без ошибки.
- apksigner подтверждает APK Signature Scheme v2 для API 24–29.
- zipalign `-c -P 16 4` и CRC всех записей корректны.
- Сертификат совпадает с v0.9.1; `adb install -r` на Android 10 x86: **Success**.
- Metadata: `artifacts/apk-verification.json`; сертификат: `artifacts/logs/signing-cert.txt`.

## Найденные и исправленные ошибки

1. Реальный Termux 0.118.3 сообщает успешный RUN_COMMAND как `err = -1`.
   Старый receiver ошибочно требовал 0. Исправлен официальный протокол: нужны
   `err = -1` и `exitCode = 0`; `err = 0` отклоняется. Nonce, срок и приватный receiver сохранены.
2. OpenSSH 10.5 выводит `Port 8022` вместо прежнего `port 8022`. Чувствительный к регистру
   awk возвращал пустой порт, хотя команда завершалась успешно. Теперь параметр
   читается через `tolower`, сохраняя совместимость со старыми версиями.
3. Официальный Termux Intent API ожидает уровень фонового журнала как строку.
   Вместо Integer 0 передаётся String "0"; предупреждение Bundle устранено,
   фоновый command logging выключен штатным способом.
4. Ошибки разрешений, apt update и установки OpenSSH показываются раздельно.
   Сырые stdout/stderr и errmsg не публикуются; диагностируются только числовые коды.

При реальном испытании v0.9.1 Termux-тест сначала упал. После первого исправления
прицельный прогон в 0.9.2 дал 5 passed и 1 failure: это обнаружило изменение регистра
в OpenSSH. Ошибка исправлена, снова выполнена clean Android-сборка и полный прогон.
Промежуточные журналы сохранены; проваленные попытки не названы успешными.

## Реально выполненные тесты

Эмулятор: Android 10, API 29, x86, 720×1520. Окончательный полный прогон:
**33 passed, 0 skipped, 0 failures**, **539.332 с**.
Логи: `artifacts/logs/instrumentation-api29-final.txt`, `test-summary.json`.

### Настоящий сервер Termux/OpenSSH

Официальный Termux 0.118.3 установлен и инициализирован в эмуляторе. Системный
SAF-доступ к его HOME выдан через Android DocumentsUI, RUN_COMMAND разрешён.
OpenSSH реально установлен графическим мастером; версия OpenSSH 10.5p1.

- Фактические пользователь и порт получены из команды Termux: u0_a118, 8022.
- Настоящая password-аутентификация по loopback и получение host fingerprint.
- Upload/download 32 KiB через настоящий SFTP с полным сравнением байтов; список папки.
- Повторный быстрый запуск мастера с `auto_prepare`, без замены пароля.
- После повторной подготовки прежний пароль работает; fingerprint, PID sshd
  и SHA-256 sshd_config не изменились. Второй процесс или boot hook не добавлен.
- Графическая кнопка проверки выполняет реальный SSH-вход и разрешает экспорт.
- Отдельный протокольный тест проверяет успешный err -1 и отклонение err 0.

Первичная установка OpenSSH в этой среде выполнялась мастером прежней 0.9.1;
его ошибочное сообщение о результате и исправлено в 0.9.2. Окончательная 0.9.2
проверена на уже установленном настоящем OpenSSH, включая повторную подготовку.
Свежая установка после полного удаления данных Termux именно APK 0.9.2 не выполнялась.

### Остальные проверки

- Запуск основных экранов, IP/порт/пользователь, отклонение неверного порта/IP.
- Шторка 50%, закрытие через Back без завершения приложения, один раскрытый раздел.
- Компактные размеры, иконки, пять тем, сохранение формы значков после recreate.
- Несохранённый адрес не теряется при возврате; устаревший TCP probe не заменяет новый.
- Wi-Fi: SSID/WPA2 validation и отсутствие пароля в saved state/prefs.
- Кнопка перезапуска отправляет подтверждённый POST /restart отдельному HTTP fixture-агенту.
  Это проверка команды; пользовательская SSH-служба не перезапускалась.
- Keystore round-trip, отсутствие открытого пароля в prefs.
- Local/Tailscale профили и выключенный по умолчанию fallback; отсутствующий VPN не имитируется.
- Вложения требуют подтверждения, графики используют SFTP progress, история сохраняется.
- Настоящий SSH/SFTP к отдельному Paramiko fixture: неверный пароль, блокировка key mismatch
  до передачи пароля, upload/download 128 KiB, mkdir/rename и безопасное удаление.
- SFTP upload 8 MiB после сворачивания; foreground service и power locks включаются/освобождаются.
- Уведомление сворачивания возвращает ту же задачу и снимается при возврате.
- Мастер не сохраняет пароль; публичный профиль импортируется без секретов,
  конфликт уже доверенного host key блокируется.

## Аудит сохранности и секретов

Файлы realme-agent побайтно совпадают с рабочей v0.8. Новый механизм автозапуска
sshd не создан. Штатная настройка через Termux остаётся доступной. Реальный
пользовательский сервер 192.168.1.82 не затрагивался; испытания выполнены в изолированной среде.

XML/Manifest, Python и shell syntax проверены. Исходники, APK entries, журналы
и logcat проверены на реальные приватные fixture-пароль и agent token;
секреты в отчёт/архив не включены. Результат logcat-аудита: `artifacts/logs/runtime-audit.json`.

Сохранены ранее собранные v0.8, v0.9 и v0.9.1 в отдельных каталогах.
Обновлённая основа v1.0: `docs/V1_0_FOUNDATION.md`; очередь в service, атомарные
uploads и release signing остаются отдельными будущими этапами.

## Границы проверки

Физические Galaxy A01 и серверный телефон не подключены. Реальное переключение
Wi-Fi, Tailscale tailnet на телефоне/сервере/ПК, 2–5 ГБ и длительные ограничения батареи
не проверены. Android 11+ системный экран добавления сети проверен сборкой/Manifest;
физическое переподключение требует системного подтверждения на серверном устройстве.
Установка настоящего debug APK и runtime-проверки выполнены на Android 10 в эмуляторе.
