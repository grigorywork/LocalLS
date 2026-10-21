package com.bitpoint.homeservercontrol;
import android.content.Context;
import android.widget.Button;
import android.view.View;
import android.view.ViewGroup;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=30)
public class EmbeddedServerScreenTest {
    @org.junit.Before public void resetServiceState() {
        EmbeddedServerService.running=false;EmbeddedServerService.starting=false;EmbeddedServerService.waitingForClient=false;
        EmbeddedServerService.launchRequested=0;EmbeddedServerService.status="Сервер выключен";EmbeddedServerService.diagnostic="";
    }
    @Test public void wifiAddressIsShownBeforeServerOrSecondPhoneExists() throws Exception {
        Context context=RuntimeEnvironment.getApplication();
        var manager=(android.net.ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
        var network=org.robolectric.shadows.ShadowNetwork.newInstance(7);
        var shadow=org.robolectric.Shadows.shadowOf(manager);
        shadow.addNetwork(network,org.robolectric.shadows.ShadowNetworkInfo.newInstance(android.net.NetworkInfo.DetailedState.CONNECTED,android.net.ConnectivityManager.TYPE_WIFI,0,true,true));
        var capabilities=org.robolectric.shadows.ShadowNetworkCapabilities.newInstance();org.robolectric.Shadows.shadowOf(capabilities).addTransportType(android.net.NetworkCapabilities.TRANSPORT_WIFI);shadow.setNetworkCapabilities(network,capabilities);
        android.net.LinkProperties properties=new android.net.LinkProperties();
        android.net.LinkAddress address=org.robolectric.util.ReflectionHelpers.callConstructor(android.net.LinkAddress.class,org.robolectric.util.ReflectionHelpers.ClassParameter.from(java.net.InetAddress.class,java.net.InetAddress.getByName("192.0.2.10")),org.robolectric.util.ReflectionHelpers.ClassParameter.from(int.class,24));
        org.robolectric.util.ReflectionHelpers.callInstanceMethod(properties,"addLinkAddress",org.robolectric.util.ReflectionHelpers.ClassParameter.from(android.net.LinkAddress.class,address));shadow.setLinkProperties(network,properties);
        context.getSharedPreferences(EmbeddedServerService.PREFS,Context.MODE_PRIVATE).edit().clear().commit();
        try(var controller=Robolectric.buildActivity(EmbeddedServerActivity.class).create()) {
            EmbeddedServerActivity a=controller.get();
            String info=((android.widget.TextView)a.findViewById(R.id.embeddedServerAddress)).getText().toString();
            assertEquals("192.0.2.10",((android.widget.TextView)a.findViewById(R.id.currentWifiIpValue)).getText().toString());
            assertTrue(info.contains("8022"));assertTrue(info.contains("localls"));
            assertFalse(EmbeddedServerService.running);
            findButton(a.findViewById(android.R.id.content),"Скопировать данные подключения").performClick();
            var clipboard=(android.content.ClipboardManager)a.getSystemService(Context.CLIPBOARD_SERVICE);
            assertTrue(clipboard.getPrimaryClip().getItemAt(0).getText().toString().contains("192.0.2.10"));
        }
    }
    @Test public void emptyClientKeyDispatchesLockedStartupInsteadOfRejectingSetup() throws Exception {
        Context c=RuntimeEnvironment.getApplication();c.getSharedPreferences(EmbeddedServerService.PREFS,Context.MODE_PRIVATE).edit().clear().commit();
        try(var controller=Robolectric.buildActivity(EmbeddedServerActivity.class).create()) {
            EmbeddedServerActivity a=controller.get();
            findButton(a.findViewById(android.R.id.content),"Включить сервер").performClick();
            android.content.Intent request=null;long limit=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(3);
            while(request==null && System.nanoTime()<limit) {
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
                request=org.robolectric.Shadows.shadowOf(RuntimeEnvironment.getApplication()).getNextStartedService();Thread.sleep(5);
            }
            assertNotNull(request);assertEquals(EmbeddedServerService.class.getName(),request.getComponent().getClassName());
            assertEquals("",c.getSharedPreferences(EmbeddedServerService.PREFS,Context.MODE_PRIVATE).getString("allowed_key","missing"));
            assertTrue(EmbeddedServerService.diagnostic.isEmpty());
        }
    }
    @Test public void setupInstructionsAreBundledAndOpenWithoutNetwork() {
        try(var controller=Robolectric.buildActivity(PhoneSetupGuideActivity.class).create()) {
            View root=controller.get().findViewById(android.R.id.content);
            assertTrue(containsText(root,"СНАЧАЛА ТЕЛЕФОН 1"));
            assertTrue(containsText(root,"ТЕПЕРЬ ТЕЛЕФОН 2"));
            assertFalse(containsText(root,"Не удалось открыть встроенную инструкцию"));
        }
    }
    @Test public void choosingFirstClientStillRequiresOwnersConfirmation() throws Exception {
        try(var controller=Robolectric.buildActivity(EmbeddedServerActivity.class).create()) {
            EmbeddedServerActivity a=controller.get();EmbeddedServerService.running=true;EmbeddedServerService.waitingForClient=true;
            var method=EmbeddedServerActivity.class.getDeclaredMethod("importedKey",String.class);method.setAccessible(true);method.invoke(a,"normalized-public-key");
            var dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();assertTrue(dialog.isShowing());
            assertNull(org.robolectric.Shadows.shadowOf(RuntimeEnvironment.getApplication()).getNextStartedService());
            dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).performClick();
            assertNull(org.robolectric.Shadows.shadowOf(RuntimeEnvironment.getApplication()).getNextStartedService());
            EmbeddedServerService.running=false;EmbeddedServerService.waitingForClient=false;
        }
    }
    @Test public void serverScreenDoesNotOpenTermuxOrStartListeningOnCreation() {
        try(var controller=Robolectric.buildActivity(EmbeddedServerActivity.class).create()) {
            EmbeddedServerActivity a=controller.get();assertNotNull(findButton(a.findViewById(android.R.id.content),"Включить сервер"));
            assertNotNull(findButton(a.findViewById(android.R.id.content),"Файлы · телефон и мой сервер"));
            assertFalse(EmbeddedServerService.running);
        }
    }
    @Test @Config(sdk=24) public void unsupportedAndroidCannotStartServer() {
        try(var controller=Robolectric.buildActivity(EmbeddedServerActivity.class).create()) {
            Button start=findButton(controller.get().findViewById(android.R.id.content),"Включить сервер");assertNotNull(start);assertFalse(start.isEnabled());
        }
    }
    @Test public void corruptStoredHostIdentityIsNeverSilentlyReplaced() throws Exception {
        Context c=RuntimeEnvironment.getApplication();
        c.getSharedPreferences(ServerConfig.PREFS,Context.MODE_PRIVATE).edit().putString("embedded_sftp_host_v1","corrupt-ciphertext").commit();
        try{EmbeddedServerIdentity.loadOrCreate(c);fail("Identity replaced");}catch(IllegalStateException expected){}
        assertEquals("corrupt-ciphertext",c.getSharedPreferences(ServerConfig.PREFS,Context.MODE_PRIVATE).getString("embedded_sftp_host_v1",""));
    }
    @Test public void invalidKeyNeverFailsSilentlyBeforeServiceStartup() throws Exception {
        EmbeddedServerService.running=false;EmbeddedServerService.starting=false;EmbeddedServerService.launchRequested=0;
        EmbeddedServerService.status="Сервер выключен";EmbeddedServerService.diagnostic="";
        try(var controller=Robolectric.buildActivity(EmbeddedServerActivity.class).create()) {
            EmbeddedServerActivity a=controller.get();
            ((android.widget.EditText)a.findViewById(R.id.embeddedServerUser)).setText("localls");
            ((android.widget.EditText)a.findViewById(R.id.embeddedServerPort)).setText("8022");
            ((android.widget.EditText)a.findViewById(R.id.embeddedServerKey)).setText("invalid public key");
            findButton(a.findViewById(android.R.id.content),"Включить сервер").performClick();
            long limit=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(3);
            while(EmbeddedServerService.diagnostic.isEmpty() && System.nanoTime()<limit) {
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();Thread.sleep(5);
            }
            assertTrue(EmbeddedServerService.diagnostic.contains("PUBLIC_KEY"));
            assertNotEquals("Сервер выключен",EmbeddedServerService.status);
            assertFalse(EmbeddedServerService.running);assertTrue(findButton(a.findViewById(android.R.id.content),"Включить сервер").isEnabled());
            assertTrue(containsText(a.findViewById(android.R.id.content),"Не удалось прочитать публичный ключ"));
        }
    }
    private boolean containsText(View view,String text) {
        if(view instanceof android.widget.TextView && ((android.widget.TextView)view).getText().toString().contains(text))return true;
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)if(containsText(group.getChildAt(i),text))return true;}
        return false;
    }
    private Button findButton(View v,String text) {
        if(v instanceof Button && ((Button)v).getText().toString().equals(text))return (Button)v;
        if(v instanceof ViewGroup){ViewGroup group=(ViewGroup)v;for(int i=0;i<group.getChildCount();i++){Button b=findButton(group.getChildAt(i),text);if(b!=null)return b;}}
        return null;
    }
}
