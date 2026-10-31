package com.bitpoint.homeservercontrol;
import android.app.*;
import android.content.*;
import android.content.pm.ProviderInfo;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.*;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=30,qualifiers="ru-rRU-w411dp-h890dp-mdpi",shadows=RouteIdentityTest.Secrets.class)
public class DashboardUpgradeTest {
 private Application app;private LastModIntegrationTest.Documents provider;private Uri uri;
 @Before public void setup()throws Exception{
  app=RuntimeEnvironment.getApplication();app.getSharedPreferences(ServerConfig.PREFS,0).edit().clear().putInt(ServerConfig.KEY_CONFIG_VERSION,3).commit();app.getSharedPreferences(EmbeddedServerService.PREFS,0).edit().clear().commit();RouteIdentityTest.Secrets.store.clear();
  provider=new LastModIntegrationTest.Documents();provider.root=Files.createTempDirectory(app.getCacheDir().toPath(),"share-preview-test").toFile();Files.write(new File(provider.root,"photo.txt").toPath(),new byte[]{1,2,3,4});
  ProviderInfo info=new ProviderInfo();info.authority="upgrade.documents";info.name=LastModIntegrationTest.Documents.class.getName();info.packageName=app.getPackageName();info.applicationInfo=app.getApplicationInfo();info.exported=true;info.grantUriPermissions=true;info.readPermission=android.Manifest.permission.MANAGE_DOCUMENTS;info.writePermission=android.Manifest.permission.MANAGE_DOCUMENTS;provider.attachInfo(app,info);ShadowContentResolver.registerProviderInternal(info.authority,provider);Shadows.shadowOf(app.getPackageManager()).addOrUpdateProvider(info);
  uri=android.provider.DocumentsContract.buildDocumentUri(info.authority,"root/photo.txt");app.getSharedPreferences(EmbeddedServerService.PREFS,0).edit().putString(FileNodes.PHONE_TREE,android.provider.DocumentsContract.buildTreeDocumentUri(info.authority,"root").toString()).commit();
 }
 private View root(Activity a){return ((ViewGroup)a.findViewById(android.R.id.content)).getChildAt(0);}
 private void layout(View view,int w,int h){view.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));view.layout(0,0,w,h);}
 @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) public void shapeChangesTheOuterButtonAndKeepsTheGlyphUnframed(){
  Button button=new CenteredActionButton(new android.view.ContextThemeWrapper(app,ThemeCatalog.style(app)));button.setText("Файлы");button.setTag("tile");button.setLayoutParams(new LinearLayout.LayoutParams(200,80));int[] corners=new int[2];
  for(int i=0;i<2;i++){IconShapeCatalog.select(app,i==0?1:3);IconButtons.decorate(button);assertTrue(button.getBackground() instanceof ButtonOutlineDrawable);Outline fixed=new Outline();button.getBackground().setBounds(0,0,200,80);button.getBackground().getOutline(fixed);assertEquals(12,fixed.getRadius(),0);layout(button,200,80);Bitmap b=Bitmap.createBitmap(200,80,Bitmap.Config.ARGB_8888);button.getBackground().setBounds(0,0,200,80);button.getBackground().draw(new Canvas(b));corners[i]=Color.alpha(b.getPixel(4,4));b.recycle();
   assertTrue("Function glyph remains a bare vector without an inner badge",ReflectionHelpers.<android.graphics.drawable.Drawable>getField(button,"tileIcon") instanceof android.graphics.drawable.VectorDrawable);
  }assertEquals("Retired shape values produce identical corners",corners[0],corners[1]);FileEntryRow row=new FileEntryRow(new android.view.ContextThemeWrapper(app,ThemeCatalog.style(app)));assertTrue(row.getChildAt(row.getChildCount()-1).getBackground() instanceof ButtonOutlineDrawable);Outline frame=new Outline();android.graphics.drawable.Drawable rowBackground=row.getChildAt(row.getChildCount()-1).getBackground();rowBackground.setBounds(0,0,80,48);rowBackground.getOutline(frame);assertEquals(12,frame.getRadius(),0);
 }
 @Test public void stagedShareSurvivesLossOfOriginalProviderAndUploadsUsingTheSavedKey()throws Exception{
  var identity=SshKeys.generate();var generator=java.security.KeyPairGenerator.getInstance("RSA");generator.initialize(2048);var host=generator.generateKeyPair();File destination=Files.createTempDirectory(app.getCacheDir().toPath(),"share-server").toFile();
  try(var server=new EmbeddedSftpServer()){
   int port=server.start("127.0.0.1",0,"localls",EmbeddedSftpServer.authorizedKey(identity.getString(SshKeys.PUBLIC)),host,destination.toPath());String pin=EmbeddedSftpServer.fingerprint(host);
   app.getSharedPreferences(ServerConfig.PREFS,0).edit().putString(ServerConfig.KEY_LOCAL_HOST,"127.0.0.1").putInt(ServerConfig.KEY_PORT,port).putString(ServerConfig.KEY_USER,"localls").putString(SshKeys.MODE,"key").commit();SshKeys.save(app,"127.0.0.1",port,"localls",identity);HostTrustStore.pair(app,"127.0.0.1",port,"localls",pin);var before=app.getSharedPreferences(ServerConfig.PREFS,0).getAll();
   var nodes=ShareOutbox.stage(app,List.of(uri),s->{});assertEquals(1,nodes.size());assertTrue(new File(provider.root,"photo.txt").delete());try(var in=FileNodes.open(app,nodes.get(0))){assertArrayEquals(new byte[]{1,2,3,4},in.readAllBytes());}
   TransferForegroundService.enqueue(app,TransferForegroundService.UPLOAD,nodes,".",null,FileConnection.current(app));Intent request=Shadows.shadowOf(app).getNextStartedService();assertNotNull(request);String id=request.getStringExtra("request_id");var restored=new TransferJournal(app).request(id);assertEquals("queued",new TransferJournal(app).list().get(0).getString("status"));
   new DurableTransferJob(app,restored,()->{},(n,total)->{}).run();assertArrayEquals(new byte[]{1,2,3,4},Files.readAllBytes(new File(destination,"photo.txt").toPath()));assertEquals(before,app.getSharedPreferences(ServerConfig.PREFS,0).getAll());ShareOutbox.completed(app,request);assertFalse(ShareOutbox.file(app,nodes.get(0).path).exists());
  }
 }
 @Test public void shareDefaultRequiresConfirmationAndSettingsPreserveConnection(){
  assertFalse(app.getSharedPreferences(ServerConfig.PREFS,0).getBoolean(ShareOutbox.AUTO,false));app.getSharedPreferences(ServerConfig.PREFS,0).edit().putString(ServerConfig.KEY_LOCAL_HOST,"192.0.2.9").putString("keep","value").commit();
  try(var c=Robolectric.buildActivity(SettingsActivity.class).setup()){
   ((CheckBox)c.get().findViewById(R.id.shareImmediatelyCheck)).setChecked(true);c.get().findViewById(R.id.settingsSaveButton).performClick();assertTrue(app.getSharedPreferences(ServerConfig.PREFS,0).getBoolean(ShareOutbox.AUTO,false));assertEquals("192.0.2.9",app.getSharedPreferences(ServerConfig.PREFS,0).getString(ServerConfig.KEY_LOCAL_HOST,""));assertEquals("value",app.getSharedPreferences(ServerConfig.PREFS,0).getString("keep",""));
  }
 }
 @Test public void releasedPreviewDoesNotReappearAndPreservesPreexistingSecureFlag()throws Exception{
  try(var c=Robolectric.buildActivity(FilesActivity.class,new Intent(app,FilesActivity.class).putExtra("own_server",true)).setup()){
   var preview=new HoldPreview(c.get());c.get().getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);preview.close();assertTrue((c.get().getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_SECURE)!=0);
   preview.open(FileNodes.document(app,uri),FileConnection.current(app));assertTrue(preview.showing());preview.close();assertFalse(preview.showing());ExecutorService worker=ReflectionHelpers.getField(preview,"worker");worker.submit(()->{}).get(5,TimeUnit.SECONDS);Shadows.shadowOf(Looper.getMainLooper()).idle();assertFalse(preview.showing());preview.destroy();assertArrayEquals(new byte[]{1,2,3,4},Files.readAllBytes(new File(provider.root,"photo.txt").toPath()));
  }
 }
 @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) public void phoneAndTabletRenderThreeRecentFilesWithBothGraphs()throws Exception{
  var previousState=TransferForegroundService.state;ThemeCatalog.select(app,0);try(var c=Robolectric.buildActivity(MainActivity.class).setup()){
   MainActivity a=c.get();DashboardLayout home=a.findViewById(R.id.dashboardScroll);TransferForegroundService.state=new TransferForegroundService.State(true,false,"Передача","",68,100,1);ServerStats stats=new ServerStats();new ServerTelemetry(35,137L*1073741824,256L*1073741824,1080000,1).apply(stats);stats.authenticated=true;stats.hostKeyTrusted=true;stats.reachable=true;
   ReflectionHelpers.callInstanceMethod(a,"renderStats",ReflectionHelpers.ClassParameter.from(ServerStats.class,stats),ReflectionHelpers.ClassParameter.from(String.class,"192.168.1.151"),ReflectionHelpers.ClassParameter.from(int.class,8022),ReflectionHelpers.ClassParameter.from(String.class,"localls"));
   ((MetricChartView)a.findViewById(R.id.dashboardSpeedChart)).setValues(Arrays.asList(8f,12f,10f,20f,15f,18.4f),0,30);((TextView)a.findViewById(R.id.dashboardSpeedSummary)).setText("18,4 МБ/с");((MetricChartView)a.findViewById(R.id.dashboardLatencyChart)).setValues(Arrays.asList(60f,80f,65f,240f,95f,103f),0,300);((TextView)a.findViewById(R.id.dashboardLatencySummary)).setText("103 мс");
   for(int[] size:new int[][]{{411,890},{1280,800},{890,411}}){home.transfers("Видео_отпуск.mp4","68% · 1,36 из 2 ГБ · осталось 36 с",68,"✓ Фото_2026.zip · Отправлено\n✓ Документы.pdf · Получено\n✓ Архив.zip · Отправлено");View view=root(a);layout(view,size[0],size[1]);assertTrue(a.findViewById(R.id.dashboardSpeedChart).isShown());assertTrue(a.findViewById(R.id.dashboardLatencyChart).isShown());TextView history=ReflectionHelpers.getField(home,"recentText");assertEquals(3,history.getText().toString().split("\n").length);List<LinearLayout> rows=ReflectionHelpers.getField(home,"recentRows");assertEquals(3,rows.size());for(LinearLayout row:rows)assertTrue(row.isShown());assertTrue("Visible plot "+size[0]+" height "+a.findViewById(R.id.dashboardSpeedChart).getHeight(),a.findViewById(R.id.dashboardSpeedChart).getHeight()>=48);TextView address=a.findViewById(R.id.ipValue);android.graphics.Rect visibleAddress=new android.graphics.Rect();assertTrue(address.getGlobalVisibleRect(visibleAddress));assertTrue("Server IP remains readable",visibleAddress.height()>=address.getLineHeight());TextView accessLabel=ReflectionHelpers.getField(home,"access");if(accessLabel.isShown()){android.graphics.Rect visibleAccess=new android.graphics.Rect();assertTrue(accessLabel.getGlobalVisibleRect(visibleAccess));assertTrue("Key access subtitle remains readable",visibleAccess.height()>=accessLabel.getLineHeight());}TextView batteryLevel=a.findViewById(R.id.batteryValue);android.widget.ImageView batteryGlyph=ReflectionHelpers.getField(batteryLevel.getParent(),"glyph");assertNotNull("Battery gauge is a real image",batteryGlyph.getDrawable());assertTrue("Battery value and gauge remain readable "+size[0]+" height "+batteryLevel.getHeight(),batteryLevel.getHeight()>=Math.max(batteryLevel.getLineHeight(),batteryGlyph.getHeight()));android.graphics.Rect visibleGlyph=new android.graphics.Rect();assertTrue("Battery gauge is visible",batteryGlyph.getGlobalVisibleRect(visibleGlyph));assertEquals("Gauge width is not cropped",batteryGlyph.getWidth(),visibleGlyph.width());assertEquals("Gauge height is not cropped",batteryGlyph.getHeight(),visibleGlyph.height());TextView currentLabel=ReflectionHelpers.getField(home,"current");assertTrue("Current filename remains readable "+size[0]+"x"+size[1]+" height "+currentLabel.getHeight()+" line "+currentLabel.getLineHeight(),currentLabel.getHeight()>=currentLabel.getLineHeight());List<TextView> recentLabels=ReflectionHelpers.getField(home,"recentNames");for(TextView label:recentLabels)assertTrue("Recent filename remains readable",label.getHeight()>=label.getLineHeight());
    Bitmap bitmap=Bitmap.createBitmap(size[0],size[1],Bitmap.Config.ARGB_8888);view.draw(new Canvas(bitmap));File folder=new File("build/ui-previews/dashboard-final-20261023");folder.mkdirs();try(OutputStream out=new FileOutputStream(new File(folder,size[0]==411?"01-phone-home.png":size[0]==1280?"02-tablet-home.png":"05-phone-landscape.png"))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}bitmap.recycle();
   }
  }finally{TransferForegroundService.state=previousState;}
 }
 @Test public void transferStopRequiresConfirmationAndOnlyAppearsForAnActiveQueue(){
  var previous=TransferForegroundService.state;try(var c=Robolectric.buildActivity(MainActivity.class).setup()){
   MainActivity a=c.get();DashboardLayout home=a.findViewById(R.id.dashboardScroll);LinearLayout card=ReflectionHelpers.getField(home,"transfer");TextView percent=ReflectionHelpers.getField(home,"transferPercent");
   TransferForegroundService.state=new TransferForegroundService.State(false,false,"","",0,0,0);home.transfers("Нет передач","",0,"");assertFalse(card.isClickable());assertNull(percent.getCompoundDrawablesRelative()[2]);
   TransferForegroundService.state=new TransferForegroundService.State(true,false,"Передача","",10,100,1);home.transfers("photo.jpg","10% · 10 из 100",10,"");assertTrue(card.isClickable());assertNotNull(percent.getCompoundDrawablesRelative()[2]);while(Shadows.shadowOf(app).getNextStartedService()!=null){}
   card.performClick();AlertDialog dialog=(AlertDialog)ShadowAlertDialog.getLatestDialog();assertNotNull(dialog);assertNull("No cancellation before confirmation",Shadows.shadowOf(app).getNextStartedService());dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();Shadows.shadowOf(Looper.getMainLooper()).idle();assertNull(Shadows.shadowOf(app).getNextStartedService());
   card.performClick();dialog=(AlertDialog)ShadowAlertDialog.getLatestDialog();dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();Shadows.shadowOf(Looper.getMainLooper()).idle();Intent cancel=Shadows.shadowOf(app).getNextStartedService();assertNotNull(cancel);assertEquals(TransferForegroundService.CANCEL,cancel.getAction());
  }finally{TransferForegroundService.state=previous;}
 }
 @Test public void historyDistinguishesFailuresAndCancellationAndProgressIsClamped(){
  try(var c=Robolectric.buildActivity(MainActivity.class).setup()){
   DashboardLayout home=c.get().findViewById(R.id.dashboardScroll);home.transfers("file","",150,"✓ done.txt · Отправлено\n! failed.txt · Ошибка\n× cancelled.txt · Отменено");List<TextView> marks=ReflectionHelpers.getField(home,"recentChecks");assertEquals(ThemeCatalog.color(c.get(),com.bitpoint.homeservercontrol.ui.R.attr.hscSuccess),marks.get(0).getCurrentTextColor());assertEquals(ThemeCatalog.color(c.get(),com.bitpoint.homeservercontrol.ui.R.attr.hscError),marks.get(1).getCurrentTextColor());assertEquals(ThemeCatalog.color(c.get(),com.bitpoint.homeservercontrol.ui.R.attr.hscWarning),marks.get(2).getCurrentTextColor());TextView value=ReflectionHelpers.getField(home,"transferPercent");ProgressBar bar=ReflectionHelpers.getField(home,"progress");assertEquals("100%",value.getText().toString());assertEquals(100,bar.getProgress());home.transfers("file","",-10,"");assertEquals("",value.getText().toString());assertEquals(0,bar.getProgress());
  }
 }
 @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) public void smallPhonesKeepNamesAndBatteryReadableAfterRotation()throws Exception{
  var previousState=TransferForegroundService.state;try(var c=Robolectric.buildActivity(MainActivity.class).setup()){
   MainActivity a=c.get();DashboardLayout home=a.findViewById(R.id.dashboardScroll);TransferForegroundService.state=new TransferForegroundService.State(true,false,"Передача","",68,100,1);ServerStats stats=new ServerStats();new ServerTelemetry(35,137L*1073741824,256L*1073741824,1080000,1).apply(stats);stats.authenticated=true;stats.hostKeyTrusted=true;stats.reachable=true;ReflectionHelpers.callInstanceMethod(a,"renderStats",ReflectionHelpers.ClassParameter.from(ServerStats.class,stats),ReflectionHelpers.ClassParameter.from(String.class,"192.168.1.151"),ReflectionHelpers.ClassParameter.from(int.class,8022),ReflectionHelpers.ClassParameter.from(String.class,"localls"));home.transfers("Видео_отпуск.mp4","68% · 1,36 из 2 ГБ",68,"✓ Фото.zip · Отправлено\n! Документы.pdf · Ошибка\n× Архив.zip · Отменено");
   for(int[] size:new int[][]{{360,640},{320,568},{411,890},{890,411},{411,890}}){layout(root(a),size[0],size[1]);TextView current=ReflectionHelpers.getField(home,"current");assertTrue("Current file "+size[0]+"x"+size[1]+" height "+current.getHeight(),current.getHeight()>=Math.max(current.getLineHeight(),20));List<TextView> names=ReflectionHelpers.getField(home,"recentNames");for(TextView name:names)assertTrue("Recent file "+size[0]+"x"+size[1]+" height "+name.getHeight(),name.getHeight()>=name.getLineHeight());TextView disk=a.findViewById(R.id.diskValue);android.graphics.Rect diskVisible=new android.graphics.Rect();assertTrue(disk.getGlobalVisibleRect(diskVisible));assertTrue("Free space value "+size[0]+"x"+size[1]+" height "+diskVisible.height(),diskVisible.height()>=disk.getLineHeight());TextView battery=a.findViewById(R.id.batteryValue);assertTrue("Battery "+size[0]+"x"+size[1]+" height "+battery.getHeight(),battery.getHeight()>=Math.max(battery.getLineHeight(),30));if(size[0]==360||size[0]==320){Bitmap image=Bitmap.createBitmap(size[0],size[1],Bitmap.Config.ARGB_8888);root(a).draw(new Canvas(image));File folder=new File("build/ui-previews/dashboard-final-20261023");folder.mkdirs();try(OutputStream out=new FileOutputStream(new File(folder,size[0]==360?"06-small-phone.png":"07-smallest-phone.png"))){assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,out));}image.recycle();}}
  }finally{TransferForegroundService.state=previousState;}
 }
 @Test public void automaticShareQueuesOnlyAfterOptInAndKeepsThePinnedDestination()throws Exception{
  var identity=SshKeys.generate();String host="127.0.0.1",pin="SHA256:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";int port=32459;
  app.getSharedPreferences(ServerConfig.PREFS,0).edit().putString(ServerConfig.KEY_LOCAL_HOST,host).putString(ServerConfig.KEY_USER,"localls").putInt(ServerConfig.KEY_PORT,port).putString(SshKeys.MODE,"key").commit();SshKeys.save(app,host,port,"localls",identity);HostTrustStore.pair(app,host,port,"localls",pin);
  for(boolean auto:new boolean[]{false,true}){
   app.getSharedPreferences(ServerConfig.PREFS,0).edit().putBoolean(ShareOutbox.AUTO,auto).commit();Intent offer=new Intent(app,ShareUploadActivity.class);FileAttachments.put(offer,List.of(uri));
   try(var c=Robolectric.buildActivity(ShareUploadActivity.class,offer).setup()){
    ExecutorService worker=ReflectionHelpers.getField(c.get(),"worker");worker.submit(()->{}).get(8,TimeUnit.SECONDS);Shadows.shadowOf(Looper.getMainLooper()).idle();Intent request=Shadows.shadowOf(app).getNextStartedService();
    if(!auto)assertNull(request);else{assertNotNull(request);assertEquals(".",request.getStringExtra("remote"));assertEquals(pin,request.getStringExtra("pin"));assertEquals(host,request.getStringExtra("host"));FileNodes.Node node=FileNodes.Node.from(request.<Bundle>getParcelableArrayListExtra("sources").get(0));assertEquals("outbox",node.kind);assertTrue(ShareOutbox.file(app,node.path).isFile());assertEquals("queued",new TransferJournal(app).list().get(0).getString("status"));}
   }
  }
 }
 @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) public void actualPhoneButtonsShowRoundAndRectangularOutlines()throws Exception{
  var previousState=TransferForegroundService.state;ThemeCatalog.select(app,0);try(var c=Robolectric.buildActivity(MainActivity.class).setup()){
   MainActivity a=c.get();for(int shape:new int[]{1,3}){IconShapeCatalog.select(app,shape);ReflectionHelpers.callInstanceMethod(a,"polishControls");View view=root(a);layout(view,411,890);Bitmap b=Bitmap.createBitmap(411,890,Bitmap.Config.ARGB_8888);view.draw(new Canvas(b));File folder=new File("build/ui-previews/dashboard-final-20261023");folder.mkdirs();try(OutputStream out=new FileOutputStream(new File(folder,shape==1?"03-round-buttons.png":"04-rectangular-buttons.png"))){assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,out));}b.recycle();}IconShapeCatalog.select(app,0);
  }
 }
 @Test public void releasingLocalVideoStopsPlaybackWithoutCopyingTheWholeFile()throws Exception{
  Files.write(new File(provider.root,"Видео_отпуск.mp4").toPath(),new byte[]{1,2,3});Uri videoUri=android.provider.DocumentsContract.buildDocumentUri("upgrade.documents","root/Видео_отпуск.mp4");
  try(var c=Robolectric.buildActivity(FilesActivity.class,new Intent(app,FilesActivity.class).putExtra("own_server",true)).setup()){
   HoldPreview preview=ReflectionHelpers.getField(c.get(),"preview");preview.open(new FileNodes.Node("doc",videoUri.toString(),"Видео_отпуск.mp4",false,3L*1024*1024*1024),FileConnection.current(app));VideoView player=ReflectionHelpers.getField(preview,"video");assertNotNull(player);assertEquals(videoUri.toString(),Shadows.shadowOf(player).getVideoURIString());player.start();assertTrue(player.isPlaying());preview.close();assertEquals(ShadowVideoView.STOP,Shadows.shadowOf(player).getCurrentVideoState());assertFalse(preview.showing());assertArrayEquals(new byte[]{1,2,3},Files.readAllBytes(new File(provider.root,"Видео_отпуск.mp4").toPath()));
  }
 }
 @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) public void actualImageDecodeIsTemporaryAndReleaseClearsItsBitmapAndCache()throws Exception{
  Bitmap image=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888);image.eraseColor(Color.RED);File file=new File(provider.root,"Фотография.png");try(OutputStream out=new FileOutputStream(file)){assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,out));}image.recycle();Uri imageUri=android.provider.DocumentsContract.buildDocumentUri("upgrade.documents","root/Фотография.png");
  try(var c=Robolectric.buildActivity(FilesActivity.class,new Intent(app,FilesActivity.class).putExtra("own_server",true)).setup()){
   HoldPreview preview=ReflectionHelpers.getField(c.get(),"preview");preview.open(FileNodes.document(app,imageUri),FileConnection.current(app));ExecutorService worker=ReflectionHelpers.getField(preview,"worker");worker.submit(()->{}).get(8,TimeUnit.SECONDS);Shadows.shadowOf(Looper.getMainLooper()).idle();Bitmap decoded=ReflectionHelpers.getField(preview,"bitmap");assertNotNull(decoded);assertEquals(Color.RED,decoded.getPixel(0,0));preview.close();assertTrue(decoded.isRecycled());File folder=new File(app.getCacheDir(),"hold-preview");assertEquals(0,Objects.requireNonNull(folder.list()).length);assertTrue(file.isFile());
  }
 }
}
