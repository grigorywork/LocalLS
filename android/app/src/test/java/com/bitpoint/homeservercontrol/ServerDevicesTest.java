package com.bitpoint.homeservercontrol;

import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=30)
public class ServerDevicesTest {
    @Test public void legacyKeyMigratesWithoutReplacementAndSevenDevicesPersistWithIndividualRevocation()throws Exception{
        var app=RuntimeEnvironment.getApplication();var prefs=app.getSharedPreferences(EmbeddedServerService.PREFS,0);
        String first=SshKeys.generate().getString(SshKeys.PUBLIC);prefs.edit().clear().putString("allowed_key",first).putInt("port",9022).putBoolean("server_role",true).commit();var before=prefs.getAll();
        assertEquals(1,ServerDevices.list(app).size());assertEquals(before,prefs.getAll());
        List<String> keys=new ArrayList<>();keys.add(EmbeddedSftpServer.normalizeAuthorizedKey(first));
        for(int i=1;i<7;i++){String key=SshKeys.generate().getString(SshKeys.PUBLIC);keys.add(EmbeddedSftpServer.normalizeAuthorizedKey(key));ServerDevices.add(app,key);}
        assertEquals(7,ServerDevices.list(app).size());assertEquals(keys.get(0),prefs.getString("allowed_key",""));assertEquals(9022,prefs.getInt("port",0));assertTrue(prefs.getBoolean("server_role",false));
        String eighth=SshKeys.generate().getString(SshKeys.PUBLIC);var full=prefs.getAll();try{ServerDevices.add(app,eighth);fail("Eighth device saved");}catch(java.io.IOException expected){}assertEquals(full,prefs.getAll());
        ServerDevices.rename(app,keys.get(1),"Ноутбук");assertEquals("Ноутбук",ServerDevices.list(app).get(1).name);
        ServerDevices.remove(app,keys.get(0));assertFalse(ServerDevices.contains(app,keys.get(0)));assertEquals(keys.get(1),prefs.getString("allowed_key",""));ServerDevices.add(app,eighth);assertEquals(7,ServerDevices.list(app).size());assertFalse(ServerDevices.contains(app,keys.get(0)));
    }
    @Test public void damagedAllowListFailsClosedWithoutDiscardingLegacyKey()throws Exception{
        var app=RuntimeEnvironment.getApplication();var prefs=app.getSharedPreferences(EmbeddedServerService.PREFS,0);String key=SshKeys.generate().getString(SshKeys.PUBLIC);prefs.edit().clear().putString("allowed_key",key).putString(ServerDevices.KEY,"broken").commit();var before=prefs.getAll();
        try{ServerDevices.list(app);fail("Damaged list accepted");}catch(Exception expected){}assertEquals(before,prefs.getAll());
    }
}
