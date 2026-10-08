package com.bitpoint.homeservercontrol;

import static org.junit.Assert.*;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.service.notification.StatusBarNotification;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Exercise the actual minimize action and the notification's return Intent. */
@RunWith(AndroidJUnit4.class)
public class TrayInstrumentedTest {
    @Test public void minimizeShowsNotificationAndReturningClearsIt() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        assertNotNull(manager);
        org.junit.Assume.assumeTrue("Notification permission required", manager.areNotificationsEnabled());
        android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        MainActivity activity = (MainActivity) instrumentation.startActivitySync(
                new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        try {
            instrumentation.runOnMainSync(() -> {
                activity.findViewById(R.id.advancedToggleButton).performClick();
                activity.findViewById(R.id.minimizeButton).performClick();
            });
            long deadline = System.currentTimeMillis() + 15000;
            StatusBarNotification tray;
            while ((tray = findTray(manager)) == null && System.currentTimeMillis() < deadline) Thread.sleep(100);
            assertNotNull("Minimize must post the return notification", tray);
            assertTrue((tray.getNotification().flags & android.app.Notification.FLAG_ONGOING_EVENT) != 0);
            deadline = System.currentTimeMillis() + 30000;
            while (stage(activity) != Stage.STOPPED && System.currentTimeMillis() < deadline) Thread.sleep(100);
            assertEquals("Task must move to background", Stage.STOPPED, stage(activity));
            assertNotNull(tray.getNotification().contentIntent);
            tray.getNotification().contentIntent.send();
            deadline = System.currentTimeMillis() + 30000;
            while ((stage(activity) != Stage.RESUMED || findTray(manager) != null)
                    && System.currentTimeMillis() < deadline) Thread.sleep(100);
            assertEquals(Stage.RESUMED, stage(activity));
            assertNull("Returning must clear the tray notification", findTray(manager));
        } finally {
            instrumentation.runOnMainSync(activity::finish);
            NotificationHelper.clearTray(context);
        }
    }

    private Stage stage(MainActivity activity) {
        java.util.concurrent.atomic.AtomicReference<Stage> value = new java.util.concurrent.atomic.AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> value.set(
                ActivityLifecycleMonitorRegistry.getInstance().getLifecycleStageOf(activity)));
        return value.get();
    }

    private StatusBarNotification findTray(NotificationManager manager) {
        for (StatusBarNotification item : manager.getActiveNotifications())
            if (item.getId() == NotificationHelper.TRAY_ID) return item;
        return null;
    }
}
