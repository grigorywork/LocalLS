package com.bitpoint.homeservercontrol;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.KeyPair;
import com.jcraft.jsch.Session;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import static org.junit.Assert.*;

public class SshCredentialsTest {
    @Test public void keyModeRestrictsAuthenticationToPublicKey() throws Exception {
        JSch jsch = new JSch();
        KeyPair pair = KeyPair.genKeyPair(jsch, KeyPair.RSA, 3072);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            pair.writePrivateKey(bytes);
            SshCredentials credentials = SshCredentials.key(bytes.toString(StandardCharsets.UTF_8.name()), "");
            Session session = jsch.getSession("fixture", "127.0.0.1", 22);
            credentials.configure(jsch, session);
            assertEquals("publickey", session.getConfig("PreferredAuthentications"));
            assertEquals("yes", session.getConfig("StrictHostKeyChecking"));
            assertEquals(1, jsch.getIdentityRepository().getIdentities().size());
            assertFalse(jsch.getIdentityRepository().getIdentities().get(0).isEncrypted());
        } finally {
            pair.dispose();
            jsch.removeAllIdentity();
        }
    }

    @Test public void missingKeyFailsBeforeConnectionRatherThanUsingPassword() throws Exception {
        SshCredentials credentials = SshCredentials.key("", "");
        assertTrue(credentials.keyMode());
        assertFalse(credentials.available());
        JSch jsch = new JSch();
        Session session = jsch.getSession("fixture", "127.0.0.1", 22);
        try {
            credentials.configure(jsch, session);
            fail("Missing identity should block authentication");
        } catch (Exception expected) {
            assertTrue(expected.getMessage().contains("SSH-ключ"));
            assertFalse(session.isConnected());
            assertTrue(jsch.getIdentityRepository().getIdentities().isEmpty());
        }
    }
}
