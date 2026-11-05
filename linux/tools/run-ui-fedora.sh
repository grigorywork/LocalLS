#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
export LOCALLS_RUN_OUTPUT=${LOCALLS_RUN_OUTPUT:-$PWD/artifacts/gui-$(date -u +%Y%m%dT%H%M%SZ)}
mkdir -p "$LOCALLS_RUN_OUTPUT"
if [ "$#" -eq 0 ]; then set -- native-vault ui-smoke server-ui-smoke glass-ui-smoke qr-ui-smoke queue-preview-ui queue-eta-ui connection-state-smoke local-root-race; fi
exec dbus-run-session -- bash tools/run-ui-session.sh "$@"
