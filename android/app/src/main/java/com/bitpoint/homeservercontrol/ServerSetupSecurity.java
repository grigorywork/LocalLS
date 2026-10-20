package com.bitpoint.homeservercontrol;

import android.content.Context;
import android.net.Uri;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import org.json.JSONObject;

/** Security checks for this phone. Never changes the selected remote client profile. */
final class ServerSetupSecurity {
    private ServerSetupSecurity() { }

    static JSONObject localKey(Context context, int port, String user) throws Exception {
        String id = "server_setup_key_" + port + "_" + user;
        if (SecurePrefs.containsNamed(context, id)) {
            JSONObject existing = new JSONObject(SecurePrefs.loadNamed(context, id));
            SshKeys.inspect(existing.getString(SshKeys.PRIVATE), existing.optString(SshKeys.PHRASE));
            return existing;
        }
        JSONObject key = SshKeys.generate();
        // Save before installing: an interrupted verification must reuse the same identity.
        SecurePrefs.saveNamed(context, id, key.toString());
        return key;
    }

    static String installAndVerifyKey(String host, int port, String user, String password,
                                      String hostPin, JSONObject key) throws Exception {
        SshCredentials credentials = SshCredentials.key(key.getString(SshKeys.PRIVATE), key.optString(SshKeys.PHRASE));
        JSONObject info = SshKeys.inspect(credentials.privateKey, credentials.passphrase);
        SftpClient.installPublicKey(host, port, user, SshCredentials.password(password), hostPin, info.getString(SshKeys.PUBLIC));
        // A separate connection accepts only publickey; no password can fall back here.
        SftpClient.list(host, port, user, credentials, hostPin, ".");
        return info.getString("fingerprint");
    }

    static String verifyExistingAgent(Context context, Uri tree) throws Exception {
        byte[] bytes = TermuxSetup.readHomeFile(context, tree, "home-server-agent/config.json", 16384);
        JSONObject config;
        try { config = new JSONObject(new String(bytes, StandardCharsets.UTF_8)); }
        finally { Arrays.fill(bytes, (byte) 0); }
        if (!config.optBoolean("tls", true)) throw new Exception("Агент должен использовать HTTPS");
        int port = config.optInt("port", 8787);
        if (port < 1 || port > 65535) throw new Exception("Неверный порт агента");
        String path = config.optString("tls_cert", TermuxSetup.HOME + "/home-server-agent/agent-cert.pem");
        if (!path.startsWith(TermuxSetup.HOME + "/")) throw new Exception("Сертификат должен находиться в HOME Termux");
        byte[] pem = TermuxSetup.readHomeFile(context, tree, path.substring(TermuxSetup.HOME.length() + 1), 16384);
        return verifyAgent("127.0.0.1", port, config.getString("token"), pem);
    }

    static String verifyAgent(String host, int port, String token, byte[] certificate) throws Exception {
        X509Certificate cert = (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(certificate));
        cert.checkValidity();
        String expected = AgentTls.fingerprint(cert);
        if (!expected.equals(AgentTls.probe(host, port))) throw new Exception("Сертификат агента изменился");
        // Recheck the pin on the authenticated connection, including a certificate change between requests.
        AgentResult result = AgentClient.request(host, port, token, "status", expected, false);
        if (!result.success || !new JSONObject(result.body).optBoolean("ok")) throw new Exception("Статус агента не подтверждён");
        return expected;
    }

    static void prepareAgent(Context context, int port, String user, SshCredentials credential, String pin) throws Exception {
        String helper, agent;
        try (java.io.InputStream in = context.getAssets().open("setup_agent.py")) {
            helper = new String(readAsset(in), StandardCharsets.UTF_8);
        }
        try (java.io.InputStream in = context.getAssets().open("agent.py")) {
            agent = android.util.Base64.encodeToString(readAsset(in), android.util.Base64.NO_WRAP);
        }
        String script = "set -eu\nexport PATH=/data/data/com.termux/files/usr/bin:$PATH\n"
                + "if ! command -v python >/dev/null 2>&1 || ! command -v openssl >/dev/null 2>&1 || ! command -v pgrep >/dev/null 2>&1; then\n"
                + " apt-get update </dev/null >/dev/null 2>&1 || exit 40\n"
                + " DEBIAN_FRONTEND=noninteractive apt-get -o Dpkg::Options::=--force-confold install -y python openssl-tool procps </dev/null >/dev/null 2>&1 || exit 41\nfi\n"
                + "python - <<'LOCALLS_AGENT_SETUP'\nimport base64\nAGENT_SOURCE = base64.b64decode('" + agent + "')\n"
                + helper + "\nLOCALLS_AGENT_SETUP\n";
        SshCommandResult result = SshClient.runCommand("127.0.0.1", port, user, credential, pin, script, 600000);
        if (!result.success || !result.output.trim().equals("AGENT_READY=1"))
            throw new AgentPreparationException(preparationFailure(result.output.trim()));
    }

    static final class AgentPreparationException extends Exception {
        AgentPreparationException(String message) { super(message); }
    }

    static String preparationFailure(String result) {
        switch (result) {
            case "AGENT_SETUP_PORT_BUSY": return "Порт агента занят. Существующий процесс сохранён; проверь, какой агент или приложение использует этот порт.";
            case "AGENT_SETUP_TLS_INCOMPLETE": return "TLS-пара неполная. Существующий ключ сохранён; восстанови соответствующий сертификат из резервной копии.";
            case "AGENT_SETUP_TLS": return "TLS-пара повреждена, не совпадает или сертификат истёк. Восстанови или обнови её отдельно; мастер не заменяет существующие ключи.";
            case "AGENT_SETUP_LEGACY": return "В настройках указан старый HTTP-агент. Его процесс и настройки сохранены; сначала выполни переход на HTTPS.";
            case "AGENT_SETUP_BUSY": return "Другая подготовка агента ещё выполняется. Дождись её завершения и повтори проверку.";
            case "AGENT_SETUP_CONFIG": return "Проверь config.json: token, порт и адрес. Мастер поддерживает локальный или общий IPv4-адрес службы; настройки не переписываются.";
            case "AGENT_SETUP_UNSAFE_FILE": return "Файлы агента имеют небезопасный путь или ссылку. Проверь папку home-server-agent и TLS-файлы внутри HOME Termux.";
            default: return "Подготовка агента не подтверждена. Проверь интернет, установку Python/OpenSSL и существующие настройки агента; затем повтори.";
        }
    }

    private static byte[] readAsset(java.io.InputStream in) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] block = new byte[2048]; int count;
        while ((count = in.read(block)) != -1) {
            if (out.size() + count > 65536) throw new java.io.IOException();
            out.write(block, 0, count);
        }
        return out.toByteArray();
    }
}
