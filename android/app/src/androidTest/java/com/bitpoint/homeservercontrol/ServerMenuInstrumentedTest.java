package com.bitpoint.homeservercontrol;

import static org.junit.Assert.*;
import android.view.View;
import android.widget.Button;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ServerMenuInstrumentedTest {
    @Test public void functionsHaveIconsAboveSmallLabelsAndServerControlsAreInsidePopup() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                for (int id : new int[]{R.id.checkButton, R.id.filesButton, R.id.attachFilesButton,
                        R.id.networkButton, R.id.settingsButton, R.id.historyButton,
                        R.id.diagnosticsButton, R.id.advancedToggleButton}) {
                    Button button = activity.findViewById(id);
                    assertNotNull(button.getCompoundDrawables()[1]);
                    assertTrue(button.getTextSize() <= 11 * activity.getResources().getDisplayMetrics().scaledDensity);
                    assertTrue(button.getText().length() > 0);
                }
                assertEquals(View.GONE, activity.findViewById(R.id.serverManagementPanel).getVisibility());
                assertFalse(activity.findViewById(R.id.agentStartButton).isShown());
                activity.findViewById(R.id.advancedToggleButton).performClick();
                activity.findViewById(R.id.serverSetupButton).performClick();
            });
            scenario.onActivity(activity -> {
                try {
                    java.lang.reflect.Field field = MainActivity.class.getDeclaredField("serverMenuDialog");
                    field.setAccessible(true);
                    android.app.AlertDialog dialog = (android.app.AlertDialog) field.get(activity);
                    assertNotNull(dialog); assertTrue(dialog.isShowing());
                    for (int id : new int[]{R.id.agentStartButton, R.id.agentStopButton, R.id.agentRestartButton, R.id.serverWizardMenuButton}) {
                        Button button = dialog.findViewById(id);
                        assertTrue(button.isShown());
                        assertNotNull(button.getCompoundDrawables()[1]);
                    }
                    assertEquals(View.GONE, dialog.findViewById(R.id.agentCredentialsPanel).getVisibility());
                    dialog.findViewById(R.id.agentCredentialsToggleButton).performClick();
                    assertEquals(View.VISIBLE, dialog.findViewById(R.id.agentCredentialsPanel).getVisibility());
                    dialog.dismiss();
                } catch (Exception e) { throw new AssertionError(e); }
            });
            java.util.concurrent.atomic.AtomicBoolean restored = new java.util.concurrent.atomic.AtomicBoolean();
            long deadline = System.currentTimeMillis() + 10000;
            while (!restored.get() && System.currentTimeMillis() < deadline) {
                scenario.onActivity(activity -> {
                    View panel = activity.findViewById(R.id.serverManagementPanel);
                    restored.set(panel != null && panel.getVisibility() == View.GONE);
                });
                if (!restored.get()) Thread.sleep(100);
            }
            assertTrue("Dismiss callback must restore the hidden server panel", restored.get());
            scenario.onActivity(activity -> {
                activity.findViewById(R.id.serverSetupButton).performClick();
                try {
                    java.lang.reflect.Field field = MainActivity.class.getDeclaredField("serverMenuDialog");
                    field.setAccessible(true);
                    android.app.AlertDialog reopened = (android.app.AlertDialog) field.get(activity);
                    assertTrue(reopened.isShowing());
                    assertTrue(reopened.findViewById(R.id.agentStartButton).isShown());
                    reopened.dismiss();
                } catch (Exception e) { throw new AssertionError(e); }
            });
        }
    }
}
