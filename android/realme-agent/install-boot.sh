#!/data/data/com.termux/files/usr/bin/sh
set -eu

mkdir -p "$HOME/.termux/boot"
cat > "$HOME/.termux/boot/home-server-agent" <<'BOOT'
#!/data/data/com.termux/files/usr/bin/sh
sleep 15
nohup python "$HOME/home-server-agent/agent.py" >> "$HOME/home-server-agent/agent.log" 2>&1 &
BOOT
chmod +x "$HOME/.termux/boot/home-server-agent"

echo "Готово. Скрипт запускает ТОЛЬКО агент, а не sshd."
echo "Для автозапуска нужен Termux:Boot из того же источника, что и Termux."
echo "После установки Termux:Boot его нужно открыть вручную один раз."
