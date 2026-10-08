package com.bitpoint.homeservercontrol;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.EditText;
import android.widget.RadioButton;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class RuntimeInstrumentedTest {
    private Context context;
    private SharedPreferences prefs;

    @Before
    public void prepare() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        prefs = context.getSharedPreferences(ServerConfig.PREFS, Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        ConfigMigrator.migrate(context);
    }

    @After
    public void cleanup() {
        MonitoringScheduler.setEnabled(context, false);
        SecurePrefs.clearPassword(context);
        SecurePrefs.clearAgentToken(context);
        prefs.edit().clear().commit();
    }

    @Test
    public void dashboardLaunchesSavesConnectionAndRejectsBadPort() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                assertEquals(android.view.View.GONE, activity.findViewById(R.id.advancedPanel).getVisibility());
                assertTrue(activity.findViewById(R.id.filesButton).isShown());
                assertTrue(activity.findViewById(R.id.dashboardSpeedChart).isShown());
                assertTrue(activity.findViewById(R.id.dashboardLatencyChart).isShown());
                activity.findViewById(R.id.advancedToggleButton).performClick();
                assertEquals(android.view.View.VISIBLE, activity.findViewById(R.id.advancedPanel).getVisibility());
                activity.findViewById(R.id.advancedToggleButton).performClick();
                assertEquals(android.view.View.GONE, activity.findViewById(R.id.advancedPanel).getVisibility());
                ((EditText) activity.findViewById(R.id.localHostInput)).setText("192.168.1.82");
                ((EditText) activity.findViewById(R.id.portInput)).setText("8022");
                ((EditText) activity.findViewById(R.id.userInput)).setText("u0_a606");
                activity.findViewById(R.id.saveButton).performClick();
                assertEquals("192.168.1.82", prefs.getString(ServerConfig.KEY_LOCAL_HOST, ""));
                assertEquals(8022, prefs.getInt(ServerConfig.KEY_PORT, 0));
                assertEquals("u0_a606", prefs.getString(ServerConfig.KEY_USER, ""));
                assertFalse(activity.findViewById(R.id.passwordInput).isSaveEnabled());
                assertFalse(activity.findViewById(R.id.agentTokenInput).isSaveEnabled());
                assertFalse(activity.getIntent().hasExtra("password"));
            });
            scenario.recreate();
            scenario.onActivity(activity -> {
                assertEquals("8022", ((EditText) activity.findViewById(R.id.portInput)).getText().toString());
                ((EditText) activity.findViewById(R.id.portInput)).setText("999999");
                activity.findViewById(R.id.saveButton).performClick();
                assertEquals(8022, prefs.getInt(ServerConfig.KEY_PORT, 0));
            });
        }
    }

    @Test
    public void networkProfilesDoNotOverwriteEachOtherAndFallbackStartsDisabled() {
        assertFalse(ConnectionSelector.autoFallback(context));
        try (ActivityScenario<NetworkActivity> scenario = ActivityScenario.launch(NetworkActivity.class)) {
            scenario.onActivity(activity -> {
                ((EditText) activity.findViewById(R.id.networkLocalHostInput)).setText("192.168.1.82");
                ((EditText) activity.findViewById(R.id.networkVpnHostInput)).setText("100.64.0.2");
                ((RadioButton) activity.findViewById(R.id.networkVpnModeButton)).setChecked(true);
                activity.findViewById(R.id.networkSaveButton).performClick();
                assertEquals("100.64.0.2", ConnectionSelector.selectedHost(context));
                assertEquals("192.168.1.82", ConnectionSelector.alternateHost(context));
                ((RadioButton) activity.findViewById(R.id.networkLocalModeButton)).setChecked(true);
                activity.findViewById(R.id.networkSaveButton).performClick();
                assertEquals("192.168.1.82", ConnectionSelector.selectedHost(context));
                assertEquals("100.64.0.2", ConnectionSelector.alternateHost(context));
            });
        }
    }

    @Test
    public void keystoreRoundTripNeverStoresPlaintext() throws Exception {
        String value = java.util.UUID.randomUUID().toString();
        SecurePrefs.savePassword(context, value);
        SecurePrefs.saveAgentToken(context, value);
        assertEquals(value, SecurePrefs.loadPassword(context));
        assertEquals(value, SecurePrefs.loadAgentToken(context));
        assertFalse(prefs.getString(ServerConfig.KEY_PASSWORD_CIPHER, "").contains(value));
        assertFalse(prefs.getString(ServerConfig.KEY_AGENT_TOKEN_CIPHER, "").contains(value));
    }

    @Test
    public void secondaryScreensInflateAndHistoryPersists() {
        try (ActivityScenario<SettingsActivity> scenario = ActivityScenario.launch(SettingsActivity.class)) {
            scenario.onActivity(activity -> {
                assertFalse(activity.findViewById(R.id.settingsPasswordInput).isSaveEnabled());
                assertFalse(activity.findViewById(R.id.settingsAgentTokenInput).isSaveEnabled());
            });
        }
        TransferLogStore.add(context, "fixture-file", "готово");
        assertTrue(TransferLogStore.render(context).contains("fixture-file"));
        MetricHistoryStore.addAvailability(context, true);
        assertFalse(MetricHistoryStore.read(context).isEmpty());
        try (ActivityScenario<HistoryActivity> ignored = ActivityScenario.launch(HistoryActivity.class)) {
            assertNotNull(ignored);
        }
        try (ActivityScenario<DiagnosticsActivity> ignored = ActivityScenario.launch(DiagnosticsActivity.class)) {
            assertNotNull(ignored);
        }
    }
}
