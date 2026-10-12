package com.bitpoint.homeservercontrol;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import org.json.JSONObject;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;

/** Uses the existing SSH/key/TLS implementations without changing the selected remote profile. */
final class LocalServerVerification {
    static final String LOOPBACK = "127.0.0.1";
    static final class Failure extends Exception {
        final String reason;
        Failure(String reason) { this.reason = reason; }
    }
    static String verifySsh(Context context, SharedPreferences setup, String password) throws Exception {
        String user = setup.getString("user", "");
        int port = setup.getInt("port", 0);
        if (!user.matches("[a-zA-Z0-9_]{1,64}") || port < 1 || port > 65535) throw new Failure("parameters");
        String actual;
        try { actual = SshClient.probeFingerprint(LOOPBACK, port, user); }
        catch (Exception e) { throw new Failure("ssh_unreachable"); }
        if (!Arrays.asList(setup.getString("host_fingerprints", "").split("\n")).contains(actual))
            throw new Failure("host_key");
        String previous = HostTrustStore.get(context, LOOPBACK, port);
        if (!previous.isEmpty() && !previous.equals(actual)) throw new Failure("host_key");
        SshCredentials credential = password.isEmpty() ? SshKeys.keyCredentials(context, LOOPBACK, port, user)
                : SshCredentials.password(password);
        if (!credential.available()) throw new Failure("credential");
        if (!SshClient.runCommand(LOOPBACK, port, user, credential, actual, ":", 5000).success)
            throw new Failure("authentication");
        HostTrustStore.trust(context, LOOPBACK, port, actual);
        return actual;
    }
    static void installAndVerifyKey(Context context, SharedPreferences setup, String password, String fingerprint) throws Exception {
        String user = setup.getString("user", ""); int port = setup.getInt("port", 0);
        if (!fingerprint.equals(HostTrustStore.get(context, LOOPBACK, port))) throw new Failure("host_key");
        SshCredentials existing = SshKeys.keyCredentials(context, LOOPBACK, port, user);
        SshCredentials bootstrap = password.isEmpty() ? existing : SshCredentials.password(password);
        if (!bootstrap.available()) throw new Failure("credential");
        if (!existing.available()) SshKeys.save(context, LOOPBACK, port, user, SshKeys.generate());
        SshCredentials key = SshKeys.keyCredentials(context, LOOPBACK, port, user);
        if (!key.available()) throw new Failure("key_storage");
        JSONObject info = SshKeys.inspect(key.privateKey, key.passphrase);
        SftpClient.installPublicKey(LOOPBACK, port, user, bootstrap, fingerprint, info.getString(SshKeys.PUBLIC));
        // A new independent SSH/SFTP connection, with only the key configured.
        SftpClient.list(LOOPBACK, port, user, key, fingerprint, ".");
    }
    static int verifyAgent(Context context, Uri tree) throws Exception {
        JSONObject config = new JSONObject(new String(TermuxSetup.readHomeFile(context, tree,
                TermuxSetup.HOME + "/home-server-agent/config.json", 16384), StandardCharsets.UTF_8));
        if (!config.optBoolean("tls", false)) throw new Failure("agent_http");
        String token = config.optString("token"); int port = config.optInt("port", 0);
        if (port < 1 || port > 65535 || token.isEmpty() || token.length() > 4096) throw new Failure("agent_config");
        X509Certificate certificate;
        try {
            byte[] pem = TermuxSetup.readHomeFile(context, tree, config.optString("tls_cert"), 16384);
            certificate = (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(new ByteArrayInputStream(pem));
            certificate.checkValidity();
        } catch (java.security.cert.CertificateException failure) { throw new Failure("agent_certificate"); }
        String pin = AgentTls.fingerprint(certificate);
        String host = config.optString("host", "0.0.0.0");
        if (host.isEmpty() || host.equals("0.0.0.0") || host.equals("::")) host = LOOPBACK;
        if (!pin.equals(AgentTls.probe(host, port))) throw new Failure("agent_certificate");
        AgentResult result = AgentClient.request(host, port, token, "status", pin, false);
        if (!result.success) throw new Failure(result.httpCode == 401 ? "agent_token" : "agent_unreachable");
        JSONObject status = new JSONObject(result.body);
        if (!"0.3".equals(status.optString("agent")) || !status.optBoolean("ok", false)) throw new Failure("agent_status");
        if (!"running".equals(status.optString("sshd"))) throw new Failure("ssh_stopped");
        // No changes to the remote profile, its token, TLS pin or legacy mode.
        return port;
    }
}
