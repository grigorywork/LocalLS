package com.bitpoint.homeservercontrol;

import android.app.Activity;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.*;
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

/** Tall tablet cards must not turn the one-line uptime into a large empty spacer. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=30,qualifiers="ru-rRU-w411dp-h890dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class TabletServerScaleTest {
 @Test public void tabletBothOrientationsKeepReadableTypeAndCompactUptime()throws Exception{
  for(int theme:new int[]{0,1,4})for(int density:new int[]{160,320})for(float font:new float[]{1,2})for(int[] size:new int[][]{{800,1280},{600,1024},{1280,800},{1024,600}}){
   Context app=RuntimeEnvironment.getApplication();Configuration conf=new Configuration(app.getResources().getConfiguration());conf.densityDpi=density;conf.fontScale=font;conf.screenWidthDp=size[0];conf.screenHeightDp=size[1];
   ThemeCatalog.select(app,theme);Context c=new ContextThemeWrapper(app.createConfigurationContext(conf),ThemeCatalog.style(app));float d=c.getResources().getDisplayMetrics().density;
   View root=LayoutInflater.from(c).inflate(R.layout.activity_main,null);ServerHomeLayout home=root.findViewById(R.id.serverHome);
   try(var screen=Robolectric.buildActivity(Activity.class).setup()){
    screen.get().setContentView(root);root.findViewById(R.id.dashboardScroll).setVisibility(View.GONE);home.setVisibility(View.VISIBLE);root.findViewById(R.id.homeClientTab).setVisibility(View.VISIBLE);IconButtons.decorate(root);
    java.util.List<ServerDeviceRow> rows=ReflectionHelpers.getField(home,"deviceRows");ReflectionHelpers.setField(home,"allowedCount",3);String[] names={"Телефон","Ноутбук","Планшет"};for(int i=0;i<3;i++)rows.get(i).bind(names[i],i<2);
    ((TextView)root.findViewById(R.id.currentWifiIpValue)).setText("192.168.1.52");((TextView)root.findViewById(R.id.localServerSummary)).setText("Сервер выключен");((TextView)root.findViewById(R.id.serverAccessSummary)).setText("Порт 8022 · localls · Доступ по ключу");((TextView)root.findViewById(R.id.serverPanelBattery)).setText("39%");((BatteryGaugeDrawable)ReflectionHelpers.getField(home,"gauge")).level(39);((TextView)root.findViewById(R.id.serverPanelSpace)).setText("73.5 ГБ");((TextView)root.findViewWithTag("disk_detail")).setText("свободно из 225.8 ГБ");((TextView)root.findViewById(R.id.serverReceiveSummary)).setText("Загрузки / LocalLS");((TextView)root.findViewById(R.id.serverPanelDeviceCount)).setText("3 из 7");((TextView)ReflectionHelpers.getField(home,"uptime")).setText("Работает 0 ч 18 мин");
    int w=Math.round(size[0]*d),h=Math.round(size[1]*d);layout(root,w,h);
    View uptime=ReflectionHelpers.getField(home,"uptime");assertTrue("Uptime must remain a single compact row at "+Arrays.toString(size),((View)uptime.getParent()).getHeight()<=Math.round(32*d));
    TextView status=root.findViewById(R.id.localServerSummary);assertTrue("Tablet text must grow with cards at "+Arrays.toString(size)+": "+status.getTextSize()/d,status.getTextSize()>=16.8f*d-.1f);
    assertEquals("Tablet metrics use the same text scale",status.getTextSize()/14,((TextView)root.findViewById(R.id.serverPanelBattery)).getTextSize()/26,.01f);
    for(int id:new int[]{R.id.serverPowerButton,R.id.serverQrToggle,R.id.serverLocalFilesButton,R.id.serverReceiveButton,R.id.localServerQuickButton}){View v=root.findViewById(id);assertTrue("48dp action "+id,v.getHeight()>=Math.round(48*d));}
    for(ServerDeviceRow row:rows){assertEquals(View.VISIBLE,row.getVisibility());assertTrue(row.getHeight()>=Math.round(48*d));}
    for(int id:new int[]{R.id.serverPanelBattery,R.id.serverPanelSpace,R.id.serverReceiveSummary,R.id.localServerSummary})assertFits(root.findViewById(id));assertFits(root.findViewWithTag("disk_detail"));assertFits(root.findViewWithTag("charging"));
    for(TextView label:new TextView[]{root.findViewById(R.id.serverPanelSpace),(TextView)uptime}){
     if(label==uptime){ViewGroup group=(ViewGroup)label.getParent();View glyph=group.getChildAt(0);assertTrue("Ribbon clock reserves a separate gap",glyph.getRight()<label.getLeft());assertEquals("Ribbon clock is vertically centered",group.getHeight()/2f,(glyph.getTop()+glyph.getBottom())/2f,1);}else{android.graphics.drawable.Drawable leading=label.getCompoundDrawablesRelative()[0];assertTrue("Scaled icon reserves its full width before text",label.getCompoundPaddingLeft()-label.getPaddingLeft()>=leading.getBounds().width()+label.getCompoundDrawablePadding());}
    }
    assertInside(home,home);
    View access=ReflectionHelpers.getField(home,"access");Rect qr=new Rect(access.getLeft(),access.getTop(),access.getRight(),access.getBottom());
    if(theme==1&&density==160&&font==1)capture(root,size[0]+"x"+size[1]);
    ((TextView)root.findViewById(R.id.serverPanelQrCaption)).setText("Сканируйте QR");((Button)root.findViewById(R.id.serverQrToggle)).setText("Скрыть QR");layout(root,w,h);assertEquals(qr,new Rect(access.getLeft(),access.getTop(),access.getRight(),access.getBottom()));
   }finally{home.close();}
  }
 }
 private void assertFits(View v){TextView t=(TextView)v;ViewGroup p=(ViewGroup)v.getParent();assertTrue("Text stays inside card: "+t.getText(),v.getTop()>=p.getPaddingTop()&&v.getBottom()<=p.getHeight()-p.getPaddingBottom());assertTrue("Text line fits: "+t.getText(),t.getHeight()>=t.getLineHeight()+t.getPaddingTop()+t.getPaddingBottom());}
 private void assertInside(View v,ViewGroup home){if(v.getVisibility()!=View.VISIBLE)return;Rect r=new Rect();v.getDrawingRect(r);home.offsetDescendantRectToMyCoords(v,r);assertTrue("Child inside server viewport: "+v+" "+r,r.top>=0&&r.left>=0&&r.bottom<=home.getHeight()&&r.right<=home.getWidth());if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)assertInside(((ViewGroup)v).getChildAt(i),home);}
 private void layout(View root,int w,int h){root.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));root.layout(0,0,w,h);}
 private void capture(View root,String name)throws Exception{File folder=new File("build/ui-previews/tablet-scale-1.24-20261026");folder.mkdirs();Bitmap b=Bitmap.createBitmap(root.getWidth(),root.getHeight(),Bitmap.Config.ARGB_8888);root.draw(new Canvas(b));try(var out=new FileOutputStream(new File(folder,name+".png"))){assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,out));}finally{b.recycle();}}
}
