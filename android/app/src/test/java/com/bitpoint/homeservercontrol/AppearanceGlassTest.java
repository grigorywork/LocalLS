package com.bitpoint.homeservercontrol;

import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=30,qualifiers="ru-rRU-w411dp-h890dp-mdpi",shadows=RouteIdentityTest.Secrets.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class AppearanceGlassTest {
 private Context app;private TransferForegroundService.State oldTransfer;
 @Before public void setup(){oldTransfer=TransferForegroundService.state;app=RuntimeEnvironment.getApplication();app.getSharedPreferences(ServerConfig.PREFS,0).edit().clear().putInt(ServerConfig.KEY_CONFIG_VERSION,3).putString(ServerConfig.KEY_LOCAL_HOST,"192.0.2.9").putString("identity_marker","preserve").commit();app.getSharedPreferences(EmbeddedServerService.PREFS,0).edit().clear().putString("server_key_marker","preserve").commit();RouteIdentityTest.Secrets.store.clear();EmbeddedServerService.running=false;EmbeddedServerService.starting=false;EmbeddedServerService.launchRequested=0;}
 @After public void cleanup(){TransferForegroundService.state=oldTransfer;EmbeddedServerService.running=false;app.getSharedPreferences(ServerConfig.PREFS,0).edit().remove(ThemeCatalog.GLASS_KEY).remove(ThemeCatalog.OUTLINE_KEY).putString(ThemeCatalog.KEY,"purple").apply();}
 @Test public void glassOutlineAndThemeAreIndependentAndPersistWithoutChangingConnections()throws Exception{
  assertEquals(8,ThemeCatalog.IDS.length);assertEquals(8,ThemeCatalog.NAMES.length);assertEquals("Красная",ThemeCatalog.NAMES[5]);assertEquals("Бирюзовая",ThemeCatalog.NAMES[6]);assertEquals("Серебристая",ThemeCatalog.NAMES[7]);
  var serverBefore=app.getSharedPreferences(EmbeddedServerService.PREFS,0).getAll();var prefs=app.getSharedPreferences(ServerConfig.PREFS,0);var before=new HashMap<>(prefs.getAll());ThemeCatalog.select(app,5);
  try(var screen=Robolectric.buildActivity(AppearanceActivity.class).setup()){
   AppearanceActivity a=screen.get();assertFalse(((Switch)a.findViewById(R.id.appearanceGlassSwitch)).isChecked());a.findViewById(R.id.appearanceGlassSwitch).performClick();assertTrue(ThemeCatalog.glass(app));screen.recreate();a=screen.get();
   assertEquals(Color.BLACK,ThemeCatalog.color(a,com.bitpoint.homeservercontrol.ui.R.attr.hscBackground));assertEquals(5,ThemeCatalog.index(a));assertTrue(((Switch)a.findViewById(R.id.appearanceGlassSwitch)).isChecked());
   a.findViewById(R.id.appearanceOutlineButton).performClick();AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();assertNotNull(dialog);assertEquals(2,dialog.getListView().getCount());dialog.getListView().performItemClick(null,1,1);assertTrue(ThemeCatalog.lightOutline(app));screen.recreate();a=screen.get();assertEquals(0xFFBDC5D2,ThemeCatalog.color(a,com.bitpoint.homeservercontrol.ui.R.attr.hscStroke));
   capture(root(a),"appearance-red-glass",411,890);
   a.findViewById(R.id.appearanceGlassSwitch).performClick();screen.recreate();a=screen.get();assertFalse(ThemeCatalog.glass(a));assertTrue(ThemeCatalog.lightOutline(a));assertEquals(5,ThemeCatalog.index(a));assertEquals(0xFF160C10,ThemeCatalog.color(a,com.bitpoint.homeservercontrol.ui.R.attr.hscBackground));
   var after=new HashMap<>(prefs.getAll());for(String key:new String[]{ThemeCatalog.KEY,ThemeCatalog.GLASS_KEY,ThemeCatalog.OUTLINE_KEY}){before.remove(key);after.remove(key);}assertEquals(before,after);assertEquals(serverBefore,app.getSharedPreferences(EmbeddedServerService.PREFS,0).getAll());
   assertTrue(a.findViewById(R.id.appearanceGlassSwitch) instanceof Switch);assertTrue(((Button)a.findViewById(R.id.themeChooserButton)).getBackground() instanceof ListActionDrawable);
   for(Class<? extends BaseActivity> kind:java.util.Arrays.asList(MainActivity.class,AppearanceActivity.class)){
    ThemeCatalog.glass(app,false);try(var resumed=Robolectric.buildActivity(kind).setup()){
     ThemeCatalog.glass(app,true);resumed.recreate();assertTrue("Stale saved switch cannot undo glass chosen on another screen: "+kind.getSimpleName(),ThemeCatalog.glass(app));assertTrue(((Switch)resumed.get().findViewById(R.id.appearanceGlassSwitch)).isChecked());
     ThemeCatalog.glass(app,false);resumed.recreate();assertFalse("Stale saved switch cannot turn glass back on: "+kind.getSimpleName(),ThemeCatalog.glass(app));assertFalse(((Switch)resumed.get().findViewById(R.id.appearanceGlassSwitch)).isChecked());
    }
   }
   float savedFont=RuntimeEnvironment.getFontScale();try{RuntimeEnvironment.setFontScale(2);for(boolean enabled:new boolean[]{false,true}){ThemeCatalog.glass(app,enabled);try(var large=Robolectric.buildActivity(AppearanceActivity.class).setup()){for(int[] size:new int[][]{{320,568},{411,890},{1280,800}}){layout(root(large.get()),size[0],size[1]);checkControls(root(large.get()));}}}}finally{RuntimeEnvironment.setFontScale(savedFont);}
  }
 }
 @Test public void glassRibbonsAndTabsFitAllThemesBothRolesAndTabletOrientations()throws Exception{
  for(int theme=0;theme<ThemeCatalog.IDS.length;theme++)for(int dpi:new int[]{160,320})for(float font:new float[]{1,2}){
   ThemeCatalog.select(app,theme);ThemeCatalog.glass(app,true);ThemeCatalog.lightOutline(app,theme%2==0);Configuration conf=new Configuration(app.getResources().getConfiguration());conf.densityDpi=dpi;conf.fontScale=font;Context c=new ContextThemeWrapper(app.createConfigurationContext(conf),ThemeCatalog.style(app));ThemeCatalog.applyOverlays(c);float d=c.getResources().getDisplayMetrics().density;
   View root=LayoutInflater.from(c).inflate(R.layout.activity_main,null);DashboardLayout client=root.findViewById(R.id.dashboardScroll);ServerHomeLayout server=root.findViewById(R.id.serverHome);
   try(var screen=Robolectric.buildActivity(Activity.class).setup()){
    screen.get().setContentView(root);IconButtons.decorate(root);ThemeCatalog.surfaces(root);
    EmbeddedServerService.running=true;TransferForegroundService.state=new TransferForegroundService.State(true,false,"Передача","",68,100,1);
    ServerStats stats=new ServerStats();new ServerTelemetry(35,137L*1073741824,256L*1073741824,1080,1).apply(stats);stats.authenticated=true;stats.hostKeyTrusted=true;client.metrics(stats);((TextView)root.findViewById(R.id.diskValue)).setText("137 ГБ");((TextView)root.findViewById(R.id.batteryValue)).setText("35%");((TextView)root.findViewById(R.id.ipValue)).setText("192.168.1.52");((TextView)root.findViewById(R.id.statusText)).setText("● Сервер работает");((TextView)root.findViewById(R.id.statusText)).setTextColor(ThemeCatalog.color(c,com.bitpoint.homeservercontrol.ui.R.attr.hscSuccess));
    ((TextView)root.findViewById(R.id.currentWifiIpValue)).setText("192.168.1.151");((TextView)root.findViewById(R.id.localServerSummary)).setText("Сервер работает");((TextView)root.findViewById(R.id.serverPanelBattery)).setText("60%");((BatteryGaugeDrawable)ReflectionHelpers.getField(server,"gauge")).level(60);((TextView)root.findViewById(R.id.serverPanelSpace)).setText("129.5 ГБ");((TextView)root.findViewWithTag("charging")).setText("От батареи");((TextView)root.findViewWithTag("disk_detail")).setText("свободно из 453.9 ГБ");StatusRibbon own=ReflectionHelpers.getField(server,"timing");own.first.setText("Работает 15 д 11 ч");own.online(0);
    java.util.List<ServerDeviceRow> devices=ReflectionHelpers.getField(server,"deviceRows");ReflectionHelpers.setField(server,"allowedCount",2);devices.get(0).bind("Устройство 1",false);devices.get(1).bind("Устройство 2",false);((TextView)root.findViewById(R.id.serverPanelDeviceCount)).setText("2 из 7");
    client.transfers("Видео_отпуск.mp4","68% · 1,36 из 2 ГБ",68,"✓ Фото.zip · 812 МБ · Отправлено\n! Документы.pdf · 16 МБ · Ошибка\n× Архив.zip · 1,2 ГБ · Отменено");client.transferTiming(18.4f,Math.round(1.36*1073741824),2L*1073741824);
    ((TextView)root.findViewById(R.id.uptimeValue)).setText("Работает 1 д 1 ч");((StatusRibbon)ReflectionHelpers.getField(client,"info")).second.setText("Обновлено 17 с назад");((Button)root.findViewById(R.id.serverPowerButton)).setText("Остановить");
    ((TextView)root.findViewById(R.id.dashboardSpeedSummary)).setText("18,4 МБ/с");((TextView)root.findViewById(R.id.dashboardLatencySummary)).setText("103 мс");((MetricChartView)root.findViewById(R.id.dashboardSpeedChart)).setValues(Arrays.asList(6f,9f,8f,14f,10f,13f,11f,17f,12f,15f,20f,14f,18.4f),0,30);((MetricChartView)root.findViewById(R.id.dashboardLatencyChart)).setValues(Arrays.asList(63f,101f,77f,99f,66f,84f,260f,83f,103f),0,300);
    for(int[] size:new int[][]{{320,568},{411,890},{890,411},{1280,800},{800,1280}})for(boolean host:new boolean[]{false,true}){
     client.setVisibility(host?View.GONE:View.VISIBLE);server.setVisibility(host?View.VISIBLE:View.GONE);root.findViewById(R.id.homeClientTab).setVisibility(View.VISIBLE);root.findViewById(R.id.homeServerTab).setBackgroundTintList(android.content.res.ColorStateList.valueOf(ThemeCatalog.color(c,host?com.bitpoint.homeservercontrol.ui.R.attr.hscButton:com.bitpoint.homeservercontrol.ui.R.attr.hscSecondaryButton)));root.findViewById(R.id.homeClientTab).setBackgroundTintList(android.content.res.ColorStateList.valueOf(ThemeCatalog.color(c,host?com.bitpoint.homeservercontrol.ui.R.attr.hscSecondaryButton:com.bitpoint.homeservercontrol.ui.R.attr.hscButton)));ViewGroup home=host?server:client;StatusRibbon ribbon=host?own:(StatusRibbon)ReflectionHelpers.getField(client,"info");layout(root,Math.round(size[0]*d),Math.round(size[1]*d));
     assertEquals("Glass preserves the selected light background",theme==4?0xFFF3F4F6:Color.BLACK,((ColorDrawable)root.findViewById(R.id.homeShell).getBackground()).getColor());assertEquals("Extra separation between the role tabs",Math.round(6*d),root.findViewById(R.id.homeClientTab).getLeft()-root.findViewById(R.id.homeServerTab).getRight());
     assertTrue("Thin ribbon height",ribbon.getHeight()>=24*d&&ribbon.getHeight()<=32*d);View before=host?ReflectionHelpers.getField(server,"access"):root.findViewById(R.id.dashboardCharts);View after=host?ReflectionHelpers.getField(server,"devices"):ReflectionHelpers.getField(client,"transfer");assertTrue("Approved order below QR/graphs",ribbon.getTop()>=before.getBottom());if(after.isShown()&&(!host||!ReflectionHelpers.<Boolean>getField(server,"tight")))assertTrue("Ribbon precedes devices/transfer",ribbon.getBottom()<=after.getTop());
     assertInside(ribbon,home);assertRibbon(ribbon,d);int[] colors=((GradientDrawable)ribbon.getBackground()).getColors();assertNotNull("Glass has a translucent gradient",colors);assertTrue(Color.alpha(colors[1])<255);assertTrue(Color.alpha(colors[1])>0);
     for(int id:host?new int[]{R.id.serverPowerButton,R.id.serverQrToggle,R.id.serverLocalFilesButton,R.id.serverReceiveButton}:new int[]{R.id.checkButton,R.id.filesButton,R.id.transferQueueButton,R.id.toolsMenuButton}){View v=root.findViewById(id);Rect r=bounds((ViewGroup)root,v);assertTrue("Full visible touch area "+id+" at "+Arrays.toString(size)+" "+r,r.height()>=48*d&&r.width()>=48*d&&r.top>=0&&r.left>=0&&r.bottom<=root.getHeight()&&r.right<=root.getWidth());}
     if(dpi==160&&font==1&&(theme==0||theme==5)&&(size[0]==411||size[0]==1280||size[0]==800))capture(root,"theme-"+theme+"-"+(host?"server":"client")+"-"+size[0]+"x"+size[1],root.getWidth(),root.getHeight());
    }
   }finally{server.close();}
  }
 }
 @Test public void trustRibbonNeverTurnsGreenFromTelemetryAlone(){
  Context c=new ContextThemeWrapper(app,com.bitpoint.homeservercontrol.ui.R.style.Theme_LocalLS_Ocean);StatusRibbon ribbon=new StatusRibbon(c,new TextView(c),new TextView(c),false);
  for(boolean authenticated:new boolean[]{false,true})for(boolean trusted:new boolean[]{false,true})for(boolean mismatch:new boolean[]{false,true})for(boolean needsTrust:new boolean[]{false,true}){
   ServerStats stats=new ServerStats();stats.telemetryAvailable=true;stats.authenticated=authenticated;stats.hostKeyTrusted=trusted;stats.hostKeyMismatch=mismatch;stats.hostKeyNeedsTrust=needsTrust;ribbon.trust(stats);boolean expected=authenticated&&trusted&&!mismatch&&!needsTrust;
   assertEquals(expected,ribbon.third.getText().toString().equals("Ключ проверен"));assertEquals(ThemeCatalog.color(c,mismatch?com.bitpoint.homeservercontrol.ui.R.attr.hscError:expected?com.bitpoint.homeservercontrol.ui.R.attr.hscSuccess:com.bitpoint.homeservercontrol.ui.R.attr.hscTextSecondary),ribbon.third.getCurrentTextColor());
  }
 }
 @Test public void serverOnlyPhoneCanReachTheSameAppearanceControls(){
  app.getSharedPreferences(EmbeddedServerService.PREFS,0).edit().putBoolean("server_role",true).putBoolean("show_client",false).apply();var before=app.getSharedPreferences(EmbeddedServerService.PREFS,0).getAll();
  try(var screen=Robolectric.buildActivity(EmbeddedServerActivity.class).setup()){
   Button button=find(root(screen.get()),"Оформление");assertNotNull(button);button.performClick();Intent intent=Shadows.shadowOf(screen.get()).getNextStartedActivity();assertNotNull(intent);assertEquals(AppearanceActivity.class.getName(),intent.getComponent().getClassName());assertEquals(before,app.getSharedPreferences(EmbeddedServerService.PREFS,0).getAll());
  }
 }
 private void checkControls(View v){if(v.getVisibility()!=View.VISIBLE)return;if(v instanceof Button||v instanceof Switch){TextView label=(TextView)v;assertTrue("Appearance touch target",v.getHeight()>=48);assertNotNull(label.getLayout());assertTrue("Appearance caption fits",label.getLayout().getHeight()<=v.getHeight()-label.getCompoundPaddingTop()-label.getCompoundPaddingBottom());for(int i=0;i<label.getLayout().getLineCount();i++)assertEquals("No truncated appearance captions",0,label.getLayout().getEllipsisCount(i));}if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)checkControls(((ViewGroup)v).getChildAt(i));}
 private Button find(View v,String text){if(v instanceof Button&&text.contentEquals(((Button)v).getText()))return (Button)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){Button b=find(((ViewGroup)v).getChildAt(i),text);if(b!=null)return b;}return null;}
 private void assertRibbon(StatusRibbon ribbon,float d){for(int i=0;i<ribbon.getChildCount();i++){ViewGroup group=(ViewGroup)ribbon.getChildAt(i);View icon=group.getChildAt(0);TextView text=(TextView)group.getChildAt(1);assertTrue("Icon and text never intersect",icon.getRight()<text.getLeft());assertEquals("Centered icon",group.getHeight()/2f,(icon.getTop()+icon.getBottom())/2f,1);assertEquals("Group is centered together",group.getWidth()/2f,(icon.getLeft()+text.getRight())/2f,1);assertTrue(text.getHeight()>=text.getLineHeight());assertEquals("Complete ribbon value "+text.getText(),0,text.getLayout().getEllipsisCount(0));}}
 private Rect bounds(ViewGroup root,View v){Rect r=new Rect();v.getDrawingRect(r);root.offsetDescendantRectToMyCoords(v,r);return r;}
 private void assertInside(View v,ViewGroup root){if(v.getVisibility()!=View.VISIBLE)return;Rect r=bounds(root,v);assertTrue("Ribbon within viewport "+r,r.top>=0&&r.left>=0&&r.bottom<=root.getHeight()&&r.right<=root.getWidth());if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)assertInside(((ViewGroup)v).getChildAt(i),root);}
 private View root(Activity a){return ((ViewGroup)a.findViewById(android.R.id.content)).getChildAt(0);}
 private void force(View v){v.forceLayout();if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)force(((ViewGroup)v).getChildAt(i));}
 private void layout(View root,int w,int h){force(root);root.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));root.layout(0,0,w,h);}
 private void capture(View root,String name,int w,int h)throws Exception{layout(root,w,h);Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);root.draw(new Canvas(b));File folder=new File("build/ui-previews/glass-1.26-20261026");folder.mkdirs();try(var stream=new FileOutputStream(new File(folder,name+".png"))){assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,stream));}finally{b.recycle();}}
}
