package com.bitpoint.homeservercontrol;

import static org.junit.Assert.*;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.widget.EditText;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Real Termux integration, executed only after the OS document-tree grant is available. */
@RunWith(AndroidJUnit4.class)
public class TermuxBootstrapInstrumentedTest {
    @Test public void realTermuxBootstrapAuthenticationSftpAndExistingDaemonPreservation() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences setup = context.getSharedPreferences(TermuxSetup.PREFS, Context.MODE_PRIVATE);
        String tree = setup.getString("tree", "");
        org.junit.Assume.assumeTrue("Termux OS folder grant required", !tree.isEmpty());
        String secret = InstrumentationRegistry.getArguments().getString("fixturePassword", "");
        org.junit.Assume.assumeTrue("Runtime credentials required", !secret.isEmpty());
        char[] password = secret.toCharArray();
        try { TermuxSetup.configure(context, Uri.parse(tree), password); }
        finally { java.util.Arrays.fill(password, '\0'); }
        String user, fingerprint, originalOutput;
        int port;
        try (ActivityScenario<ServerSetupActivity> scenario = ActivityScenario.launch(ServerSetupActivity.class)) {
            scenario.onActivity(TermuxSetup::run);
            waitForResult(setup);
            user = setup.getString("user", "");
            assertFalse("Termux must return actual user", user.isEmpty());
            port = setup.getInt("port", 0);
            fingerprint = SshClient.probeFingerprint("127.0.0.1", port, user);
            SshCommandResult original = SshClient.runCommand("127.0.0.1", port, user, secret, fingerprint,
                    "cat \"$PREFIX/var/run/sshd.pid\"; sha256sum \"$PREFIX/etc/ssh/sshd_config\"", 5000);
            assertTrue("Real Termux password authentication must succeed", original.success);
            originalOutput = original.output;
            byte[] value = new byte[32768]; new java.util.Random(29).nextBytes(value);
            String path = TermuxSetup.HOME + "/LocalLS/instrumentation-roundtrip.bin";
            SftpClient.upload("127.0.0.1", port, user, secret, fingerprint, new ByteArrayInputStream(value), path, value.length, null);
            ByteArrayOutputStream read = new ByteArrayOutputStream();
            SftpClient.download("127.0.0.1", port, user, secret, fingerprint, path, read, value.length, null);
            assertArrayEquals(value, read.toByteArray());
            assertTrue(SftpClient.list("127.0.0.1", port, user, secret, fingerprint, TermuxSetup.HOME + "/LocalLS").size() > 0);
        }
        // Exercise the same quick setup entry as the drawer, with no password replacement.
        long lastAttempt = setup.getLong("pending_at", 0);
        android.content.Intent quick = new android.content.Intent(context, ServerSetupActivity.class).putExtra("auto_prepare", true);
        try (ActivityScenario<ServerSetupActivity> scenario = ActivityScenario.launch(quick)) {
            long deadline = System.currentTimeMillis() + 30000;
            while (setup.getLong("pending_at", 0) == lastAttempt && System.currentTimeMillis() < deadline) Thread.sleep(100);
            assertNotEquals("Quick setup must start an actual Termux command", lastAttempt, setup.getLong("pending_at", 0));
            waitForResult(setup);
            assertEquals(fingerprint, SshClient.probeFingerprint("127.0.0.1", port, user));
            SshCommandResult repeated = SshClient.runCommand("127.0.0.1", port, user, secret, fingerprint,
                    "cat \"$PREFIX/var/run/sshd.pid\"; sha256sum \"$PREFIX/etc/ssh/sshd_config\"", 5000);
            assertTrue("Quick setup must preserve the existing password", repeated.success);
            assertEquals("Existing daemon PID and configuration must remain unchanged", originalOutput, repeated.output);
            scenario.onActivity(activity -> {
                ((EditText) activity.findViewById(R.id.serverSetupPasswordInput)).setText(secret);
                activity.findViewById(R.id.verifyServerSetupButton).performClick();
            });
            java.util.concurrent.atomic.AtomicBoolean enabled = new java.util.concurrent.atomic.AtomicBoolean();
            deadline = System.currentTimeMillis() + 30000;
            while (!enabled.get() && System.currentTimeMillis() < deadline) {
                scenario.onActivity(activity -> enabled.set(activity.findViewById(R.id.exportServerProfileButton).isEnabled()));
                Thread.sleep(200);
            }
            assertTrue("GUI must enable export after real SSH login", enabled.get());
        }
    }
    private void waitForResult(SharedPreferences setup) throws Exception {
        long deadline = System.currentTimeMillis() + 360000;
        while (!setup.getString("pending", "").isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(500);
        assertEquals("Termux callback must arrive", "", setup.getString("pending", ""));
        assertTrue("Termux preparation: " + setup.getString("status", "") + " (Termux code "
                + setup.getInt("last_termux_error", -2) + ", exit " + setup.getInt("last_exit_code", -1) + ")",
                setup.getString("status", "").contains("OpenSSH подготовлен"));
    }
}
