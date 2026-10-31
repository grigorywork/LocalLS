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

@RunWith(RobolectricTestRunner.class)
@Config(sdk=30,qualifiers="ru-rRU-w411dp-h890dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class MetricsOptionThreeTest {
 private String screen="";
 @Test public void approvedMetricsAndTransferStatesHaveNoOverlapsOrClippedLines()throws Exception{
  var previous=TransferForegroundService.state;
  try{
   for(int theme=0;theme<ThemeCatalog.IDS.length;theme++)for(int dpi:new int[]{160,320})for(float font:new float[]{1,2}){
    Context app=RuntimeEnvironment.getApplication();ThemeCatalog.select(app,theme);Configuration conf=new Configuration(app.getResources().getConfiguration());conf.densityDpi=dpi;conf.fontScale=font;Context c=new ContextThemeWrapper(app.createConfigurationContext(conf),ThemeCatalog.style(app));float d=c.getResources().getDisplayMetrics().density;
    View root=LayoutInflater.from(c).inflate(R.layout.activity_main,null);ServerHomeLayout server=root.findViewById(R.id.serverHome);DashboardLayout home=root.findViewById(R.id.dashboardScroll);
    try(var screen=Robolectric.buildActivity(Activity.class).setup()){
     screen.get().setContentView(root);server.setVisibility(View.GONE);home.setVisibility(View.VISIBLE);IconButtons.decorate(root);
     for(int[] size:new int[][]{{320,568},{360,728},{411,890},{890,411},{1280,800},{800,1280}}){
      Rect metricsBefore=null;
      for(int state=0;state<7;state++){
       this.screen="theme="+theme+" dpi="+dpi+" font="+font+" size="+Arrays.toString(size)+" state="+state;
       ((TextView)root.findViewById(R.id.statusText)).setText(state==3?"Показатели недоступны":"● Сервер работает");((TextView)root.findViewById(R.id.ipValue)).setText("192.168.1.151");((TextView)root.findViewById(R.id.uptimeValue)).setText("Работает 12 д 12 ч");root.findViewById(R.id.homeClientTab).setBackgroundTintList(android.content.res.ColorStateList.valueOf(ThemeCatalog.color(c,com.bitpoint.homeservercontrol.ui.R.attr.hscButton)));root.findViewById(R.id.homeServerTab).setBackgroundTintList(android.content.res.ColorStateList.valueOf(ThemeCatalog.color(c,com.bitpoint.homeservercontrol.ui.R.attr.hscSecondaryButton)));
       boolean active=state==0||state==1||state==4||state==6,unknown=state==3;((TextView)root.findViewById(R.id.statusText)).setTextColor(ThemeCatalog.color(c,unknown?com.bitpoint.homeservercontrol.ui.R.attr.hscTextSecondary:com.bitpoint.homeservercontrol.ui.R.attr.hscSuccess));
       ((TextView)root.findViewById(R.id.dashboardSpeedSummary)).setText(state==1?"0 МБ/с":"18,4 МБ/с");((TextView)root.findViewById(R.id.dashboardLatencySummary)).setText("103 мс");
       ((MetricChartView)root.findViewById(R.id.dashboardSpeedChart)).setValues(Arrays.asList(6f,9f,8f,14f,10f,13f,11f,17f,12f,15f,20f,14f,18.4f),0,30);((MetricChartView)root.findViewById(R.id.dashboardLatencyChart)).setValues(Arrays.asList(63f,101f,77f,99f,66f,84f,260f,83f,103f),0,300);TransferForegroundService.state=new TransferForegroundService.State(active,state==2,"Состояние","",68,100,1);
       ServerStats stats=new ServerStats();if(!unknown)new ServerTelemetry(state==4?100:35,137L*1073741824,256L*1073741824,1080,state==4?0:1).apply(stats);
       ((TextView)root.findViewById(R.id.diskValue)).setText(unknown?"—":"137 ГБ");((TextView)root.findViewById(R.id.batteryValue)).setText(unknown?"—":stats.battery);if(unknown)home.resetMetrics();else home.metrics(stats);
       home.transfers(active?"Видео_отпуск_очень_длинное_название.mp4":state==2?"Ошибка передачи":"Передача не выполняется",active?"1,36 из 2 ГБ":state==2?"Сервер недоступен. Открой очередь для повтора":"Передач пока нет",state==4?100:state==6?0:active?68:0,"✓ Фото.zip · 812 МБ · Отправлено\n! Документы.pdf · 16 МБ · Ошибка\n× Архив.zip · 1,2 ГБ · Отменено");
       home.transferTiming(state==1?0:18.4f,state==4?2L*1073741824:state==6?0:Math.round(1.36*1073741824),state==6?0:2L*1073741824);layout(root,Math.round(size[0]*d),Math.round(size[1]*d));
       if(theme==0&&dpi==160&&font==1&&state==0&&(size[0]==320||size[0]==890))capture(root,"debug-"+size[0]+"x"+size[1]);
       ViewGroup metrics=root.findViewById(R.id.dashboardMetrics);Rect mb=bounds(home,metrics);if(metricsBefore==null)metricsBefore=mb;else assertEquals("Telemetry changes never shift card bounds",metricsBefore,mb);
       for(int i=0;i<metrics.getChildCount();i++)assertDisjoint((ViewGroup)metrics.getChildAt(i));
       for(String field:new String[]{"transfer","recent"}){ViewGroup card=ReflectionHelpers.getField(home,field);if(card.isShown())assertContained(card,home);}
       TextView eta=ReflectionHelpers.getField(home,"transferEta"),rate=ReflectionHelpers.getField(home,"transferRate"),amount=ReflectionHelpers.getField(home,"progressText"),current=ReflectionHelpers.getField(home,"current");
       if(((View)eta.getParent()).isShown()){
        assertEquals(active?View.VISIBLE:View.INVISIBLE,eta.getVisibility());if(active){ViewGroup row=(ViewGroup)eta.getParent();assertEquals("Clock and ETA are centered together",row.getWidth()/2f,(eta.getLeft()+eta.getRight())/2f,1);assertTrue("Full ETA line fits vertically",eta.getHeight()>=eta.getLineHeight());assertEquals("ETA is not ellipsized",0,eta.getLayout().getEllipsisCount(0));if(state==1||state==6)assertEquals("Рассчитываем время…",eta.getText().toString());else if(state==4)assertEquals("Завершаем передачу…",eta.getText().toString());else assertEquals("Осталось ≈ 36 с",eta.getText().toString());}
        assertTrue("Amount and rate are separate",amount.getRight()<=rate.getLeft());assertTrue("Filename remains readable",current.getHeight()>=current.getLineHeight());
        for(TextView label:ReflectionHelpers.<java.util.List<TextView>>getField(home,"recentNames"))assertTrue("Recent filenames keep a complete line",label.getHeight()>=label.getLineHeight());
       }
       if(theme==1&&dpi==160&&font==1&&(size[0]==411||size[0]==1280||size[0]==800))capture(root,size[0]+"x"+size[1]+"-state-"+state);
      }
     }
    }finally{server.close();}
   }
  }finally{TransferForegroundService.state=previous;}
 }
 @Test public void estimateNeverInventsAZeroOrInfiniteCountdown(){
  assertEquals(-1,DashboardLayout.estimateSeconds(0,100,0));assertEquals(-1,DashboardLayout.estimateSeconds(0,100,Float.NaN));assertEquals(-1,DashboardLayout.estimateSeconds(0,100,Float.POSITIVE_INFINITY));assertEquals(-1,DashboardLayout.estimateSeconds(-1,100,1));assertEquals(-1,DashboardLayout.estimateSeconds(100,100,1));assertEquals(-1,DashboardLayout.estimateSeconds(100,0,1));assertEquals(1,DashboardLayout.estimateSeconds(99,100,1));assertEquals(36,DashboardLayout.estimateSeconds(Math.round(1.36*1073741824),2L*1073741824,18.4f));
  assertEquals("Осталось ≈ 1 с",DashboardLayout.formatEta(1));assertEquals("Осталось ≈ 2 мин 15 с",DashboardLayout.formatEta(135));assertEquals("Осталось ≈ 2 ч 15 мин",DashboardLayout.formatEta(8100));
 }
 private void assertDisjoint(ViewGroup card){List<Rect> rects=new ArrayList<>();for(int i=0;i<card.getChildCount();i++){View v=card.getChildAt(i);if(v.getVisibility()!=View.VISIBLE)continue;Rect r=new Rect(v.getLeft(),v.getTop(),v.getRight(),v.getBottom());assertTrue("Metric content stays in card "+v+" "+r,r.top>=card.getPaddingTop()&&r.bottom<=card.getHeight()-card.getPaddingBottom()&&r.left>=card.getPaddingLeft()&&r.right<=card.getWidth()-card.getPaddingRight());for(Rect other:rects)assertFalse("No metric content overlap",Rect.intersects(r,other));rects.add(r);}}
 private Rect bounds(ViewGroup root,View child){Rect r=new Rect();child.getDrawingRect(r);root.offsetDescendantRectToMyCoords(child,r);return r;}
 private void assertContained(View v,ViewGroup card){if(v.getVisibility()!=View.VISIBLE)return;Rect r=bounds(card,v);assertTrue("Transfer/recent content stays in viewport "+v+" "+r,r.left>=0&&r.top>=0&&r.right<=card.getWidth()&&r.bottom<=card.getHeight());if(v instanceof TextView){TextView t=(TextView)v;assertTrue("One complete line "+screen+": "+t.getText()+" "+t.getHeight()+"/"+t.getLineHeight(),t.getHeight()>=t.getLineHeight()+t.getPaddingTop()+t.getPaddingBottom());}if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)assertContained(((ViewGroup)v).getChildAt(i),card);}
 private void force(View v){v.forceLayout();if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)force(((ViewGroup)v).getChildAt(i));}
 private void layout(View root,int w,int h){force(root);root.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));root.layout(0,0,w,h);}
 private void capture(View root,String name)throws Exception{File folder=new File("build/ui-previews/metrics-option3-1.25-20261026");folder.mkdirs();Bitmap b=Bitmap.createBitmap(root.getWidth(),root.getHeight(),Bitmap.Config.ARGB_8888);root.draw(new Canvas(b));try(var out=new FileOutputStream(new File(folder,name+".png"))){assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,out));}finally{b.recycle();}}
}
