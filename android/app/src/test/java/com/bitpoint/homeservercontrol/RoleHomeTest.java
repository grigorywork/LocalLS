package com.bitpoint.homeservercontrol;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=30,qualifiers="ru-rRU-w360dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class RoleHomeTest {
    private android.app.Application app;
    @Before public void prepare(){
        app=RuntimeEnvironment.getApplication();
        app.getSharedPreferences(ServerConfig.PREFS,0).edit().clear().putInt(ServerConfig.KEY_CONFIG_VERSION,3)
                .putString(SshKeys.MODE,"key").putBoolean(ServerConfig.KEY_SAVE_PASSWORD,false)
                .putString(ServerConfig.KEY_LOCAL_HOST,"192.0.2.42").putInt(ServerConfig.KEY_PORT,9022)
                .putString("ssh_identity_fixture","saved-encrypted-identity").putBoolean(ServerConfig.KEY_BACKGROUND_MONITOR,true).commit();
        app.getSharedPreferences(EmbeddedServerService.PREFS,0).edit().clear().putBoolean("server_role",true)
                .putBoolean("show_client",false).putString("allowed_key","existing-public-key").putInt("port",8022).commit();
        clearState();
    }
    @After public void clearState(){EmbeddedServerService.running=false;EmbeddedServerService.starting=false;EmbeddedServerService.launchRequested=0;EmbeddedServerService.status="Сервер выключен";EmbeddedServerService.diagnostic="";}

    @Test public void clientSettingsHaveOneMenuEntryAndNoHomeTileWhileBothChartsRemain(){
        app.getSharedPreferences(EmbeddedServerService.PREFS,0).edit().putBoolean("server_role",false).putBoolean("show_client",true).commit();
        try(var screen=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity a=screen.get();assertEquals(0,a.getResources().getIdentifier("settingsButton","id",a.getPackageName()));
            for(int id:new int[]{R.id.dashboardSpeedChart,R.id.dashboardLatencyChart})assertTrue(a.findViewById(id).isShown());
            a.findViewById(R.id.toolsMenuButton).performClick();assertEquals(View.VISIBLE,a.findViewById(R.id.advancedPanel).getVisibility());
            assertEquals(0,a.getResources().getIdentifier("drawerSettingsHeader","id",a.getPackageName()));assertTrue(a.findViewById(R.id.appSettingsButton).isShown());assertEquals(0,a.getResources().getIdentifier("drawerSettingsPane","id",a.getPackageName()));a.findViewById(R.id.appSettingsButton).performClick();Intent intent=Shadows.shadowOf(a).getNextStartedActivity();assertEquals(SettingsActivity.class.getName(),intent.getComponent().getClassName());
        }
    }

    @Test public void serverOnlyHomeContainsOnlySharingControlsEvenWhenServiceStops(){
        var server=app.getSharedPreferences(EmbeddedServerService.PREFS,0);var client=app.getSharedPreferences(ServerConfig.PREFS,0);
        var beforeServer=server.getAll();var beforeClient=client.getAll();
        try(var c=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity a=c.get();assertServerOnly(a);
            a.findViewById(R.id.toolsMenuButton).performClick();a.findViewById(R.id.drawerConnectionHeader).performClick();
            assertEquals(View.GONE,a.findViewById(R.id.advancedPanel).getVisibility());assertEquals(View.GONE,a.findViewById(R.id.connectionSheet).getVisibility());
            EmbeddedServerService.running=true;org.robolectric.util.ReflectionHelpers.callInstanceMethod(a,"refreshLocalServer");
            assertEquals("Остановить",((Button)a.findViewById(R.id.serverPowerButton)).getText().toString());
            EmbeddedServerService.running=false;org.robolectric.util.ReflectionHelpers.callInstanceMethod(a,"refreshLocalServer");
            assertServerOnly(a);assertEquals(beforeServer,server.getAll());assertEquals(beforeClient,client.getAll());
            assertNull(Shadows.shadowOf(app).getNextStartedService());
        }
    }

    @Test public void serverHomeShowsCompactLivePortAndKeyStatusWithoutChangingSettings(){
        var prefs=app.getSharedPreferences(EmbeddedServerService.PREFS,0);var before=prefs.getAll();
        try(var c=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity a=c.get();EmbeddedServerService.running=true;EmbeddedServerService.boundPort=9123;EmbeddedServerService.waitingForClient=false;
            org.robolectric.util.ReflectionHelpers.callInstanceMethod(a,"refreshLocalServer");
            assertEquals("● Сервер работает",((TextView)a.findViewById(R.id.localServerSummary)).getText().toString());
            assertTrue(((TextView)a.findViewById(R.id.serverAccessSummary)).getText().toString().contains("Порт 9123"));
            EmbeddedServerService.waitingForClient=true;org.robolectric.util.ReflectionHelpers.callInstanceMethod(a,"refreshLocalServer");
            assertTrue(((TextView)a.findViewById(R.id.serverAccessSummary)).getText().toString().contains("Вход закрыт"));assertEquals(before,prefs.getAll());assertServerOnly(a);
        }finally{EmbeddedServerService.waitingForClient=false;}
    }
    @Test public void explicitOptInAloneRestoresClientTabAndSurvivesRecreationWithoutReplacingKeys(){
        var server=app.getSharedPreferences(EmbeddedServerService.PREFS,0);var client=app.getSharedPreferences(ServerConfig.PREFS,0);
        var beforeServer=server.getAll();var beforeClient=client.getAll();
        try(var c=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity a=c.get();assertServerOnly(a);
            ((Switch)a.findViewById(R.id.showClientSwitch)).setChecked(true);
            assertEquals(View.VISIBLE,a.findViewById(R.id.homeClientTab).getVisibility());
            assertTrue(a.findViewById(R.id.serverHome).isShown());
            a.findViewById(R.id.homeClientTab).performClick();
            assertTrue(a.findViewById(R.id.homeClientTab).isSelected());assertFalse(a.findViewById(R.id.homeServerTab).isSelected());
            assertTrue(a.findViewById(R.id.dashboardSpeedChart).isShown());assertTrue(a.findViewById(R.id.dashboardLatencyChart).isShown());
            assertFalse(a.findViewById(R.id.serverHome).isShown());
            c.recreate();a=c.get();assertTrue(a.findViewById(R.id.dashboardSpeedChart).isShown());
            a.findViewById(R.id.homeServerTab).performClick();
            ((Switch)a.findViewById(R.id.showClientSwitch)).setChecked(false);assertServerOnly(a);
            assertEquals(beforeServer,server.getAll());assertEquals(beforeClient,client.getAll());
            c.recreate();assertServerOnly(c.get());
        }
    }

    @Test public void clientStartsWithGraphsAndServerShortcutsUseOnlyLocalSharingRoutes(){
        var prefs=app.getSharedPreferences(EmbeddedServerService.PREFS,0);prefs.edit().putBoolean("server_role",false).commit();
        var before=prefs.getAll();
        try(var c=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity a=c.get();assertTrue(a.findViewById(R.id.dashboardSpeedChart).isShown());
            assertFalse(a.findViewById(R.id.localServerCard).isShown());
            a.findViewById(R.id.homeServerTab).performClick();assertTrue(a.findViewById(R.id.serverHome).isShown());assertEquals(before,prefs.getAll());
            a.findViewById(R.id.serverLocalFilesButton).performClick();Intent intent=Shadows.shadowOf(a).getNextStartedActivity();
            assertEquals(FilesActivity.class.getName(),intent.getComponent().getClassName());assertTrue(intent.getBooleanExtra("own_server",false));
            a.findViewById(R.id.serverQrButton).performClick();intent=Shadows.shadowOf(a).getNextStartedActivity();
            assertEquals(QrPairingActivity.class.getName(),intent.getComponent().getClassName());assertTrue(intent.getBooleanExtra("server",false));
            a.findViewById(R.id.serverReceiveButton).performClick();intent=Shadows.shadowOf(a).getNextStartedActivity();
            assertEquals(EmbeddedServerActivity.class.getName(),intent.getComponent().getClassName());assertTrue(intent.getBooleanExtra("open_receive_folder",false));
            a.findViewById(R.id.localServerQuickButton).performClick();intent=Shadows.shadowOf(a).getNextStartedActivity();assertEquals(EmbeddedServerActivity.class.getName(),intent.getComponent().getClassName());
            assertFalse(intent.getBooleanExtra("open_receive_folder",false));
            a.findViewById(R.id.serverBackgroundButton).performClick();intent=Shadows.shadowOf(a).getNextStartedActivity();
            assertEquals(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,intent.getAction());assertEquals("package:"+a.getPackageName(),intent.getDataString());
            a.findViewById(R.id.homeClientTab).performClick();assertTrue(a.findViewById(R.id.dashboardLatencyChart).isShown());assertEquals(before,prefs.getAll());
        }
    }

    @Test public void startButtonDispatchesExistingServerServiceOnceWithoutEditingAnyKeyOrProfile(){
        var server=app.getSharedPreferences(EmbeddedServerService.PREFS,0);var client=app.getSharedPreferences(ServerConfig.PREFS,0);
        var beforeServer=server.getAll();var beforeClient=client.getAll();
        try(var c=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity a=c.get();assertNull(Shadows.shadowOf(app).getNextStartedService());
            a.findViewById(R.id.serverPowerButton).performClick();
            Intent request=Shadows.shadowOf(app).getNextStartedService();assertNotNull(request);assertEquals(EmbeddedServerService.class.getName(),request.getComponent().getClassName());
            a.findViewById(R.id.serverPowerButton).performClick();assertNull(Shadows.shadowOf(app).getNextStartedService());
            assertEquals(beforeServer,server.getAll());assertEquals(beforeClient,client.getAll());
        }
    }

    @Test public void folderShortcutUsesExistingAndroidGrantFlowAndDoesNotReplaceFolderOnCancellation(){
        var prefs=app.getSharedPreferences(EmbeddedServerService.PREFS,0);prefs.edit().putString(ReceiveFolder.TREE,"content://fixture/tree/kept").commit();var before=prefs.getAll();
        Intent request=new Intent(app,EmbeddedServerActivity.class).putExtra("open_receive_folder",true);
        try(var c=Robolectric.buildActivity(EmbeddedServerActivity.class,request).create()){
            var launch=Shadows.shadowOf(c.get()).getNextStartedActivityForResult();assertNotNull(launch);assertEquals(914,launch.requestCode);assertEquals(Intent.ACTION_OPEN_DOCUMENT_TREE,launch.intent.getAction());
            assertTrue((launch.intent.getFlags()&Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)!=0);assertTrue((launch.intent.getFlags()&Intent.FLAG_GRANT_WRITE_URI_PERMISSION)!=0);
            org.robolectric.util.ReflectionHelpers.callInstanceMethod(c.get(),"onActivityResult",org.robolectric.util.ReflectionHelpers.ClassParameter.from(int.class,914),org.robolectric.util.ReflectionHelpers.ClassParameter.from(int.class,android.app.Activity.RESULT_CANCELED),org.robolectric.util.ReflectionHelpers.ClassParameter.from(Intent.class,null));
            assertEquals(before,prefs.getAll());assertNull(Shadows.shadowOf(app).getNextStartedService());
        }
        try(var c=Robolectric.buildActivity(EmbeddedServerActivity.class,request).create(new Bundle())){
            assertNull("Rotation must not reopen the folder picker",Shadows.shadowOf(c.get()).getNextStartedActivityForResult());
        }
    }

    @Test public void serverHomeFitsBothOrientationsAndEveryThemeAndFontWithoutClientMenuOrHeroBanner()throws Exception{
        for(int theme=0;theme<ThemeCatalog.IDS.length;theme++)for(float scale:new float[]{1f,1.5f,2f}){
            ThemeCatalog.select(app,theme);Configuration config=new Configuration(app.getResources().getConfiguration());config.fontScale=scale;
            Context context=new ContextThemeWrapper(app.createConfigurationContext(config),ThemeCatalog.style(app));
            for(int[] size:new int[][]{{360,560},{320,568},{640,300}}){
                View root=LayoutInflater.from(context).inflate(R.layout.activity_main,null);IconButtons.decorate(root);
                root.findViewById(R.id.dashboardScroll).setVisibility(View.GONE);root.findViewById(R.id.serverHome).setVisibility(View.VISIBLE);root.findViewById(R.id.homeClientTab).setVisibility(View.GONE);root.findViewById(R.id.showClientSwitch).setVisibility(View.VISIBLE);
                ((TextView)root.findViewById(R.id.localServerSummary)).setText("Сервер работает, порт 8022. Разрешён вход по добавленному ключу.");
                StatusBadge.apply(root.findViewById(R.id.localServerSummary),StatusBadge.Tone.SUCCESS);
                ((Button)root.findViewById(R.id.serverPowerButton)).setText("Остановить сервер");root.findViewById(R.id.homeServerTab).setSelected(true);
                ((TextView)root.findViewById(R.id.serverReceiveSummary)).setText("Новые файлы → Выбранная папка телефона\nАвтосохранение готово");
                root.measure(View.MeasureSpec.makeMeasureSpec(size[0],View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(size[1],View.MeasureSpec.EXACTLY));root.layout(0,0,size[0],size[1]);
                ViewGroup shell=root.findViewById(R.id.homeShell);checkVisible(shell,shell);
                if(size[0]==360){
                    Bitmap bitmap=Bitmap.createBitmap(size[0],size[1],Bitmap.Config.ARGB_8888);shell.draw(new Canvas(bitmap));File dir=new File("build/ui-previews");dir.mkdirs();
                    try(FileOutputStream out=new FileOutputStream(new File(dir,"server-home-"+ThemeCatalog.IDS[theme]+"-font-"+scale+".png"))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}bitmap.recycle();
                }
            }
        }
    }
    private void assertServerOnly(MainActivity a){
        assertTrue(a.findViewById(R.id.homeServerTab).isSelected());assertFalse(a.findViewById(R.id.homeClientTab).isSelected());
        assertTrue(a.findViewById(R.id.serverHome).isShown());assertEquals(View.GONE,a.findViewById(R.id.dashboardScroll).getVisibility());assertEquals(View.GONE,a.findViewById(R.id.homeClientTab).getVisibility());
        for(int id:new int[]{R.id.historyButton,R.id.diagnosticsButton,R.id.transferQueueButton,R.id.toolsMenuButton,R.id.dashboardSpeedChart,R.id.dashboardLatencyChart,R.id.dashboardMetrics})assertFalse("Client control must be hidden: "+id,a.findViewById(id).isShown());
        for(int id:new int[]{R.id.serverPowerButton,R.id.serverQrButton,R.id.serverLocalFilesButton,R.id.serverReceiveButton,R.id.localServerQuickButton,R.id.serverBackgroundButton})assertTrue("Sharing control missing: "+id,a.findViewById(id).isShown());
    }
    private void checkVisible(View view,ViewGroup shell){
        if(view.getVisibility()==View.GONE)return;
        assertFalse("Static home must not scroll",view instanceof ScrollView);
        if(view instanceof TextView){String text=((TextView)view).getText().toString();assertNotEquals("No large app banner","LocalLS",text);assertFalse("No protocol banner",text.contains("SSH / SFTP ·"));}
        if(view instanceof Button){
            Button b=(Button)view;Rect rect=new Rect(0,0,b.getWidth(),b.getHeight());shell.offsetDescendantRectToMyCoords(b,rect);
            assertTrue(b.getText()+" outside "+shell.getWidth()+"x"+shell.getHeight()+": "+rect,rect.left>=0&&rect.top>=0&&rect.right<=shell.getWidth()&&rect.bottom<=shell.getHeight());
            assertTrue(b.getText()+" touch height",b.getHeight()>=48);assertTrue(b.getText()+" touch width",b.getWidth()>=48);
            if(b instanceof CenteredActionButton&&((CenteredActionButton)b).hasTileIcon()){android.graphics.RectF content=((CenteredActionButton)b).contentBounds();assertTrue(b.getText()+" clipped",content.top>=b.getPaddingTop()-1&&content.bottom<=b.getHeight()-b.getPaddingBottom()+1);}
        }
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)checkVisible(((ViewGroup)view).getChildAt(i),shell);
    }
}
