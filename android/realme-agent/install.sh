#!/data/data/com.termux/files/usr/bin/sh
set -eu

pkg install -y python openssh procps openssl-tool
mkdir -p "$HOME/home-server-agent"

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
cp "$SCRIPT_DIR/agent.py" "$HOME/home-server-agent/agent.py"
chmod 700 "$HOME/home-server-agent/agent.py"

if [ ! -f "$HOME/home-server-agent/config.json" ]; then
  python - <<'PY'
import json, secrets
from pathlib import Path
p = Path.home()/"home-server-agent"/"config.json"
p.write_text(json.dumps({"host":"0.0.0.0","port":8787,"token":secrets.token_urlsafe(32)}, indent=2), encoding="utf-8")
p.chmod(0o600)
print("Создан", p)
PY
fi

# Preserve existing token, bind address, port and boot scripts. TLS is explicit on upgrade.
if [ ! -f "$HOME/home-server-agent/agent-key.pem" ] || [ ! -f "$HOME/home-server-agent/agent-cert.pem" ]; then
  umask 077
  openssl req -x509 -newkey rsa:3072 -sha256 -nodes -days 365 \
    -keyout "$HOME/home-server-agent/agent-key.pem" \
    -out "$HOME/home-server-agent/agent-cert.pem" -subj '/CN=LocalLS Agent' \
    -addext 'subjectAltName=DNS:localhost,IP:127.0.0.1' >/dev/null 2>&1
fi
chmod 600 "$HOME/home-server-agent/agent-key.pem"
python - <<'TLS_CONFIG'
import json
from pathlib import Path
base=Path.home()/"home-server-agent"
p=base/"config.json";cfg=json.loads(p.read_text())
cfg.update(tls=True,tls_cert=str(base/"agent-cert.pem"),tls_key=str(base/"agent-key.pem"))
p.write_text(json.dumps(cfg,indent=2));p.chmod(0o600)
TLS_CONFIG
printf 'Сверьте SHA-256 HTTPS-сертификата в LocalLS:\n'
openssl x509 -in "$HOME/home-server-agent/agent-cert.pem" -noout -fingerprint -sha256

echo
echo "Установлено в: $HOME/home-server-agent"
echo "Токен агента (не отправляй посторонним):"
python - <<'PY'
import json
from pathlib import Path
p=Path.home()/"home-server-agent"/"config.json"
print(json.loads(p.read_text())["token"])
PY

echo
echo "Запуск вручную:"
echo "  python $HOME/home-server-agent/agent.py"
echo
echo "Проверка локально:"
echo "  Откройте LocalLS → Безопасность → Проверить HTTPS-сертификат агента."
echo "  Подтвердите только совпадающий отпечаток, затем проверьте статус агента."
echo "  Существующий процесс агента не перезапускается установщиком; остановите его и запустите обновлённый после проверки."
