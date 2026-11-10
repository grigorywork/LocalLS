# LocalLS Fedora / Debian 1.44 — MSS9LN

Клиент и встроенный SSH/SFTP-сервер. Системный OpenSSH не меняется.

1. Установи RPM через `sudo dnf install ./LocalLS-*.rpm` или DEB через `sudo apt install ./LocalLS-*.deb`.
2. Выбери папку сервера, включи LocalLS и покажи QR-код. На втором устройстве сверь код и подтверди доступ.
3. Передавай файлы в «Файлах». В «Очереди» — время, отмена, очистка и повтор.

**Куда сохраняется:** входящие попадают в папку, путь к которой показан на вкладке «Сервер». Её можно открыть оттуда же; менять — при выключенном сервере. Локальные файлы доступны с обычными правами пользователя.

До 10 устройств. Крестик оставляет сервер в фоне; «Выйти» останавливает его. Автоматический сон блокируется на время работы сервера; ручной сон/гибернация останавливает сеть. Для ключей нужна разблокированная системная связка ключей. В доверенной сети разреши выбранный порт брандмауэра.

Язык, цвет, светлый фон, матовые панели и обводка переключаются отдельно. Размытие рабочего стола зависит от окружения, внутренние матовые панели работают независимо. Пакеты пока без подписи издателя.

English: Install the RPM on Fedora or DEB on Debian. Select a server folder, start LocalLS and approve the client using its QR code. Uploads go to the folder displayed on the Server screen; open it there or change it while stopped. Files supports local folders permitted by your user account. Up to 10 devices. Closing the window keeps the server running; Quit stops it. Automatic idle sleep is inhibited while hosting; manual suspend interrupts networking. Unlock the system keyring and allow the selected port in your trusted network. Language, accent, light background and matte panels are independent settings. Packages are currently unsigned.

Build: `npm ci`, `npm test`, `npm run build:rpm`. Environment: `tools/Fedora.Dockerfile`.
