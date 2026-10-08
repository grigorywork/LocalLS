# BUILD REPORT — Home Server Control v0.8

Дата: 6 октября 2026 года (Europe/Moscow).

## Итог

**BUILD SUCCESSFUL. Настоящий устанавливаемый debug APK создан.**

Команда итоговой Android-сборки:

```bash
./gradlew clean assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --stacktrace
```

Итог: `BUILD SUCCESSFUL in 2m 19s`, 73 задачи выполнены.
Подтверждение: `artifacts/logs/purple-ui-final-build-2.log`.
Отдельный первый вызов `./gradlew clean assembleDebug` также реально выполнен:
первоначальная ошибка и успешный повтор сохранены в build-attempt-1/2.log.

| Параметр | Значение |
| --- | --- |
| Gradle | 9.6.0, официальный Wrapper JAR включён |
| Android Gradle Plugin | 9.4.1 |
| compileSdk | 37 |
| minSdk | 24 — Android 7.0 |
| targetSdk | 37 |
| JDK | Eclipse Temurin OpenJDK 17.0.20.1+1, Java compilation target 17 |
| Android SDK Platform | android-37.0, revision 2 |
| Build Tools | 36.0.0 |
| Platform Tools | 37.0.1, adb установлен |
| applicationId | com.bitpoint.homeservercontrol |
| versionCode / versionName | 8 / 0.8.0 |
| Исходный выход сборки | app/build/outputs/apk/debug/app-debug.apk |
| Итоговый APK | artifacts/HomeServerControl-v0.8-debug.apk |
| Размер APK | 1272394 байт |
| SHA-256 APK | `7dcf2b3fb01a7ab6ead039704fcbe84e343f347d65e1a4d3688de42a7272fc83` |

APK скопирован из успешного Android build output; это не переименованный архив.
`aapt`, `apksigner` и `zipalign` проверили структуру, binary Manifest, package,
launcher, SDK, подпись и выравнивание. Подпись v2 валидна для API 24–29.
Нативных ABI-библиотек приложение не содержит: APK не ограничен x86 эмулятора.

Samsung Galaxy A01 изначально работает с Android 10 (API 29), что выше minSdk 24.
compileSdk/targetSdk 37 **не** требуют установки Android 17 на телефон.
AndroidManifest не задаёт maxSdkVersion и не требует более новой версии, чем Galaxy A01.

## Обновлённый интерфейс

- Фиолетовые кнопки, тёмные панели, компактный главный экран и проводник
  в стиле Solid Explorer. Сенсорные кнопки сохраняют высоту не менее 48 dp.
- Главный экран: настройки подключения, основные показатели, два графика —
  фактическая скорость SFTP за 60 секунд и последние измерения отклика SSH.
- Управление собрано в небольшие понятные блоки; подробные команды, агент,
  история и журналы доступны через раскрываемый блок инструментов.
- Проводник: значки папок/файлов, двухстрочные компактные элементы,
  текущая/пиковая скорость и график во время передачи.
- График получает реальные progress samples, без демонстрационных значений;
  средняя скорость SFTP сохраняется в истории. Обновление экрана прекращается
  при сворачивании Activity, фоновая передача продолжает выполняться.
- `artifacts/HomeServerControl-v0.8-purple-debug.apk` — точная копия итогового APK
  с отдельным именем для скачивания обновлённого оформления.

## Найдено и исправлено

1. Отсутствовал `gradle-wrapper.jar`; вместо нестандартных скриптов с неподходящим
   wrapper-download URL установлены официальные gradlew/gradlew.bat/JAR.
   Wrapper генерировался официальным Gradle в отдельном bootstrap-проекте;
   SHA-256 Gradle distribution проверен и закреплён в wrapper properties.
2. Не было Android SDK/JDK 17. Установлены официальный SDK, Platform 37.0,
   Build Tools 36.0.0, Platform Tools и JDK 17. Настроены SDK location,
   writable Android user directory, proxy и Java CA trust для этой среды.
3. Первая Android-сборка падала из-за read-only `/home/agent/.android` при создании
   debug keystore. Служебные Android-файлы перенесены в `/workspace/android-user`.
   Для adb/emulator обеспечен доступ к служебному каталогу пользователя.
