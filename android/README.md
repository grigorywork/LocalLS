# Home Server Control v0.8

Нативный Android-пульт для домашнего сервера **Realme GT Neo 2 + Termux + OpenSSH/SFTP**. Минимальная версия Android — 7.0 (API 24), поэтому проект рассчитан в том числе на старый Samsung Galaxy A01.

## Что нового в v0.8

### Приоритет — не новые функции, а стабильный APK
v0.8 считается release-candidate веткой. Новые функции добавляются только если они напрямую повышают стабильность сборки или работы на реальном телефоне. Основное тестовое устройство — **Samsung Galaxy A01**, при этом `minSdk 24` сохранён для Android 7+.

### Передача файлов защищена от фонового убийства
Во время непустой SFTP-очереди теперь одновременно работают три защиты:
- `PARTIAL_WAKE_LOCK` — CPU клиента не засыпает посреди большого файла;
- `WIFI_MODE_FULL_HIGH_PERF` — Wi‑Fi не уходит в агрессивное энергосбережение;
- `TransferForegroundService` — процесс приложения получает foreground priority и показывает системное уведомление о передаче.

Foreground service запускается **только** на время активной очереди и останавливается после последней задачи. Он не запускает и не перезапускает `sshd`, поэтому серверная схема Realme остаётся неизменной.

### Поворот экрана не должен рвать очередь
`FilesActivity` обрабатывает обычные изменения orientation/screenSize без пересоздания Activity. Это убирает один из типичных способов случайно оборвать большую передачу на телефоне.

### Диагностика клиента
Экран диагностики теперь показывает модель телефона, приблизительный объём RAM и системный low-RAM флаг Android. Это поможет отличить сетевую проблему от ограничений слабого клиента.

### Сохранено из v0.7
Остаются pre-auth SSH fingerprint pinning, локальный поиск Realme по SSH banner, один retry после обрыва, скорость/ETA, история передач и Local/Tailscale fallback.

## Компактный фиолетовый интерфейс

Главный экран показывает состояние сервера, основные показатели и графики скорости SFTP / отклика SSH. Управление собрано в небольшие блоки с фиолетовыми кнопками; подробные инструменты раскрываются отдельно. В проводнике — значки файлов/папок, компактные строки и живой график передачи. Средняя скорость SFTP попадает в историю.

## Возможности проекта
- статус SSH/SFTP, задержка, IP, uptime, свободное место;
- батарея/температура Realme при наличии Termux:API;
- TOFU/fingerprint защита SSH с pre-auth host-key pinning;
- Local + Tailscale профиль и опциональный fallback;
- встроенный SFTP-проводник;
- очередь upload/download, отмена, прогресс, МБ/с, ETA;
- один автоматический retry при обрыве;
- mkdir / rename / удаление файлов и только пустых папок;
- история метрик и передач;
- `iperf3` из интерфейса;
- Realme Agent для start/stop/restart `sshd` независимым каналом;
- фоновый монитор доступности;
- экспорт диагностики без SSH-пароля и agent token;
- автоматический поиск сервера в домашней Wi‑Fi сети.

## Базовая конфигурация пользователя
- Local host: `192.168.1.82` (может измениться из-за DHCP)
- SSH/SFTP port: `8022`
- user: `u0_a606`
- remote root: `/data/data/com.termux/files/home/storage/shared`
- Realme Agent: `8787`
- Tailscale host: второй профиль, не заменяет локальный.

Пароль SSH и token агента намеренно не входят в проект.

## Сборка
Актуальная связка проекта:
- compile/target SDK 37;
- minSdk 24;
- Java 17;
- Android Gradle Plugin 9.4.1;
- Gradle 9.6.0;
- Build Tools 36.0.0.

На машине с Android SDK:

```bash
python3 tools/audit_project.py
./tools/bootstrap-and-build.sh
```

На Windows:

```powershell
python .\tools\audit_project.py
.\tools\bootstrap-and-build.ps1
```

Успешный build gate должен создать:

`artifacts/HomeServerControl-v0.8-debug.apk`

и `BUILD_REPORT.md` с SHA-256.

## Текущий статус

Получен настоящий debug APK после `BUILD SUCCESSFUL`:
`artifacts/HomeServerControl-v0.8-debug.apk`.

Версии инструментов, SHA-256, результат APK-проверки и фактически выполненные
тесты приведены в `BUILD_REPORT.md`. Установка и smoke test на физических
Samsung Galaxy A01 / Realme остаются отдельной проверкой владельца устройств.

SDK manager использует имя пакета `platforms;android-37.0`; приложение по-прежнему
собирается с compileSdk/targetSdk 37. В архив включён официальный Wrapper JAR.

Проверка уже собранного APK:

```bash
python3 tools/verify_apk.py --sdk "$ANDROID_SDK_ROOT"
```

Основа следующей версии: `docs/V1_0_FOUNDATION.md`; versionCode 8 / versionName 0.8.0 сохранены.
