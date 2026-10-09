# LocalLS independent agent 0.3 — HTTPS

Независимый Python-агент Termux для Start/Stop/Restart SSH. Обычный SFTP работает без него.

Обновите файлы на серверном устройстве, сохраните резервную копию config.json и выполните `./install.sh`. Установщик сохраняет токен/адрес/порт, добавляет TLS-сертификат RSA 3072 и не изменяет sshd_config, работающий sshd или схему автозапуска. Сертификат действителен 365 дней. Приватный TLS-ключ и конфигурация имеют права 600.

Старый процесс агента автоматически не перезапускается. Завершите именно его существующим способом и запустите обновлённый: `python ~/home-server-agent/agent.py`. Не запускайте дубликаты. В LocalLS → Безопасность проверьте HTTPS-сертификат, сверьте SHA-256 с выводом установщика и подтвердите. Только затем проверяйте статус с токеном. Без сертификата новый агент отказывается работать и не откатывается на HTTP.

Порт 8787 не пробрасывайте в интернет. Для удалённого доступа используйте ограниченный Tailscale. Старый HTTP допускается только явно: сервер `tls=false` вместе с `allow_legacy_http=true`, клиент — временный режим совместимости; токен тогда передаётся без TLS.

Автозапуск `./install-boot.sh` включайте только после ручной проверки и только если он не дублирует имеющийся механизм. Boot-скрипт агента не запускает второй sshd.

## English

Agent 0.3 is independent of SSH and provides service start/stop/restart over TLS 1.2+ with a Bearer token. Back up config.json, run the updated install.sh in Termux, manually replace the old running agent process, then compare and trust the SHA-256 certificate in LocalLS Security. The installer preserves the existing token, address, port and boot scheme. It neither changes sshd configuration nor restarts the running sshd. Certificates expire after 365 days. Missing TLS files fail closed; legacy HTTP requires explicit opt-in on both sides. Never forward the agent port to the public internet. Use restricted Tailscale for remote access.
