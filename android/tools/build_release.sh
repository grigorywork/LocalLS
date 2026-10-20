#!/usr/bin/env bash
# Run from any directory; credentials are environment bindings, never command arguments.
set -euo pipefail
cd "$(dirname "$0")/.."
: "${LOCALLS_RELEASE_STORE:?Configure the existing release keystore}"
: "${LOCALLS_RELEASE_STORE_PASSWORD:?Configure the keystore password}"
: "${LOCALLS_RELEASE_KEY_ALIAS:?Configure the key alias}"
: "${LOCALLS_RELEASE_KEY_PASSWORD:?Configure the key password}"
: "${ANDROID_HOME:?Configure the Android SDK}"
test "$#" -eq 1 || { echo 'Usage: build_release.sh /absolute/path/to/previous-release.apk OR --initial-release' >&2; exit 2; }
previous_apk="$1"
if [ "$previous_apk" != "--initial-release" ]; then
 test -f "$previous_apk" || { echo 'Previous APK not found' >&2; exit 2; }
fi
python3 ../tools/check_versions.py
bash gradlew --no-daemon :app:assembleRelease
if [ "$previous_apk" = "--initial-release" ]; then
    verification_args=(--initial-release)
else
    verification_args=(--previous-apk "$previous_apk")
fi
python3 tools/verify_apk.py app/build/outputs/apk/release/app-release.apk \
    --sdk "$ANDROID_HOME" --require-release-signer "${verification_args[@]}"
