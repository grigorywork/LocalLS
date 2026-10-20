package com.bitpoint.homeservercontrol;

import android.content.Context;
import android.content.SharedPreferences;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public class KeyOnlyMigrationTest {
    @Test public void upgradeRemovesPasswordButPreservesKeysTokenAndConnection() {
        Context context = RuntimeEnvironment.getApplication();
        SharedPreferences prefs = context.getSharedPreferences(ServerConfig.PREFS, Context.MODE_PRIVATE);
        prefs.edit().clear().putInt(ServerConfig.KEY_CONFIG_VERSION, 2)
                .putString(ServerConfig.KEY_PASSWORD_CIPHER, "old-password-cipher")
                .putBoolean(ServerConfig.KEY_SAVE_PASSWORD, true).putString("password", "old-password")
                .putString(SshKeys.MODE, "password").putString("ssh_identity_fixture", "encrypted-key")
                .putString(ServerConfig.KEY_AGENT_TOKEN_CIPHER, "encrypted-token")
                .putString(ServerConfig.KEY_LOCAL_HOST, "192.0.2.1").commit();
        ConfigMigrator.migrate(context);
        ConfigMigrator.migrate(context);
        assertEquals(3, prefs.getInt(ServerConfig.KEY_CONFIG_VERSION, 0));
        assertFalse(prefs.contains(ServerConfig.KEY_PASSWORD_CIPHER));
        assertFalse(prefs.contains("password"));
        assertFalse(prefs.getBoolean(ServerConfig.KEY_SAVE_PASSWORD, true));
        assertEquals("key", prefs.getString(SshKeys.MODE, ""));
        assertEquals("encrypted-key", prefs.getString("ssh_identity_fixture", ""));
        assertEquals("encrypted-token", prefs.getString(ServerConfig.KEY_AGENT_TOKEN_CIPHER, ""));
        assertEquals("192.0.2.1", prefs.getString(ServerConfig.KEY_LOCAL_HOST, ""));
    }
}
