# LocalLS 1.33 / code55

[Скачать APK](../LocalLS-1.33-code55-devices-20261028.apk) · [SHA-256](../LocalLS-1.33-code55-devices-20261028.apk.sha256) · [Короткая инструкция](https://github.com/grigorywork/LocalLS/blob/main/android/app/src/main/assets/phone-setup.txt)

- Файловый менеджер различает телефон, планшет и ПК: вкладки, кнопки копирования и меню. Для точной подписи удалённого устройства обнови LocalLS с обеих сторон; старый сервер остаётся «Сервером».
- Встроенная справка сокращена до пяти шагов. Главная инструкция GitHub обновлена отдельно, release-ветка/тег сохранены.
- Прежние ключи, доверие, настройки и дизайн сохранены. APK обновляет1.32/code54 с прежним сертификатом.

Проверки: **462 JVM +19 Python +9 Node**, без падений/пропусков; все455 прежних JVM ID и102 ссылки матрицы сохранены. Lint debug/release:0 Error/Fatal, по153 Warning. **BUILD SUCCESSFUL**; release/debug/androidTest собраны. Подпись API24–36, ZIPalignment и совместимость обновления проверены. Старые артефакты не заменялись.

Среда: native Robolectric и реальный loopback SSH/SFTP; физические телефоны/планшеты и Windows runtime не проверялись. Windows-установщик этим APK не является.

Автор: **MSS9LN**. Package: `com.bitpoint.homeservercontrol`.

SHA-256 APK: `e4a2a6285ee407ca91683573ef280acd9e2d52be785ab6dc468718a82cc0ef56`.
Сертификат SHA-256: `a2230c3032135def93cd5e8224ca3e26ad8e98f294b5e372a33b1e468caa221a`.
