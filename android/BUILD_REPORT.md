# BUILD_REPORT — LocalLS: универсальные названия и молния

Дата: 7 октября 2026. **BUILD SUCCESSFUL**.

## Изменения

- Производители и модели телефонов убраны из заголовков, кнопок, подсказок,
  диалогов доверия SSH, VPN-инструкций и мастера настройки.
- Используются названия «Сервер», «Серверное устройство», «Другое устройство».
  Главная подпись: «Личный сервер • SSH / SFTP».
- Значок: белое облако с золотой молнией на фиолетовом фоне. Стрела удалена.
  Обновлены обычная и адаптивная иконки, monochrome для Android 13+ и уведомления.
  Превью в artifacts/icons/localls-lightning.png и .svg получено из vector paths.
- Соединения, серверные команды, адреса профилей, host keys и схема запуска sshd
  не менялись. Файлы серверного агента и рабочая v0.8 сохранены.

## Сборка

```bash
./gradlew --no-daemon clean assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
```

Финальная сборка после замены иконки: **BUILD SUCCESSFUL in 1m 45s**, 73 задачи.
Лог: artifacts/logs/neutral-lightning-build-2026-10-07.log.
Промежуточная сборка текстов также успешна; она не публикуется как итоговая версия.

| Параметр | Значение |
|---|---|
| Gradle / Android Gradle Plugin | 9.6.0 / 9.4.1 |
| compileSdk / minSdk / targetSdk | 37 / 24 / 37 |
| JDK | Eclipse Temurin 17.0.20.1+1 |
| SDK Platform | android-37.0 revision 2 |
| Build Tools / Platform Tools | 36.0.0 / 37.0.1 |
| Package | com.bitpoint.homeservercontrol |
| Название / версия | LocalLS / 0.9.0, versionCode 9 |
| Android build output | app/build/outputs/apk/debug/app-debug.apk |
| Основной APK | artifacts/LocalLS-v0.9-debug.apk |
| APK для скачивания | artifacts/LocalLS-v0.9-lightning-debug.apk |
| Размер | 1,335,730 байт |
| SHA-256 обоих APK | `6fd419f1d9dfc210cd24561f86b160d9357779b4d9bd15741e14f4ea877690ce` |

Оба итоговых файла — побайтные копии настоящего Gradle build output.
Сертификат совпадает с предыдущим debug APK: обновление сохраняет данные приложения.
minSdk 24 совместим с Android 10 на основном тестовом телефоне; compile/targetSdk 37
не требуют новой версии Android для установки.

## Реально выполненные проверки

- Статический аудит: 0 errors, 0 warnings; Java/XML и ресурсы скомпилированы.
- Android lint: 0 ошибок, 291 предупреждение; отчёты в artifacts/lint-results-debug.*.
  Остаются прежние замечания по русским UI-строкам, стилям, layout и версиям зависимостей.
- APK существует и не пустой. Android aapt прочитал binary Manifest, package,
  launcher и SDK. apksigner проверил v2 подпись на API 24–29; zipalign 4/16 и ZIP CRC валидны.
- Поиск названий устройств в Java/XML и compiled Android resources: совпадений нет.
- Проверен сертификат подписи относительно предыдущего APK — тот же.
- Проверен случайный секрет из SSH fixture: отсутствует в app sources, опубликованных
  логах и APK. Пользовательских секретов здесь не было; логи редактируются до публикации.
- adb install -r актуального APK на эмулятор Android 10 / API 29: **Success**.
- Три существующие instrumentation-проверки актуального APK: **OK (3 tests)**,
  70.209 секунды, без пропусков и ошибок. Проверены запуск дополнительных экранов,
  история, иконки функций, серверное popup-меню, его закрытие/повторное открытие,
  безопасный запуск мастера и отсутствие сохранённого пароля.
  Лог: artifacts/logs/instrumentation-neutral-lightning-api29.txt.
  Машинная сводка: artifacts/test-summary-api29.json.

Полная предыдущая проверка функций дала 25 успешных тестов и один пропуск после
целевых исправлений. Она не выдаётся за повторный полный прогон нового APK.
Исторический отчёт: docs/BUILD_REPORT_v0.9_BEFORE_NEUTRAL_UI.md.

Ручная проверка после пробуждения экрана эмулятора: am start -W вернул **Status: ok**,
текущий экран MainActivity. Лог: artifacts/logs/neutral-launch-api29.txt; снимок:
artifacts/screenshots/localls-main.png. До пробуждения ручной вызов возвращал timeout;
это не выдаётся за успешный первый запуск. FATAL EXCEPTION в проверенном logcat нет.

## Ошибки и ограничения

Ошибок компиляции и сборки при этой правке не возникло. Проверка промежуточного
состояния до изменения иконки не использовалась вместо финальной пересборки.

Физические устройства клиента и сервера здесь не подключены. Полная настройка
Termux с нуля, реальные команды серверного агента, Tailscale на телефоне/сервере/ПК,
произвольная папка системного picker и 2–5 ГБ передачи остаются device smoke-tests.
Очередь по-прежнему живёт в FilesActivity: Home/сворачивание поддержаны, уничтожение
Activity отменяет очередь; при обрыве возможен частичный файл. Основа v1.0 в
 docs/V1_0_FOUNDATION.md сохраняется отдельно.

Предыдущий APK до универсальных надписей и молнии сохранён:
artifacts/baseline/LocalLS-v0.9-before-neutral-debug.apk, SHA-256
`99c6051b641503e554ea501f54dc8d4089be53a8fb074e8ebb1c12649a369247`.
