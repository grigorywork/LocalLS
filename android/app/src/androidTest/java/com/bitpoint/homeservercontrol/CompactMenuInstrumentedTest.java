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
                activity.findViewById(R.id.settingsButton).performClick();
                assertEquals(View.VISIBLE, activity.findViewById(R.id.drawerSettingsPane).getVisibility());
                activity.findViewById(R.id.drawerWifiHeader).performClick();
                assertEquals(View.GONE, activity.findViewById(R.id.drawerSettingsPane).getVisibility());
                assertEquals(View.VISIBLE, activity.findViewById(R.id.drawerWifiPane).getVisibility());
                activity.findViewById(R.id.drawerVpnHeader).performClick();
                assertEquals(View.GONE, activity.findViewById(R.id.drawerWifiPane).getVisibility());
                assertEquals(View.VISIBLE, activity.findViewById(R.id.drawerVpnPane).getVisibility());
                activity.findViewById(R.id.drawerVpnHeader).performClick();
                assertEquals(View.GONE, activity.findViewById(R.id.drawerVpnPane).getVisibility());
                for (int id : new int[]{R.id.restartServerButton, R.id.checkButton, R.id.drawerVpnHeader,
                        R.id.wifiConnectButton, R.id.agentRestartButton}) {
                    Button button = activity.findViewById(id);
                    assertTrue(button.getLayoutParams().height >= Math.round(48 * activity.getResources().getDisplayMetrics().density));
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
        IconShapeCatalog.select(context, 2);
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> assertTrue(((Button)activity.findViewById(R.id.restartServerButton))
                    .getCompoundDrawables()[1] instanceof IconBadgeDrawable));
            scenario.recreate();
            scenario.onActivity(activity -> {
                assertEquals(2, IconShapeCatalog.index(activity));
                assertTrue(((Button)activity.findViewById(R.id.checkButton)).getCompoundDrawables()[1] instanceof IconBadgeDrawable);
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
                activity.findViewById(R.id.advancedToggleButton).performClick();
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
        Bundle args = InstrumentationRegistry.getArguments();
        try (ActivityScenario<NetworkActivity> scenario = ActivityScenario.launch(NetworkActivity.class)) {
            scenario.onActivity(activity -> {
                ((EditText)activity.findViewById(R.id.networkLocalHostInput)).setText("192.0.2.1");
                activity.findViewById(R.id.networkTestButton).performClick();
                context.getSharedPreferences(ServerConfig.PREFS, Context.MODE_PRIVATE).edit()
                        .putInt(ServerConfig.KEY_PORT, Integer.parseInt(args.getString("fixturePort"))).commit();
                ((EditText)activity.findViewById(R.id.networkLocalHostInput)).setText(args.getString("fixtureHost"));
                activity.findViewById(R.id.networkTestButton).performClick();
            });
            AtomicBoolean done = new AtomicBoolean();
            long deadline=System.currentTimeMillis()+15000;
            while (!done.get() && System.currentTimeMillis()<deadline) {
                scenario.onActivity(activity -> done.set(((TextView)activity.findViewById(R.id.networkResultText))
                        .getText().toString().contains("SSH-порт доступен")));
                if (!done.get()) Thread.sleep(100);
            }
            assertTrue("Current request must complete without waiting for old timeout", done.get());
            Thread.sleep(4500);
            scenario.onActivity(activity -> {
                String result=((TextView)activity.findViewById(R.id.networkResultText)).getText().toString();
                assertTrue(result.contains(args.getString("fixtureHost")));
                assertFalse(result.contains("192.0.2.1"));
            });
        }
    }
    @Test public void restartButtonSendsConfirmedRequestToIndependentAgent() throws Exception {
        Bundle args = InstrumentationRegistry.getArguments();
        String host=args.getString("fixtureHost"), token=args.getString("fixtureAgentToken");
        int port=Integer.parseInt(args.getString("fixtureAgentPort"));
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                ((EditText)activity.findViewById(R.id.localHostInput)).setText(host);
                ((EditText)activity.findViewById(R.id.agentPortInput)).setText(String.valueOf(port));
                ((EditText)activity.findViewById(R.id.agentTokenInput)).setText(token);
                activity.findViewById(R.id.restartServerButton).performClick();
                try {
                    java.lang.reflect.Field field=MainActivity.class.getDeclaredField("pendingAgentDialog");field.setAccessible(true);
                    AlertDialog dialog=(AlertDialog)field.get(activity);
                    assertTrue(dialog.isShowing());
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                } catch (Exception e) { throw new AssertionError("Restart confirmation unavailable"); }
            });
            AtomicBoolean done = new AtomicBoolean();
            long deadline=System.currentTimeMillis()+15000;
            while (!done.get() && System.currentTimeMillis()<deadline) {
                scenario.onActivity(activity -> done.set(((TextView)activity.findViewById(R.id.agentOutputText))
                        .getText().toString().contains("fixture-restart-ok")));
                if (!done.get()) Thread.sleep(100);
            }
            assertTrue("Confirmed restart must reach separate HTTP agent", done.get());
            scenario.onActivity(activity -> assertFalse(((TextView)activity.findViewById(R.id.logText)).getText().toString().contains(token)));
        }
    }
}
