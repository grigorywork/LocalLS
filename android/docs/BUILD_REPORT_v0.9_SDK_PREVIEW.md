> Исторический отчёт. Актуальная Gradle-сборка от 7 октября 2026 описана в корневом BUILD_REPORT.md. Старый debug APK сохранён в artifacts/baseline/LocalLS-v0.9-debug.apk.

# BUILD_REPORT — LocalLS v0.9, актуальный APK с последними функциями

Дата: 2026-10-06. **Настоящая SDK-сборка нового APK успешно выполнена.**
Обязательный Gradle gate для этой версии остаётся заблокирован ограничением среды.
Не утверждается `BUILD SUCCESSFUL` для новых исходников. Предыдущая Gradle-сборка
действительно имела `BUILD SUCCESSFUL` и сохранена отдельно.

## Новый APK

| Параметр | Значение |
|---|---|
| Файл | artifacts/LocalLS-v0.9-preview.apk |
| Размер | 1,181,205 байт |
| SHA-256 | `78452d86d992252b89d5bf33b9ad34d2847ae583ec5e1a20eeadc18e699b3a9e` |
| Package | com.bitpoint.homeservercontrol |
| Имя / версия | LocalLS / 0.9.0, versionCode 9 |
| compileSdk / targetSdk | 37 / 37 |
| minSdk | 24 — Android 7.0+ |
| SDK Platform | android-37.0, revision 2, Android 17 |
| SDK Build Tools | 36.0.0 |
| D8 | 9.4.24 из официального cached builder AGP 9.4.1 |
| Gradle / AGP в проекте | 9.6.0 / 9.4.1 |
| JDK | Eclipse Temurin 17.0.20.1+1 |
| Platform Tools | 37.0.1 |
| Подпись | тот же debug certificate, что у предыдущего APK |

Этот файл собран из актуальных Java/XML исходников реальными AAPT2, javac, D8,
zipalign и apksigner; это не переименованный архив. Pipeline:
`tools/offline_android_build.py`. Лог: `artifacts/logs/sdk-offline-build.log`.
Установленные runtime JAR: JSch 2.28.7, Kotlin stdlib 2.3.21, JetBrains annotations 23.0.0.
Лицензии и Java resources зависимостей включены. Ни Gradle wrapper, ни его бинарники
для обхода ограничения не изменялись.

Сборка SDK не заменяет требуемую команду `./gradlew clean assembleDebug`.
Новый APK публикуется как preview для установки и проверки пользователем.
Android 10 Galaxy A01 совместим с minSdk 24; target/compileSdk 37 не требуют
Android 17 на устройстве.

## Что включено в новый APK

- Иконка над небольшой подписью каждой функции; компактные кнопки с тонкой
  полупрозрачной чёрной окантовкой на пяти темах.
- Dashboard со скоростью SFTP и графиком SSH-задержки, фиолетовые кнопки.
- Правая вертикальная шторка шириной 50%, темы и официальный API Tailscale.
- Popup «Сервер»: графический мастер, Включить SSH, Отключить SSH,
  Перезапустить SSH, статус и настройки доступа Realme Agent.
  Эти команды управляют SSH-службой, не питанием телефона. Агент требуется отдельно.
- Мастер на Realme через официальный Termux SAF/RUN_COMMAND без ручного ввода команд.
  Установка/первый запуск Termux и системные разрешения остаются действиями пользователя.
- Существующие host keys/config/sshd/autostart сохраняются. Смена пароля только явно;
  пароль преобразуется в штатный Termux auth hash и не отправляется в shell/Intent.
- Экспорт публичного профиля для Samsung после реального SSH login, импорт с проверкой
  ключа и блокировкой конфликта с уже доверенным fingerprint.
- Вложения через SAF/«Поделиться», подтверждение отправки, переход в серверную папку.
- Выделение до 256 файлов в разных папках; «Скачать выбранное» → любая доступная через
  системный picker папка телефона → подтверждение → очередь, прогресс, график, история.
  Одноимённые локальные файлы сохраняются: новый файл получает номер.
  Папки открываются для навигации; рекурсивное скачивание целой папки не добавлялось.
- «Свернуть» в главной шторке и в проводнике: moveTaskToBack + постоянное уведомление
  Android для возврата. Активная передача использует отдельный foreground service.
  «Убрать» удаляет только уведомление сворачивания; при возврате оно снимается.
  Без активной передачи это не бессрочный service, и Android может выгрузить процесс.

## Реально выполненные проверки нового APK

