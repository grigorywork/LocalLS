package com.bitpoint.homeservercontrol;

import android.content.*;
import android.content.pm.ProviderInfo;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.provider.DocumentsContract;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.security.KeyPairGenerator;
import java.util.*;
import java.util.concurrent.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.*;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

/** Native Android views with isolated demonstration files and a real private loopback SSH service. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=30,qualifiers="ru-rRU-w360dp-h640dp-mdpi",shadows={ServerLifecycleTest.Identity.class,ServerLifecycleTest.Receive.class,RouteIdentityTest.Secrets.class,QrLoginIntegrationTest.Addresses.class})
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class InterfaceScreenshotsTest {
    private android.app.Application app;
    private void await(java.util.function.BooleanSupplier condition)throws Exception{long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(12);while(!condition.getAsBoolean()&&System.nanoTime()<until){Shadows.shadowOf(Looper.getMainLooper()).idle();Thread.sleep(10);}assertTrue(EmbeddedServerService.status,condition.getAsBoolean());}
    private View root(android.app.Activity a){return ((ViewGroup)a.findViewById(android.R.id.content)).getChildAt(0);}
    private void layout(View view,int w,int h){view.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));view.layout(0,0,w,h);}
    private void capture(android.app.Activity activity,String name,int w,int h)throws Exception{
        View view=root(activity);layout(view,w,h);view.jumpDrawablesToCurrentState();Bitmap bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);canvas.drawColor(ThemeCatalog.color(activity,com.bitpoint.homeservercontrol.ui.R.attr.hscBackground));view.draw(canvas);
        File folder=new File("build/ui-previews/seven-devices-20261023");folder.mkdirs();try(var out=new FileOutputStream(new File(folder,name+".png"))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}bitmap.recycle();
    }
    private CheckBox checkbox(View view){if(view instanceof CheckBox)return (CheckBox)view;if(view instanceof ViewGroup){ViewGroup g=(ViewGroup)view;for(int i=0;i<g.getChildCount();i++){CheckBox c=checkbox(g.getChildAt(i));if(c!=null)return c;}}return null;}
    private void select(FilesActivity a,boolean local,String name){
        ListView list=a.findViewById(local?R.id.phoneFileList:R.id.remoteFileList);layout(root(a),360,640);
        for(int i=0;i<list.getCount();i++)if(list.getItemAtPosition(i).toString().contains(name)){
            View row=list.getChildAt(i-list.getFirstVisiblePosition());assertNotNull(row);CheckBox check=checkbox(row);assertNotNull(check);check.performClick();assertTrue(check.isChecked());assertTrue(list.isItemChecked(i));return;
        }throw new AssertionError("Missing demonstration file "+name);
    }
    @Test public void renderBothRolesRealFileSelectionsAndVisibleServerQr()throws Exception{
        app=RuntimeEnvironment.getApplication();RouteIdentityTest.Secrets.store.clear();app.getSharedPreferences(ServerConfig.PREFS,0).edit().clear().putInt(ServerConfig.KEY_CONFIG_VERSION,3).putString(SshKeys.MODE,"key").putBoolean(ServerConfig.KEY_BACKGROUND_MONITOR,false).commit();app.getSharedPreferences(EmbeddedServerService.PREFS,0).edit().clear().commit();ThemeCatalog.select(app,1);
        var documents=new LastModIntegrationTest.Documents();documents.root=Files.createTempDirectory(app.getCacheDir().toPath(),"screenshots-").toFile();assertTrue(new File(documents.root,"Документы").mkdir());assertTrue(new File(documents.root,"Фотографии").mkdir());Files.write(new File(documents.root,"Отчёт.pdf").toPath(),new byte[246000]);Files.write(new File(documents.root,"Фото 2026.jpg").toPath(),new byte[1350000]);Files.write(new File(documents.root,"Заметки.txt").toPath(),new byte[1280]);
        ProviderInfo info=new ProviderInfo();info.authority="screenshots.documents";info.name=LastModIntegrationTest.Documents.class.getName();info.packageName=app.getPackageName();info.applicationInfo=app.getApplicationInfo();info.exported=true;info.grantUriPermissions=true;info.readPermission=android.Manifest.permission.MANAGE_DOCUMENTS;info.writePermission=android.Manifest.permission.MANAGE_DOCUMENTS;documents.attachInfo(app,info);ShadowContentResolver.registerProviderInternal(info.authority,documents);Shadows.shadowOf(app.getPackageManager()).addOrUpdateProvider(info);var resolved=new android.content.pm.ResolveInfo();resolved.providerInfo=info;Shadows.shadowOf(app.getPackageManager()).addResolveInfoForIntent(new Intent(DocumentsContract.PROVIDER_INTERFACE),resolved);
        Uri tree=DocumentsContract.buildTreeDocumentUri(info.authority,"root");app.getSharedPreferences(EmbeddedServerService.PREFS,0).edit().putString(FileNodes.PHONE_TREE,tree.toString()).commit();
        File share=EmbeddedServerService.share(app);share.mkdirs();assertTrue(new File(share,"Общие документы").mkdir());Files.write(new File(share,"Инструкция.pdf").toPath(),new byte[318000]);Files.write(new File(share,"Архив.zip").toPath(),new byte[2120000]);Files.write(new File(share,"Фото.jpg").toPath(),new byte[1120000]);
        var gen=KeyPairGenerator.getInstance("RSA");gen.initialize(2048);ServerLifecycleTest.Identity.pair=gen.generateKeyPair();ServerLifecycleTest.Receive.hold=false;ServerLifecycleTest.Receive.entered=new CountDownLatch(1);ServerLifecycleTest.Receive.release=new CountDownLatch(1);int port;try(ServerSocket socket=new ServerSocket(0)){port=socket.getLocalPort();}
        var identity=SshKeys.generate();app.getSharedPreferences(EmbeddedServerService.PREFS,0).edit().putString("allowed_key",identity.getString(SshKeys.PUBLIC)).putInt("port",port).putString("user","localls").commit();EmbeddedServerService.running=false;EmbeddedServerService.starting=false;EmbeddedServerService.launchRequested=0;
        var controller=Robolectric.buildService(EmbeddedServerService.class).create();var service=controller.get();
        try{
            service.onStartCommand(new Intent(app,EmbeddedServerService.class),0,1);await(()->EmbeddedServerService.running);SshKeys.save(app,"127.0.0.1",port,"localls",identity);HostTrustStore.pair(app,"127.0.0.1",port,"localls",EmbeddedServerService.fingerprint);
            app.getSharedPreferences(EmbeddedServerService.PREFS,0).edit().putBoolean("server_role",false).putBoolean("show_client",true).commit();
            try(var screen=Robolectric.buildActivity(MainActivity.class).setup()){
                MainActivity a=screen.get();assertTrue(a.findViewById(R.id.dashboardSpeedChart).isShown());assertTrue(a.findViewById(R.id.dashboardLatencyChart).isShown());
                app.sendStickyBroadcast(new Intent(Intent.ACTION_BATTERY_CHANGED).putExtra(BatteryManager.EXTRA_LEVEL,73).putExtra(BatteryManager.EXTRA_SCALE,100));
                ServerStats measured=SshClient.probe(app,"127.0.0.1",port,"localls","",EmbeddedServerService.fingerprint);assertTrue(measured.error,measured.telemetryAvailable);assertEquals("73%",measured.battery);
                ReflectionHelpers.callInstanceMethod(a,"renderStats",ReflectionHelpers.ClassParameter.from(ServerStats.class,measured),ReflectionHelpers.ClassParameter.from(String.class,"127.0.0.1"),ReflectionHelpers.ClassParameter.from(int.class,port),ReflectionHelpers.ClassParameter.from(String.class,"localls"));
                ((TextView)a.findViewById(R.id.statusText)).setText("Сервер доступен · вход по ключу");StatusBadge.apply(a.findViewById(R.id.statusText),StatusBadge.Tone.SUCCESS);
                ((MetricChartView)a.findViewById(R.id.dashboardSpeedChart)).setValues(Arrays.asList(0f,1.4f,3.8f,6.1f,5.4f,8.2f,7.7f,6.9f,8.4f),0,10);((TextView)a.findViewById(R.id.dashboardSpeedSummary)).setText("Сейчас 8,4 МБ/с • пик 8,4");
                ((MetricChartView)a.findViewById(R.id.dashboardLatencyChart)).setValues(Arrays.asList(14f,12f,13f,10f,11f,12f,10f,12f),0,20);((TextView)a.findViewById(R.id.dashboardLatencySummary)).setText("Последний 12 мс • средний 12 мс");capture(a,"01-client-home",360,640);
            }
            app.getSharedPreferences(EmbeddedServerService.PREFS,0).edit().putBoolean("server_role",true).putBoolean("show_client",false).commit();
            try(var screen=Robolectric.buildActivity(MainActivity.class).setup()){assertTrue(screen.get().findViewById(R.id.serverHome).isShown());assertFalse(screen.get().findViewById(R.id.dashboardSpeedChart).isShown());capture(screen.get(),"02-server-home",360,640);}
            try(var screen=Robolectric.buildActivity(FilesActivity.class,new Intent(app,FilesActivity.class).putExtra("own_server",true)).create().start().resume().visible()){
                FilesActivity a=screen.get();await(()->ReflectionHelpers.<Boolean>getField(a,"phoneReady")&&ReflectionHelpers.<Boolean>getField(a,"remoteReady"));select(a,true,"Отчёт.pdf");capture(a,"03-phone-files-selected",360,640);
                ReflectionHelpers.<Button>getField(a,"serverTab").performClick();select(a,false,"Инструкция.pdf");capture(a,"04-server-files-selected",360,640);
                Set<FileNodes.Node> selected=ReflectionHelpers.getField(a,"phoneSelected");assertEquals(1,selected.size());assertEquals("Отчёт.pdf",selected.iterator().next().name);
            }
            try(var screen=Robolectric.buildActivity(ServerDevicesActivity.class).setup()){capture(screen.get(),"06-device-access",360,640);}
            try(var screen=Robolectric.buildActivity(QrPairingActivity.class,new Intent(app,QrPairingActivity.class).putExtra("server",true)).setup()){
                ImageView qr=ReflectionHelpers.getField(screen.get(),"qr");assertNotNull(qr);capture(screen.get(),"05-server-qr",360,640);assertEquals(qr.getWidth(),qr.getHeight());assertTrue(qr.isShown());
            }
        }finally{controller.destroy();ExecutorService worker=ReflectionHelpers.getField(service,"worker");assertTrue(worker.awaitTermination(8,TimeUnit.SECONDS));}
    }
}
