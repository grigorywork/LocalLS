package com.bitpoint.homeservercontrol;

import static org.junit.Assert.*;

import android.app.ActivityManager;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Optional isolated fixture. No connection to, or change on, the user's Realme. */
@RunWith(AndroidJUnit4.class)
public class SshSftpInstrumentedTest {
    private Context context;
    private String host, user, secret, pin;
    private int port;

    @Before
    public void prepare() {
        Bundle args = InstrumentationRegistry.getArguments();
        org.junit.Assume.assumeTrue(args.containsKey("fixtureHost"));
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        host = args.getString("fixtureHost");
        port = Integer.parseInt(args.getString("fixturePort"));
        user = args.getString("fixtureUser");
        secret = args.getString("fixturePassword");
        ServerStats probe = SshClient.probe(host, port, user, secret, "");
        assertTrue(probe.error, probe.hostKeyNeedsTrust);
        assertFalse(probe.authenticated);
        pin = probe.hostFingerprint;
    }

    @After
    public void cleanup() {
        if (context != null) {
            TransferForegroundService.stop(context);
            context.getSharedPreferences(ServerConfig.PREFS, Context.MODE_PRIVATE).edit().clear().commit();
        }
    }

    @Test
    public void sshAuthenticatesRejectsWrongPasswordAndBlocksFingerprintMismatch() {
        ServerStats good = SshClient.probe(host, port, user, secret, pin);
        assertTrue(good.error, good.authenticated);
        ServerStats bad = SshClient.probe(host, port, user, java.util.UUID.randomUUID().toString(), pin);
        assertFalse(bad.authenticated);
        assertFalse(bad.error.contains(secret));
        ServerStats changed = SshClient.probe(host, port, user, secret, pin + "changed");
        assertTrue(changed.hostKeyMismatch);
        assertFalse(changed.authenticated);
        SshCommandResult command = SshClient.runCommand(host, port, user, secret, pin, "fixture-command", 5000);
        assertTrue(command.error, command.success);
        assertTrue(command.output.contains("fixture-ok"));
    }

    @Test
    public void sftpRoundTripMkdirRenameAndSafeDelete() throws Exception {
        String folder = "/roundtrip-" + java.util.UUID.randomUUID();
        byte[] data = new byte[128 * 1024];
        new java.util.Random(42).nextBytes(data);
        SftpClient.mkdir(host, port, user, secret, pin, folder);
        SftpClient.upload(host, port, user, secret, pin, new ByteArrayInputStream(data), folder + "/file.bin", data.length, null);
        List<RemoteEntry> entries = SftpClient.list(host, port, user, secret, pin, folder);
        assertEquals(1, entries.size());
        assertEquals(data.length, entries.get(0).size);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        SftpClient.download(host, port, user, secret, pin, folder + "/file.bin", output, data.length, null);
        assertArrayEquals(data, output.toByteArray());
        try {
            SftpClient.delete(host, port, user, secret, pin, new RemoteEntry("folder", folder, true, 0, 0));
            fail("Non-empty directory must not be removed");
        } catch (Exception expected) { }
        SftpClient.rename(host, port, user, secret, pin, folder + "/file.bin", folder + "/renamed.bin");
        SftpClient.delete(host, port, user, secret, pin, new RemoteEntry("file", folder + "/renamed.bin", false, data.length, 0));
        SftpClient.delete(host, port, user, secret, pin, new RemoteEntry("folder", folder, true, 0, 0));
        try {
            SftpClient.list(host, port, user, secret, pin + "changed", "/");
            fail("Changed host key must block SFTP");
        } catch (Exception expected) { }
    }

