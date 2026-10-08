# Audit v0.8

## Статус
Release-candidate подготовлен. APK **не объявляется готовым**, пока нет реального Android `BUILD SUCCESSFUL`.

## Выполнено
- [x] versionCode 8 / versionName 0.8.0.
- [x] minSdk 24, compile/targetSdk 37.
- [x] XML и AndroidManifest парсятся без ошибок.
- [x] Все `R.id`/layout ссылки проходят статический аудит.
- [x] Manifest-компоненты имеют соответствующие Java-классы.
- [x] `TransferForegroundService` объявлен как `dataSync` и не управляет `sshd`.
- [x] Добавлены `FOREGROUND_SERVICE` и `FOREGROUND_SERVICE_DATA_SYNC`.
- [x] SFTP-очередь включает foreground service только при первой активной задаче и выключает после последней.
- [x] CPU/Wi-Fi locks из v0.7 сохранены и освобождаются после очереди.
- [x] FilesActivity переживает обычный rotation без пересоздания Activity.
- [x] Диагностика клиента показывает модель/RAM/low-RAM flag без секретов.
- [x] Python agent и audit scripts проходят `py_compile`.
- [x] Shell scripts проходят `bash -n`.
- [x] Статический проектный аудит: 0 errors, 1 warning.

## Единственное предупреждение статического аудита
`gradle/wrapper/gradle-wrapper.jar` отсутствует в исходном архиве. Build scripts и GitHub Actions умеют восстановить wrapper через официальный Gradle 9.6.0 при наличии сети.

## Реальный build attempt в текущей среде
`tools/bootstrap-and-build.sh` действительно запущен.

Результат:
- статический аудит прошёл;
- Java доступна;
- build остановлен до Gradle/AGP стадии, потому что `ANDROID_SDK_ROOT` отсутствует;
- Android SDK в текущем контейнере не установлен.

См. `BUILD_ATTEMPT_v0.8.txt`.

## Smoke-test после получения APK
1. Установка на Samsung Galaxy A01.
2. Local SFTP к Realme GT Neo 2 (`192.168.1.82:8022`, если DHCP не изменил адрес).
3. Первый trust fingerprint и блокировка mismatch.
4. Upload/download большого файла 2–5 ГБ с выключенным экраном клиента.
5. Проверка foreground notification во время передачи.
6. Проверка одного retry после краткого Wi-Fi обрыва.
7. Cancel current / clear queue.
8. Local discovery при смене IP Realme.
9. Tailscale профиль позже, не меняя локальную схему.
10. Проверка, что пароль/token не попадают в diagnostics/logs.


## Последующий реальный build gate

Исходный инфраструктурный blocker снят. Официальный Wrapper восстановлен,
Android SDK/JDK настроены и debug APK действительно собран.
Актуальный аудит, исправления, результаты тестов и SHA-256: `BUILD_REPORT.md`.

## Компактный интерфейс — 6 октября 2026

Фиолетовые кнопки, две панели графиков на Dashboard и график SFTP в FilesActivity
реализованы. Подробное управление раскрывается отдельно, функции сохранены.
Статический аудит: 0 errors, 0 warnings. Повторная clean Android-сборка успешна.
Актуальные результаты runtime-проверок и контрольная сумма: `BUILD_REPORT.md`.
