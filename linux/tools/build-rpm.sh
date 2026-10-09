#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p artifacts
npm run build:linux-dir -- --config.electronDist="${LOCALLS_ELECTRON_DIST:-node_modules/electron/dist}"
LOCALLS_RPM_TOP="$PWD/artifacts/rpmbuild"
mkdir -p "$LOCALLS_RPM_TOP"/{BUILD,BUILDROOT,RPMS,SOURCES,SPECS,SRPMS}
LOCALLS_PAYLOAD="$PWD/artifacts/payload"
mkdir -p "$LOCALLS_PAYLOAD/app"
cp -a artifacts/linux-unpacked/. "$LOCALLS_PAYLOAD/app/"
cp tools/localls-server-helper.py tools/localls.desktop assets/icon.png "$LOCALLS_PAYLOAD/"
tar -C "$LOCALLS_PAYLOAD" -czf "$LOCALLS_RPM_TOP/SOURCES/localls-payload.tar.gz" .
rpmbuild -bb --define "_topdir $LOCALLS_RPM_TOP" tools/localls.spec
cp "$LOCALLS_RPM_TOP/RPMS/x86_64/localls-0.1.1-1.x86_64.rpm" artifacts/LocalLS-0.1.1-Fedora-x86_64.rpm
rpm -K artifacts/LocalLS-0.1.1-Fedora-x86_64.rpm
sha256sum artifacts/LocalLS-0.1.1-Fedora-x86_64.rpm
