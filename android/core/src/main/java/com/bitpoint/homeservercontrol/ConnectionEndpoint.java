package com.bitpoint.homeservercontrol;

import java.net.URI;

/** Parses public connection details only; passwords and URL paths are rejected. */
final class ConnectionEndpoint {
    final String host, user;
    final int port;
    private ConnectionEndpoint(String host, String user, int port) {
        this.host = host; this.user = user; this.port = port;
    }
    static ConnectionEndpoint parse(String text, String defaultUser, int defaultPort) {
        try {
            String value = text.trim();
            URI uri = new URI(value.contains("://") ? value : "ssh://" + value);
            if (!("ssh".equals(uri.getScheme()) || "sftp".equals(uri.getScheme()))
                    || uri.getRawAuthority() == null || uri.getRawAuthority().endsWith(":")
                    || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || (uri.getRawPath() != null && !uri.getRawPath().isEmpty())) throw new Exception();
            String host = uri.getHost();
            if (host != null && host.startsWith("[")) host = host.substring(1, host.length() - 1);
            String user = uri.getRawUserInfo() == null ? defaultUser : uri.getRawUserInfo();
            int port = uri.getPort() == -1 ? defaultPort : uri.getPort();
            if (!validHost(host) || !validUser(user) || port < 1 || port > 65535) throw new Exception();
            return new ConnectionEndpoint(host, user, port);
        } catch (Exception e) {
            throw new IllegalArgumentException("Нужен адрес вида ssh://user@192.168.1.82:8022, без пароля и пути");
        }
    }
    static boolean validUser(String user) {
        return user != null && user.matches("[A-Za-z0-9_][A-Za-z0-9_.-]{0,63}");
    }
    static boolean validHost(String host) {
        if (host == null || host.isEmpty() || host.length() > 253) return false;
        if (host.contains(":")) {
            try {
                return host.matches("[0-9A-Fa-f:.]+") && new URI("ssh://[" + host + "]").getHost() != null;
            } catch (Exception e) { return false; }
        }
        if (host.matches("[0-9.]+")) {
            String[] parts = host.split("\\.", -1);
            if (parts.length != 4) return false;
            for (String part : parts) {
                if (!part.matches("[0-9]{1,3}") || Integer.parseInt(part) > 255) return false;
            }
            return true;
        }
        for (String part : host.split("\\.", -1))
            if (!part.matches("[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?")) return false;
        return true;
    }
}
