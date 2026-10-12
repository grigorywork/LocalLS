package com.bitpoint.homeservercontrol;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30, qualifiers = "ru-rRU-w360dp-h640dp-mdpi")
public class ServerWizardTest {
    @Test public void missingTermuxNeverStartsCommandsOrClaimsReady() {
        Context app = RuntimeEnvironment.getApplication();
        app.getSharedPreferences(TermuxSetup.PREFS, Context.MODE_PRIVATE).edit().clear().commit();
        try (org.robolectric.android.controller.ActivityController<ServerSetupActivity> controller =
                Robolectric.buildActivity(ServerSetupActivity.class).setup()) {
            ServerSetupActivity activity = controller.get();
            assertFalse(activity.findViewById(R.id.setupServerKeyButton).isEnabled());
            assertFalse(activity.findViewById(R.id.setupServerAgentButton).isEnabled());
            assertFalse(activity.findViewById(R.id.exportServerProfileButton).isEnabled());
            activity.findViewById(R.id.startServerSetupButton).performClick();
            assertEquals("", app.getSharedPreferences(TermuxSetup.PREFS, Context.MODE_PRIVATE).getString("pending", ""));
            assertFalse(((TextView) activity.findViewById(R.id.serverWizardSummary)).getText().toString().contains("Сервер готов:"));
        }
    }
    @Test public void agentCallbackRequiresExactPublicFieldsAndDropsRawOutput() {
        Context app = RuntimeEnvironment.getApplication();
        android.content.SharedPreferences prefs = app.getSharedPreferences(TermuxSetup.PREFS, Context.MODE_PRIVATE);
        prefs.edit().clear().putString("pending", "agent-test").putString("pending_kind", "agent")
                .putLong("pending_at", System.currentTimeMillis()).commit();
        Bundle result = new Bundle(); result.putInt("err", -1); result.putInt("exitCode", 0);
        result.putString("stdout", "AGENT_PORT=8787\nnot-AGENT_READY=1\nprivate-response-marker\n");
        new TermuxResultReceiver().onReceive(app, new Intent("localls.setup.agent-test").putExtra("result", result));
        assertEquals(56, prefs.getInt("last_exit_code", 0));
        assertFalse(prefs.getAll().toString().contains("private-response-marker"));
        prefs.edit().putString("pending", "agent-valid").putString("pending_kind", "agent")
                .putLong("pending_at", System.currentTimeMillis()).commit();
        result.putString("stdout", "AGENT_PORT=8787\nAGENT_READY=1\nprivate-response-marker\n");
        new TermuxResultReceiver().onReceive(app, new Intent("localls.setup.agent-valid").putExtra("result", result));
        assertEquals(0, prefs.getInt("last_exit_code", -1));
        assertEquals("agent", prefs.getString("last_kind", ""));
        assertFalse(prefs.getAll().toString().contains("private-response-marker"));
    }
    @Test public void unrelatedSignerCannotPassTermuxIdentityCheck() {
        assertTrue(TermuxPackage.accepted(TermuxPackage.FDROID));
        assertTrue(TermuxPackage.accepted(TermuxPackage.GITHUB));
        assertFalse(TermuxPackage.accepted("unrecognized"));
        assertFalse(TermuxPackage.accepted(""));
    }
}
