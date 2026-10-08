# BUILD_REPORT — LocalLS v0.9, обновлённая Gradle-сборка

Дата: 7 октября 2026. **BUILD SUCCESSFUL** для актуального приложения с иконками,
меню сервера, выделением файлов, скачиванием в выбранную папку и сворачиванием.
Основной APK теперь действительно собран Gradle, а не запасным SDK pipeline.

## Результат и APK

Команда выполнена реально:

```bash
./gradlew --no-daemon clean assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
```

Результат: **BUILD SUCCESSFUL in 1m 53s**, 73 задачи выполнены.
Лог: artifacts/logs/rebuild-2026-10-07.log.
После исправлений тестов повторные assembleDebugAndroidTest + lintDebug также успешны;
логи rebuild-test-update, rebuild-test-fix, rebuild-tray-test-fix и rebuild-tray-lifecycle-test.
Изменялись тестовые исходники, production APK оставался тем же.

| Параметр | Значение |
|---|---|
| APK Android build output | app/build/outputs/apk/debug/app-debug.apk |
| Основной артефакт | artifacts/LocalLS-v0.9-debug.apk |
| Размер | 1,335,010 байт |
| SHA-256 | `99c6051b641503e554ea501f54dc8d4089be53a8fb074e8ebb1c12649a369247` |
| Package / namespace | com.bitpoint.homeservercontrol |
| Название / версия | LocalLS / 0.9.0, versionCode 9 |
| Gradle | 9.6.0, официальный Wrapper |
| Android Gradle Plugin | 9.4.1 |
| compileSdk / targetSdk | 37 / 37 |
| minSdk | 24 — Android 7.0+ |
| Android Platform | android-37.0, revision 2, Android 17 |
| Build Tools / Platform Tools | 36.0.0 / 37.0.1 |
| JDK | Eclipse Temurin 17.0.20.1+1 |
| Runtime dependencies | JSch 2.28.7, Kotlin stdlib 2.3.21, JetBrains annotations 23.0.0 |

minSdk совместим с Samsung Galaxy A01 на Android 10. compile/targetSdk 37
не требуют Android 17 для установки. Установка в эмулятор Android 10 через
`adb install -r artifacts/LocalLS-v0.9-debug.apk` завершилась **Success**.
Package и signing certificate сохранены для обновления предыдущих debug APK.

## Функции в этом APK

- Компактный Dashboard с основной информацией, графиком скорости SFTP и отклика SSH.
- Иконки над небольшими подписями, тонкая полупрозрачная чёрная окантовка кнопок,
  фиолетовая тема по умолчанию и четыре дополнительные темы.
- Вертикальная шторка справа на половину экрана: подключение, инструменты, темы, VPN.
- Серверное popup-меню: мастер настройки, включить/отключить/перезапустить SSH,
  статус и настройки независимого Realme Agent. Это управление SSH-службой,
  а не питанием Realme; агент требуется отдельно.
- Графический мастер на Realme через официальный Termux SAF/RUN_COMMAND,
  без ручного ввода команд. Существующие конфигурация, ключи и автозапуск сохраняются;
  новый пароль задаётся только явно. Публичный профиль экспортируется после SSH login.
- Прикрепление и подтверждённая отправка файлов, Android Share, открытие серверной папки.
- Выделение до 256 файлов в разных папках и скачивание в выбранную через Android SAF
  локальную папку. При совпадении имён создаются новые имена с номером. Навигация по
  папкам сохраняется; рекурсивное скачивание всей папки не реализовано.
- Сворачивание через «Свернуть» в шторке и проводнике: задача уходит в фон,
  постоянное Android-уведомление возвращает приложение. При возврате оно убирается.
  Активные передачи имеют отдельный foreground service.
- Тумблер официального Tailscale-клиента в шторке, действительное состояние VPN Android.
  Отдельные входы/разрешения на телефоне, Realme и ПК остаются обязательными.

## Проверки APK и аудит

- APK существует, не пустой и побайтно совпадает с Gradle build output.
- Android build-tools aapt прочитал binary Manifest, package, launcher, min/targetSdk.
- apksigner: v2 подпись валидна для API 24–29; сертификат совпадает с предыдущим APK.
- zipalign 4/16 и ZIP CRC — успешно. DEX Adler-32/SHA-1 проверены.
- Новые IconButtons, LocalDownloadFolder, TrayNotificationReceiver,
  ServerSetupActivity и TransferForegroundService присутствуют в DEX.
- Статический tools/audit_project.py: **0 errors, 0 warnings**.
- Android lint: **0 ошибок, 291 предупреждений**. Предупреждения включают
  локализацию русских UI-строк, стили, размеры layouts, устаревшие проверки API,
  версии зависимостей и штатные пути Termux; их нельзя выдавать за отсутствие предупреждений.
  Полные отчёты сохранены в artifacts/lint-results-debug.xml и .html.
- Python tools/agent компилируются; shell bootstrap проходит bash -n.
- Случайный пароль из изолированного SSH fixture отсутствует в исходниках,
  опубликованных логах и APK. Реальных пользовательских секретов здесь не было.
- Проверен Keystore roundtrip без plaintext в prefs, секреты исключаются из Intent/Bundle,
  сообщения ошибок и тестовые журналы редактируются до публикации.
- Все файлы realme-agent побайтно совпадают с v0.8. Рабочий APK v0.8 сохранён неизменным.

## Реально выполненные runtime-тесты

