# syntax=docker/dockerfile:1
FROM fedora:44
RUN --mount=type=secret,id=proxy_ca,required=true \
    dnf -y --setopt=sslcacert=/run/secrets/proxy_ca install \
    rpm-build nodejs npm gtk3 nss alsa-lib libXScrnSaver libXtst libsecret \
    at-spi2-atk mesa-libgbm xorg-x11-server-Xvfb \
    dbus-daemon dbus-tools openssh-server polkit firewalld \
    desktop-file-utils openbox tar xz unzip git liberation-sans-fonts \
    && dnf clean all
WORKDIR /work
