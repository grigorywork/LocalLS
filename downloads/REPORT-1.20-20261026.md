# LocalLS 1.20 / code42

Номер изменён по выбору владельца. APK устанавливается обновлением поверх 0.11.21/code41: package com.bitpoint.homeservercontrol и прежний сертификат сохранены, versionCode42 >41.

Изменены только release.properties и проверка формата Android versionName (теперь допускает две или три числовые части). Код функций и дизайн совпадают с проверенной сборкой аудита. Для 1.20 пройдены19Python, аудит проекта/архитектуры, lintDebug/lintRelease, assembleRelease и assembleDebugAndroidTest; BUILD SUCCESSFUL. Дополнительно проверены корректные/некорректные варианты метаданных, apksigner API24–36, zipalign и совместимость обновления с подписанным APK code41.

Полный набор415JVM был пройден до изменения только номера версии; заново для1.20 не запускался. [Подробный аудит и известный единичный таймаут](https://github.com/grigorywork/LocalLS/blob/2bd429d937f2c234b994c053874e2d64f2c35c39/downloads/REPORT-audit10h-final-20261026.md). Физических телефонов в среде нет. SSH-ключи/доверие/настройки не менялись. Прежние APK и release-ветка/тег сохранены.

SHA256 APK: `b2f6330f99e129855d84e542ce8363c5d2f816d926df51f19704898c1d965166`.

Сертификат: `a2230c3032135def93cd5e8224ca3e26ad8e98f294b5e372a33b1e468caa221a`.
