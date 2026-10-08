# Основа v1.0

v0.8 остаётся самостоятельной рабочей версией: applicationId
`com.bitpoint.homeservercontrol`, versionCode 8, versionName 0.8.0, minSdk 24.
APK и его SHA-256 фиксируются в BUILD_REPORT.md. Версию 1.0 разрабатывать
в отдельной ветке/копии; не перезаписывать проверенный APK v0.8.

## Уже подготовлено

- Официальный Gradle Wrapper с проверкой SHA-256 дистрибутива.
- Реальные Android-сборки приложения и instrumentation APK.
- Регрессионные проверки настроек, Keystore, fingerprint, SSH/SFTP и фоновой очереди.
- `tools/verify_apk.py`: package, SDK, binary Manifest, подпись, выравнивание и SHA-256.
- Обработка новых Android: local-network permission, системные отступы, dataSync timeout.
- Защита секретов: Keystore, отключённый backup/saved state, передача пароля внутри процесса.

- Компактные фиолетовые панели, живой график скорости и история SFTP-измерений.

## Следующие изменения только в v1.0

1. Перенести реальное выполнение очереди из FilesActivity в foreground service.
   В v0.8 Home проверен на эмуляторе; выключение экрана требует проверки на Samsung.
   Закрытие Activity отменяет очередь.
   Переход на service должен сохранить отмену, один retry, SAF и освобождение locks.
2. Явная обработка лимита dataSync Android 15+: пауза/отмена с записью в историю.
   Текущий onTimeout останавливает service и предотвращает системное падение;
   это не обещание бесконечной фоновой передачи на новых Android.
3. Атомарные upload через временное имя + rename, чтобы отмена/обрыв не оставляли
   частично перезаписанный целевой файл. До этого предупреждать при замене файла.
4. Проверить отмену/повтор/потерю Wi-Fi, поворот экрана, очистку очереди, файлы 2–5 ГБ
   и ограничения батареи Samsung непосредственно на Galaxy A01 + Realme.
5. Транспорт агента: спланировать TLS/Tailscale и отказ от передачи Bearer token
   по обычному HTTP. Существующий протокол v0.8 оставить совместимым до миграции.
6. Вынести строки в resources, улучшить accessibility и маленький/повреждённый экран.
7. Release signing через внешнее хранилище секретов. Не коммитить keystore/пароли.

## Gate v1.0

- `python3 tools/audit_project.py` — 0 errors.
- `./gradlew clean assembleDebug :app:assembleDebugAndroidTest :app:lintDebug` — BUILD SUCCESSFUL.
- `python3 tools/verify_apk.py` — валидный настоящий APK.
- Instrumentation без failures и проверка API 24 / 29 / нового Android.
- Ручной smoke test на Samsung + Realme, включая крупные файлы и сбои Wi-Fi.
- Release signing, миграция настроек, установка обновления поверх v0.8 без потери профилей.
- Схема автозапуска sshd остаётся единственной; stop/restart только независимым агентом.
