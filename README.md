# LocalLS — MSS9LN

Обмен файлами по SSH/SFTP между Android, Windows, Fedora и Debian.

- [Android 1.41/code63](downloads/LocalLS-1.41-code63-background-language-20261110.apk)
- [Windows 1.43 x64](downloads/LocalLS-1.43-Windows-x64-Setup-unsigned-background-language-20261110.exe)
- [Fedora 1.44 x86_64](downloads/LocalLS-1.44.0-Fedora-x86_64-background-language-20261110.rpm)
- [Debian 13 / 1.44 amd64](downloads/LocalLS-1.44.0-Debian13-amd64-background-language-20261110.deb)

[Проверки и скриншоты / Tests and screenshots](downloads/localls-background-language-20261110/README.md)


1. Подключи устройства к одной сети. На сервере включи LocalLS и покажи QR-код.
2. На другом устройстве считай QR-код или открой его из файла. Сверь код и разреши доступ на сервере.
3. Открой «Файлы», выбери папки и направление передачи. Прогресс, время, отмена и повтор — в «Очереди».

Входящие сохраняются в выбранную папку сервера; её путь виден на вкладке «Сервер». До **10** разрешённых устройств. На Android разреши работу без ограничений батареи. На ПК крестик оставляет сервер в фоне; «Выйти» останавливает его. Настоящий сон или выключение компьютера прерывает сеть.

Язык, цвет, светлый фон и матовые панели выбираются отдельно в настройках. Обновляй поверх прежней установки, сохраняя ключи и настройки.

[Android](android/README.md) · [Windows](desktop/README.md) · [Fedora / Debian](linux/README.md)

---

SSH/SFTP file sharing across Android, Windows, Fedora and Debian.

1. Use the same network. Start LocalLS on the server and show its QR code.
2. Scan or open the QR image on the other device, compare the code and approve access on the server.
3. In **Files**, choose folders and a transfer direction. **Queue** shows progress, time, cancel and retry.

Uploads go to the server folder shown on its Server screen. Up to **10** approved devices. Allow unrestricted battery use on Android. Closing a PC window keeps the server running; Quit stops it. A suspended or powered-off computer cannot serve files.

Language, accent, light background and matte panels are separate settings. Install updates over the previous version to retain keys and settings.
