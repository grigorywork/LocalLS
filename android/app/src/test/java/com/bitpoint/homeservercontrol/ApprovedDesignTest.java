package com.bitpoint.homeservercontrol;

import android.app.*;import android.content.*;import android.graphics.*;import android.os.*;import android.view.*;import android.widget.*;
import java.io.*;import java.net.*;import java.security.*;import java.util.*;import java.util.concurrent.*;
import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.*;import org.robolectric.util.ReflectionHelpers;
import com.jcraft.jsch.*;import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=30,qualifiers="ru-rRU-w411dp-h890dp-mdpi",shadows={ServerLifecycleTest.Identity.class,ServerLifecycleTest.Receive.class,RouteIdentityTest.Secrets.class,QrLoginIntegrationTest.Addresses.class})
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ApprovedDesignTest {
    private final android.app.Application app=RuntimeEnvironment.getApplication();
    private void await(java.util.function.BooleanSupplier ready)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);while(!ready.getAsBoolean()&&System.nanoTime()<end){Shadows.shadowOf(Looper.getMainLooper()).idle();Thread.sleep(10);}assertTrue(EmbeddedServerService.status,ready.getAsBoolean());}
    private View root(Activity a){return ((ViewGroup)a.findViewById(android.R.id.content)).getChildAt(0);}
    private void layout(View v,int w,int h){v.measure(View.MeasureSpec.makeMeasureSpec(w,1073741824),View.MeasureSpec.makeMeasureSpec(h,1073741824));v.layout(0,0,w,h);}
    private void capture(Activity a,String name,int w,int h)throws Exception{View v=root(a);layout(v,w,h);Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);v.draw(new Canvas(b));File dir=new File("build/ui-previews/approved-design-20261024");dir.mkdirs();try(var out=new FileOutputStream(new File(dir,name+".png"))){assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,out));}b.recycle();}
    @Before public void reset(){RouteIdentityTest.Secrets.store.clear();app.getSharedPreferences(ServerConfig.PREFS,0).edit().clear().putInt(ServerConfig.KEY_CONFIG_VERSION,3).commit();app.getSharedPreferences(EmbeddedServerService.PREFS,0).edit().clear().commit();EmbeddedServerService.running=false;EmbeddedServerService.starting=false;EmbeddedServerService.launchRequested=0;app.deleteDatabase("server-receipts.db");ThemeCatalog.select(app,1);ServerInvitation.reset();
        // Lifecycle cases interrupt their workers. Initialize real native SQLite on the
        // test thread before a cancellable worker can own Robolectric's library loading.
        try(ServerReceiptStore initial=new ServerReceiptStore(app)){assertTrue("Receipt fixture starts empty",initial.list().isEmpty());}
    }
    @Test public void liveHostQrTogglePreservesKeysSessionsAndCardBoundsAndReceiptsSurviveRestart()throws Exception{
        KeyPairGenerator gen=KeyPairGenerator.getInstance("RSA");gen.initialize(2048);ServerLifecycleTest.Identity.pair=gen.generateKeyPair();ServerLifecycleTest.Receive.hold=false;ServerLifecycleTest.Receive.entered=new CountDownLatch(1);ServerLifecycleTest.Receive.release=new CountDownLatch(1);
        int port;try(ServerSocket pick=new ServerSocket(0)){port=pick.getLocalPort();}var identity=SshKeys.generate();ServerDevices.add(app,identity.getString(SshKeys.PUBLIC));ServerDevices.rename(app,identity.getString(SshKeys.PUBLIC),"Телефон");
        for(String name:new String[]{"Ноутбук","Планшет"}){String key=SshKeys.generate().getString(SshKeys.PUBLIC);ServerDevices.add(app,key);ServerDevices.rename(app,key,name);}
        app.getSharedPreferences(EmbeddedServerService.PREFS,0).edit().putInt("port",port).putString("user","localls").commit();
        org.robolectric.shadows.ShadowStatFs.registerStats(EmbeddedServerService.share(app).getAbsolutePath(),67108864,35913728,35913728);
        app.sendStickyBroadcast(new Intent(Intent.ACTION_BATTERY_CHANGED).putExtra(BatteryManager.EXTRA_LEVEL,35).putExtra(BatteryManager.EXTRA_SCALE,100).putExtra(BatteryManager.EXTRA_STATUS,BatteryManager.BATTERY_STATUS_CHARGING));
        var serviceController=Robolectric.buildService(EmbeddedServerService.class).create();EmbeddedServerService service=serviceController.get();Session session=null;
        try{
            service.onStartCommand(new Intent(app,EmbeddedServerService.class),0,1);await(()->EmbeddedServerService.running);var before=app.getSharedPreferences(EmbeddedServerService.PREFS,0).getAll();
            JSch jsch=new JSch();jsch.addIdentity("disposable-test",identity.getString(SshKeys.PRIVATE).getBytes(java.nio.charset.StandardCharsets.UTF_8),null,null);session=jsch.getSession("localls","127.0.0.1",port);session.setConfig("StrictHostKeyChecking","no");session.setConfig("PreferredAuthentications","publickey");session.connect(4000);await(()->EmbeddedServerService.onlineDevices().size()==1);
            ChannelSftp files=(ChannelSftp)session.openChannel("sftp");files.connect(4000);files.put(new ByteArrayInputStream(new byte[]{1,2,3}),"/Фото.jpg");await(()->{try(ServerReceiptStore store=new ServerReceiptStore(app)){return store.list().size()==1;}});
            try(var controller=Robolectric.buildActivity(MainActivity.class).setup()){
                MainActivity a=controller.get();ServerHomeLayout home=a.findViewById(R.id.serverHome);await(()->!ReflectionHelpers.<Boolean>getField(home,"reading"));capture(a,"01-server-phone-hidden",411,890);assertFalse(home.qrShown());
                View card=(View)a.findViewById(R.id.serverQrButton).getParent().getParent();Rect bounds=new Rect(card.getLeft(),card.getTop(),card.getRight(),card.getBottom());a.findViewById(R.id.serverQrToggle).performClick();await(()->!ReflectionHelpers.<Boolean>getField(home,"reading"));if(ReflectionHelpers.getField(home,"bitmap")==null)capture(a,"qr-error",411,890);assertNotNull(((TextView)a.findViewById(R.id.serverPanelQrCaption)).getText().toString(),ReflectionHelpers.getField(home,"bitmap"));capture(a,"02-server-phone-qr",411,890);assertTrue(home.qrShown());assertEquals(bounds,new Rect(card.getLeft(),card.getTop(),card.getRight(),card.getBottom()));
                Bitmap qr=ReflectionHelpers.getField(home,"bitmap");int[] pixels=new int[qr.getWidth()*qr.getHeight()];qr.getPixels(pixels,0,qr.getWidth(),0,0,qr.getWidth(),qr.getHeight());String encoded=new com.google.zxing.MultiFormatReader().decode(new com.google.zxing.BinaryBitmap(new com.google.zxing.common.HybridBinarizer(new com.google.zxing.RGBLuminanceSource(qr.getWidth(),qr.getHeight(),pixels)))).getText();assertEquals(EmbeddedServerService.fingerprint,QrPairing.decode(encoded,System.currentTimeMillis()).pin);
                assertTrue((a.getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_SECURE)!=0);a.findViewById(R.id.serverQrToggle).performClick();layout(root(a),411,890);assertFalse(home.qrShown());assertEquals(bounds,new Rect(card.getLeft(),card.getTop(),card.getRight(),card.getBottom()));assertTrue(EmbeddedServerService.running);assertTrue(session.isConnected());assertEquals(1,EmbeddedServerService.onlineDevices().size());assertEquals(before,app.getSharedPreferences(EmbeddedServerService.PREFS,0).getAll());
                capture(a,"03-server-tablet-hidden",1280,800);a.findViewById(R.id.serverQrToggle).performClick();await(()->home.qrShown()&&!ReflectionHelpers.<Boolean>getField(home,"reading"));capture(a,"04-server-tablet-qr",1280,800);
                home.suspend();assertFalse(home.qrShown());assertTrue(session.isConnected());
            }
            files.disconnect();session.disconnect();session=null;await(()->EmbeddedServerService.onlineDevices().isEmpty());
        }finally{if(session!=null)session.disconnect();serviceController.destroy();ExecutorService worker=ReflectionHelpers.getField(service,"worker");assertTrue(worker.awaitTermination(10,TimeUnit.SECONDS));ExecutorService journalWorker=ReflectionHelpers.getField(service,"receiptWorker");assertTrue(journalWorker.awaitTermination(10,TimeUnit.SECONDS));}
        try(var screen=Robolectric.buildActivity(ServerReceiptActivity.class).setup()){await(()->findList(root(screen.get()))!=null&&findList(root(screen.get())).getCount()==1);capture(screen.get(),"06-receipts",411,890);}
        try(ServerReceiptStore store=new ServerReceiptStore(app)){var entries=store.list();assertEquals(1,entries.size());assertEquals("Фото.jpg",entries.get(0).name());assertEquals(3,entries.get(0).bytes);assertEquals(EmbeddedSftpServer.publicFingerprint(identity.getString(SshKeys.PUBLIC)),entries.get(0).sender);}
    }
    @Test public void receiptLogDeduplicatesCapsAt100AndRejectsTraversal(){
        try(ServerReceiptStore store=new ServerReceiptStore(app)){for(int i=0;i<110;i++)store.record(new EmbeddedSftpServer.Receipt("file"+i,"sender",i,i));assertEquals(100,store.list().size());store.record(new EmbeddedSftpServer.Receipt("file109","sender",109,109));assertEquals(100,store.list().size());assertEquals("file109",store.list().get(0).path);try{store.record(new EmbeddedSftpServer.Receipt("../outside","sender",1,1));fail("Traversal logged");}catch(IllegalArgumentException expected){}assertEquals(100,store.list().size());}
    }
    @Test public void connectionHasPersistentFieldLabelsCommandArrowsAndExplicitClientOptIn()throws Exception{
        try(var controller=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity a=controller.get();a.findViewById(R.id.drawerConnectionHeader).performClick();capture(a,"05-connection",411,890);
            for(int id:new int[]{R.id.localHostInput,R.id.portInput,R.id.userInput})assertTrue("Permanent label for "+id,hasLabel(root(a),id));assertNull(((Button)a.findViewById(R.id.forgetKeyButton)).getCompoundDrawablesRelative()[2]);assertNotNull(((Button)a.findViewById(R.id.scanQrButton)).getCompoundDrawablesRelative()[2]);
        }
        var clientPreferences=app.getSharedPreferences(ServerConfig.PREFS,0);clientPreferences.edit().putString(ServerConfig.KEY_MODE,ServerConfig.MODE_VPN).putString(ServerConfig.KEY_VPN_HOST,"remembered.example").commit();var preserved=clientPreferences.getAll();
        try(var controller=Robolectric.buildActivity(MainActivity.class).setup()){MainActivity a=controller.get();a.findViewById(R.id.drawerConnectionHeader).performClick();assertTrue(a.findViewById(R.id.vpnHostInput).isShown());assertFalse(a.findViewById(R.id.localHostInput).isShown());assertEquals(View.GONE,a.findViewById(R.id.modeGroup).getVisibility());assertEquals(preserved,clientPreferences.getAll());}
        var prefs=app.getSharedPreferences(EmbeddedServerService.PREFS,0);prefs.edit().putBoolean("server_role",true).putBoolean("show_client",false).commit();Map<String,Object> before=new HashMap<>(prefs.getAll());
        try(var controller=Robolectric.buildActivity(EmbeddedServerActivity.class).setup()){CheckBox option=controller.get().findViewById(R.id.serverClientEnabledCheck);assertTrue(option.isShown());assertFalse(option.isChecked());option.performClick();assertTrue(prefs.getBoolean("show_client",false));before.put("show_client",true);assertEquals(before,prefs.getAll());}
    }
    @Test @Config(qualifiers="ru-rRU-w640dp-h300dp-land-mdpi") public void largeFontLandscapeHostKeepsEveryPrimaryActionInsideTheViewport()throws Exception{
        app.getSharedPreferences(EmbeddedServerService.PREFS,0).edit().putBoolean("server_role",true).putBoolean("show_client",false).commit();float original=RuntimeEnvironment.getFontScale();
        try{RuntimeEnvironment.setFontScale(2);try(var controller=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity a=controller.get();assertEquals(2,a.getResources().getConfiguration().fontScale,0);View view=root(a);layout(view,640,300);capture(a,"07-server-large-font-landscape",640,300);
            for(int id:new int[]{R.id.serverPowerButton,R.id.serverQrToggle,R.id.serverLocalFilesButton,R.id.serverReceiveButton,R.id.localServerQuickButton,R.id.serverBackgroundButton}){
                View action=a.findViewById(id);Rect visible=new Rect();assertTrue("Visible action "+id,action.getGlobalVisibleRect(visible));assertTrue("Full touch height "+id,visible.height()>=48);assertTrue("Full touch width "+id+" "+visible+" actualWidth="+action.getWidth()+" parentWidth="+((View)action.getParent()).getWidth(),visible.width()>=48);assertTrue("Action stays inside viewport "+visible,visible.top>=0&&visible.bottom<=300&&visible.left>=0&&visible.right<=640);
            }
        }}finally{RuntimeEnvironment.setFontScale(original);}
    }
    @Test public void serverShutdownWaitsForItsQueuedReceiptWrite()throws Exception{
        var controller=Robolectric.buildService(EmbeddedServerService.class).create();EmbeddedServerService service=controller.get();ExecutorService writer=ReflectionHelpers.getField(service,"receiptWorker"),owner=ReflectionHelpers.getField(service,"worker");CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        writer.execute(()->{entered.countDown();try{release.await(5,TimeUnit.SECONDS);try(ServerReceiptStore journal=new ServerReceiptStore(app)){journal.record(new EmbeddedSftpServer.Receipt("late.txt","sender",7,1));}}catch(InterruptedException error){Thread.currentThread().interrupt();}});
        try{assertTrue(entered.await(2,TimeUnit.SECONDS));controller.destroy();assertFalse("Service shutdown must wait for its pending receipt",owner.awaitTermination(100,TimeUnit.MILLISECONDS));}
        finally{release.countDown();assertTrue(owner.awaitTermination(8,TimeUnit.SECONDS));assertTrue(writer.awaitTermination(2,TimeUnit.SECONDS));}
        try(ServerReceiptStore journal=new ServerReceiptStore(app)){assertEquals(1,journal.list().size());assertEquals("late.txt",journal.list().get(0).path);}
    }
    private ListView findList(View v){if(v instanceof ListView)return (ListView)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){ListView found=findList(((ViewGroup)v).getChildAt(i));if(found!=null)return found;}return null;}
    private boolean hasLabel(View v,int id){if(v instanceof TextView&&v.getLabelFor()==id&&((TextView)v).length()>0)return true;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)if(hasLabel(((ViewGroup)v).getChildAt(i),id))return true;return false;}
}
