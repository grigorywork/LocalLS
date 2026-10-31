package com.bitpoint.homeservercontrol;
import static org.junit.Assert.*;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.concurrent.atomic.AtomicBoolean;

@RunWith(AndroidJUnit4.class)
public class CompactMenuInstrumentedTest {
    private Context context;
    @Before public void setup() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.getSharedPreferences(ServerConfig.PREFS, Context.MODE_PRIVATE).edit().clear().commit();
        ConfigMigrator.migrate(context);
    }
    @After public void cleanup() {
        context.getSharedPreferences(ServerConfig.PREFS, Context.MODE_PRIVATE).edit().clear().commit();
        MonitoringScheduler.setEnabled(context, false);
    }
    @Test public void sectionsKeepOnlySelectedPanelAndButtonsRemainCompact() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                activity.findViewById(R.id.toolsMenuButton).performClick();
                assertEquals(0,activity.getResources().getIdentifier("drawerSettingsHeader","id",activity.getPackageName()));
                activity.findViewById(R.id.drawerWifiHeader).performClick();
                assertEquals(View.VISIBLE, activity.findViewById(R.id.drawerWifiPane).getVisibility());
                activity.findViewById(R.id.drawerAppearanceHeader).performClick();
                assertEquals(View.GONE, activity.findViewById(R.id.drawerWifiPane).getVisibility());
                assertEquals(View.VISIBLE, activity.findViewById(R.id.drawerAppearancePane).getVisibility());
                activity.findViewById(R.id.drawerAppearanceHeader).performClick();
                assertEquals(View.GONE, activity.findViewById(R.id.drawerAppearancePane).getVisibility());
                activity.findViewById(R.id.drawerWifiHeader).performClick();
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> {
                assertTrue(activity.findViewById(R.id.wifiConnectButton).isShown());
                for (int id : new int[]{R.id.checkButton, R.id.drawerAppearanceHeader, R.id.wifiConnectButton}) {
                    Button button=activity.findViewById(id);
                    assertTrue(button.getText()+" touch target",button.getHeight()>=Math.round(48*activity.getResources().getDisplayMetrics().density));
                }
            });
        }
    }
    @Test public void returningFromOtherScreenPreservesUnsavedConnection() {
        try (ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> ((EditText)activity.findViewById(R.id.localHostInput)).setText("192.168.2.24"));
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.STARTED);
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED);
            scenario.onActivity(activity -> {
                assertEquals("192.168.2.24",((EditText)activity.findViewById(R.id.localHostInput)).getText().toString());
                assertEquals("● НЕ ПРОВЕРЕНО", ((TextView)activity.findViewById(R.id.statusText)).getText().toString());
            });
        }
    }
    @Test public void iconShapePersistsAndChangesFunctionGlyphs() {
        context.getSharedPreferences(ServerConfig.PREFS,0).edit().putInt("icon_shape",2).commit();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> assertTrue(((CenteredActionButton)activity.findViewById(R.id.checkButton)).hasTileIcon()));
            scenario.recreate();
            scenario.onActivity(activity -> {
                assertEquals(2,activity.getSharedPreferences(ServerConfig.PREFS,0).getInt("icon_shape",0));
                assertEquals(0,activity.getResources().getIdentifier("iconShapeButton","id",activity.getPackageName()));
                assertTrue(((CenteredActionButton)activity.findViewById(R.id.checkButton)).hasTileIcon());
            });
        }
    }
    @Test public void wifiValidationAndPasswordNeverEnterSavedState() {
        assertFalse(WifiSetupController.validationError("", "password", false).isEmpty());
        assertFalse(WifiSetupController.validationError("xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx", "password", false).isEmpty());
        assertFalse(WifiSetupController.validationError("home", "short", false).isEmpty());
        assertEquals("", WifiSetupController.validationError("home", "", true));
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                activity.findViewById(R.id.toolsMenuButton).performClick();
                activity.findViewById(R.id.drawerWifiHeader).performClick();
                EditText name=activity.findViewById(R.id.wifiSsidInput), password=activity.findViewById(R.id.wifiPasswordInput);
                name.setText("home"); password.setText("short");
                activity.findViewById(R.id.wifiConnectButton).performClick();
                assertTrue(((TextView)activity.findViewById(R.id.wifiStatusText)).getText().toString().contains("8–63"));
                assertFalse(password.isSaveEnabled());
                assertFalse(context.getSharedPreferences(ServerConfig.PREFS, Context.MODE_PRIVATE).getAll().containsValue("short"));
            });
            scenario.recreate();
            scenario.onActivity(activity -> assertEquals("", ((EditText)activity.findViewById(R.id.wifiPasswordInput)).getText().toString()));
        }
    }
    @Test public void obsoleteNetworkProbeCannotOverwriteNewAddress() throws Exception {
        try (ActivityScenario<NetworkActivity> scenario=ActivityScenario.launch(NetworkActivity.class)){
            scenario.onActivity(activity->{
                try{
                    java.util.concurrent.CompletableFuture<Void> pending=new java.util.concurrent.CompletableFuture<>();
                    java.lang.reflect.Field task=NetworkActivity.class.getDeclaredField("requestTask");task.setAccessible(true);task.set(activity,pending);
                    ((TextView)activity.findViewById(R.id.networkResultText)).setText("Ищу устройства…");
                    ((EditText)activity.findViewById(R.id.networkLocalHostInput)).setText("192.0.2.99");
                    assertTrue("Address edits cancel obsolete discovery",pending.isCancelled());
                    assertFalse(((TextView)activity.findViewById(R.id.networkResultText)).getText().toString().startsWith("Ищу устройства"));
                }catch(ReflectiveOperationException e){throw new AssertionError(e);}
            });
        }
    }
}
