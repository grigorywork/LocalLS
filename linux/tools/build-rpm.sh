#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
LOCALLS_VERSION=$(node -p 'require("./package.json").version')
LOCALLS_STAMP=${LOCALLS_BUILD_LABEL:-$(date -u +%Y%m%dT%H%M%SZ)}
[[ "$LOCALLS_STAMP" =~ ^[a-zA-Z0-9-]+$ ]] || exit 2
LOCALLS_OUT="$PWD/artifacts/fedora-$LOCALLS_VERSION-$LOCALLS_STAMP"
mkdir -p artifacts
mkdir "$LOCALLS_OUT"
node -e 'const fs=require("fs"),v=require("./package.json").version;if(!fs.readFileSync("tools/localls.spec","utf8").includes("Version: "+v+"\n"))throw new Error("RPM version mismatch")'
npm run build:linux-dir -- --config.directories.output="$LOCALLS_OUT" --config.electronDist="${LOCALLS_ELECTRON_DIST:-node_modules/electron/dist}"
LOCALLS_RPM_TOP="$LOCALLS_OUT/rpmbuild"
mkdir -p "$LOCALLS_RPM_TOP"/{BUILD,BUILDROOT,RPMS,SOURCES,SPECS,SRPMS}
LOCALLS_PAYLOAD="$LOCALLS_OUT/payload"
mkdir -p "$LOCALLS_PAYLOAD/app"
cp -a "$LOCALLS_OUT/linux-unpacked/." "$LOCALLS_PAYLOAD/app/"
cp tools/localls.desktop assets/icon.png "$LOCALLS_PAYLOAD/"
tar -C "$LOCALLS_PAYLOAD" -czf "$LOCALLS_RPM_TOP/SOURCES/localls-payload.tar.gz" .
rpmbuild -bb --define "_topdir $LOCALLS_RPM_TOP" tools/localls.spec
LOCALLS_RPM="$LOCALLS_OUT/LocalLS-$LOCALLS_VERSION-Fedora-x86_64.rpm"
cp "$LOCALLS_RPM_TOP/RPMS/x86_64/localls-$LOCALLS_VERSION-1.x86_64.rpm" "$LOCALLS_RPM"
rpm -K "$LOCALLS_RPM"
sha256sum "$LOCALLS_RPM"
printf '%s\n' "$LOCALLS_RPM" > "$LOCALLS_OUT/rpm-path.txt"
