# BUILD_REPORT — LocalLS 0.9.3

Дата: 7 октября 2026. Реальный debug APK собран Android Gradle.

## Итог

**BUILD SUCCESSFUL in 43s**, 73 задачи выполнены.

```bash
./gradlew clean assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
```

Лог: `artifacts/logs/build-v0.9.3-final.txt`.
Статический аудит: 0 errors, 0 warnings.
Android Lint: 0 errors/fatal, 317 warnings. Предупреждения о строках,
Autofill и layout не скрывались baseline или отключением проверки.

## Конфигурация и APK

| Параметр | Значение |
|---|---|
| Имя / package | LocalLS / com.bitpoint.homeservercontrol |
| versionCode / versionName | 12 / 0.9.3 |
| Gradle / Android Gradle Plugin | 9.6.0 / 9.4.1 |
| compileSdk / minSdk / targetSdk | 37 / 24 / 37 |
| JDK | Eclipse Temurin 17.0.20.1+1 |
| Android Platform | android-37.0, revision 2 |
| Build Tools / Platform Tools | 36.0.0 / 37.0.1 |
| SSH/SFTP dependency | com.github.mwiede:jsch:2.28.7 |
| APK | artifacts/LocalLS-v0.9.3-debug.apk |
| Размер | 1,359,122 байт |
| SHA-256 | 596a64d653aafed579cce21ab6f04c3c2f8275a8dc3e1c48e058620f55914b71 |

Проверены settings/build Gradle Kotlin DSL, официальный Wrapper с закреплённой SHA-256,
зависимости, Manifest и SDK-конфигурация. SDK/JDK находятся в `/workspace/toolchains`.
minSdk 24 совместим с Galaxy A01 на Android 10/11; targetSdk не повышает минимум для установки.

APK побайтно совпадает с `app/build/outputs/apk/debug/app-debug.apk`, реально существует
и имеет ненулевой размер. aapt читает package, launcher и бинарный Manifest.
apksigner подтверждает v2-подпись для API 24–36, включая Android 16.
zipalign `-c -P 16 4` и CRC записей корректны. Debug-сертификат совпадает с 0.9.2;
`adb install -r` на Android 10 x86: Success. Metadata: `artifacts/apk-verification.json`.

## Подтверждённая ошибка пользователя

Пользователь прислал отчёт Termux **0.119.0-beta.3, F-Droid**, Android 16/API 36:
RUN_COMMAND отказан до исполнения с **err = 2**, поскольку не применено
`allow-external-apps=true`. Вывод команд и exitCode отсутствовали: sshd ещё не запускался этой командой.

Официальный исходник 0.119.0-beta.3 проверяет `TermuxAppSharedProperties.shouldAllowExternalApps()`
из кэша. В 0.118.3 PluginUtils читал значение с диска. Поэтому проверка предыдущей
версии Termux не выявила этот отказ: запись файла через SAF не обновляет кэш 0.119.

## Исправления

1. Перед RUN_COMMAND файл проверяется стандартным Properties parser, записывается
   и перечитывается через штатный SAF. Некорректный файл блокирует команду и смену пароля.
2. Исправлен незавершённый escape в конце Properties-файла: Android parser пропускает
   пустые строки продолжения и мог поглотить добавленное разрешение. Непарный EOF-escape,
   который уже отбрасывает parser, удаляется перед добавлением нового ключа.
3. Для Termux с кэшем мастер открывает официальный экран, отправляет пакетные
   `com.termux.app.reload_style` с `EXTRA_RECREATE_ACTIVITY=false`, затем продолжает
   подготовку после возврата в LocalLS. Broadcast применяется только к видимому Termux;
   никакой команды для обхода запрета RUN_COMMAND не используется.
4. Статус применённой версии сохраняется только после успешного native callback.
   Настоящий policy refusal очищает его. Слишком быстрый возврат просит повторить шаг.
