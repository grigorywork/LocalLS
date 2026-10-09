# LocalLS Windows 0.9.5

Добро пожаловать в LocalLS! Откройте свой сервер с компьютера, работайте в двух
файловых панелях, скачивайте и отправляйте файлы. Очередь продолжает работать
при сворачивании в трей, пока компьютер не спит и сеть доступна.

- **[Скачать комплект с папкой Windows — EXE и инструкции RU/EN](https://github.com/grigorywork/LocalLS/releases/download/v0.9.5/LocalLS-Windows-0.9.5-with-guides.zip)**
- [Скачать EXE отдельно](https://github.com/grigorywork/LocalLS/releases/download/v0.9.5/LocalLS-0.9.5-Windows-x64-Setup.exe)
- [Подробная инструкция на русском — TXT](INSTRUCTIONS_RU.txt)
- [Detailed English user guide — TXT](INSTRUCTIONS_EN.txt)

В ZIP: `Windows/LocalLS-0.9.5-Windows-x64-Setup.exe`, обе инструкции,
`BUILD_REPORT.md` и `SHA256SUMS.txt`. Распакуйте архив, откройте TXT в Блокноте
и запустите установщик. Нужен Windows 10/11 x64 и уже работающий SSH/SFTP-сервер.
Эта версия работает клиентом; создание сервера на ПК не входит в неё.
Установщик пока без подписи издателя.

Welcome to LocalLS! Use two file panes to browse your personal server and upload
or download files. Transfers continue in the system tray while the PC is awake
and connected. Extract the complete Windows package, open the English TXT guide
in Notepad and run the installer. Windows 10/11 x64 and an existing SSH/SFTP server
are required. The current interface uses Russian labels. This version is a client;
PC server creation is not included. The installer currently has no publisher signature.

Обновление безопасности: SSH-ключи без парольного fallback; HTTPS-агент с проверкой сертификата. Подробная миграция описана в TXT-инструкциях.
