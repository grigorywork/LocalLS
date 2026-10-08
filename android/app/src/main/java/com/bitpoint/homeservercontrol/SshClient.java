package com.bitpoint.homeservercontrol;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.HostKey;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

final class SshClient {
    private SshClient() {}

    static ServerStats probe(String host, int port, String user, String password, String trustedFingerprint) {
        ServerStats stats = new ServerStats();
        long start = System.nanoTime();

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 2500);
            stats.reachable = true;
            stats.latencyMs = Math.max(1, (System.nanoTime() - start) / 1_000_000L);
        } catch (Exception e) {
            stats.error = "Порт " + port + " недоступен: " + safeMessage(e);
            return stats;
        }

        try {
            // First perform only SSH key exchange, without sending the password.
            // This lets us show/compare the fingerprint before any credential authentication.
            stats.hostFingerprint = probeFingerprint(host, port, user);
        } catch (Exception e) {
            stats.error = "SSH handshake не выполнен: " + safeMessage(e);
            return stats;
        }

        if (trustedFingerprint == null || trustedFingerprint.trim().isEmpty()) {
            stats.hostKeyNeedsTrust = true;
            stats.error = "Первое подключение: проверь и подтверди fingerprint сервера.";
            return stats;
        }

        if (!trustedFingerprint.equals(stats.hostFingerprint)) {
            stats.hostKeyMismatch = true;
            stats.error = "ВНИМАНИЕ: fingerprint сервера изменился. Пароль не отправлен; команды заблокированы.";
            return stats;
        }
        stats.hostKeyTrusted = true;

        if (password == null || password.isEmpty()) {
            stats.error = "SSH-ключ совпал. Введите пароль для полной диагностики.";
            return stats;
        }

        Session session = null;
        try {
            JSch jsch = new JSch();
            session = buildSession(jsch, host, port, user, password, trustedFingerprint);
            session.connect(5000);
            stats.authenticated = true;
            stats.uptime = clean(execute(session, "uptime", 6000));
            stats.disk = parseDisk(clean(execute(session,
                    "df -h \"$HOME/storage/shared\" 2>/dev/null | tail -n 1", 6000)));
            stats.ip = parseIp(clean(execute(session,
                    "ip addr show wlan0 2>/dev/null | grep 'inet ' | head -n 1", 6000)));

            String sshd = clean(execute(session, "pgrep -a sshd 2>/dev/null | head -n 3", 6000));
            stats.sshd = sshd.isEmpty() ? "процесс не найден" : sshd.replace('\n', ' ');

            String batteryJson = clean(execute(session,
                    "command -v termux-battery-status >/dev/null 2>&1 && termux-battery-status 2>/dev/null || echo __NO_TERMUX_API__",
                    7000));
            parseBattery(stats, batteryJson);

            String iperf = clean(execute(session,
                    "command -v iperf3 >/dev/null 2>&1 && iperf3 --version 2>/dev/null | head -n 1 || echo 'не установлен'",
                    6000));
            stats.iperf = iperf.isEmpty() ? "—" : iperf;

        } catch (Exception e) {
            stats.error = "SSH доступен, но вход не выполнен: "
                    + SecretRedactor.redact(safeMessage(e), password);
        } finally {
            if (session != null && session.isConnected()) session.disconnect();
        }
        return stats;
    }

    static SshCommandResult runCommand(String host, int port, String user, String password,
                                       String trustedFingerprint, String command, int timeoutMs) {
        SshCommandResult result = new SshCommandResult();
        Session session = null;
        try {
            JSch jsch = new JSch();
            session = buildSession(jsch, host, port, user, password, trustedFingerprint);
            session.connect(5000);

            HostKey hostKey = session.getHostKey();
            result.fingerprint = hostKey == null ? "" : hostKey.getFingerPrint(jsch);
            if (trustedFingerprint == null || trustedFingerprint.isEmpty()) {
                result.error = "Сначала подтверди fingerprint сервера.";
                return result;
            }
            if (!trustedFingerprint.equals(result.fingerprint)) {
                result.error = "Fingerprint изменился. Выполнение команды заблокировано.";
                return result;
            }

            result.output = SecretRedactor.redact(clean(execute(session, command, timeoutMs)), password);
            result.success = true;
        } catch (Exception e) {
            result.error = SecretRedactor.redact(safeMessage(e), password);
        } finally {
            if (session != null && session.isConnected()) session.disconnect();
        }
        return result;
    }

    private static Session buildSession(JSch jsch, String host, int port, String user, String password,
                                        String trustedFingerprint) throws Exception {
        if (trustedFingerprint == null || trustedFingerprint.trim().isEmpty()) {
            throw new Exception("Сначала подтверди fingerprint сервера.");
        }
        // Pin the already trusted fingerprint inside JSch itself. A changed key is rejected
        // during key exchange, before password authentication is attempted.
        jsch.setHostKeyRepository(new PinnedHostKeyRepository(jsch, trustedFingerprint));
        Session session = jsch.getSession(user, host, port);
        session.setPassword(password);

        Properties config = new Properties();
        config.put("StrictHostKeyChecking", "yes");
        config.put("PreferredAuthentications", "password,keyboard-interactive");
        session.setConfig(config);
        // Не даём Android/Wi-Fi тихо "заморозить" длительное SSH-соединение.
        session.setServerAliveInterval(15_000);
        session.setServerAliveCountMax(3);
        session.setTimeout(15_000);
        return session;
    }

    /**
     * Reads the remote host key using SSH key exchange only. No password is configured or sent.
     * Authentication is deliberately limited to "none" and is expected to fail on normal sshd;
     * the key has already been captured by HostKeyRepository at that point.
     */
    private static String probeFingerprint(String host, int port, String user) throws Exception {
        JSch jsch = new JSch();
        PinnedHostKeyRepository repository = new PinnedHostKeyRepository(jsch, "");
        jsch.setHostKeyRepository(repository);
        Session session = jsch.getSession(user, host, port);
        Properties config = new Properties();
        config.put("StrictHostKeyChecking", "yes");
        config.put("PreferredAuthentications", "none");
        session.setConfig(config);
        try {
            session.connect(5000);
        } catch (Exception authExpected) {
            if (repository.getLastFingerprint().isEmpty()) throw authExpected;
        } finally {
            if (session.isConnected()) session.disconnect();
        }
        String fingerprint = repository.getLastFingerprint();
        if (fingerprint.isEmpty()) throw new Exception("сервер не передал SSH host key");
        return fingerprint;
    }

    private static String execute(Session session, String command, int timeoutMs) throws Exception {
        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        channel.setCommand(command);
        channel.setInputStream(null);
        InputStream input = channel.getInputStream();
        channel.connect(4000);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        long deadline = System.currentTimeMillis() + Math.max(1000, timeoutMs);
        try {
            while (true) {
                while (input.available() > 0) {
                    int n = input.read(buffer);
                    if (n < 0) break;
                    out.write(buffer, 0, n);
                }
                if (channel.isClosed()) {
                    while (input.available() > 0) {
                        int n = input.read(buffer);
                        if (n < 0) break;
                        out.write(buffer, 0, n);
                    }
                    break;
                }
                if (System.currentTimeMillis() > deadline) {
                    throw new Exception("Команда превысила таймаут " + timeoutMs + " мс");
                }
                Thread.sleep(40);
            }
        } finally {
            channel.disconnect();
        }
        return out.toString(StandardCharsets.UTF_8.name());
    }

    private static void parseBattery(ServerStats stats, String raw) {
        if (raw.isEmpty() || raw.contains("__NO_TERMUX_API__")) {
            stats.battery = "нужен Termux:API";
            stats.temperature = "—";
            stats.charging = "—";
            return;
        }
        try {
            JSONObject json = new JSONObject(raw);
            int percentage = json.optInt("percentage", -1);
            double temperature = json.optDouble("temperature", Double.NaN);
            String status = json.optString("status", "—");
            String plugged = json.optString("plugged", "—");
            stats.battery = percentage >= 0 ? percentage + "%" : "—";
            stats.temperature = Double.isNaN(temperature) ? "—" : String.format(java.util.Locale.US, "%.1f °C", temperature);
            stats.charging = status + ("—".equals(plugged) ? "" : " • " + plugged);
        } catch (Exception e) {
            stats.battery = "Termux:API: ответ не распознан";
        }
    }

    private static String parseDisk(String line) {
        if (line.isEmpty()) return "—";
        String[] p = line.trim().split("\\s+");
        if (p.length >= 5) {
            return "всего " + p[p.length - 5]
                    + " • занято " + p[p.length - 4]
                    + " • свободно " + p[p.length - 3]
                    + " • " + p[p.length - 2];
        }
        return line;
    }

    private static String parseIp(String line) {
        if (line.isEmpty()) return "—";
        String[] p = line.trim().split("\\s+");
        for (int i = 0; i < p.length - 1; i++) {
            if ("inet".equals(p[i])) return p[i + 1];
        }
        return line;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static String safeMessage(Exception e) {
        String m = e.getMessage();
        return (m == null || m.trim().isEmpty()) ? e.getClass().getSimpleName() : m;
    }
}
