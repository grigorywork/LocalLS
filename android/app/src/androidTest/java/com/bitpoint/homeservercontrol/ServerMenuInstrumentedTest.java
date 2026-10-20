package com.bitpoint.homeservercontrol;

import static org.junit.Assert.*;
import android.view.View;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ServerMenuInstrumentedTest {
    @Test public void localServerAndRemoteConnectionHaveSeparateBlocks() {
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(a -> {
                View local=a.findViewById(R.id.localServerCard),remote=a.findViewById(R.id.remoteConnectionCard);
                assertNotNull(local.findViewById(R.id.localServerQuickButton));
                assertNull(local.findViewById(R.id.localHostInput));
                assertNotNull(remote.findViewById(R.id.localHostInput));
                assertNull(remote.findViewById(R.id.localServerQuickButton));
                a.findViewById(R.id.drawerConnectionHeader).performClick();
                assertEquals(View.VISIBLE,a.findViewById(R.id.drawerConnectionPane).getVisibility());
            });
        }
    }
}
