# Audit v0.3

Дата: 2026-10-04

## Выполнено автоматически

- [x] `activity_main.xml` парсится как XML.
- [x] `activity_files.xml` парсится как XML.
- [x] `row_remote_file.xml` парсится как XML.
- [x] `AndroidManifest.xml` парсится как XML.
- [x] Все Java-ссылки `R.id.*` имеют соответствующий `@+id` в layout.
- [x] Все компоненты `.MainActivity`, `.FilesActivity`, `.ServerMonitorJobService` из Manifest имеют Java-классы.
- [x] У всех Java-файлов сбалансированы фигурные скобки.
- [x] `javac` не показал синтаксических/структурных Java-ошибок; оставшиеся diagnostics связаны с отсутствующим Android SDK/JSch classpath в среде аудита.
- [x] Поиск по проекту не нашёл ранее засвеченный пароль и не нашёл хардкод реального password/token.
- [x] Версия приложения поднята до `0.3.0`, versionCode `3`.

## Риски, которые требуют реального Android build/device test

- [ ] Android SDK 37 + AGP 9.4.1 Gradle Sync.
- [ ] Совместимость JSch 2.28.7 с фактической Gradle/JDK сборкой.
- [ ] Storage Access Framework на Nokia 5 / Android 7.
- [ ] SFTP upload/download с кириллицей и крупными файлами.
- [ ] JobScheduler на прошивке Nokia/Realme и влияние энергосбережения.
- [ ] POST_NOTIFICATIONS на Android 13+.
- [ ] Поведение очереди при потере Wi-Fi.
- [ ] Реальная читаемость UI на повреждённом дисплее Nokia 5.

## Release gate

Не считать v0.3 production-ready, пока не пройдены:
1. `assembleDebug`;
2. установка APK на Nokia 5;
3. минимум один upload + download через встроенный SFTP;
4. notification test server-down/recovered;
5. проверка diagnostic export без секретов.
