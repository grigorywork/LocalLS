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

@RunWith(RobolectricTestRunner.class) @Config(sdk=30,qualifiers="ru-rRU-w411dp-h890dp-mdpi",shadows=RouteIdentityTest.Secrets.class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class CompactMetricsTest {
 @Before public void setup(){var app=RuntimeEnvironment.getApplication();app.getSharedPreferences(ServerConfig.PREFS,0).edit().clear().putInt(ServerConfig.KEY_CONFIG_VERSION,3).putString("keep_identity","protected").commit();app.getSharedPreferences(EmbeddedServerService.PREFS,0).edit().clear().commit();RouteIdentityTest.Secrets.store.clear();EmbeddedServerService.running=false;EmbeddedServerService.starting=false;EmbeddedServerService.launchRequested=0;ThemeCatalog.select(app,0);}
 @Test public void screenshotValuesHaveNoOverlappingOrClippedLinesAcrossViewportsThemesAndDensities()throws Exception{
  Context app=RuntimeEnvironment.getApplication();var preferences=app.getSharedPreferences(ServerConfig.PREFS,0).getAll();
  for(int theme=0;theme<ThemeCatalog.IDS.length;theme++)for(int dpi:new int[]{160,320})for(float font:new float[]{1,2}){
   ThemeCatalog.select(app,theme);Configuration conf=new Configuration(app.getResources().getConfiguration());conf.densityDpi=dpi;conf.fontScale=font;Context c=new ContextThemeWrapper(app.createConfigurationContext(conf),ThemeCatalog.style(app));float d=c.getResources().getDisplayMetrics().density;
   View root=LayoutInflater.from(c).inflate(R.layout.activity_main,null);ServerHomeLayout server=root.findViewById(R.id.serverHome);DashboardLayout home=root.findViewById(R.id.dashboardScroll);server.setVisibility(View.GONE);home.setVisibility(View.VISIBLE);
   try(var screen=Robolectric.buildActivity(Activity.class).setup()){
    screen.get().setContentView(root);IconButtons.decorate(root);ServerStats stats=new ServerStats();new ServerTelemetry(39,Math.round(73.3*1073741824d),Math.round(225.8*1073741824d),1080,0).apply(stats);((TextView)root.findViewById(R.id.batteryValue)).setText(stats.battery);((TextView)root.findViewById(R.id.diskValue)).setText(SurfaceStyle.quantity(stats.freeSpace));home.metrics(stats);
    CompactMetricCard storage=ReflectionHelpers.getField(home,"storage"),battery=ReflectionHelpers.getField(home,"battery");
    for(int[] size:new int[][]{{320,568},{360,640},{411,890},{890,411},{1280,800}}){
     layout(root,Math.round(size[0]*d),Math.round(size[1]*d));assertCard(storage);assertCard(battery);assertEquals(68,storage.indicator.getProgress());assertEquals(39,battery.indicator.getProgress());assertTrue(storage.footer.getText().toString().contains("152.5"));assertTrue(storage.footer.getText().toString().contains("68%"));assertEquals("От батареи",battery.detail.getText().toString());
     for(int id:new int[]{R.id.dashboardSpeedChart,R.id.dashboardLatencyChart,R.id.checkButton,R.id.filesButton,R.id.transferQueueButton,R.id.toolsMenuButton}){View view=root.findViewById(id);assertTrue("Existing primary content remains shown",view.isShown());Rect visible=new Rect();view.getDrawingRect(visible);((ViewGroup)root).offsetDescendantRectToMyCoords(view,visible);assertTrue("Content is fully inside the tested viewport id="+id+" size="+Arrays.toString(size)+" rect="+visible,visible.left>=0&&visible.top>=0&&visible.right<=root.getWidth()&&visible.bottom<=root.getHeight());assertTrue(view.getHeight()>0);}
     if(dpi==160&&font==1&&(theme==0||theme==1)&&size[0]!=320&&size[0]!=360){capture(root,"theme-"+theme+"-"+size[0]+"x"+size[1]);if(theme==0&&size[0]==411)capturePair(storage,battery);}
    }
   }finally{server.close();}
  }
  // Theme selection is the only explicit change made by this test, never the saved connection.
  var after=new HashMap<>(app.getSharedPreferences(ServerConfig.PREFS,0).getAll());var before=new HashMap<>(preferences);after.remove(ThemeCatalog.KEY);before.remove(ThemeCatalog.KEY);assertEquals(before,after);
 }
 @Test public void zeroFullUnknownAndLargeCapacityNeverInventTelemetryOrLoseTheCompleteNumber(){
  try(var screen=Robolectric.buildActivity(MainActivity.class).setup()){
   MainActivity a=screen.get();DashboardLayout home=a.findViewById(R.id.dashboardScroll);CompactMetricCard disk=ReflectionHelpers.getField(home,"storage"),battery=ReflectionHelpers.getField(home,"battery");
   for(int level:new int[]{0,100,-1})for(long free:new long[]{0,1024L*1073741824,-1}){
    ServerStats stats=new ServerStats();new ServerTelemetry(level,free,1024L*1073741824,1080,level==100?1:0).apply(stats);((TextView)a.findViewById(R.id.batteryValue)).setText(stats.battery);((TextView)a.findViewById(R.id.diskValue)).setText(SurfaceStyle.quantity(stats.freeSpace));home.metrics(stats);layout(root(a),320,728);assertCard(disk);assertCard(battery);assertEquals(level<0?0:level,battery.indicator.getProgress());assertEquals(free<0||free==1024L*1073741824?0:100,disk.indicator.getProgress());assertEquals(level<0?"—":level+"%",battery.value.getText().toString());
   }
   home.resetMetrics();assertEquals(0,battery.indicator.getProgress());assertEquals(0,disk.indicator.getProgress());assertEquals("Объём недоступен",disk.detail.getText().toString());assertEquals("",disk.footer.getText().toString());
  }
 }
 private void assertCard(CompactMetricCard card){
  List<Rect> areas=new ArrayList<>();
  for(int i=0;i<card.getChildCount();i++){View child=card.getChildAt(i);if(child.getVisibility()!=View.VISIBLE)continue;Rect r=new Rect(child.getLeft(),child.getTop(),child.getRight(),child.getBottom());assertTrue("All metric content stays in its own card: "+child+" "+r+" height="+card.getHeight(),r.top>=card.getPaddingTop()&&r.bottom<=card.getHeight()-card.getPaddingBottom()&&r.left>=card.getPaddingLeft()&&r.right<=card.getWidth()-card.getPaddingRight());for(Rect other:areas)assertFalse("No metric lines or indicators overlap: "+r+" and "+other,Rect.intersects(r,other));areas.add(r);
   if(child instanceof TextView){TextView text=(TextView)child;assertNotNull(text.getLayout());assertTrue(text.getHeight()>=text.getLineHeight());assertEquals("Complete metric text is visible: "+text.getText()+" width="+text.getWidth()+" paint="+text.getPaint().measureText(text.getText().toString())+" textSize="+text.getTextSize()+" layoutWidth="+text.getLayout().getWidth()+" pads="+text.getCompoundPaddingLeft()+","+text.getCompoundPaddingRight(),0,text.getLayout().getEllipsisCount(0));assertTrue("Full number is never clipped horizontally",text.getPaint().measureText(text.getText().toString())<=text.getWidth()+1);}
  }
  if(ReflectionHelpers.<Boolean>getField(card,"rich")){assertEquals("Approved number-first alignment",card.getPaddingLeft(),card.value.getLeft());assertEquals("Artwork aligned to the card end",card.getWidth()-card.getPaddingRight(),card.artwork.getRight());assertTrue("Number and artwork have an explicit gap",card.value.getRight()<card.artwork.getLeft());}else assertEquals(card.getWidth()-card.getPaddingRight(),card.value.getRight());assertTrue(card.glyph.getWidth()>=28);assertEquals(5,Math.round(card.indicator.getHeight()/card.getResources().getDisplayMetrics().density));
 }
 private View root(Activity a){return ((ViewGroup)a.findViewById(android.R.id.content)).getChildAt(0);}
 private void layout(View root,int w,int h){root.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));root.layout(0,0,w,h);}
 private void capture(View v,String name)throws Exception{Bitmap b=Bitmap.createBitmap(v.getWidth(),v.getHeight(),Bitmap.Config.ARGB_8888);v.draw(new Canvas(b));save(b,name);}
 private void capturePair(View left,View right)throws Exception{Bitmap b=Bitmap.createBitmap(left.getWidth()+right.getWidth()+8,Math.max(left.getHeight(),right.getHeight()),Bitmap.Config.ARGB_8888);b.eraseColor(0xff120f1b);Canvas canvas=new Canvas(b);left.draw(canvas);canvas.translate(left.getWidth()+8,0);right.draw(canvas);save(b,"00-compact-metric-pair");}
 private void save(Bitmap b,String name)throws Exception{File dir=new File("build/ui-previews/compact-metrics-r8-20261025");dir.mkdirs();try(var out=new FileOutputStream(new File(dir,name+".png"))){assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,out));}b.recycle();}
}
