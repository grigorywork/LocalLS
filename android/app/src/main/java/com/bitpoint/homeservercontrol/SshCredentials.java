package com.bitpoint.homeservercontrol;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Immutable credential snapshot. Never serialize it to intents, state or logs. */
final class SshCredentials {
    final String password, privateKey, passphrase;
    private SshCredentials(String password, String privateKey, String passphrase) {
        this.password = password == null ? "" : password;
        this.privateKey = privateKey; this.passphrase = passphrase == null ? "" : passphrase;
    }
    static SshCredentials password(String value) { return new SshCredentials(value, null, ""); }
    static SshCredentials key(String value, String passphrase) { return new SshCredentials("", value == null ? "" : value, passphrase); }
    boolean keyMode() { return privateKey != null; }
    boolean available() { return keyMode() ? !privateKey.isEmpty() : !password.isEmpty(); }
    void configure(JSch jsch, Session session) throws Exception {
        session.setConfig("StrictHostKeyChecking", "yes");
        if (keyMode()) {
            if (privateKey.isEmpty()) throw new Exception("SSH-ключ не загружен. Откройте «Безопасность».");
            byte[] raw = privateKey.getBytes(StandardCharsets.UTF_8);
            byte[] phrase = passphrase.getBytes(StandardCharsets.UTF_8);
            try { jsch.addIdentity("LocalLS", raw, null, phrase); }
            catch (LinkageError e) { throw new Exception("Алгоритм ключа не поддерживается на этом Android"); }
            finally { Arrays.fill(raw, (byte) 0); Arrays.fill(phrase, (byte) 0); }
            if (jsch.getIdentityRepository().getIdentities().isEmpty()
                    || jsch.getIdentityRepository().getIdentities().get(0).isEncrypted())
                throw new Exception("Ключ не распознан или неверна парольная фраза.");
            session.setConfig("PreferredAuthentications", "publickey");
        } else {
            session.setPassword(password);
            session.setConfig("PreferredAuthentications", "password");
        }
    }
}