Android 10 / API 29, эмулятор 720×1520. SSH/SFTP — изолированный Paramiko fixture;
домашний адрес пользователя и физический Realme не использовались.

Финальный ручной холодный запуск через am start -W: **Status: ok**, TotalTime 10361 мс
на программном эмуляторе. Лог: artifacts/logs/final-launch-api29.txt.
В финальном logcat нет FATAL EXCEPTION; опубликованная копия редактируется до записи.
Проверенные снимки интерфейса находятся в artifacts/screenshots/.

После целевых повторов: **25 уникальных тестов прошли, 1 пропущен, 0 остающихся ошибок**.
Это сводка последних результатов отдельных тестов, а не заявление об одном полном
запуске без ошибок. Первый полный запуск: 26 обнаружено, 23 прошли, 2 ошибки тестов,
1 пропуск. Исправленные проверки menu/tray запущены повторно; production APK не менялся.
Машинная сводка: artifacts/test-summary-api29.json. Исходные логи:
artifacts/logs/instrumentation-api29.txt, instrumentation-menu-tray-api29.txt,
instrumentation-tray-api29.txt.

Проверялись:

- Запуск Dashboard и всех дополнительных экранов, сохранение IP/порта/пользователя,
  неправильный порт, сохранение пяти тем и смена темы при recreate.
- Иконки над маленькими подписями, правая шторка 50%, закрытие кнопкой Back.
- Серверное popup-меню, скрытые настройки доступа, закрытие и повторное открытие.
- Local/Tailscale-профили, выключенный fallback, отсутствие фиктивного VPN без клиента.
- Передача пароля в сессию только один раз, Keystore, redaction, отсутствие пароля в Intent.
- SSH login, неверный пароль/IP, первый trust, блокировка fingerprint mismatch до команд/SFTP.
- SFTP upload/download 128 КиБ с побайтным сравнением, mkdir, rename, удаление файла,
  запрет удаления непустой папки.
- Прикрепление файла без немедленной отправки, подтверждение, переход к папке,
  реальная отправка и скачивание с побайтным сравнением.
- Передача 8 МиБ после Home: foreground service, измеренная скорость, история,
  освобождение foreground guard после завершения очереди.
- «Свернуть»: реальное STOPPED-состояние Activity, постоянное уведомление,
  возврат через его PendingIntent, RESUMED-состояние и удаление уведомления.
- Мастер: безопасный старт, отсутствие сохранённого пароля, независимый UTF-8 PBKDF2-вектор,
  сохранение Termux properties, публичный импорт и запрет замены доверенного ключа,
  отклонение неподходящего/неуспешного callback.

## Найденные проблемы и исправления

1. Gradle ранее блокировался из-за запрета локальных сокетов. Разрешённый пользователем
   дополнительный сетевой доступ теперь сработал; выполнена нормальная Gradle-сборка.
   Wrapper и Gradle бинарники не изменялись для обхода ограничения.
2. Проверка закрытия серверного меню искала панель до асинхронного OnDismiss callback.
   Исправлена на ожидание возврата панели, сохранено реальное повторное открытие popup.
3. ActivityScenario.getState/close не поддержал переходы задачи при возврате через
   PendingIntent. Tray-тест использует реальный ActivityLifecycleMonitor и Instrumentation;
   проверки ухода в фон, уведомления и возврата сохранены.
4. Исправленные ранее Android PBKDF2-вектор и ожидание отправки из AlertDialog теперь
   реально прошли на API 29.
5. Первый ручной am start -W вернул timeout на программном эмуляторе. Последующие
   Activity launch/foreground/tray-тесты подтвердили запуск и работу приложения;
   этот тайм-аут не выдаётся за успешный первый запуск.

## Что не проверено на физических устройствах

Samsung Galaxy A01 и Realme GT Neo 2 здесь не подключены. Полный Termux bootstrap
пропущен: отсутствует системное разрешение корневой папки Termux. Реальные создание
сервера с нуля, сохранение процесса sshd после мастера, питание/батарея Realme,
команды Realme Agent, Tailscale tailnet трёх устройств и 2–5 ГБ передачи не проверены.
Пакетное скачивание через системный выбор произвольной папки требует отдельного
runtime smoke-test на телефоне; существующие SFTP-download/SAF-пути проверялись.

Очередь пока принадлежит FilesActivity: Home/«Свернуть» поддержаны, уничтожение Activity
отменяет её. Без активной передачи уведомление не является вечным service, Android может
выгрузить процесс. При обрыве или отмене возможен частичный файл. Перенос очереди в
service и атомарность — отдельная основа v1.0 в docs/V1_0_FOUNDATION.md.

## Сохранённые версии

- Рабочая v0.8: /workspace/HomeServerControl/HomeServerControl_v0.8/artifacts/
  HomeServerControl-v0.8-debug.apk; SHA-256
  `7dcf2b3fb01a7ab6ead039704fcbe84e343f347d65e1a4d3688de42a7272fc83`.
- Предыдущая Gradle v0.9: artifacts/baseline/LocalLS-v0.9-debug.apk,
  1,307,735 байт; SHA-256
  `cba474fb936149b68ec701d9aa98926b8b74cec074a76b8939df3e47dc99202f`.
- Исторический SDK preview: artifacts/LocalLS-v0.9-preview.apk,
  SHA-256 `78452d86d992252b89d5bf33b9ad34d2847ae583ec5e1a20eeadc18e699b3a9e`.

Для установки актуальной версии используй **artifacts/LocalLS-v0.9-debug.apk**.
