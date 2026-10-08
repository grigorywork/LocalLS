#!/data/data/com.termux/files/usr/bin/sh
set -eu

pkg install -y python openssh procps
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
echo "  curl -H 'Authorization: Bearer ТВОЙ_ТОКЕН' http://127.0.0.1:8787/status"