- Компиляция всех актуальных ресурсов AAPT2 и всех Java-файлов javac 17 — успешна.
- D8 преобразовал классы и все runtime JAR в настоящие DEX — успешно.
- ZIP CRC, DEX Adler-32 и SHA-1, binary Manifest, package, SDK, launcher — проверены.
- apksigner подтвердил v2/v3 подпись для API 24–29; zipalign 4/16 — успешен.
- Signing certificate SHA-256 совпадает с предыдущим debug APK: поддерживается обновление.
- В DEX найдены новые IconButtons, LocalDownloadFolder, TrayNotificationReceiver,
  ServerSetupActivity, MainActivity, FilesActivity и TransferForegroundService.
- Статический аудит: 0 ошибок, 0 предупреждений.
- Реальный случайный пароль из изолированного SSH fixture отсутствует в исходниках,
  опубликованных логах и новом APK. Реальные пользовательские секреты не предоставлялись.
- Все файлы realme-agent побайтно совпадают с сохранённой v0.8.
- Исходная рабочая v0.8 не изменялась.

## Ошибки и исправления

- Обнаружен блок локальных сокетов: Gradle падает при создании FileLockContentionHandler
  с «Could not determine a usable wildcard IP». SDK pipeline обошёл необходимость IPC
  без изменения сетевой политики и без выдачи ложного Gradle результата.
- Старый D8 SDK 36.0.0 выдавал предупреждения о Kotlin 2.3 metadata.
  Применён официальный D8 9.4.24 из AGP 9.4.1, повторная сборка без этих предупреждений.
- Исправлены ссылки на стиль новых кнопок и lint WrongConstant для SAF-разрешений
  на предыдущем этапе.
- В тестах исправлены пустой PBKDF2-вектор (Android provider запрещает пустой пароль)
  и ожидание асинхронного callback AlertDialog вместо немедленного assert.
  Эти исправленные instrumentation tests в текущей среде ещё не запускались.
- Существующие SSH host keys не дополняются, чтобы не изменить выбранный ключ после restart.
- Завершённые вложения не восстанавливаются из старого Intent после recreate.
- Публичный импорт не заменяет доверенный ключ и очищает прежний сохранённый пароль.
- Пароли и токены не сохраняются в Bundle/открытых prefs, внешних файлах или логах.

## Что блокируется и не проверено

В sandbox даже bind(127.0.0.1) запрещён: Operation not permitted. Дополнительные
сетевые/расширенные разрешения запрашивались после согласия пользователя, но ответы
инструмента о выдаче прав не получены. Автоматическое отклонение не утверждается.
Лог обычной Gradle-попытки: artifacts/logs/latest-build-default.log.

adb доступен, но не может создать smartsocket listener: Operation not permitted.
Проверка `adb devices` завершилась ошибкой; новый APK не установлен и не запускался
здесь. Лог: artifacts/logs/adb-current-access.txt. Physical Samsung/Realme не подключены.
Поэтому runtime-проверка новых иконок/menu/download/tray и полный `BUILD SUCCESSFUL`
для актуальных исходников остаются невыполненными.

На предыдущем APK реально запускались 24 instrumentation tests на API 29:
21 прошёл, 2 были ошибками тестовых предположений, 1 Termux bootstrap test пропущен
без системного разрешения папки. Проверялись native UI, сохранение параметров,
Keystore, ошибочные пароли/порты, fingerprint pinning, SSH/SFTP roundtrip, mkdir/rename,
вложения, передача 8 МиБ после Home, foreground service, скорость и история.
Эти результаты не выдаются за проверку нового APK. Полный прежний отчёт:
docs/BUILD_REPORT_v0.9_GRADLE_BASELINE.md.

Настоящая домашняя сеть 192.168.1.82, полный bootstrap Realme, Tailscale-account и VPN
всех трёх устройств, 2–5 ГБ передачи и физические ограничения батареи не проверены.
Текущая схема Realme никогда не трогалась. Queue пока живёт в FilesActivity:
Home/minimize поддержаны, уничтожение Activity отменяет её. При отмене/обрыве скачанный
или загружаемый файл может быть частичным; атомарность и service-owned queue — план v1.0.

## Сохранённая Gradle baseline

artifacts/LocalLS-v0.9-debug.apk — 1,307,735 байт,
SHA-256 `cba474fb936149b68ec701d9aa98926b8b74cec074a76b8939df3e47dc99202f`. Для этой предыдущей сборки было настоящее
`BUILD SUCCESSFUL` (39 секунд, 73 задачи). Она не содержит последних иконок,
popup сервера, batch download и minimize-to-tray. Не путать её с актуальным preview.

artifacts/HomeServerControl-v0.8-debug.apk находится в отдельном сохранённом проекте
/workspace/HomeServerControl/HomeServerControl_v0.8, и его успешная сборка сохранена.
