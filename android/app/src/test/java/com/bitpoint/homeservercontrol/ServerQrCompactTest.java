package com.bitpoint.homeservercontrol;

import android.app.Activity;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=30,qualifiers="ru-rRU-w411dp-h890dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ServerQrCompactTest {
 @Test public void probeAcceptsOnlySshAndHasAbsoluteDeadline()throws Exception{
  assertTrue(Float.isNaN(ServerHealthProbe.read(0)));assertTrue(Float.isNaN(ServerHealthProbe.read(65536)));
  for(String banner:new String[]{"SSH-2.0-test\r\n","HTTP/1.1 200 OK\r\n","SSH-1.99-test\r\n"}){
   try(ServerSocket listener=new ServerSocket(0,1,InetAddress.getLoopbackAddress())){ExecutorService worker=Executors.newSingleThreadExecutor();Future<?> sent=worker.submit(()->{try(Socket s=listener.accept()){s.getOutputStream().write(banner.getBytes(java.nio.charset.StandardCharsets.US_ASCII));}catch(IOException e){throw new RuntimeException(e);}});try{float sample=ServerHealthProbe.read(listener.getLocalPort());assertEquals(banner.startsWith("SSH"),Float.isFinite(sample));sent.get(2,TimeUnit.SECONDS);}finally{worker.shutdownNow();assertTrue(worker.awaitTermination(2,TimeUnit.SECONDS));}}
  }
  try(ServerSocket listener=new ServerSocket(0,1,InetAddress.getLoopbackAddress())){ExecutorService worker=Executors.newSingleThreadExecutor();worker.submit(()->{try(Socket s=listener.accept()){for(int i=0;i<20;i++){s.getOutputStream().write('S');s.getOutputStream().flush();Thread.sleep(100);}}catch(Exception ignored){}});try{long start=System.nanoTime();assertTrue(Float.isNaN(ServerHealthProbe.read(listener.getLocalPort())));assertTrue("Slow bytes cannot extend the absolute deadline",TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start)<1300);}finally{worker.shutdownNow();assertTrue(worker.awaitTermination(2,TimeUnit.SECONDS));}}
 }
 @Test public void healthShowsMissingSamplesStopsAndForegroundGapsHonestly(){
  ServerHealthPanel panel=new ServerHealthPanel(new ContextThemeWrapper(RuntimeEnvironment.getApplication(),ThemeCatalog.style(RuntimeEnvironment.getApplication())));panel.sample(1000,24,true);assertEquals("Собираем замеры",panel.state.getText());panel.sample(6000,26,true);assertEquals("Без сбоев в окне",panel.state.getText());panel.sample(11000,Float.NaN,true);assertEquals("Нет ответа",panel.state.getText());assertEquals("—",panel.value.getText());panel.sample(16000,25,true);assertNotEquals("Do not erase recent failure", "Без сбоев в окне",panel.state.getText());panel.sample(40000,23,true);assertEquals("No continuous history after background gap","Собираем замеры",panel.state.getText());panel.sample(45000,22,false);assertEquals("Сервер выключен",panel.state.getText());assertEquals("—",panel.value.getText());
 }
 @Test public void compactDevicesLargeQrAndStripFitStatesAndThemes()throws Exception{
  Context app=RuntimeEnvironment.getApplication();
  for(int theme=0;theme<8;theme++)for(int dpi:new int[]{160,320})for(float font:new float[]{1,2}){
   ThemeCatalog.select(app,theme);Configuration cfg=new Configuration(app.getResources().getConfiguration());cfg.densityDpi=dpi;cfg.fontScale=font;Context c=new ContextThemeWrapper(app.createConfigurationContext(cfg),ThemeCatalog.style(app));float d=c.getResources().getDisplayMetrics().density;
   View root=LayoutInflater.from(c).inflate(R.layout.activity_main,null);ServerHomeLayout home=root.findViewById(R.id.serverHome);
   try(var owner=Robolectric.buildActivity(Activity.class).setup()){
    owner.get().setContentView(root);root.findViewById(R.id.dashboardScroll).setVisibility(View.GONE);home.setVisibility(View.VISIBLE);IconButtons.decorate(root);
    ServerHealthPanel panel=ReflectionHelpers.getField(home,"health");for(int i=0;i<13;i++)panel.sample(1000+5000*i,20+(i%4)*3,true);
    ((TextView)root.findViewById(R.id.currentWifiIpValue)).setText("192.168.1.151");((TextView)root.findViewById(R.id.localServerSummary)).setText("Сервер работает");((Button)root.findViewById(R.id.serverPowerButton)).setText("Остановить");((TextView)root.findViewById(R.id.serverPanelDeviceCount)).setText("2 из 7");((BatteryGaugeDrawable)ReflectionHelpers.getField(home,"gauge")).level(47);((TextView)root.findViewWithTag("charging")).setText("От батареи");((TextView)root.findViewWithTag("disk_detail")).setText("свободно из 453.9 ГБ");((StatusRibbon)ReflectionHelpers.getField(home,"timing")).first.setText("Работает 15 д 21 ч");((TextView)root.findViewById(R.id.serverPanelBattery)).setText("47%");((TextView)root.findViewById(R.id.serverPanelSpace)).setText("129.6 ГБ");
    List<ServerDeviceRow> rows=ReflectionHelpers.getField(home,"deviceRows");for(int i=0;i<3;i++)rows.get(i).bind("Устройство "+(i+1),i==0);
    for(int count:new int[]{0,1,2,3,7})for(int[] size:new int[][]{{320,568},{360,728},{411,890},{890,411},{1280,800},{800,1280}}){
     ReflectionHelpers.setField(home,"allowedCount",count);layout(root,Math.round(size[0]*d),Math.round(size[1]*d));inside(home,home);
     for(int id:new int[]{R.id.serverPowerButton,R.id.serverQrToggle,R.id.serverLocalFilesButton,R.id.serverReceiveButton,R.id.localServerQuickButton,R.id.serverBackgroundButton}){View action=root.findViewById(id);assertTrue("Primary target "+id+" at "+Arrays.toString(size)+" count="+count+" height="+action.getHeight(),action.getHeight()>=48*d);}TextView receive=root.findViewById(R.id.serverReceiveSummary);assertTrue("Receive folder caption retains its full line",receive.getHeight()>=receive.getLineHeight()+receive.getPaddingTop()+receive.getPaddingBottom());Button toggle=root.findViewById(R.id.serverQrToggle);assertTrue("48dp target",toggle.getHeight()>=48*d);assertTrue(toggle.getBackground() instanceof StripActionDrawable);assertTrue("QR caption fits",toggle.getLayout().getHeight()<=toggle.getHeight()-toggle.getCompoundPaddingTop()-toggle.getCompoundPaddingBottom());
     View qr=root.findViewById(R.id.serverQrButton);assertEquals("QR stays square",qr.getWidth(),qr.getHeight());
     View explanation=(View)panel.getParent();Rect square=new Rect(),column=new Rect();qr.getDrawingRect(square);home.offsetDescendantRectToMyCoords(qr,square);explanation.getDrawingRect(column);home.offsetDescendantRectToMyCoords(explanation,column);assertEquals("SSH column shares shield square top",square.top,column.top);assertEquals("SSH column shares shield square bottom",square.bottom,column.bottom);
     for(TextView label:new TextView[]{panel.state,panel.value,panel.scope})if(label.getVisibility()==View.VISIBLE){assertEquals("Text aligns with plot left",(int)Math.ceil(panel.chart.plotLeftInset()),label.getPaddingLeft());assertTrue("Labels precede chart",label.getBottom()<=panel.chart.getTop()||panel.chart.getVisibility()!=View.VISIBLE);}
     for(int child=0;child<panel.getChildCount();child++)if(panel.getChildAt(child) instanceof TextView)assertNotEquals("No redundant heading","Стабильность",((TextView)panel.getChildAt(child)).getText().toString());
     if(size[0]==411&&size[1]==890&&count==2){assertTrue("Larger readable QR",qr.getWidth()>=170*d);View list=ReflectionHelpers.getField(home,"devices");assertTrue("Two device rows have no large blank reserve",list.getHeight()<=132*d);for(int i=0;i<2;i++)assertEquals(Math.round(48*d),rows.get(i).getHeight());}
     if(theme==0&&dpi==160&&font==1&&count==2&&(size[0]==411||size[0]==1280||size[0]==800)){
      capture(root,size[0]+"x"+size[1]+"-hidden");ImageView image=(ImageView)qr;Bitmap demo=QrPairingActivity.codeBitmap("LocalLS layout preview - not a login credential. "+"DEMO ONLY ".repeat(30),qr.getWidth());image.setBackground(null);image.setPadding(0,0,0,0);image.setImageBitmap(demo);toggle.setText("Скрыть QR-код");IconButtons.decorate(toggle);layout(root,Math.round(size[0]*d),Math.round(size[1]*d));capture(root,size[0]+"x"+size[1]+"-qr");image.setImageResource(com.bitpoint.homeservercontrol.ui.R.drawable.ic_qr_shield);ReflectionHelpers.callInstanceMethod(home,"qrAppearance",ReflectionHelpers.ClassParameter.from(boolean.class,false));toggle.setText("Показать QR-код");IconButtons.decorate(toggle);demo.recycle();
     }
    }
   }finally{home.close();}
  }
 }
 private void inside(View v,ViewGroup home){if(v.getVisibility()!=View.VISIBLE)return;Rect r=new Rect();v.getDrawingRect(r);home.offsetDescendantRectToMyCoords(v,r);assertTrue("Child in bounds "+v+" "+r,r.top>=0&&r.left>=0&&r.bottom<=home.getHeight()&&r.right<=home.getWidth());if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)inside(((ViewGroup)v).getChildAt(i),home);}
 private void layout(View root,int w,int h){force(root);root.measure(View.MeasureSpec.makeMeasureSpec(w,1073741824),View.MeasureSpec.makeMeasureSpec(h,1073741824));root.layout(0,0,w,h);}
 private void force(View v){v.forceLayout();if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)force(((ViewGroup)v).getChildAt(i));}
 private void capture(View root,String n)throws Exception{Bitmap b=Bitmap.createBitmap(root.getWidth(),root.getHeight(),Bitmap.Config.ARGB_8888);root.draw(new Canvas(b));File dir=new File("build/ui-previews/qr-1.27-20261027");dir.mkdirs();try(var out=new FileOutputStream(new File(dir,n+".png"))){assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,out));}finally{b.recycle();}}
}
