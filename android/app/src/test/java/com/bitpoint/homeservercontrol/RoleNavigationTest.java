package com.bitpoint.homeservercontrol;

import android.content.Context;
import android.content.Intent;
import android.widget.EditText;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=30)
public class RoleNavigationTest {
    @Test public void keySetupSavesRemoteEndpointWithoutChangingLocalServer() {
        try(var controller=Robolectric.buildActivity(MainActivity.class).create()) {
            MainActivity a=controller.get();
            var local=a.getSharedPreferences(EmbeddedServerService.PREFS,Context.MODE_PRIVATE);
            local.edit().clear().putString("user","my-receiver").putInt("port",9000).putString("allowed_key","keep-public").commit();
            ((EditText)a.findViewById(R.id.localHostInput)).setText("192.168.1.74");
            ((EditText)a.findViewById(R.id.vpnHostInput)).setText("");
            ((android.widget.RadioButton)a.findViewById(R.id.localModeButton)).setChecked(true);
            ((EditText)a.findViewById(R.id.userInput)).setText("localls");
            ((EditText)a.findViewById(R.id.portInput)).setText("8022");
            a.findViewById(R.id.sshKeySetupButton).performClick();
            Intent next=Shadows.shadowOf(a).getNextStartedActivity();
            assertEquals(SecurityActivity.class.getName(),next.getComponent().getClassName());
            var remote=a.getSharedPreferences(ServerConfig.PREFS,Context.MODE_PRIVATE);
            assertEquals("192.168.1.74",remote.getString(ServerConfig.KEY_LOCAL_HOST,""));
            assertEquals("localls",remote.getString(ServerConfig.KEY_USER,""));
            assertEquals(9000,local.getInt("port",0));assertEquals("my-receiver",local.getString("user",""));
            assertEquals("keep-public",local.getString("allowed_key",""));
        }
    }
    @Test public void myServerOpensLocalSetupAndRetiredCommandsAreAbsent() {
        try(var controller=Robolectric.buildActivity(MainActivity.class).create()) {
            MainActivity a=controller.get();
            assertNotNull(a.findViewById(R.id.localServerCard));assertNotNull(a.findViewById(R.id.remoteConnectionCard));
            for(String id:new String[]{"serverSetupButton","currentServerSettingsButton","restartServerButton","agentStartButton","quickSshdButton"})assertEquals(0,a.getResources().getIdentifier(id,"id",a.getPackageName()));
            a.findViewById(R.id.localServerQuickButton).performClick();
            Intent next=Shadows.shadowOf(a).getNextStartedActivity();
            assertEquals(EmbeddedServerActivity.class.getName(),next.getComponent().getClassName());
        }
    }
}
