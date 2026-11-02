#!/bin/bash
set -euo pipefail
LOCALLS_APP_ARGS=()
for argument in "$@"; do
 if [ "$argument" != --no-sandbox ]; then LOCALLS_APP_ARGS+=("$argument"); fi
done
exec /opt/localls/localls --enable-logging=stderr --log-level=0 "${LOCALLS_APP_ARGS[@]}"
