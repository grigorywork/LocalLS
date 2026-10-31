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
public class RenderingEdgesTest {
 private Context context(int theme,int density,float font){Context app=RuntimeEnvironment.getApplication();ThemeCatalog.select(app,theme);Configuration conf=new Configuration(app.getResources().getConfiguration());conf.densityDpi=density;conf.fontScale=font;return new ContextThemeWrapper(app.createConfigurationContext(conf),ThemeCatalog.style(app));}
 @Test public void overflowGlyphIsFullyVisibleAndKeepsItsTouchAreaWithLargeFonts(){
  for(int theme=0;theme<ThemeCatalog.IDS.length;theme++)for(float font:new float[]{1,2})for(int dpi:new int[]{160,320}){
   Context c=context(theme,dpi,font);float d=c.getResources().getDisplayMetrics().density;FileEntryRow row=new FileEntryRow(c);row.bind(new FileNodes.Node("share","photo.jpg","Фото.jpg",false,123),false,v->fail("Rendering cannot change selection"));layout(row,Math.round(360*d),0,false);Button more=(Button)row.getChildAt(row.getChildCount()-1);
   assertTrue("Icon touch target",more.getHeight()>=48*d);assertTrue("Overflow icon line is not clipped theme="+theme+" font="+font+" line="+more.getLayout().getHeight()+" content="+(more.getHeight()-more.getCompoundPaddingTop()-more.getCompoundPaddingBottom()),more.getLayout().getHeight()<=more.getHeight()-more.getCompoundPaddingTop()-more.getCompoundPaddingBottom());assertEquals("Действия с файлом",more.getContentDescription());
  }
 }
 @Test public void ordinaryTelemetryLossDoesNotMoveDashboardCards()throws Exception{
  for(int theme:new int[]{0,1,4})for(float font:new float[]{1,2})for(int[] size:new int[][]{{411,890},{1280,800}}){
   Context c=context(theme,160,font);View root=LayoutInflater.from(c).inflate(R.layout.activity_main,null);ServerHomeLayout server=root.findViewById(R.id.serverHome);DashboardLayout home=root.findViewById(R.id.dashboardScroll);
   try(var screen=Robolectric.buildActivity(Activity.class).setup()){
    screen.get().setContentView(root);server.setVisibility(View.GONE);home.setVisibility(View.VISIBLE);IconButtons.decorate(root);ServerStats stats=new ServerStats();new ServerTelemetry(39,Math.round(73.3*1073741824d),Math.round(225.8*1073741824d),1080,1).apply(stats);
    ((TextView)root.findViewById(R.id.diskValue)).setText(SurfaceStyle.quantity(stats.freeSpace));((TextView)root.findViewById(R.id.batteryValue)).setText(stats.battery);home.metrics(stats);layout(root,size[0],size[1],true);Map<View,Rect> before=new LinkedHashMap<>();
    for(View v:new View[]{ReflectionHelpers.getField(home,"storage"),ReflectionHelpers.getField(home,"battery"),root.findViewById(R.id.dashboardCharts),ReflectionHelpers.getField(home,"transfer"),ReflectionHelpers.getField(home,"recent")})before.put(v,new Rect(v.getLeft(),v.getTop(),v.getRight(),v.getBottom()));
    ((TextView)root.findViewById(R.id.diskValue)).setText("—");((TextView)root.findViewById(R.id.batteryValue)).setText("—");home.resetMetrics();TextView charging=ReflectionHelpers.getField(home,"charge");assertEquals("Unavailable battery must not retain charging green",ThemeCatalog.color(c,com.bitpoint.homeservercontrol.ui.R.attr.hscTextSecondary),charging.getCurrentTextColor());layout(root,size[0],size[1],true);
    for(var e:before.entrySet()){View v=e.getKey();assertEquals("Card remains stable after telemetry loss font="+font+" size="+Arrays.toString(size),e.getValue(),new Rect(v.getLeft(),v.getTop(),v.getRight(),v.getBottom()));}
    if(theme==1&&font==1)capture(root,"unknown-"+size[0]+"x"+size[1]);
   }finally{server.close();}
  }
 }
 @Test public void chartLabelsRemainInsideCanvasAndEmptyTextIsCentered()throws Exception{
  for(float font:new float[]{1,2})for(int dpi:new int[]{160,320})for(int[] size:new int[][]{{160,120},{220,160},{400,240}}){
   Context c=context(1,dpi,font);float d=c.getResources().getDisplayMetrics().density;MetricChartView chart=new MetricChartView(c);chart.setLabels("мс","20 замеров");int w=Math.round(size[0]*d),h=Math.round(size[1]*d);layout(chart,w,h,true);
   for(boolean empty:new boolean[]{false,true}){
    chart.setValues(empty?Collections.emptyList():Arrays.asList(1000f,5000f,9000f),empty?Float.NaN:0,empty?Float.NaN:10000);Bitmap bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);RecordedCanvas canvas=new RecordedCanvas(bitmap);chart.draw(canvas);
    assertTrue("Empty-state label is visible before the first sample",!empty||canvas.labels.stream().anyMatch(l->l.text.equals("Нет замеров")));for(int i=0;i<canvas.labels.size();i++)for(int j=0;j<i;j++)assertFalse("Chart labels do not overlap",RectF.intersects(canvas.labels.get(i).bounds,canvas.labels.get(j).bounds));
    for(Label label:canvas.labels){assertTrue("Chart text is not clipped: "+label.text+" "+label.bounds+" canvas="+w+"x"+h,label.bounds.left>=-.5f&&label.bounds.right<=w+.5f&&label.bounds.top>=-.5f&&label.bounds.bottom<=h+.5f);if(label.text.equals("Нет замеров"))assertEquals("Empty-state text is visually centered in the plot",(canvas.firstGridY+canvas.lastGridY)/2,label.metricCenter,1*d);}
    if(dpi==160&&font==1&&size[0]==400){File folder=new File("build/ui-previews/render-1.22-20261026");folder.mkdirs();try(var output=new FileOutputStream(new File(folder,empty?"chart-empty.png":"chart-delay.png"))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,output));}}bitmap.recycle();
   }
  }
 }
 private static class Label{String text;RectF bounds;float metricCenter;Label(String s,float x,float y,Paint p){text=s;Rect r=new Rect();p.getTextBounds(s,0,s.length(),r);bounds=new RectF(x+r.left,y+r.top,x+r.right,y+r.bottom);Paint.FontMetrics fm=p.getFontMetrics();metricCenter=y+(fm.ascent+fm.descent)/2;}}
 private static class RecordedCanvas extends Canvas{List<Label> labels=new ArrayList<>();float firstGridY=Float.NaN,lastGridY;RecordedCanvas(Bitmap b){super(b);}@Override public void drawText(String s,float x,float y,Paint p){labels.add(new Label(s,x,y,p));super.drawText(s,x,y,p);}@Override public void drawLine(float x1,float y1,float x2,float y2,Paint p){if(Float.isNaN(firstGridY))firstGridY=y1;lastGridY=y1;super.drawLine(x1,y1,x2,y2,p);}}
 private void force(View v){v.forceLayout();if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)force(((ViewGroup)v).getChildAt(i));}
 private void layout(View v,int w,int h,boolean exactHeight){force(v);v.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,exactHeight?View.MeasureSpec.EXACTLY:View.MeasureSpec.UNSPECIFIED));v.layout(0,0,v.getMeasuredWidth(),v.getMeasuredHeight());}
 private void capture(View root,String name)throws Exception{Bitmap b=Bitmap.createBitmap(root.getWidth(),root.getHeight(),Bitmap.Config.ARGB_8888);root.draw(new Canvas(b));File folder=new File("build/ui-previews/render-1.22-20261026");folder.mkdirs();try(var out=new FileOutputStream(new File(folder,name+".png"))){assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,out));}finally{b.recycle();}}
}
