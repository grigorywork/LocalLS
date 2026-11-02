# syntax=docker/dockerfile:1
FROM fedora:44@sha256:43b29f65a41eb9c35e1cd5323e3bdf3b655c2357a9f4f1ff2f9c2798e5045d80
RUN --mount=type=secret,id=proxy_ca,required=true \
    dnf -y --setopt=sslcacert=/run/secrets/proxy_ca install \
    rpm-build nodejs npm gtk3 nss alsa-lib libXScrnSaver libXtst libsecret \
    at-spi2-atk mesa-libgbm xorg-x11-server-Xvfb dbus-daemon dbus-tools \
    gnome-keyring openssh-clients openssh-server desktop-file-utils openbox \
    tar xz unzip git liberation-sans-fonts findutils file procps-ng shadow-utils ffmpeg-free \
    && dnf clean all
RUN --mount=type=secret,id=proxy_ca,required=true \
    dnf -y --setopt=sslcacert=/run/secrets/proxy_ca install openssl && dnf clean all
WORKDIR /work
