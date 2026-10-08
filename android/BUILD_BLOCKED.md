# Исторический build blocker — снят

Android SDK и JDK настроены, реальная сборка успешна.
Актуальное состояние: BUILD_REPORT.md. Ниже — исходная запись из архива.

# BUILD BLOCKED — v0.8

Полный Android APK build в текущей изолированной среде остановлен на инфраструктуре, а не на найденной ошибке исходников.

## Подтверждено фактическим запуском
Команда:

```bash
./tools/bootstrap-and-build.sh
```

прошла static audit и Java check, затем остановилась с:

```text
Android SDK not found. Set ANDROID_SDK_ROOT.
```

Лог сохранён в `BUILD_ATTEMPT_v0.8.txt`.

## Что уже подготовлено для машины с Android SDK
- Windows/Linux one-shot build scripts;
- если `gradle-wrapper.jar` отсутствует и system Gradle не установлен, scripts умеют скачать официальный Gradle 9.6.0 и восстановить wrapper;
- GitHub Actions workflow устанавливает Android SDK packages, Gradle, запускает audit + `assembleDebug` + `assembleDebugAndroidTest`, затем публикует APK artifact и SHA-256 report.

## Критерий снятия blocker
Только реальный:

```text
BUILD SUCCESSFUL
```

и существующий файл:

```text
artifacts/HomeServerControl-v0.8-debug.apk
```

До этого никакой ZIP/JAR не называется APK.