4. SDK manager ожидает `platforms;android-37.0`, а исходные bootstrap/CI скрипты
   использовали `platforms;android-37`; имена исправлены.
5. Windows bootstrap присваивал `$home`, конфликтующий с PowerShell `$HOME`.
   Используется `$gradleHome`, добавлены проверки exit codes аудита и SDK manager.
6. Пароль передавался через Intent extra и мог попасть в системное сохранение task.
   Теперь используется одноразовая передача внутри процесса (SessionPassword).
   У полей SSH password/agent token отключён saved state. Секреты сохраняются
   только зашифрованными AES-GCM с ключом Android Keystore.
7. Отключены backup, fullBackupContent и cloud/device extraction настроек.
   Добавлено удаление секретов из сообщений SSH, ответов агента, журнала и экспорта.
   HTTP agent redirects отключены, ответы ограничены по размеру, поток закрывается.
8. SHA-256 fingerprints сравнивались без учёта регистра; сравнение теперь точное.
   SSH runCommand без доверенного ключа блокируется до попытки подключения/пароля.
   SFTP освобождает session при сбое connect/openChannel, задан socket timeout.
9. При retry download режим `w` не гарантировал truncation у всех SAF providers;
   используется `wt`. Завершение очереди и освобождение locks/service перенесены
   на UI thread, чтобы исключить гонку с добавлением следующей передачи.
   Закрытие FilesActivity блокирует callback refresh после shutdown executor.
10. Добавлены ограничение времени WakeLock с продлением по прогрессу, foreground
    service onTimeout для Android 15+, повторное открытие существующего FilesActivity
    из уведомления. FilesActivity по-прежнему отменяет очередь при своём уничтожении.
11. Добавлены ACCESS_LOCAL_NETWORK + runtime request для Android 17, обработка
    системных отступов на Android 15+, package visibility для Solid Explorer.
12. Исправлен false positive статического аудита для `android.R.id.content`,
    locale в discovery, актуализированы README и исторический BUILD_BLOCKED.md.
13. Первый расширенный прогон: 13/14 успешны, один тест ошибочно ожидал сброс
    несохранённого текста EditText при recreation. Исправлен порядок проверки:
    проверяется восстановление сохранённого значения и отказ записать неверный порт.

14. Повторная проверка нового графика выявила гонку публикации истории: запись
    «готово» становилась доступна до записи средней скорости. Сначала сохраняется
    метрика скорости, затем публикуется результат завершения передачи.

Ошибок компиляции Java/resources/Manifest после настройки toolchain не было;
функции ради успешной компиляции не удалялись.

## Реально выполненные проверки

- Обязательные AGENT.md, CODEX_TASK.md, README.md, AUDIT_v0.8.md прочитаны.
- Просмотрены все Java-компоненты, Manifest/resources, Gradle settings, зависимости,
  wrapper, install/boot scripts и Python agent. Build gate и дополнительные
  проверки выполнены на Linux; Windows/CI конфигурация проверена статически.
- `python3 tools/audit_project.py`: **0 errors, 0 warnings**.
- `py_compile` audit/validator/agent, `bash -n` bootstrap/install/boot: успешно.
- Debug APK и instrumentation APK собраны; Android Lint: **0 errors, 223 warnings**,
  преимущественно hardcoded Russian strings, SetTextI18n, autofill и оформление.
  Security warnings отсутствуют. Полный отчёт сохранён в artifacts/logs.
- SDK validator: APK существует, размер > 0, CRC/структура валидны,
  Manifest парсится build-tools, package/minSdk/targetSdk/launcher правильные,
  подпись v2 и zipalign проверены.
- adb install -r на Android 10 / API 29 emulator: **Success**.
- Итоговый APK установлен; MainActivity и вторичные экраны успешно запущены
  в instrumentation на программном эмуляторе без KVM.
- Экран эмулятора установлен 720×1520, density 320; снимки Dashboard и
  SFTP с настоящей скоростью сохранены в artifacts/final-dashboard.png и
  artifacts/sftp-speed-preview.png.
- Instrumentation: **OK (14 tests)**, время 231.453 с:
  7 core tests, 4 настройки/экраны/Keystore, 3 SSH/SFTP integration tests.
