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
    @Test public void serverScreenDoesNotOpenTermuxOrStartListeningOnCreation() {
        try(var controller=Robolectric.buildActivity(EmbeddedServerActivity.class).create()) {
            EmbeddedServerActivity a=controller.get();assertNotNull(findButton(a.findViewById(android.R.id.content),"Включить сервер"));
            assertNotNull(findButton(a.findViewById(android.R.id.content),"Файлы сервера / сохранить на телефон"));
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
