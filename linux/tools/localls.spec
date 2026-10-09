%global _build_id_links none
%global debug_package %{nil}
%global __strip /bin/true
%global _binary_payload w6.zstdio
Name: localls
Version: 0.1.1
Release: 1
Summary: LocalLS SSH/SFTP client and graphical Fedora server setup
License: LicenseRef-Proprietary
URL: https://github.com/grigorywork/LocalLS
Source0: localls-payload.tar.gz
BuildArch: x86_64
AutoReqProv: no
Requires: gtk3, nss, alsa-lib, libXScrnSaver, libXtst, libsecret, mesa-libgbm
Requires: at-spi2-atk, dbus-libs, polkit, python3, systemd, dnf

%description
Two-pane SSH/SFTP client with transfer history, measured speed graphs,
themes, background transfers, Tailscale integration and graphical setup
of the existing Fedora OpenSSH server. Administration uses OS polkit prompts.

%prep
%setup -q -c -T
tar -xzf %{SOURCE0}

%build

%install
mkdir -p %{buildroot}/opt/localls %{buildroot}/usr/bin %{buildroot}/usr/libexec
cp -a app/. %{buildroot}/opt/localls/
chmod 4755 %{buildroot}/opt/localls/chrome-sandbox
ln -s /opt/localls/localls %{buildroot}/usr/bin/localls
install -m 0644 localls-server-helper.py %{buildroot}/usr/libexec/localls-server-helper.py
install -D -m 0644 localls.desktop %{buildroot}/usr/share/applications/localls.desktop
install -D -m 0644 icon.png %{buildroot}/usr/share/icons/hicolor/256x256/apps/localls.png

%files
%defattr(-,root,root,-)
/opt/localls
/usr/bin/localls
/usr/libexec/localls-server-helper.py
/usr/share/applications/localls.desktop
/usr/share/icons/hicolor/256x256/apps/localls.png

%changelog
* Fri Oct 09 2026 LocalLS <localls-archive@localhost> - 0.1.1-1
- Add strict SSH public-key authentication and pinned HTTPS agent control.

* Thu Oct 08 2026 LocalLS <localls-archive@localhost> - 0.1.0-1
- Initial Fedora x86_64 client and graphical system server setup.
