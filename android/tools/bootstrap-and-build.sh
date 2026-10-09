#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo '[1/6] Static audit'
python3 tools/audit_project.py
python3 tools/audit_architecture.py

echo '[2/6] Java'
java -version

if [[ -z "${ANDROID_SDK_ROOT:-}" && -n "${ANDROID_HOME:-}" ]]; then export ANDROID_SDK_ROOT="$ANDROID_HOME"; fi
if [[ -z "${ANDROID_SDK_ROOT:-}" && -d "$HOME/Android/Sdk" ]]; then export ANDROID_SDK_ROOT="$HOME/Android/Sdk"; fi
if [[ -z "${ANDROID_SDK_ROOT:-}" || ! -d "$ANDROID_SDK_ROOT" ]]; then
  echo 'Android SDK not found. Set ANDROID_SDK_ROOT.' >&2; exit 2
fi

echo "[3/6] Android SDK: $ANDROID_SDK_ROOT"
SDKMANAGER="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
if [[ -x "$SDKMANAGER" ]]; then
  "$SDKMANAGER" 'platform-tools' 'platforms;android-37.0' 'build-tools;36.0.0'
else
  echo 'WARN: sdkmanager not found; assuming Android Studio already installed required packages.'
fi

echo '[4/6] Gradle wrapper'
if [[ ! -f gradle/wrapper/gradle-wrapper.jar ]]; then
  if command -v gradle >/dev/null 2>&1; then
    gradle wrapper --gradle-version 9.6.0
  else
    echo 'System Gradle not found. Downloading official Gradle 9.6.0...'
    TOOLS="$ROOT/.build-tools"
    ZIP="$TOOLS/gradle-9.6.0-bin.zip"
    HOME_GRADLE="$TOOLS/gradle-9.6.0"
    mkdir -p "$TOOLS"
    if [[ ! -f "$ZIP" ]]; then
      if command -v curl >/dev/null 2>&1; then
        curl -fL --retry 3 'https://services.gradle.org/distributions/gradle-9.6.0-bin.zip' -o "$ZIP"
      elif command -v wget >/dev/null 2>&1; then
        wget -O "$ZIP" 'https://services.gradle.org/distributions/gradle-9.6.0-bin.zip'
      else
        echo 'curl/wget not found; cannot download Gradle.' >&2; exit 3
      fi
    fi
    if [[ ! -x "$HOME_GRADLE/bin/gradle" ]]; then
      command -v unzip >/dev/null 2>&1 || { echo 'unzip not found.' >&2; exit 3; }
      unzip -q -o "$ZIP" -d "$TOOLS"
    fi
    "$HOME_GRADLE/bin/gradle" wrapper --gradle-version 9.6.0
  fi
fi
chmod +x gradlew

echo '[5/6] Assemble Debug APK'
./gradlew --no-daemon --stacktrace clean :core:test :transport:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug

echo '[6/6] Collect artifact'
APK='app/build/outputs/apk/debug/app-debug.apk'
[[ -s "$APK" ]] || { echo "APK not found: $APK" >&2; exit 4; }
mkdir -p artifacts
OUT='artifacts/LocalLS-v0.9.3-debug.apk'
cp -f "$APK" "$OUT"
python3 tools/verify_apk.py "$OUT" --sdk "$ANDROID_SDK_ROOT"
HASH="$(sha256sum "$OUT" | awk '{print $1}')"
SIZE="$(stat -c%s "$OUT" 2>/dev/null || stat -f%z "$OUT")"
cat > BUILD_REPORT.md <<EOF
# BUILD REPORT

- Status: BUILD SUCCESSFUL
- APK: $OUT
- Size: $SIZE bytes
- Gradle: 9.6.0
- Android Gradle Plugin: 9.4.1
- compileSdk / minSdk / targetSdk: 37 / 24 / 37
- Build Tools: 36.0.0
- JDK: $(java -version 2>&1 | head -n 1)
- Validation: tools/verify_apk.py; static audit; assembleDebugAndroidTest; lintDebug
- Device tests: not executed by this script
- SHA-256: $HASH
- Built: $(date -Iseconds)
EOF
echo "DONE: $OUT"
echo "SHA-256: $HASH"