    @Test
    public void filesQueueContinuesInBackgroundAndStopsForegroundGuard() throws Exception {
        org.junit.Assume.assumeTrue(android.os.Build.VERSION.SDK_INT >= 29);
        context.getSharedPreferences(ServerConfig.PREFS, Context.MODE_PRIVATE).edit().clear()
                .putString(ServerConfig.KEY_LOCAL_HOST, host)
                .putInt(ServerConfig.KEY_PORT, port).putString(ServerConfig.KEY_USER, user)
                .putString("last_remote_path", "/").commit();
        HostTrustStore.trust(context, host, port, pin);
        String name = "background-" + java.util.UUID.randomUUID() + ".bin";
        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, name);
        values.put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream");
        Uri uri = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        assertNotNull(uri);
        try {
            try (OutputStream out = context.getContentResolver().openOutputStream(uri, "wt")) {
                assertNotNull(out);
                byte[] chunk = new byte[1024 * 1024];
                for (int i = 0; i < 8; i++) out.write(chunk);
            }
            SessionPassword.put(secret);
            try (ActivityScenario<FilesActivity> scenario = ActivityScenario.launch(FilesActivity.class)) {
                scenario.onActivity(activity -> {
                    try {
                        Method method = FilesActivity.class.getDeclaredMethod("enqueueUpload", Uri.class);
                        method.setAccessible(true);
                        method.invoke(activity, uri);
                    } catch (Exception e) { throw new AssertionError(e); }
                    assertFalse(activity.getIntent().hasExtra("password"));
                });
                long deadline = System.currentTimeMillis() + 15000;
                while (!guardRunning() && System.currentTimeMillis() < deadline) Thread.sleep(100);
                assertTrue("Foreground guard must start", guardRunning());
                java.util.concurrent.atomic.AtomicBoolean graphHasSpeed = new java.util.concurrent.atomic.AtomicBoolean();
                deadline = System.currentTimeMillis() + 25000;
                while (!graphHasSpeed.get() && System.currentTimeMillis() < deadline) {
                    scenario.onActivity(activity -> {
                        try {
                            java.lang.reflect.Field field = MetricChartView.class.getDeclaredField("values");
                            field.setAccessible(true);
                            @SuppressWarnings("unchecked") List<Float> visible = (List<Float>) field.get(
                                    activity.findViewById(R.id.filesSpeedChart));
                            graphHasSpeed.set(visible.stream().anyMatch(value -> value != null && value > 0));
                        } catch (Exception e) { throw new AssertionError(e); }
                    });
                    if (!graphHasSpeed.get()) Thread.sleep(250);
                }
                assertTrue("Visible chart must contain measured speed", graphHasSpeed.get());
                android.graphics.Bitmap screenshot = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
                if (screenshot != null) {
                    try (java.io.FileOutputStream image = new java.io.FileOutputStream(new java.io.File(
                            context.getExternalFilesDir(null), "transfer-preview.png"))) {
                        screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, image);
                    } finally { screenshot.recycle(); }
                }
                InstrumentationRegistry.getInstrumentation().getUiAutomation()
                        .executeShellCommand("input keyevent KEYCODE_HOME").close();
                deadline = System.currentTimeMillis() + 120000;
                while (!TransferLogStore.render(context).contains(name)
                        && System.currentTimeMillis() < deadline) Thread.sleep(250);
                String history = TransferLogStore.render(context);
                assertTrue(history, history.contains(name) && history.contains("готово"));
                assertFalse(history.contains(secret));
                assertFalse("Foreground guard must stop after queue", guardRunning());
                TransferSpeedStore.Snapshot speed = TransferSpeedStore.snapshot();
                assertTrue("Real transfer must produce speed samples", speed.peak > 0);
                assertFalse(speed.active);
                assertEquals("Завершено", speed.state);
                assertTrue("Speed history must include SFTP", MetricHistoryStore.read(context).stream()
                        .anyMatch(sample -> sample.speedMbps > 0));
            }
            assertEquals("Завершено", TransferSpeedStore.snapshot().state);
            SftpClient.delete(host, port, user, secret, pin, new RemoteEntry(name, "/" + name, false, 0, 0));
        } finally {
            context.getContentResolver().delete(uri, null, null);
        }
    }

    private boolean guardRunning() {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(100)) {
            if (service.service.getClassName().equals(TransferForegroundService.class.getName()) && service.foreground) return true;
        }
        return false;
    }
}
