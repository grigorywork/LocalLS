# Основа LocalLS v1.0

Рабочая Home Server Control v0.8 сохранена отдельно. Новая LocalLS v0.9.3 имеет
тот же applicationId com.bitpoint.homeservercontrol, versionCode 12, versionName 0.9.3
и minSdk 24. v1.0 разрабатывать отдельно; versionCode должен быть больше 12.
Проверенные APK и SHA-256 фиксируются в соответствующих BUILD_REPORT.md.

## Уже подготовлено

- Официальный Wrapper, JDK/SDK toolchain и реальная Android-сборка.
- APK validator: package/SDK/Manifest/signature/alignment/SHA-256.
- SSH/SFTP pinning до парольной аутентификации, Keystore, secret redaction.
- Компактный Dashboard, правая шторка 50%, пять семантических тем и значок LocalLS.
- Вложения, Android share entry point, подтверждение отправки, открытие папки по пути.
- Реальные SFTP-измерения скорости, история и foreground guard.
- Официальные broadcasts Tailscale; состояние тумблера из сети Android.
- Регрессионные instrumentation проверки UI/тем/вложений и SSH/SFTP fixture.

## Следующие изменения только в v1.0

1. Перенести выполнение очереди из FilesActivity в foreground service. Сейчас Home
   поддерживается, а уничтожение Activity отменяет очередь. Сохранить retry/cancel/SAF.
2. Атомарные uploads через временный файл + rename, чтобы обрыв overwrite не оставлял
   частичный целевой файл. Явно учитывать dataSync timeout новых Android.
3. Проверить большие 2–5 ГБ файлы, отмену, retry, потерю Wi-Fi, выключение экрана и
   ограничения батареи на Samsung + Realme.
4. Реальная Tailscale tailnet на телефоне/сервере/ПК, exit node + Allow LAN access,
   отказ аутентификации, другой VPN, always-on/lockdown и старые версии клиента.
5. Совместимая миграция Realme Agent HTTP/Bearer к TLS/Tailscale. Существующий
   серверный протокол и единственный механизм запуска sshd не менять внезапно.
6. Строки/localization/accessibility, дополнительные Android API 24/нового Android.
7. Release signing вне исходников, обновление поверх v0.9 без потери настроек.

## Gate v1.0

Статический аудит без ошибок; clean assembleDebug + Android tests + lint успешны;
настоящий проверенный APK; регрессии без failures; physical smoke-test Samsung/Realme;
действительная VPN-сеть трёх устройств; релизная подпись и проверка миграции.
