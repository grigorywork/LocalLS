#!/bin/bash
set -euo pipefail
LOCALLS_PROJECT_ROOT=$(cd "$(dirname "$0")/.." && pwd)
export DISPLAY=:99
export XDG_CURRENT_DESKTOP=GNOME
export HOME=$(mktemp -d /tmp/localls-fedora-ui-home-XXXXXX)
export XDG_CONFIG_HOME=$HOME/.config
export XDG_CACHE_HOME=$HOME/.cache
export XDG_DATA_HOME=$HOME/.local/share
export XDG_RUNTIME_DIR=$HOME/runtime
mkdir -p "$HOME" "$XDG_RUNTIME_DIR"
chmod 700 "$HOME" "$XDG_RUNTIME_DIR"
Xvfb :99 -screen 0 1600x1100x24 -nolisten tcp >"$LOCALLS_RUN_OUTPUT/xvfb.log" 2>&1 &
LOCALLS_XVFB_PID=$!
trap 'kill "$LOCALLS_XVFB_PID" 2>/dev/null || true' EXIT
for attempt in {1..50}; do [ -S /tmp/.X11-unix/X99 ] && break; sleep .1; done
openbox >"$LOCALLS_RUN_OUTPUT/wm.log" 2>&1 &
dbus-update-activation-environment DISPLAY HOME XDG_CURRENT_DESKTOP XDG_CONFIG_HOME XDG_CACHE_HOME XDG_DATA_HOME XDG_RUNTIME_DIR
node -e 'process.stdout.write(require("crypto").randomBytes(32).toString("base64"))' | gnome-keyring-daemon --unlock --components=secrets >"$LOCALLS_RUN_OUTPUT/keyring-start.log" 2>&1
export NODE_PATH=$LOCALLS_PROJECT_ROOT/node_modules
export LOCALLS_TEST_APP=/opt/localls/resources/app.asar
export LOCALLS_TEST_EXECUTABLE=$LOCALLS_PROJECT_ROOT/tools/test-executable.sh
cd "$LOCALLS_PROJECT_ROOT"
for scenario in "$@"; do
 export LOCALLS_UI_OUTPUT=$LOCALLS_RUN_OUTPUT/$scenario
 mkdir -p "$LOCALLS_UI_OUTPUT"
 timeout 240s node --require "$LOCALLS_PROJECT_ROOT/tools/launch-ui.cjs" "$LOCALLS_PROJECT_ROOT/tools/ui/$scenario.cjs" > "$LOCALLS_RUN_OUTPUT/$scenario.log" 2>&1
 printf 'PASS %s\n' "$scenario"
done

if [ -s "$LOCALLS_RUN_OUTPUT/render-crashes.jsonl" ]; then exit 1; fi
if grep -qE 'Render frame was disposed|Error sending from webContents' "$LOCALLS_RUN_OUTPUT/electron-stderr.log"; then exit 1; fi
