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
}