5. Известные признаки errmsg преобразуются в фиксированные инструкции с числовыми
   кодами. Сырой errmsg/stdout/stderr и секреты не сохраняются. Добавлена помощь с Termux/разрешениями.
6. Подсказка уточняет возврат: первый Back может скрыть клавиатуру; нужно вернуться
   именно в LocalLS. Test driver проверяет фактическое окно, а не предполагает успех по одному Back.

В этой работе реальные промежуточные прогоны падали: незавершённый Properties escape,
раннее нажатие кнопки проверки и возврат, который только скрывал клавиатуру Termux.
Исправления внесены, clean-сборка повторена. Журналы попыток сохранены.

## Реально выполненные тесты 0.9.3

Прицельный окончательный прогон ServerSetupInstrumentedTest + TermuxBootstrapInstrumentedTest:
**9 passed, 0 skipped, 0 failures**, 310.895 с.
Лог: `artifacts/logs/instrumentation-termux-v0.9.3.txt`.

Эмулятор Android 10/API 29/x86. Установлен настоящий официальный GitHub APK
Termux **0.119.0-beta.3**, APT Android 7, с совпадающим сертификатом тестового 0.118.3.
Это та же версия исходников, но другая подпись/канал APK, чем F-Droid на физическом телефоне.

- Реальное выключение external-apps в тестовом Termux, обновление его кэша и native
  RUN_COMMAND отказ. Проверены правильная причина, числовой код и очищенный pending.
- Отдельно воспроизведён кэш false при файле true — случай первоначального мастера.
- Настоящий UI: быстрый запуск мастера, кнопка «Открыть и применить», экран Termux,
  возврат в LocalLS, native callback после применения настройки.
- Настоящая SSH password-аутентификация и SFTP upload/download 32 KiB с полным сравнением.
- Повторная подготовка сохраняет пароль, fingerprint, PID sshd и SHA-256 sshd_config.
- GUI-проверка входа разрешает экспорт публичного профиля.
- Properties continuation/malformed Unicode, независимый PBKDF2 vector, сохранение других настроек.
- Nonce/exitCode/err -1 и err 0, отсутствие raw error и фактического fixture-пароля в статусе.
- Публичный профиль без секретов, блокировка уже доверенного ключа, пароль не сохраняется/recreate.

Полный прежний прогон 33 тестов UI/Files/tray/SSH/SFTP зафиксирован отдельно в
`docs/BUILD_REPORT_v0.9.2_BASELINE.md`. В 0.9.3 выполнен указанный прицельный прогон;
полный набор всех экранов заново не запускался.

## Аудит сохранности

Исходные файлы realme-agent совпадают с 0.9.2/v0.8. SCRIPT установки OpenSSH,
host keys, sshd_config, схема автозапуска не заменены. Reload обновляет настройки
приложения Termux; процесс SSH и его службу не останавливает. Настройка через штатный Termux сохранена.
Предыдущие APK 0.8–0.9.2 сохранены отдельно.

Источники, журналы, APK entries и logcat проверены на реальные приватные fixture-пароль
и agent token; значения не публикуются. Crash buffer проверен. Результат:
`artifacts/logs/runtime-audit.json`. SDK local.properties и приватный keystore в архив не входят.
Основа v1.0 обновлена в `docs/V1_0_FOUNDATION.md`.

## Шаги на телефоне

Установить APK поверх 0.9.2, открыть мастер, нажать «Подготовить сервер».
Нажать «Открыть и применить», подождать около 5 секунд в Termux, вернуться в LocalLS
через Back; при открытой клавиатуре нажать Back ещё раз. Вводить команды не требуется.
Затем выполнить «Проверить SSH и пароль» перед экспортом.

## Ограничения

Физический Android 16 пользователя не подключён: исправление требует установки
и повторной проверки на нём. Runtime подтверждён в эмуляторе с Termux beta.3;
подпись APK проверена на API 36, это не заменяет runtime-тест Android 16.
Реальные Wi-Fi/Tailscale трёх устройств, 2–5 ГБ и длительные ограничения батареи здесь не проверены.
