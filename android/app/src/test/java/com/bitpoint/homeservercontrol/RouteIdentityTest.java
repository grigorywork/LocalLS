package com.bitpoint.homeservercontrol;
import android.content.Context;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import java.util.*;
import static org.junit.Assert.*;

/** The secure store is replaced only in this test; parsing, route migration and pin checks are production code. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=30,shadows=RouteIdentityTest.Secrets.class)
public class RouteIdentityTest {
    @Implements(value=SecurePrefs.class,isInAndroidSdk=false)
    public static class Secrets {
        static final Map<String,String> store=new HashMap<>();
        @Implementation protected static void saveNamed(Context c,String id,String value){store.put(id,value);}
        @Implementation protected static String loadNamed(Context c,String id){return store.getOrDefault(id,"");}
        @Implementation protected static void clearNamed(Context c,String id){store.remove(id);}
        @Implementation protected static boolean containsNamed(Context c,String id){return store.containsKey(id);}
    }
    @Test public void sameServerNewWifiAddressKeepsPrivateIdentityAndOriginalPin()throws Exception{
        Context c=RuntimeEnvironment.getApplication();Secrets.store.clear();JSONObject key=SshKeys.generate();String first="192.0.2.11",next="198.51.100.22";
        SshKeys.save(c,first,8022,"localls",key);HostTrustStore.trust(c,first,8022,"SHA256:original");
        assertTrue(SshKeys.moveRoute(c,first,next,8022,"localls"));
        assertEquals(SshKeys.info(c,first,8022,"localls").getString(SshKeys.PUBLIC),SshKeys.info(c,next,8022,"localls").getString(SshKeys.PUBLIC));
        assertEquals("SHA256:original",HostTrustStore.get(c,next,8022));
        SshKeys.clear(c,next,8022,"localls");assertEquals(0,SshKeys.info(c,first,8022,"localls").length());
    }
    @Test public void differentKnownServerIsNeverReboundAndUnknownUnpinnedEndpointHasNoMigration()throws Exception{
        Context c=RuntimeEnvironment.getApplication();Secrets.store.clear();String first="192.0.2.1",next="192.0.2.2";
        SshKeys.save(c,first,8022,"localls",SshKeys.generate());assertFalse(SshKeys.moveRoute(c,first,next,8022,"localls"));
        HostTrustStore.trust(c,first,8022,"SHA256:one");HostTrustStore.trust(c,next,8022,"SHA256:other");assertFalse(SshKeys.moveRoute(c,first,next,8022,"localls"));assertEquals("SHA256:other",HostTrustStore.get(c,next,8022));
    }
    @Test public void addressChangesNeverReplaceAnotherIdentityOfSameServerUser()throws Exception{
        Context c=RuntimeEnvironment.getApplication();Secrets.store.clear();
        String first="192.0.2.81",second="192.0.2.82",next="198.51.100.81",otherNext="198.51.100.82";
        JSONObject firstKey=SshKeys.generate(),secondKey=SshKeys.generate();
        SshKeys.save(c,first,8022,"localls",firstKey);SshKeys.save(c,second,8022,"localls",secondKey);
        HostTrustStore.trust(c,first,8022,"SHA256:same-server");HostTrustStore.trust(c,second,8022,"SHA256:same-server");
        assertTrue(SshKeys.moveRoute(c,first,next,8022,"localls"));
        assertTrue(SshKeys.moveRoute(c,second,otherNext,8022,"localls"));
        assertEquals(firstKey.getString(SshKeys.PUBLIC),SshKeys.info(c,first,8022,"localls").getString(SshKeys.PUBLIC));
        assertEquals(firstKey.getString(SshKeys.PUBLIC),SshKeys.info(c,next,8022,"localls").getString(SshKeys.PUBLIC));
        assertEquals(secondKey.getString(SshKeys.PUBLIC),SshKeys.info(c,second,8022,"localls").getString(SshKeys.PUBLIC));
        assertEquals(secondKey.getString(SshKeys.PUBLIC),SshKeys.info(c,otherNext,8022,"localls").getString(SshKeys.PUBLIC));
        assertEquals("SHA256:same-server",HostTrustStore.get(c,otherNext,8022));
    }
    @Test public void existingDestinationIdentityIsNeverOverwrittenEvenWhenPinMatches()throws Exception{
        Context c=RuntimeEnvironment.getApplication();Secrets.store.clear();String first="192.0.2.91",next="198.51.100.91";
        JSONObject source=SshKeys.generate(),target=SshKeys.generate();
        SshKeys.save(c,first,8022,"localls",source);SshKeys.save(c,next,8022,"localls",target);
        HostTrustStore.trust(c,first,8022,"SHA256:same-server");HostTrustStore.trust(c,next,8022,"SHA256:same-server");
        assertFalse(SshKeys.moveRoute(c,first,next,8022,"localls"));
        assertEquals(target.getString(SshKeys.PUBLIC),SshKeys.info(c,next,8022,"localls").getString(SshKeys.PUBLIC));
    }
    @Test public void consecutiveWifiMovesKeepOriginalClientIdentityAndServerPin()throws Exception{
        Context c=RuntimeEnvironment.getApplication();Secrets.store.clear();String first="192.0.2.101",second="198.51.100.101",third="203.0.113.101";
        JSONObject identity=SshKeys.generate();SshKeys.save(c,first,8022,"localls",identity);HostTrustStore.trust(c,first,8022,"SHA256:original");
        assertTrue(SshKeys.moveRoute(c,first,second,8022,"localls"));assertTrue(SshKeys.moveRoute(c,second,third,8022,"localls"));
        for(String host:new String[]{first,second,third}){
            assertEquals(identity.getString(SshKeys.PUBLIC),SshKeys.info(c,host,8022,"localls").getString(SshKeys.PUBLIC));assertEquals("SHA256:original",HostTrustStore.get(c,host,8022));
        }
    }
    @Test public void legacySharedAliasRemainsReadableWhenMovingToAnotherWifi()throws Exception{
        Context c=RuntimeEnvironment.getApplication();Secrets.store.clear();String first="192.0.2.111",next="198.51.100.111";
        JSONObject identity=SshKeys.generate();SshKeys.save(c,first,8022,"localls",identity);
        String routeId=Secrets.store.keySet().iterator().next(),raw=Secrets.store.get(routeId),legacy="server_legacy_fixture";
        Secrets.store.put(legacy,raw);Secrets.store.remove(routeId);
        SshKeys.prefs(c).edit().putString("route_"+routeId,legacy).commit();HostTrustStore.trust(c,first,8022,"SHA256:legacy");
        assertTrue(SshKeys.moveRoute(c,first,next,8022,"localls"));
        assertEquals(raw,Secrets.store.get(legacy));
        assertEquals(identity.getString(SshKeys.PUBLIC),SshKeys.info(c,first,8022,"localls").getString(SshKeys.PUBLIC));
        assertEquals(identity.getString(SshKeys.PUBLIC),SshKeys.info(c,next,8022,"localls").getString(SshKeys.PUBLIC));
        assertEquals("SHA256:legacy",HostTrustStore.get(c,next,8022));
    }
}
