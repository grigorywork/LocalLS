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
    @Test public void realTermuxPolicyRefusalReportsRecoveryWithoutChangingExistingDaemon() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences setup = context.getSharedPreferences(TermuxSetup.PREFS, Context.MODE_PRIVATE);
        String granted = setup.getString("tree", "");
        org.junit.Assume.assumeTrue("Termux OS folder grant required", !granted.isEmpty());
        Uri properties = android.provider.DocumentsContract.buildDocumentUriUsingTree(Uri.parse(granted),
                TermuxSetup.HOME + "/.termux/termux.properties");
        byte[] original;
        try (java.io.InputStream input = context.getContentResolver().openInputStream(properties)) {
            assertNotNull(input);
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] block = new byte[4096]; int n;
            while ((n = input.read(block)) != -1) {
                buffer.write(block, 0, n); assertTrue(buffer.size() <= 65536);
            }
            original = buffer.toByteArray();
        }
        String blocked = TermuxSetup.enableExternalApps(new String(original, java.nio.charset.StandardCharsets.UTF_8))
                .replace("\nallow-external-apps=true\n", "\nallow-external-apps=false\n");
        try {
            try (java.io.OutputStream output = context.getContentResolver().openOutputStream(properties, "wt")) {
                assertNotNull(output); output.write(blocked.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            try (ActivityScenario<ServerSetupActivity> scenario = ActivityScenario.launch(ServerSetupActivity.class)) {
                if (!TermuxSetup.version(context).startsWith("0.118")) reloadAndReturn(scenario);
                scenario.onActivity(TermuxSetup::run);
                long deadline = System.currentTimeMillis() + 120000;
                while (!setup.getString("pending", "").isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(250);
                assertEquals("Actual Termux error callback required", "", setup.getString("pending", ""));
                assertNotEquals(-1, setup.getInt("last_termux_error", -1));
                assertTrue(setup.getString("status", "").contains("внешних команд"));
                assertTrue(setup.getString("status", "").contains("Код Termux:"));
                assertFalse(setup.getString("status", "").contains("allow-external-apps"));
            }
        } finally {
            try (java.io.OutputStream output = context.getContentResolver().openOutputStream(properties, "wt")) {
                assertNotNull(output); output.write(original);
            }
            setup.edit().remove("pending").commit();
        }
    }

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
        if (!TermuxSetup.version(context).startsWith("0.118")) cacheDisabledPolicyWithEnabledFile(context, Uri.parse(tree));
        String user, fingerprint, originalOutput;
        int port;
        setup.edit().remove("applied_termux_version").putString("status", "Waiting for quick preparation").commit();
        android.content.Intent firstQuick = new android.content.Intent(context, ServerSetupActivity.class).putExtra("auto_prepare", true);
        try (ActivityScenario<ServerSetupActivity> scenario = ActivityScenario.launch(firstQuick)) {
            if (TermuxSetup.needsSettingsReload(context)) {
                clickReloadDialog();
                Thread.sleep(4200);
                backFromTermux();
            }
            long started = System.currentTimeMillis() + 30000;
            while (setup.getString("pending", "").isEmpty() && !setup.getString("status", "").contains("OpenSSH подготовлен")
                    && System.currentTimeMillis() < started) Thread.sleep(100);
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
            java.util.concurrent.atomic.AtomicBoolean verificationReady = new java.util.concurrent.atomic.AtomicBoolean();
            deadline = System.currentTimeMillis() + 30000;
            while (!verificationReady.get() && System.currentTimeMillis() < deadline) {
                scenario.onActivity(activity -> verificationReady.set(activity.findViewById(R.id.verifyServerSetupButton).isEnabled()));
                Thread.sleep(100);
            }
            assertTrue("Verify button must reflect completed preparation", verificationReady.get());
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
            assertTrue("GUI must enable export after real SSH login: " + setup.getString("status", ""), enabled.get());
        }
    }
    private void reloadAndReturn(ActivityScenario<ServerSetupActivity> scenario) throws Exception {
        scenario.onActivity(TermuxSetup::openForSettingsReload);
        Thread.sleep(4200);
        backFromTermux();
    }
    private void cacheDisabledPolicyWithEnabledFile(Context context, Uri tree) throws Exception {
        Uri properties = android.provider.DocumentsContract.buildDocumentUriUsingTree(tree,
                TermuxSetup.HOME + "/.termux/termux.properties");
        byte[] enabled;
        try (java.io.InputStream input = context.getContentResolver().openInputStream(properties)) {
            assertNotNull(input);
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] block = new byte[4096]; int n;
            while ((n = input.read(block)) != -1) { buffer.write(block, 0, n); assertTrue(buffer.size() <= 65536); }
            enabled = buffer.toByteArray();
        }
        try {
            byte[] disabled = new String(enabled, java.nio.charset.StandardCharsets.UTF_8)
                    .replace("allow-external-apps=true", "allow-external-apps=false")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            try (java.io.OutputStream output = context.getContentResolver().openOutputStream(properties, "wt")) {
                assertNotNull(output); output.write(disabled);
            }
            try (ActivityScenario<ServerSetupActivity> scenario = ActivityScenario.launch(ServerSetupActivity.class)) {
                reloadAndReturn(scenario);
            }
        } finally {
            // Match the reported defect: disk says true while Termux still caches false.
            try (java.io.OutputStream output = context.getContentResolver().openOutputStream(properties, "wt")) {
                assertNotNull(output); output.write(enabled);
            }
        }
    }
    private void backFromTermux() throws Exception {
        for (int attempt = 0; attempt < 4; attempt++) {
            try (android.os.ParcelFileDescriptor fd = InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation().executeShellCommand("input keyevent KEYCODE_BACK")) {
                try (java.io.InputStream input = new android.os.ParcelFileDescriptor.AutoCloseInputStream(fd)) {
                    while (input.read() != -1) { }
                }
            }
            long deadline = System.currentTimeMillis() + 3000;
            while (System.currentTimeMillis() < deadline) {
                android.view.accessibility.AccessibilityNodeInfo root = InstrumentationRegistry.getInstrumentation()
                        .getUiAutomation().getRootInActiveWindow();
                if (root != null && "com.bitpoint.homeservercontrol".contentEquals(root.getPackageName())) return;
                Thread.sleep(200);
            }
        }
        fail("Back must return to LocalLS, not only hide Termux's keyboard");
    }
    private void clickReloadDialog() throws Exception {
        long deadline = System.currentTimeMillis() + 30000;
        while (System.currentTimeMillis() < deadline) {
            android.view.accessibility.AccessibilityNodeInfo root = InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation().getRootInActiveWindow();
            if (root != null) {
                for (android.view.accessibility.AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByText("Открыть и применить")) {
                    if (node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)) return;
                }
            }
            Thread.sleep(200);
        }
        fail("Settings reload dialog must be available");
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
