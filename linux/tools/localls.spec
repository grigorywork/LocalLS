%global _build_id_links none
%global debug_package %{nil}
%global __strip /bin/true
%global _binary_payload w9.xzdio
Name: localls
Version: 1.43.0
Release: 1
Summary: LocalLS SSH/SFTP client and built-in QR server
License: LicenseRef-Proprietary
URL: https://github.com/grigorywork/LocalLS
Source0: localls-payload.tar.gz
BuildArch: x86_64
AutoReqProv: no
Requires: gtk3, nss, alsa-lib, libXScrnSaver, libXtst, libsecret, mesa-libgbm
Requires: at-spi2-atk, dbus-libs
Recommends: gnome-keyring

%description
LocalLS by MSS9LN. SSH/SFTP file manager and application server with QR pairing,
explicit device approval, a shared folder, persistent transfer queue and previews.
The existing system OpenSSH service and its configuration are not changed.

%prep
%setup -q -c -T
tar -xzf %{SOURCE0}

%build

%install
mkdir -p %{buildroot}/opt/localls %{buildroot}/usr/bin
cp -a app/. %{buildroot}/opt/localls/
chmod 4755 %{buildroot}/opt/localls/chrome-sandbox
ln -s /opt/localls/localls %{buildroot}/usr/bin/localls
install -D -m 0644 localls.desktop %{buildroot}/usr/share/applications/localls.desktop
install -D -m 0644 icon.png %{buildroot}/usr/share/icons/hicolor/256x256/apps/localls.png

%files
%defattr(-,root,root,-)
/opt/localls
/usr/bin/localls
/usr/share/applications/localls.desktop
/usr/share/icons/hicolor/256x256/apps/localls.png

%changelog
* Thu Nov 05 2026 MSS9LN <localls-archive@localhost> - 1.43.0-1
- Invalidate stale listings when the local folder is changed.

* Mon Nov 02 2026 MSS9LN <localls-archive@localhost> - 1.42.0-1
- Keep valid shared files visible beside incompatible POSIX names.

* Mon Nov 02 2026 MSS9LN <localls-archive@localhost> - 1.41.0-1
- Built-in QR server and current LocalLS interface; no system SSH management.
