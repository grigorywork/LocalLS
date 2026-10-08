#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
LOCALLS_UI_TMP=$(mktemp -d)
Xvfb :89 -screen 0 1440x1000x24 -nolisten tcp >"$LOCALLS_UI_TMP/xvfb.log" 2>&1 &
LOCALLS_XVFB_PID=$!
LOCALLS_WM_PID=''
trap 'if [ -n "$LOCALLS_WM_PID" ]; then kill "$LOCALLS_WM_PID" 2>/dev/null || true; fi; kill "$LOCALLS_XVFB_PID" 2>/dev/null || true; rm -rf "$LOCALLS_UI_TMP"' EXIT
for LOCALLS_WAIT in {1..50}; do
  if [ -S /tmp/.X11-unix/X89 ]; then break; fi
  sleep 0.1
done
DISPLAY=:89 openbox >"$LOCALLS_UI_TMP/wm.log" 2>&1 &
LOCALLS_WM_PID=$!
DISPLAY=:89 dbus-run-session -- node tools/ui-smoke.cjs
