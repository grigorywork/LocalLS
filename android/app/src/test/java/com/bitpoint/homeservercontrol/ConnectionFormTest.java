package com.bitpoint.homeservercontrol;

import android.app.AlertDialog;
import android.content.Context;
import android.widget.EditText;
import android.widget.RadioButton;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public class ConnectionFormTest {
    @Test public void invalidImportStaysOpenAndValidImportFillsActiveEndpoint() {
        try (var controller = Robolectric.buildActivity(MainActivity.class).create()) {
            MainActivity activity = controller.get();
            ((RadioButton) activity.findViewById(R.id.vpnModeButton)).setChecked(true);
            activity.findViewById(R.id.importEndpointButton).performClick();
            AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
            EditText input = findInput(dialog.getWindow().getDecorView());
            assertNotNull(input);
            input.setText("ssh://user:secret@host");
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            assertTrue(dialog.isShowing()); assertNotNull(input.getError());
            input.setText("ssh://termux@100.64.0.5:8022");
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            assertFalse(dialog.isShowing());
            assertEquals("100.64.0.5", ((EditText) activity.findViewById(R.id.vpnHostInput)).getText().toString());
            assertEquals("termux", ((EditText) activity.findViewById(R.id.userInput)).getText().toString());
        }
    }
    @Test public void vpnOnlyProfileSavesAndInvalidPortDoesNotOverwriteIt() {
        try (var controller = Robolectric.buildActivity(MainActivity.class).create()) {
            MainActivity a = controller.get();
            ((RadioButton) a.findViewById(R.id.vpnModeButton)).setChecked(true);
            ((EditText) a.findViewById(R.id.localHostInput)).setText("");
            ((EditText) a.findViewById(R.id.vpnHostInput)).setText("100.64.0.5");
            ((EditText) a.findViewById(R.id.userInput)).setText("termux");
            EditText port = a.findViewById(R.id.portInput); port.setText("8022");
            a.findViewById(R.id.saveButton).performClick();
            var prefs = a.getSharedPreferences(ServerConfig.PREFS, Context.MODE_PRIVATE);
            assertEquals("100.64.0.5", prefs.getString(ServerConfig.KEY_VPN_HOST, ""));
            assertEquals("", prefs.getString(ServerConfig.KEY_LOCAL_HOST, "missing"));
            assertNull(((EditText) a.findViewById(R.id.localHostInput)).getError());
            port.setText("65536"); a.findViewById(R.id.saveButton).performClick();
            assertNotNull(port.getError()); assertEquals(8022, prefs.getInt(ServerConfig.KEY_PORT, 0));
        }
    }
    private static EditText findInput(android.view.View view) {
        if (view instanceof EditText) return (EditText) view;
        if (view instanceof android.view.ViewGroup) {
            var group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                EditText found = findInput(group.getChildAt(i)); if (found != null) return found;
            }
        }
        return null;
    }
}
