package com.bitpoint.homeservercontrol;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;
import java.nio.file.Files;
import java.nio.file.Path;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public class ServerSetupSecurityIntegrationTest {
    private Path root;
    private JSONObject fixture;
    @Before public void fixture() throws Exception {
        String location = System.getenv("LOCALLS_SECURITY_FIXTURE");
        assumeTrue("Requires tools/security_fixture.py on isolated loopback", location != null);
        root = Path.of(location);
        fixture = new JSONObject(Files.readString(root.resolve("fixture.json")));
    }
    @Test public void installsOncePreservesExistingKeysAndUsesSeparatePublicKeyAuthentication() throws Exception {
        Path authorized = root.resolve("home/.ssh/authorized_keys");
        byte[] before = Files.readAllBytes(authorized);
        JSONObject key = SshKeys.generate();
        int port = fixture.getInt("sshPort");
        int passwords = metrics().getInt("passwordAuths");
        String fingerprint = ServerSetupSecurity.installAndVerifyKey("127.0.0.1", port, "fixture",
                fixture.getString("password"), fixture.getString("hostPin"), key);
        assertEquals(key.getString("fingerprint"), fingerprint);
        byte[] first = Files.readAllBytes(authorized);
        assertTrue(new String(first, java.nio.charset.StandardCharsets.UTF_8)
                .startsWith(new String(before, java.nio.charset.StandardCharsets.UTF_8)));
        assertEquals(passwords + 1, metrics().getInt("passwordAuths"));
        int keyAttempts = metrics().getInt("keyAuths");
        assertTrue(keyAttempts > 0);
        ServerSetupSecurity.installAndVerifyKey("127.0.0.1", port, "fixture", fixture.getString("password"),
                fixture.getString("hostPin"), key);
        assertArrayEquals(first, Files.readAllBytes(authorized));
        assertEquals(passwords + 2, metrics().getInt("passwordAuths"));
        assertTrue(metrics().getInt("keyAuths") > keyAttempts);
    }
    @Test public void deniedKeyCannotFallbackToPasswordAndChangedHostPinBlocksAuthentication() throws Exception {
        JSONObject key = SshKeys.generate();
        JSONObject counters = metrics(); counters.put("rejectKeys", true); writeMetrics(counters);
        int passwords = counters.getInt("passwordAuths");
        try {
            try {
                ServerSetupSecurity.installAndVerifyKey("127.0.0.1", fixture.getInt("sshPort"), "fixture",
                        fixture.getString("password"), fixture.getString("hostPin"), key);
                fail("Denied key must not report success");
            } catch (Exception expected) { }
            assertEquals(passwords + 1, metrics().getInt("passwordAuths"));
            try {
                ServerSetupSecurity.installAndVerifyKey("127.0.0.1", fixture.getInt("sshPort"), "fixture",
                        fixture.getString("password"), "SHA256:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", key);
                fail("Changed host key must stop authentication");
            } catch (Exception expected) { }
            assertEquals(passwords + 1, metrics().getInt("passwordAuths"));
        } finally { counters = metrics(); counters.put("rejectKeys", false); writeMetrics(counters); }
    }
    @Test public void verifiesRealAgentWithoutChangingTokenCertificateOrConfiguration() throws Exception {
        byte[] config = Files.readAllBytes(root.resolve("config.json"));
        byte[] certificate = Files.readAllBytes(root.resolve("agent-cert.pem"));
        String fingerprint = ServerSetupSecurity.verifyAgent("127.0.0.1", fixture.getInt("agentPort"), fixture.getString("token"), certificate);
        assertTrue(fingerprint.matches("SHA256:[a-f0-9]{64}"));
        assertArrayEquals(config, Files.readAllBytes(root.resolve("config.json")));
        assertArrayEquals(certificate, Files.readAllBytes(root.resolve("agent-cert.pem")));
    }
    @Test public void wrongCertificateIsRejectedBeforeAnAuthenticatedHttpRequest() throws Exception {
        Path log = root.resolve("agent.log");
        long requests = Files.readString(log).lines().filter(line -> line.contains("GET /status")).count();
        try {
            ServerSetupSecurity.verifyAgent("127.0.0.1", fixture.getInt("agentPort"), fixture.getString("token"),
                    Files.readAllBytes(root.resolve("other-cert.pem")));
            fail("Changed certificate must fail");
        } catch (Exception expected) { }
        assertEquals(requests, Files.readString(log).lines().filter(line -> line.contains("GET /status")).count());
    }
    @Test public void missingSharedStorageCanRecoverToCanonicalHomeAndTransferFile() throws Exception {
        int port = fixture.getInt("sshPort");
        SshCredentials credential = SshCredentials.password(fixture.getString("password"));
        String pin = fixture.getString("hostPin");
        try {
            SftpClient.browse("127.0.0.1", port, "fixture", credential, pin,
                    "/data/data/com.termux/files/home/storage/shared");
            fail("Unavailable shared storage must report a directory error");
        } catch (Exception expected) {
            assertTrue(SftpClient.isDirectoryError(expected));
        }
        SftpClient.DirectoryListing home = SftpClient.browse("127.0.0.1", port, "fixture", credential, pin, ".");
        assertEquals("/", home.path);
        String path = SftpClient.join(home.path, "transfer-regression.txt");
        byte[] data = "LocalLS transfer test".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        SftpClient.upload("127.0.0.1", port, "fixture", credential, pin,
                new java.io.ByteArrayInputStream(data), path, data.length, null);
        java.io.ByteArrayOutputStream downloaded = new java.io.ByteArrayOutputStream();
        SftpClient.download("127.0.0.1", port, "fixture", credential, pin, path, downloaded, data.length, null);
        assertArrayEquals(data, downloaded.toByteArray());
    }

    @Test public void remoteExitStatusIsRequiredAndStderrIsNotExposed() throws Exception {
        SshCommandResult ok = command("fixture-ok", 3000);
        assertTrue(ok.success);
        assertEquals(0, ok.exitCode);
        assertEquals("ready", ok.output);
        SshCommandResult failure = command("fixture-fail", 3000);
        assertFalse(failure.success);
        assertEquals(41, failure.exitCode);
        assertFalse(failure.error.contains(fixture.getString("password")));
        assertFalse(failure.output.contains("private diagnostic"));
        assertTrue(command("fixture-stderr", 3000).success);
    }

    @Test public void oversizedAndTimedOutCommandsCannotReportSuccess() throws Exception {
        assertFalse(command("fixture-oversize", 3000).success);
        long started = System.nanoTime();
        SshCommandResult timeout = command("fixture-timeout", 1000);
        assertFalse(timeout.success);
        assertEquals(-1, timeout.exitCode);
        assertTrue(java.util.concurrent.TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - started) < 5);
    }

    @Test public void normalClientIgnoresLegacyPasswordModeAndNeverSendsPasswordWithoutKey() throws Exception {
        android.content.Context context = org.robolectric.RuntimeEnvironment.getApplication();
        SshKeys.prefs(context).edit().putString(SshKeys.MODE, "password").commit();
        int passwordAuths = metrics().getInt("passwordAuths");
        SshCommandResult result = SshClient.runCommand(context, "127.0.0.1", fixture.getInt("sshPort"), "fixture",
                fixture.getString("password"), fixture.getString("hostPin"), "fixture-ok", 3000);
        assertFalse(result.success);
        assertEquals(passwordAuths, metrics().getInt("passwordAuths"));
        assertTrue(SshKeys.credentials(context, "127.0.0.1", fixture.getInt("sshPort"), "fixture", "password").keyMode());
    }

    private SshCommandResult command(String command, int timeout) throws Exception {
        return SshClient.runCommand("127.0.0.1", fixture.getInt("sshPort"), "fixture",
                fixture.getString("password"), fixture.getString("hostPin"), command, timeout);
    }

    private JSONObject metrics() throws Exception { return new JSONObject(Files.readString(root.resolve("metrics.json"))); }
    private void writeMetrics(JSONObject value) throws Exception { Files.writeString(root.resolve("metrics.json"), value.toString()); }
}
