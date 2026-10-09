# Модули LocalLS

Рефакторинг от 8 октября 2026 на основе main 101a888. applicationId, имена экранов,
ключи SharedPreferences, alias Android Keystore и серверный протокол сохранены.

| Модуль | Ответственность | Зависимости |
|---|---|---|
| core | Конфигурационные константы, модели ответов, секреты в памяти, скрытие секретов | JDK 17 |
| data | SharedPreferences, Keystore, история, SAF и данные скорости | core, Android |
| transport | SSH/SFTP, pinning, поиск сервера, HTTP-клиент агента | core, Android JSON, JSch |
| ui | Темы, рисунки/рамки, типографика, графики и раскрывающиеся разделы | core, data, Android |
| app | Экраны, Android services/receivers и связывание функций | все четыре модуля |

UI-ресурсы используют namespace com.bitpoint.homeservercontrol.ui. Экраны и их R.id/R.layout
остаются в app; ссылки на R.attr/R.color/R.drawable/R.style оформлены через UI namespace.
Non-transitive R остаётся включённым.

Java package com.bitpoint.homeservercontrol сохранён при переносе: это поддерживает
существующие package-private API и instrumentation tests. Это ограничение текущего этапа:
модули разделены сборкой и проверкой направления зависимостей, но Java package пока общий.
Следующий этап — явные публичные интерфейсы и отдельные пакеты, выделение контроллеров
MainActivity/FilesActivity и переноса владельца очереди в service. Перенос файлов сам по себе
не решает зависимость очереди от lifecycle Activity.

Проверки:

```text
python tools/audit_project.py
python tools/audit_architecture.py
./gradlew :core:test :transport:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

Последняя команда требует подключённого Android-устройства/эмулятора. Сборка test APK
не означает выполнение instrumentation tests. Сетевые SSH/SFTP fixture tests запускаются
только с явными параметрами отдельного тестового сервера.

ActionGridLayout переносит действия в дополнительные строки при крупном шрифте.
ControlsScrollView оставляет место для списка файлов при увеличении текста.
Результаты двух тестов отрисовки (18 изображений) и полного Gradle build gate
зафиксированы в LOCAL_BUILD_REPORT.md и docs/verification-2026-10-09.json.
