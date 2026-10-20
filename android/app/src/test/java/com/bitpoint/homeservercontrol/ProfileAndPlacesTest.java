package com.bitpoint.homeservercontrol;
import android.content.Context;
import android.content.SharedPreferences;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.json.JSONObject;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=30)
public class ProfileAndPlacesTest {
    private SharedPreferences prefs() { return RuntimeEnvironment.getApplication().getSharedPreferences("profile-tests",Context.MODE_PRIVATE); }
    private ConnectionProfile profile() { return new ConnectionProfile("", "100.64.0.5", "termux", "vpn",8022,8787); }
    @Test public void roundTripContainsOnlyPublicFields() throws Exception {
        SharedPreferences p=prefs();p.edit().clear().putString(ServerConfig.KEY_AGENT_TOKEN_CIPHER,"secret-token")
                .putString("ssh_identity_fixture","private-key").commit();
        profile().apply(p);
        String encoded=ConnectionProfile.fromPrefs(p).encode();
        assertFalse(encoded.contains("secret"));assertFalse(encoded.contains("private-key"));
        ConnectionProfile copy=ConnectionProfile.decode(encoded);
        assertEquals("100.64.0.5",copy.vpn);assertEquals("",copy.local);assertEquals(8022,copy.port);
    }
    @Test public void malformedProfilesAreRejectedBeforeApplying() throws Exception {
        String valid=profile().encode();
        for(String bad:new String[]{valid+"garbage",new JSONObject(valid).put("password","bad").toString(),
                new JSONObject(valid).put("port",65536).toString(),new JSONObject(valid).put("port","8022").toString(),
                new JSONObject(valid).put("version",2).toString(),new JSONObject(valid).put("vpn","host;reboot").toString(),
                new JSONObject(valid).put("vpn","").toString()," ".repeat(8193)}) {
            try {ConnectionProfile.decode(bad);fail("Accepted malformed profile");} catch(Exception expected) { }
        }
    }
    @Test public void importingClearsAgentSessionButPreservesLocalKeysAndTrust() {
        SharedPreferences p=prefs();p.edit().clear().putString(ServerConfig.KEY_AGENT_TOKEN_CIPHER,"cipher")
                .putBoolean(ServerConfig.KEY_SAVE_AGENT_TOKEN,true).putBoolean(ServerConfig.KEY_BACKGROUND_MONITOR,true)
                .putString(ServerConfig.KEY_LAST_WORKING_HOST,"old.host").putString("ssh_identity_fixture","key")
                .putString("trusted_fp_old.host:8022","fingerprint").commit();
        profile().apply(p);
        assertFalse(p.contains(ServerConfig.KEY_AGENT_TOKEN_CIPHER));assertFalse(p.contains(ServerConfig.KEY_LAST_WORKING_HOST));
        assertFalse(p.getBoolean(ServerConfig.KEY_BACKGROUND_MONITOR,true));assertFalse(p.getBoolean(ServerConfig.KEY_SAVE_AGENT_TOKEN,true));
        assertEquals("key",p.getString("ssh_identity_fixture",""));assertEquals("fingerprint",p.getString("trusted_fp_old.host:8022",""));
    }
    @Test public void placesAreIsolatedByHostPortAndUser() {
        SharedPreferences p=prefs();p.edit().clear().putString("last_remote_path","/wrong-server").commit();
        RemotePlaces first=new RemotePlaces(p,"server",8022,"alice");assertEquals(".",first.last());
        first.remember("/home/alice");first.add("/home/alice");first.add("/home/alice");
        assertEquals(1,first.favorites().size());assertEquals("/home/alice",new RemotePlaces(p,"server",8022,"alice").last());
        for(RemotePlaces other:new RemotePlaces[]{new RemotePlaces(p,"other",8022,"alice"),new RemotePlaces(p,"server",22,"alice"),new RemotePlaces(p,"server",8022,"bob")}) {
            assertEquals(".",other.last());assertTrue(other.favorites().isEmpty());
        }
        first.remove("/home/alice");assertTrue(first.favorites().isEmpty());
    }
    @Test public void placesRejectUnsafePathsAndLimitSize() {
        SharedPreferences p=prefs();p.edit().clear().commit();RemotePlaces places=new RemotePlaces(p,"server",22,"user");
        for(String path:new String[]{"relative","/path\nnext","/path\u0000"}) {
            try {places.add(path);fail();} catch(IllegalArgumentException expected) { }
        }
        for(int i=0;i<20;i++)places.add("/folder"+i);
        try {places.add("/overflow");fail();} catch(IllegalArgumentException expected) { }
        places.add("/folder1");assertEquals(20,places.favorites().size());
        places.remember("relative");assertEquals(".",places.last());
    }
}
