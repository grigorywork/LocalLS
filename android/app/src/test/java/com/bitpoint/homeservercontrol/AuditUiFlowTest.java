package com.bitpoint.homeservercontrol;

import android.content.*;
import android.content.res.Configuration;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.time.Duration;
import java.util.concurrent.*;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=30,qualifiers="ru-rRU-w360dp-h640dp-mdpi",shadows=RouteIdentityTest.Secrets.class)
public class AuditUiFlowTest {
    private android.app.Application app;
    @Before public void prepare(){app=RuntimeEnvironment.getApplication();app.getSharedPreferences(ServerConfig.PREFS,0).edit().clear().commit();app.getSharedPreferences(EmbeddedServerService.PREFS,0).edit().clear().commit();RouteIdentityTest.Secrets.store.clear();resetService();}
    @After public void resetService(){EmbeddedServerService.running=false;EmbeddedServerService.starting=false;EmbeddedServerService.launchRequested=0;}
    private View root(android.app.Activity a){return ((ViewGroup)a.findViewById(android.R.id.content)).getChildAt(0);}
    private void measure(View root,int w,int h){root.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));root.layout(0,0,w,h);}

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void changingIconStyleDoesNotLetControlsCollapseTheFileListsInAShortWindow(){
        Configuration old=new Configuration(app.getResources().getConfiguration()),config=new Configuration(old);config.fontScale=2;config.screenWidthDp=640;config.screenHeightDp=300;app.getResources().updateConfiguration(config,app.getResources().getDisplayMetrics());
        try(var screen=Robolectric.buildActivity(FilesActivity.class,new Intent(app,FilesActivity.class).putExtra("own_server",true)).create().start().resume().visible()){
            View root=root(screen.get());measure(root,640,300);assertTrue(((View)screen.get().findViewById(R.id.phoneFileList).getParent()).getHeight()>=80);
            IconShapeCatalog.select(app,2);ReflectionHelpers.callInstanceMethod(screen.get(),"polishControls");measure(root,640,300);
            for(int id:new int[]{R.id.phoneFileList,R.id.remoteFileList}){View frame=(View)screen.get().findViewById(id).getParent();assertTrue("Icon refresh collapsed file viewport to "+frame.getHeight(),frame.getHeight()>=80);}
            assertEquals(2f,app.getResources().getConfiguration().fontScale,0);assertEquals(2,IconShapeCatalog.index(app));
        }finally{app.getResources().updateConfiguration(old,app.getResources().getDisplayMetrics());}
    }
    @Test @Config(shadows={RouteIdentityTest.Secrets.class,QrPairingTest.HeldIdentity.class})
    public void savedIdentityProbeCannotReopenOldFilesActionOnceANewScanHasStarted()throws Exception{
        String host="192.168.1.3",pin="SHA256:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";JSONObject identity=SshKeys.generate();SshKeys.save(app,host,8022,"localls",identity);HostTrustStore.pair(app,host,8022,"localls",pin);
        QrPairingTest.HeldIdentity.info=SshKeys.inspect(identity.getString(SshKeys.PRIVATE),"");QrPairingTest.HeldIdentity.entered=new CountDownLatch(1);QrPairingTest.HeldIdentity.release=new CountDownLatch(1);
        var before=app.getSharedPreferences(ServerConfig.PREFS,0).getAll();
        try(var screen=Robolectric.buildActivity(QrPairingActivity.class).create()){
            assertTrue(QrPairingTest.HeldIdentity.entered.await(5,TimeUnit.SECONDS));ReflectionHelpers.callInstanceMethod(screen.get(),"scan");TextView status=ReflectionHelpers.getField(screen.get(),"status");String scanning=status.getText().toString();
            QrPairingTest.HeldIdentity.release.countDown();ExecutorService worker=ReflectionHelpers.getField(screen.get(),"worker");worker.submit(()->{}).get(5,TimeUnit.SECONDS);Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertEquals("Old saved-key callback exposed a different server while scanning",View.GONE,ReflectionHelpers.<Button>getField(screen.get(),"openFiles").getVisibility());assertEquals(scanning,status.getText().toString());assertEquals(before,app.getSharedPreferences(ServerConfig.PREFS,0).getAll());
        }finally{QrPairingTest.HeldIdentity.release.countDown();}
    }
    @Test public void timedOutServerDispatchDoesNotLeaveQrStartDisabledForever(){
        org.robolectric.shadows.ShadowSystemClock.advanceBy(Duration.ofSeconds(20));EmbeddedServerService.launchRequested=SystemClock.elapsedRealtime()-15000;
        app.getSharedPreferences(EmbeddedServerService.PREFS,0).edit().putString("allowed_key","preserved").commit();var before=app.getSharedPreferences(EmbeddedServerService.PREFS,0).getAll();
        try(var screen=Robolectric.buildActivity(QrPairingActivity.class,new Intent(app,QrPairingActivity.class).putExtra("server",true)).setup()){
            Shadows.shadowOf(Looper.getMainLooper()).idle();Button start=ReflectionHelpers.getField(screen.get(),"startServer");assertTrue("Timed-out dispatch permanently disabled start",start.isEnabled());assertEquals(0,EmbeddedServerService.launchRequested);assertEquals(before,app.getSharedPreferences(EmbeddedServerService.PREFS,0).getAll());
        }
    }
    @Test public void recentOrActivelyStartingServerDispatchCannotBeDuplicatedByQrRetry(){
        org.robolectric.shadows.ShadowSystemClock.advanceBy(Duration.ofSeconds(20));
        for(boolean active:new boolean[]{false,true}){
            EmbeddedServerService.starting=active;EmbeddedServerService.launchRequested=SystemClock.elapsedRealtime()-(active?15000:1000);long requested=EmbeddedServerService.launchRequested;
            try(var screen=Robolectric.buildActivity(QrPairingActivity.class,new Intent(app,QrPairingActivity.class).putExtra("server",true)).setup()){
                Shadows.shadowOf(Looper.getMainLooper()).idle();Button start=ReflectionHelpers.getField(screen.get(),"startServer");assertFalse(start.isEnabled());start.performClick();assertEquals(requested,EmbeddedServerService.launchRequested);assertNull(Shadows.shadowOf(app).getNextStartedService());
            }
        }
    }

}
