# LocalLS Fedora 0.1.0

Клиент SSH/SFTP и графический мастер штатного OpenSSH-сервера для Fedora x86_64.
Две панели, выбор файлов в разных папках, очередь передач, график скорости,
темы и работа после сворачивания. Мастер управляет существующим `sshd.service`
с подтверждением администратора; настройки и механизмы запуска не заменяются.

- [Скачать комплект RPM + инструкции RU/EN](https://github.com/grigorywork/LocalLS/releases/download/fedora-v0.1.0/LocalLS-Fedora-0.1.0-with-guides.zip)
- [Скачать RPM отдельно](https://github.com/grigorywork/LocalLS/releases/download/fedora-v0.1.0/LocalLS-0.1.0-Fedora-x86_64.rpm)
- [Подробная инструкция на русском](INSTRUCTIONS_RU.txt)
- [Detailed English guide](INSTRUCTIONS_EN.txt)
- [Результаты сборки и проверок](../../linux/BUILD_REPORT.md)

В архиве — отдельная папка `Fedora/`: RPM, обе инструкции TXT в UTF-8 с BOM,
BUILD_REPORT.md и SHA256SUMS.txt. Установите RPM средствами Fedora и откройте
LocalLS из меню приложений. Интерфейс пока на русском. Проверки выполнены
в Fedora 44 x86_64 в контейнере с Xvfb/Openbox, не на физическом GNOME/Wayland.
RPM пока без подписи издателя. Мастер DNF не предназначен для Atomic/Silverblue.

Welcome to LocalLS! Browse your personal SSH/SFTP server or prepare this Fedora
computer as a server for Android and Windows clients. Extract the Fedora package,
install the RPM using Fedora's package tools, and follow the English TXT guide.
Native administrative actions require polkit authorization. See the build report
for actual checks and desktop-session limitations.
