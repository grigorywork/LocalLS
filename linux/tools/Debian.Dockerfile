# syntax=docker/dockerfile:1
FROM debian:13-slim
RUN --mount=type=secret,id=proxy_ca,required=true \
    apt-get -o Acquire::https::CaInfo=/run/secrets/proxy_ca update && \
    apt-get -o Acquire::https::CaInfo=/run/secrets/proxy_ca install -y --no-install-recommends \
    ca-certificates nodejs dpkg-dev libgtk-3-0t64 libnss3 libasound2t64 libxss1 libxtst6 libsecret-1-0 \
    libgbm1 libatspi2.0-0t64 xvfb xauth dbus-x11 gnome-keyring openbox openssl ffmpeg \
    desktop-file-utils xdg-utils fonts-liberation procps xz-utils && rm -rf /var/lib/apt/lists/*
RUN mkdir -p /tmp/.X11-unix && chmod 1777 /tmp/.X11-unix
WORKDIR /work
