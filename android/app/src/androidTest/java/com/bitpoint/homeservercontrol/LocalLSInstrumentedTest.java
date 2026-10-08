package com.bitpoint.homeservercontrol;

import static org.junit.Assert.*;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.view.View;
import android.view.ContextThemeWrapper;
import android.widget.Button;
import android.widget.FrameLayout;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.ArrayList;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class LocalLSInstrumentedTest {
    private Context context;
    private SharedPreferences prefs;

    @Before public void prepare() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        prefs = context.getSharedPreferences(ServerConfig.PREFS, Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        ConfigMigrator.migrate(context);
    }

    @After public void cleanup() {
        MonitoringScheduler.setEnabled(context, false);
        prefs.edit().clear().commit();
    }

    @Test public void drawerOccupiesRightHalfAndBackClosesIt() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                assertEquals("LocalLS", activity.getApplicationInfo().loadLabel(activity.getPackageManager()).toString());
                assertNotNull(activity.getApplicationInfo().loadIcon(activity.getPackageManager()));
                assertFalse(activity.findViewById(R.id.drawerVpnSwitch).isShown());
                assertEquals(View.GONE, activity.findViewById(R.id.drawerScrim).getVisibility());
            });
            screenshot("localls-main.png");
            scenario.onActivity(activity -> activity.findViewById(R.id.advancedToggleButton).performClick());
            java.util.concurrent.atomic.AtomicBoolean opened = new java.util.concurrent.atomic.AtomicBoolean();
            long deadline = System.currentTimeMillis() + 10000;
            while (!opened.get() && System.currentTimeMillis() < deadline) {
                scenario.onActivity(activity -> {
                    View drawer = activity.findViewById(R.id.advancedPanel);
                    opened.set(drawer.getWidth() > 0 && Math.abs(drawer.getTranslationX()) < 0.1f);
                });
                if (!opened.get()) Thread.sleep(100);
            }
            assertTrue("Drawer animation must finish", opened.get());
            scenario.onActivity(activity -> {
                View root = activity.findViewById(R.id.dashboardRoot);
                View drawer = activity.findViewById(R.id.advancedPanel);
                assertEquals(root.getWidth() / 2, ((FrameLayout.LayoutParams) drawer.getLayoutParams()).width);
                assertEquals(root.getWidth() / 2, drawer.getWidth());
                assertEquals(root.getWidth(), drawer.getRight());
                assertEquals(0f, drawer.getTranslationX(), 0.1f);
                assertFalse(activity.findViewById(R.id.drawerVpnSwitch).isShown());
                assertFalse(activity.findViewById(R.id.themeChooserButton).isShown());
                activity.findViewById(R.id.drawerVpnHeader).performClick();
                assertEquals(View.VISIBLE, activity.findViewById(R.id.drawerVpnPane).getVisibility());
                activity.findViewById(R.id.drawerAppearanceHeader).performClick();
                assertEquals(View.GONE, activity.findViewById(R.id.drawerVpnPane).getVisibility());
                assertEquals(View.VISIBLE, activity.findViewById(R.id.drawerAppearancePane).getVisibility());
                assertEquals(View.VISIBLE, activity.findViewById(R.id.drawerScrim).getVisibility());
            });
            screenshot("localls-drawer.png");
            scenario.onActivity(activity -> {
                activity.onBackPressed();
                assertEquals(View.GONE, activity.findViewById(R.id.advancedPanel).getVisibility());
                assertEquals(View.GONE, activity.findViewById(R.id.drawerScrim).getVisibility());
                assertFalse(activity.isFinishing());
            });
        }
    }

    @Test public void palettesInflateAndLightThemePersistsAcrossRecreation() throws Exception {
        int[] backgrounds = {Color.rgb(20,16,25), Color.rgb(13,23,37), Color.rgb(14,25,22),
                Color.rgb(28,21,16), Color.rgb(244,241,248)};
        for (int i = 0; i < ThemeCatalog.IDS.length; i++) {
            ThemeCatalog.select(context, i);
            Context themed = new ContextThemeWrapper(context, ThemeCatalog.style(context));
            assertEquals(backgrounds[i], ThemeCatalog.color(themed, R.attr.hscBackground));
            assertNotEquals(ThemeCatalog.color(themed, R.attr.hscBackground), ThemeCatalog.color(themed, R.attr.hscTextPrimary));
        }
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                assertEquals(4, ThemeCatalog.index(activity));
                assertEquals(backgrounds[4], ((ColorDrawable) activity.findViewById(R.id.dashboardRoot).getBackground()).getColor());
                try {
                    java.lang.reflect.Field field = MetricChartView.class.getDeclaredField("linePaint");
                    field.setAccessible(true);
                    Paint paint = (Paint) field.get(activity.findViewById(R.id.dashboardSpeedChart));
                    assertEquals(ThemeCatalog.color(activity, R.attr.hscAccent), paint.getColor());
                } catch (Exception e) { throw new AssertionError(e); }
            });
            screenshot("localls-light.png");
            ThemeCatalog.select(context, 1);
            scenario.recreate();
            scenario.onActivity(activity -> assertEquals(backgrounds[1],
                    ((ColorDrawable) activity.findViewById(R.id.dashboardRoot).getBackground()).getColor()));
            assertEquals("ocean", prefs.getString(ThemeCatalog.KEY, ""));
        }
    }

    @Test public void incomingShareIsStagedAndUnsafeFileUrisAreRejected() {
        Uri document = Uri.parse("content://fixture.documents/first");
        Intent share = new Intent(context, MainActivity.class).setAction(Intent.ACTION_SEND)
                .putExtra(Intent.EXTRA_STREAM, document).setType("application/octet-stream");
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(share)) {
            scenario.onActivity(activity -> assertTrue(((Button) activity.findViewById(R.id.attachFilesButton))
                    .getText().toString().contains("Вложений: 1")));
            scenario.recreate();
            scenario.onActivity(activity -> assertTrue(((Button) activity.findViewById(R.id.attachFilesButton))
                    .getText().toString().contains("Вложений: 1")));
            scenario.onActivity(activity -> {
                try {
                    java.lang.reflect.Field field = MainActivity.class.getDeclaredField("attachedFiles");
                    field.setAccessible(true);
                    ((ArrayList<?>) field.get(activity)).clear(); // Same completed handoff as openFiles.
                } catch (Exception e) { throw new AssertionError(e); }
            });
            scenario.recreate();
            scenario.onActivity(activity -> assertFalse("Completed attachments must not reappear",
                    ((Button) activity.findViewById(R.id.attachFilesButton)).getText().toString().contains("Вложений:")));
        }
        ArrayList<Uri> items = new ArrayList<>();
        assertFalse(FileAttachments.add(items, Uri.parse("file:///data/data/private/settings.xml")));
        assertTrue(FileAttachments.add(items, document));
        assertFalse(FileAttachments.add(items, document));
        for (int i = 0; i < 100; i++) FileAttachments.add(items, Uri.parse("content://fixture.documents/" + i));
        assertEquals(FileAttachments.MAX, items.size());
        assertEquals("file.bin", FileAttachments.safeName(".."));
        String name = FileAttachments.safeName("../bad\\name\0.bin");
        assertFalse(name.contains("/")); assertFalse(name.contains("\\")); assertFalse(name.contains("\0"));
    }

    @Test public void absentVpnClientDoesNotFakeConnectionOrChangeProfiles() {
        org.junit.Assume.assumeFalse(VpnController.installed(context));
        prefs.edit().putString(ServerConfig.KEY_LOCAL_HOST,"192.168.1.82")
                .putString(ServerConfig.KEY_VPN_HOST,"100.64.0.2")
                .putString(ServerConfig.KEY_MODE,ServerConfig.MODE_LOCAL).commit();
        Intent connect = VpnController.command(true), disconnect = VpnController.command(false);
        assertEquals("com.tailscale.ipn.CONNECT_VPN", connect.getAction());
        assertEquals("com.tailscale.ipn.DISCONNECT_VPN", disconnect.getAction());
        assertEquals("com.tailscale.ipn", connect.getPackage());
        assertEquals("com.tailscale.ipn", disconnect.getPackage());
        assertEquals(VpnController.Request.NOT_INSTALLED, VpnController.request(context, true));
        assertEquals(VpnController.Request.NOT_INSTALLED, VpnController.request(context, false));
        assertNotEquals(VpnController.State.TAILSCALE, VpnController.state(context));
        assertEquals("192.168.1.82", ConnectionSelector.selectedHost(context));
        assertEquals("100.64.0.2", ConnectionSelector.alternateHost(context));
        assertFalse(ConnectionSelector.autoFallback(context));
    }

    private void screenshot(String name) throws Exception {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        Thread.sleep(500);
        android.graphics.Bitmap bitmap = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        if (bitmap == null) return;
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(
                new java.io.File(context.getExternalFilesDir(null), name))) {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out);
        } finally { bitmap.recycle(); }
    }
}
