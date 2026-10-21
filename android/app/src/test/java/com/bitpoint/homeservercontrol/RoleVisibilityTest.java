package com.bitpoint.homeservercontrol;

import android.app.job.JobScheduler;
import android.content.Context;
import android.view.View;
import android.widget.CheckBox;
import android.widget.Switch;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=30)
public class RoleVisibilityTest {
    private Context app;
    @Before public void setup(){app=RuntimeEnvironment.getApplication();app.getSharedPreferences(EmbeddedServerService.PREFS,0).edit().clear().putBoolean("server_role",true).putBoolean("show_client",false).commit();app.getSharedPreferences(ServerConfig.PREFS,0).edit().clear().putBoolean(ServerConfig.KEY_BACKGROUND_MONITOR,true).commit();}
    @Test public void clientNetworkSettingsAppearOnlyWhenRoleSliderEnablesThem(){
        try(var c=Robolectric.buildActivity(MainActivity.class).create()){
            assertEquals(View.GONE,c.get().findViewById(R.id.networkButton).getVisibility());
            ((Switch)c.get().findViewById(R.id.showClientSwitch)).setChecked(true);assertEquals(View.VISIBLE,c.get().findViewById(R.id.networkButton).getVisibility());
            assertNotNull(((JobScheduler)app.getSystemService(Context.JOB_SCHEDULER_SERVICE)).getPendingJob(8032));
            ((Switch)c.get().findViewById(R.id.showClientSwitch)).setChecked(false);assertEquals(View.GONE,c.get().findViewById(R.id.networkButton).getVisibility());
            assertNull(((JobScheduler)app.getSystemService(Context.JOB_SCHEDULER_SERVICE)).getPendingJob(8032));
        }
    }
    @Test public void hiddenClientMonitorCannotOverwriteRememberedPreference(){
        try(var c=Robolectric.buildActivity(SettingsActivity.class).create()){
            CheckBox check=c.get().findViewById(R.id.settingsBackgroundMonitorCheck);assertEquals(View.GONE,check.getVisibility());
            check.setChecked(false);c.get().findViewById(R.id.settingsSaveButton).performClick();
            assertTrue(app.getSharedPreferences(ServerConfig.PREFS,0).getBoolean(ServerConfig.KEY_BACKGROUND_MONITOR,false));
        }
    }
    @Test public void serverOnlyRoleDoesNotScheduleClientMonitoring(){
        assertTrue(MonitoringScheduler.setEnabled(app,true));
        assertNull(((JobScheduler)app.getSystemService(Context.JOB_SCHEDULER_SERVICE)).getPendingJob(8032));
    }
    @Test public void staleClientMonitoringJobDoesNotRunInServerOnlyRole(){
        var c=Robolectric.buildService(ServerMonitorJobService.class).create();try{assertFalse(c.get().onStartJob(null));}finally{c.destroy();}
    }
}
