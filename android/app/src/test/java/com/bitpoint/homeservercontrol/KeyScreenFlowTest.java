package com.bitpoint.homeservercontrol;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import java.util.concurrent.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=30,shadows=RouteIdentityTest.Secrets.class)
public class KeyScreenFlowTest {
    private Context app;private String first="192.0.2.31",other="198.51.100.32";private org.json.JSONObject original,alternate;
    @Before public void setup()throws Exception{
        app=RuntimeEnvironment.getApplication();RouteIdentityTest.Secrets.store.clear();app.getSharedPreferences(ServerConfig.PREFS,0).edit().clear().putString(ServerConfig.KEY_LOCAL_HOST,first).putString(ServerConfig.KEY_VPN_HOST,other).putString(ServerConfig.KEY_MODE,ServerConfig.MODE_LOCAL).putInt(ServerConfig.KEY_PORT,8022).putString(ServerConfig.KEY_USER,"localls").commit();
        original=SshKeys.generate();alternate=SshKeys.generate();SshKeys.save(app,first,8022,"localls",original);SshKeys.save(app,other,8022,"localls",alternate);HostTrustStore.trust(app,first,8022,"SHA256:first");HostTrustStore.trust(app,other,8022,"SHA256:other");
    }
    private Button button(View v,String text){if(v instanceof Button && ((Button)v).getText().toString().equals(text))return (Button)v;if(v instanceof ViewGroup)for(int n=0;n<((ViewGroup)v).getChildCount();n++){Button b=button(((ViewGroup)v).getChildAt(n),text);if(b!=null)return b;}return null;}
    private void await(java.util.function.BooleanSupplier ready)throws Exception{Shadows.shadowOf(Looper.getMainLooper()).idle();long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);while(!ready.getAsBoolean() && System.nanoTime()<end){Shadows.shadowOf(Looper.getMainLooper()).idle();Thread.sleep(10);}assertTrue(ready.getAsBoolean());}
    @Test public void replacingSelectedKeyNeverReplacesDifferentAlternateServerIdentity()throws Exception{
        try(var c=Robolectric.buildActivity(SecurityActivity.class).create()){
            button(c.get().findViewById(android.R.id.content),"Создать ключ RSA 3072").performClick();AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            await(()->!SshKeys.info(app,first,8022,"localls").optString(SshKeys.PUBLIC).equals(original.optString(SshKeys.PUBLIC)) && !(boolean)ReflectionHelpers.getField(c.get(),"busy"));
            assertEquals(alternate.getString(SshKeys.PUBLIC),SshKeys.info(app,other,8022,"localls").optString(SshKeys.PUBLIC));assertEquals("SHA256:other",HostTrustStore.get(app,other,8022));
        }
    }
    @Test public void removingSelectedKeyNeverRemovesDifferentAlternateServerIdentity()throws Exception{
        try(var c=Robolectric.buildActivity(SecurityActivity.class).create()){
            button(c.get().findViewById(android.R.id.content),"Дополнительно").performClick();button(c.get().findViewById(android.R.id.content),"Удалить локальный ключ").performClick();
            AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();await(()->!RouteIdentityTest.Secrets.store.containsValue(original.toString()) && !(boolean)ReflectionHelpers.getField(c.get(),"busy"));
            assertEquals(0,SshKeys.info(app,first,8022,"localls").length());assertEquals(alternate.getString(SshKeys.PUBLIC),SshKeys.info(app,other,8022,"localls").optString(SshKeys.PUBLIC));
        }
    }
    public static class KeyFile extends ContentProvider {
        java.io.File file;
        @Override public boolean onCreate(){return true;}
        @Override public ParcelFileDescriptor openFile(android.net.Uri uri,String mode)throws java.io.FileNotFoundException{return ParcelFileDescriptor.open(file,ParcelFileDescriptor.parseMode(mode));}
        @Override public String getType(android.net.Uri uri){return "text/plain";}
        @Override public android.database.Cursor query(android.net.Uri uri,String[] p,String w,String[] a,String o){return null;}
        @Override public android.net.Uri insert(android.net.Uri u,ContentValues v){throw new UnsupportedOperationException();}
        @Override public int update(android.net.Uri u,ContentValues v,String w,String[] a){throw new UnsupportedOperationException();}
        @Override public int delete(android.net.Uri u,String w,String[] a){throw new UnsupportedOperationException();}
    }
    private KeyFile provider()throws Exception{
        KeyFile p=new KeyFile();p.file=java.io.File.createTempFile("key-flow-",".txt",app.getCacheDir());android.content.pm.ProviderInfo info=new android.content.pm.ProviderInfo();info.authority="key.flow";p.attachInfo(app,info);org.robolectric.shadows.ShadowContentResolver.registerProviderInternal(info.authority,p);return p;
    }
    @Test public void publicKeyExportSurvivesActivityRecreationWhilePickerIsOpen()throws Exception{
        KeyFile target=provider();Bundle saved=new Bundle();
        try(var c=Robolectric.buildActivity(SecurityActivity.class).create()){
            button(c.get().findViewById(android.R.id.content),"Экспортировать публичный ключ .pub").performClick();await(()->!((String)ReflectionHelpers.getField(c.get(),"exportedPublic")).isEmpty() && !(boolean)ReflectionHelpers.getField(c.get(),"busy"));c.saveInstanceState(saved);
        }
        try(var c=Robolectric.buildActivity(SecurityActivity.class).create(saved)){
            c.get().onActivityResult(72,Activity.RESULT_OK,new Intent().setData(android.net.Uri.parse("content://key.flow/public")));await(()->!(boolean)ReflectionHelpers.getField(c.get(),"busy"));
            assertEquals(original.getString(SshKeys.PUBLIC)+"\n",java.nio.file.Files.readString(target.file.toPath()));
        }
    }
    @Test public void keyImportRequiresConfirmationAndNeverChangesOtherServer()throws Exception{
        KeyFile source=provider();var imported=SshKeys.generate();java.nio.file.Files.writeString(source.file.toPath(),imported.getString(SshKeys.PRIVATE));Intent picked=new Intent().setData(android.net.Uri.parse("content://key.flow/private"));
        try(var c=Robolectric.buildActivity(SecurityActivity.class).create()){
            SecurityActivity a=c.get();a.onActivityResult(71,Activity.RESULT_OK,picked);await(()->!(boolean)ReflectionHelpers.getField(a,"busy"));
            AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();assertNotNull(dialog);dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();assertEquals(original.getString(SshKeys.PUBLIC),SshKeys.info(app,first,8022,"localls").optString(SshKeys.PUBLIC));
            a.onActivityResult(71,Activity.RESULT_OK,picked);await(()->!(boolean)ReflectionHelpers.getField(a,"busy"));dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            await(()->!(boolean)ReflectionHelpers.getField(a,"busy"));assertEquals("Import result: "+((TextView)ReflectionHelpers.getField(a,"result")).getText(),imported.getString(SshKeys.PUBLIC),SshKeys.info(app,first,8022,"localls").optString(SshKeys.PUBLIC));assertEquals(alternate.getString(SshKeys.PUBLIC),SshKeys.info(app,other,8022,"localls").optString(SshKeys.PUBLIC));
        }
    }

}