- Проверены default host/port/user, сохранение настроек и recreation,
  неверный порт, профили Local/Tailscale, fallback по умолчанию false,
  Keystore round-trip без plaintext, History/Diagnostics/Settings/Network/Dashboard.
- Локальный изолированный SSH/SFTP fixture: first-trust probe без password auth,
  успешный пароль, неправильный пароль без падения, mismatch блокирует SSH/SFTP,
  команда, list/mkdir/upload/download с byte-for-byte сравнением 128 КиБ,
  rename, удаление файла и пустой папки, отказ удалить непустую папку.
- Видимый график FilesActivity содержит положительные реальные измерения скорости;
  записана средняя скорость SFTP, состояние «Завершено» сохраняется после закрытия.
- Компактные панели, два графика Dashboard и раскрытие инструментов проверены.
- FilesActivity + SAF: upload **8 МиБ**, active foreground service,
  сворачивание через Home, завершение передачи/запись истории,
  остановка foreground service после опустошения очереди.
- Runtime пароль fixture не найден в исходниках, APK, build/test logs и полном
  emulator logcat. AndroidRuntime crash приложения в проверенном logcat не найден.

Все 14 тестов выполнены на итоговом APK с SHA-256 `7dcf2b3fb01a7ab6ead039704fcbe84e343f347d65e1a4d3688de42a7272fc83`.
После этого код, resources и Manifest не изменялись.
Лог: `artifacts/logs/instrumentation-api29.txt`.

## Физические проверки и ограничения

Samsung Galaxy A01 и Realme GT Neo 2 не подключены к среде. Установка на физический
Samsung, настоящая связь с 192.168.1.82:8022/u0_a606, реальный Tailscale tunnel,
Termux/OpenSSH host key algorithms, agent Start/Stop/Restart, файлы 2–5 ГБ,
Wi-Fi interruption/retry/cancel, rotation, выключенный экран и ограничения батареи
Samsung **не проверялись физически**. Учётные данные пользователя не запрашивались.

Software emulator без KVM загружался медленно; наблюдался отдельный ANR System UI.
Отдельный `am start -W` вернул timeout ожидания отрисовки; приложение фактически
открылось, что подтверждено instrumentation и снимком главного экрана.
ANR System UI не является падением Home Server Control. Результаты эмулятора не заменяют
проверку быстродействия и фоновых ограничений на Samsung.

Текущий Agent протокол использует HTTP с Bearer token (унаследовано из v0.8).
Секретов в репозитории нет, но канал агента не обеспечивает TLS.
Изменение протокола оставлено для отдельной совместимой миграции v1.0.
Очередь живёт в FilesActivity: Home поддерживается, уничтожение Activity отменяет её.
При отмене/сбое overwrite upload возможен частичный целевой файл; атомарные
временные файлы и перенос очереди в service описаны в основе v1.0.

## Realme и v1.0

Все четыре исходных текстовых файла realme-agent (agent.py, README.md, install.sh,
install-boot.sh) побайтно совпадают с исходным архивом. Серверные команды не запускались,
новых механизмов автозапуска sshd не добавлено.

Основа v1.0: `docs/V1_0_FOUNDATION.md`, расширенные instrumentation tests,
`tools/verify_apk.py`, обновлённые build/CI scripts. Версия рабочего APK остаётся 0.8.0.

## Воспроизведение

Обычная среда с JDK 17 и Android SDK:

```bash
python3 tools/audit_project.py
./gradlew clean assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
mkdir -p artifacts
cp app/build/outputs/apk/debug/app-debug.apk artifacts/HomeServerControl-v0.8-debug.apk
python3 tools/verify_apk.py --sdk "$ANDROID_SDK_ROOT"
adb install -r artifacts/HomeServerControl-v0.8-debug.apk
```

Для текущей облачной среды toolchain доступен после
`source /workspace/toolchains/sdk-env.sh`; SDK и локальные credentials не входят в проект.
Обычный connectedAndroidTest запускает core/runtime tests; интеграционные tests
требуют отдельного SSH/SFTP fixture и runtime arguments fixtureHost/Port/User/Password,
при их отсутствии они явно пропускаются. Никакие пользовательские секреты
не должны добавляться в исходники, CI или опубликованные логи.
