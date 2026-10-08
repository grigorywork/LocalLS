package com.bitpoint.homeservercontrol;

import static org.junit.Assert.*;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ServerSetupInstrumentedTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

    @Test public void trailingPropertiesContinuationCannotHideExternalAppsPermission() throws Exception {
        String previous = "extra-keys=kept" + (char) 92;
        // Reproduce the former file: allow-external-apps becomes part of extra-keys.
        assertFalse(TermuxSetup.externalAppsAllowed(previous + "\nallow-external-apps=true\n"));
        String corrected = TermuxSetup.enableExternalApps(previous);
        java.util.Properties parsed = new java.util.Properties();
        parsed.load(new java.io.ByteArrayInputStream(corrected.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertEquals("true", parsed.getProperty("allow-external-apps"));
        assertEquals("kept", parsed.getProperty("extra-keys"));
        assertTrue(TermuxSetup.externalAppsAllowed(corrected));
        assertFalse(TermuxSetup.externalAppsAllowed("bad=" + (char) 92 + "u12ZZ\nallow-external-apps=true\n"));
    }

    @Test public void refusedRunCommandShowsSafeRecoveryAndCodesWithoutRawError() {
        SharedPreferences prefs = context.getSharedPreferences(TermuxSetup.PREFS, Context.MODE_PRIVATE);
        String oldStatus = prefs.getString("status", "");
        String secret = InstrumentationRegistry.getArguments().getString("fixturePassword", "private error sample");
        try {
            prefs.edit().putString("pending", "policy-refusal").putLong("pending_at", System.currentTimeMillis()).commit();
            Bundle denied = new Bundle();
            denied.putInt("err", 0); denied.putInt("exitCode", -1);
            denied.putString("errmsg", "allow-external-apps policy refused " + secret);
            new TermuxResultReceiver().onReceive(context,
                    new Intent("localls.setup.policy-refusal").putExtra("result", denied));
            String message = prefs.getString("status", "");
            assertTrue(message.contains("внешних команд"));
            assertTrue(message.contains("Код Termux: 0"));
            assertFalse(message.contains(secret));
            assertFalse(message.contains("policy refused"));
            assertEquals("", prefs.getString("pending", ""));
            assertTrue(TermuxResultReceiver.failureMessage(true, 2, -1, "executable /usr/bin/bash unavailable")
                    .contains("установки среды"));
            assertTrue(TermuxResultReceiver.failureMessage(true, 2, -1, "permission denied")
                    .contains("Разрешения"));
        } finally {
            prefs.edit().putString("status", oldStatus).remove("pending")
                    .remove("last_termux_error").remove("last_exit_code").commit();
        }
    }

    @Test public void termuxPropertiesPreserveUnrelatedSettingsAndHashMatchesIndependentVector() throws Exception {
        String result = TermuxSetup.enableExternalApps("# preserved\nextra-keys=[['ESC']]\nallow-external-apps=false\n allow-external-apps = false\n");
        assertTrue(result.contains("extra-keys=[['ESC']]"));
        assertTrue(result.contains("# preserved"));
        assertFalse(result.contains("=false"));
        assertEquals(1, result.split("allow-external-apps=true", -1).length - 1);
        byte[] hash = TermuxSetup.passwordHash("Совместимость".toCharArray());
        StringBuilder hex = new StringBuilder();
        for (byte value : hash) hex.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
        assertEquals("ade953a8aedd3aa07f1622cdea575068766adaa4", hex.toString());
        assertFalse(TermuxSetup.SCRIPT.contains("pkill"));
        assertFalse(TermuxSetup.SCRIPT.contains("passwd"));
        assertFalse(TermuxSetup.SCRIPT.contains(".bashrc"));
        assertFalse(TermuxSetup.SCRIPT.contains(".termux/boot"));
    }

    @Test public void publicProfileImportsParametersAndRejectsPinnedKeyReplacement() throws Exception {
        SharedPreferences prefs = context.getSharedPreferences(ServerConfig.PREFS, Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        String fingerprint = "SHA256:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
        JSONObject profile = ServerSetupProfile.create("192.168.1.82", 8022, "u0_a606", fingerprint);
        assertFalse(profile.has("password"));
        assertFalse(profile.has("token"));
        ServerSetupProfile.apply(context, profile);
        assertEquals("192.168.1.82", prefs.getString(ServerConfig.KEY_LOCAL_HOST, ""));
        assertEquals(8022, prefs.getInt(ServerConfig.KEY_PORT, 0));
        assertEquals("u0_a606", prefs.getString(ServerConfig.KEY_USER, ""));
        assertEquals(fingerprint, HostTrustStore.get(context, "192.168.1.82", 8022));
        profile.put("fingerprint", "SHA256:BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB");
        try { ServerSetupProfile.apply(context, profile); fail("Changed key must be rejected"); }
        catch (IllegalArgumentException expected) { }
        assertEquals(fingerprint, HostTrustStore.get(context, "192.168.1.82", 8022));
        profile.put("host", "localhost; arbitrary-command");
        try { ServerSetupProfile.validate(profile); fail("Invalid host must be rejected"); }
        catch (IllegalArgumentException expected) { }
        prefs.edit().clear().commit();
    }

    @Test public void privateCallbackRejectsUnknownSessionAndUnsuccessfulTermuxResult() {
        SharedPreferences prefs = context.getSharedPreferences(TermuxSetup.PREFS, Context.MODE_PRIVATE);
        String originalUser = prefs.getString("user", "");
        prefs.edit().putString("pending", "test-session").putLong("pending_at", System.currentTimeMillis()).commit();
        Bundle result = new Bundle(); result.putInt("exitCode", 0); result.putString("stdout", "USER=u0_a123\nPORT=8022\n");
        new TermuxResultReceiver().onReceive(context, new Intent("localls.setup.wrong").putExtra("result", result));
        assertEquals("test-session", prefs.getString("pending", ""));
        assertEquals(originalUser, prefs.getString("user", ""));
        result.putInt("exitCode", 41);
        new TermuxResultReceiver().onReceive(context, new Intent("localls.setup.test-session").putExtra("result", result));
        assertEquals("", prefs.getString("pending", ""));
        assertEquals(originalUser, prefs.getString("user", ""));
        assertTrue(prefs.getString("status", "").contains("не завершена"));
    }

    @Test public void callbackAcceptsOfficialTermuxSuccessAndRejectsZeroErrorCode() {
        SharedPreferences prefs=context.getSharedPreferences(TermuxSetup.PREFS, Context.MODE_PRIVATE);
        // Restore setup data after this protocol test; keep an actual OS folder grant intact.
        String oldUser=prefs.getString("user", ""), oldRole=prefs.getString("device_role", "");
        int oldPort=prefs.getInt("port",8022);
        String oldStatus=prefs.getString("status", ""), oldApplied=prefs.getString("applied_termux_version", "");
        try {
            prefs.edit().putString("pending","official-success").putLong("pending_at",System.currentTimeMillis()).commit();
            Bundle success=new Bundle();success.putInt("err",-1);success.putInt("exitCode",0);
            success.putString("stdout","USER=u0_a123\nPORT=8022\n");
            new TermuxResultReceiver().onReceive(context,new Intent("localls.setup.official-success").putExtra("result",success));
            assertEquals("",prefs.getString("pending",""));
            assertEquals("u0_a123",prefs.getString("user",""));
            assertEquals("server",prefs.getString("device_role",""));
            assertTrue(prefs.getString("status","").contains("OpenSSH подготовлен"));
            prefs.edit().putString("pending","official-error").putLong("pending_at",System.currentTimeMillis()).commit();
            success.putInt("err",0);success.putString("stdout","USER=untrusted\nPORT=9000\n");
            new TermuxResultReceiver().onReceive(context,new Intent("localls.setup.official-error").putExtra("result",success));
            assertEquals("u0_a123",prefs.getString("user",""));
            assertEquals(0,prefs.getInt("last_termux_error",-2));
            assertTrue(prefs.getString("status","").contains("не завершена"));
        } finally {
            prefs.edit().putString("user",oldUser).putInt("port",oldPort).putString("device_role",oldRole)
                    .putString("applied_termux_version",oldApplied)
                    .putString("status",oldStatus).remove("pending").remove("last_termux_error").remove("last_exit_code").commit();
        }
    }

    @Test public void wizardStartsSafelyAndDoesNotSaveOrRestorePassword() {
        try (ActivityScenario<ServerSetupActivity> scenario = ActivityScenario.launch(ServerSetupActivity.class)) {
            scenario.onActivity(activity -> {
                assertFalse(activity.findViewById(R.id.exportServerProfileButton).isEnabled());
                EditText password = activity.findViewById(R.id.serverSetupPasswordInput);
                assertFalse(password.isSaveEnabled());
                assertFalse(activity.findViewById(R.id.serverSetupPasswordConfirm).isSaveEnabled());
                assertFalse(((CheckBox) activity.findViewById(R.id.createServerPasswordCheck)).isChecked());
                assertEquals(View.GONE, activity.findViewById(R.id.serverSetupPasswordConfirm).getVisibility());
                password.setText("disposable UI input");
            });
            scenario.recreate();
            scenario.onActivity(activity -> {
                assertEquals("", ((EditText) activity.findViewById(R.id.serverSetupPasswordInput)).getText().toString());
                assertFalse(activity.findViewById(R.id.exportServerProfileButton).isEnabled());
            });
        }
    }
}
