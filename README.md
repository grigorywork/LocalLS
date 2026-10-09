# LocalLS

Компактный SSH/SFTP-клиент для Android, Windows и Fedora: две панели файлов, загрузка и скачивание,
история передач, график скорости, темы оформления и работа в фоне.
Общий значок — облако с молнией.

![LocalLS Windows](docs/screenshots/LocalLS-overview.png)

## Приложения и сборки

Готовые комплекты разделены по платформам: **[Android](distributions/Android/)**
**[Windows](distributions/Windows/)** и **[Fedora](distributions/Fedora/)**. В каждом ZIP — отдельная папка,
установщик и подробные инструкции для Блокнота на русском и английском,
с приветствием и описанием назначения приложения.

- [Android: полный комплект с инструкциями RU/EN](https://github.com/grigorywork/LocalLS/releases/download/v0.9.5/LocalLS-Android-0.9.5-with-guides.zip)
- [Fedora: полный комплект RPM с инструкциями RU/EN](https://github.com/grigorywork/LocalLS/releases/download/v0.9.5/LocalLS-Fedora-0.9.5-with-guides.zip)
- [Windows: полный комплект с инструкциями RU/EN](https://github.com/grigorywork/LocalLS/releases/download/v0.9.5/LocalLS-Windows-0.9.5-with-guides.zip)

- [Скачать Windows EXE — LocalLS 0.9.5](https://github.com/grigorywork/LocalLS/releases/download/v0.9.5/LocalLS-0.9.5-Windows-x64-Setup.exe)
- [Скачать Android APK — LocalLS 0.9.5](https://github.com/grigorywork/LocalLS/releases/download/v0.9.5/LocalLS-v0.9.5-debug.apk)
- [Windows ZIP](https://github.com/grigorywork/LocalLS/releases/download/v0.9.5/LocalLS-Windows-0.9.5-with-guides.zip) и [все сохранённые релизы](https://github.com/grigorywork/LocalLS/releases).

| Приложение | Версия | Система | Установочный файл |
| --- | --- | --- | --- |
| LocalLS Android | 0.9.5 | Android 7.0+ (minSdk 24) | `LocalLS-v0.9.5-debug.apk` |
| LocalLS Fedora | 0.9.5 | Fedora x86_64 (проверено в Fedora 44) | `LocalLS-0.9.5-Fedora-x86_64.rpm` |
| LocalLS Desktop | 0.9.5 | Windows 10/11 x64 | `LocalLS-0.9.5-Windows-x64-Setup.exe` |

APK, EXE и RPM размещаются в **Releases** этого репозитория отдельно от исходников.
Загрузка GitHub «Source code» содержит исходники; установочные файлы выбирайте в списке Assets.
Android APK подписан debug-сертификатом; Windows EXE и Fedora RPM пока без подписи издателя.
SHA-256 установщиков указан в `SHA256SUMS.txt` каждого релиза.

## Структура

- [`android/`](android/README.md) — Android 0.9.5, Gradle Wrapper, UI, SSH/SFTP, мастер Termux, тесты и документация.
- [`android/realme-agent/`](android/realme-agent/README.md) — существующий независимый агент управления SSH.
- [`desktop/`](desktop/README.md) — Windows-клиент 0.9.5 на Electron, установщик NSIS, тесты и значки.
- [`android/BUILD_REPORT.md`](android/BUILD_REPORT.md) и [`desktop/BUILD_REPORT.md`](desktop/BUILD_REPORT.md) — результаты настоящих сборок и выполненных проверок.
- [`linux/`](linux/README.md) — Fedora-клиент и графический мастер штатного OpenSSH, RPM и тесты.
- [`docs/`](docs/) — сведения о проверке артефактов и скриншоты тестового интерфейса.

Android и Windows продолжают работать с существующим Termux/OpenSSH-сервером.
Fedora 0.9.5 — клиент и мастер настройки текущего ПК как сервера; используется существующий sshd.service.
Windows 0.9.5 — клиент; создание сервера на ПК в этой версии не реализовано.
VPN использует установленный официальный Tailscale, собственный VPN-сервис не встроен.
Приложения не добавляют конкурирующий механизм запуска sshd.

## Сборка из исходников

Android: JDK 17, Android SDK Platform `android-37.0`, Build Tools 36.0.0,
Platform Tools и официальный Gradle Wrapper 9.6.0 (AGP — в build.gradle.kts).
Настройте `ANDROID_SDK_ROOT` или собственный `android/local.properties`.

```sh
cd android
python3 tools/audit_project.py
./gradlew clean assembleDebug
```

Windows: Node.js 24 и npm. Сборка создаёт установщик в `desktop/artifacts/`.

```sh
cd desktop
npm ci
npm test
npm run build:win
node tools/verify-package.cjs
```

Подробности сборки, UI smoke test и ограничения реальных устройств — в README и BUILD_REPORT
соответствующего приложения. Linux UI-проверки Windows-клиента не означают тест на Windows.

## Сохранённые версии

Fedora: Node.js 22, rpm-build, штатные библиотеки рабочего стола; `cd linux`,
`npm ci`, `npm test`, `npm run build:rpm`. Подробности — в linux/README.md.

Теги `fedora-v0.1.0`, `android-v0.8`, `android-v0.9`, `android-v0.9.1`, `android-v0.9.2`,
`android-v0.9.3` и `windows-v0.1.0` содержат сохранившиеся снимки исходников.
История импортирована из этих снимков при подготовке репозитория; это не восстановленная
история всех отдельных правок. Прежние Android-версии доступны по тегам и в Releases.
Исходная рабочая v0.8 сохранена; дальнейшие планы v1.0 находятся в документации приложений.

Публикация и проверка ссылок от 8 октября 2026: [`docs/PUBLICATION_REPORT.md`](docs/PUBLICATION_REPORT.md).

## Безопасность и права

Пароли, токены, ключи подписи, приватные профили, журналы устройств, кэши и зависимости
не входят в репозиторий. Первый SSH-вход требует проверки ключа; изменившийся ключ
блокирует авторизацию. Тестовые ключи и пароли в тестах генерируются во время запуска.

Лицензия для исходников пока не выбрана. Публичное размещение само по себе не выдаёт
разрешение на распространение и модификацию; лицензии зависимостей принадлежат их авторам.

## Обновление безопасности от 9 октября 2026

SSH-ключи с проверкой нового входа, без парольного fallback; HTTPS-агент 0.3 с проверкой точного сертификата. Рабочая схема запуска сервера сохраняется. [Переход на ключи — русский](SECURITY_GUIDE_RU.txt), [English](SECURITY_GUIDE_EN.txt). Парольный вход сервера отдельно отключается только после проверки всех ключей и резервного доступа.

Публикация обновления безопасности от 9 октября 2026: [проверенные установщики и SHA-256](docs/SECURITY_PUBLICATION_REPORT.md).
