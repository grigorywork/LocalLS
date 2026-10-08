package com.bitpoint.homeservercontrol;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import com.jcraft.jsch.HostKey;
import com.jcraft.jsch.HostKeyRepository;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.KeyPair;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CoreInstrumentedTest {
    @Test
    public void defaultsMatchRealmeSetup() {
        assertEquals(8022, ServerConfig.DEFAULT_PORT);
        assertEquals("u0_a606", ServerConfig.DEFAULT_USER);
        assertTrue(ServerConfig.DEFAULT_REMOTE_PATH.endsWith("/storage/shared"));
    }

    @Test
    public void sftpPathHelpersAreStable() {
        assertEquals("/a/b/file.txt", SftpClient.join("/a/b", "file.txt"));
        assertEquals("/a/b", SftpClient.parent("/a/b/file.txt"));
        assertEquals("/", SftpClient.parent("/a"));
    }

    @Test
    public void iperfParserUsesLastMbpsValue() {
        String sample = "[  5] 0.00-5.00 sec 100 MBytes 167.8 Mbits/sec sender\n"
                + "[  5] 0.00-5.00 sec 99 MBytes 166.1 Mbits/sec receiver";
        assertEquals(166.1, MetricHistoryStore.parseIperfMbps(sample), 0.01);
    }

    @Test
    public void fingerprintPinRejectsChangedKeysAndCase() throws Exception {
        JSch jsch = new JSch();
        KeyPair key = KeyPair.genKeyPair(jsch, KeyPair.RSA, 2048);
        try {
            byte[] blob = key.getPublicKeyBlob();
            String fingerprint = new HostKey("fixture", blob).getFingerPrint(jsch);
            assertEquals(HostKeyRepository.OK, new PinnedHostKeyRepository(jsch, fingerprint).check("fixture", blob));
            assertEquals(HostKeyRepository.CHANGED, new PinnedHostKeyRepository(jsch, fingerprint + "x").check("fixture", blob));
            String changedCase = fingerprint.toUpperCase(java.util.Locale.ROOT);
            if (changedCase.equals(fingerprint)) changedCase = fingerprint.toLowerCase(java.util.Locale.ROOT);
            assertFalse(changedCase.equals(fingerprint));
            assertEquals(HostKeyRepository.CHANGED, new PinnedHostKeyRepository(jsch, changedCase).check("fixture", blob));
        } finally {
            key.dispose();
        }
    }

    @Test
    public void commandsWithoutTrustAreRejectedBeforeConnecting() {
        SshCommandResult result = SshClient.runCommand("127.0.0.1", 1, "fixture", "", "", "true", 1000);
        assertFalse(result.success);
        assertTrue(result.error.contains("fingerprint"));
    }

    @Test
    public void invalidAddressReturnsErrorInsteadOfThrowing() {
        ServerStats stats = SshClient.probe("127.0.0.1", -1, "fixture", "", "");
        assertFalse(stats.reachable);
        assertFalse(stats.error.isEmpty());
    }

    @Test
    public void sessionHandoffIsOneShotAndRedactionRemovesRuntimeSecrets() {
        String value = java.util.UUID.randomUUID().toString();
        SessionPassword.put(value);
        assertEquals(value, SessionPassword.take());
        assertNull(SessionPassword.take());
        assertFalse(SecretRedactor.redact("error " + value, value).contains(value));
    }
}
