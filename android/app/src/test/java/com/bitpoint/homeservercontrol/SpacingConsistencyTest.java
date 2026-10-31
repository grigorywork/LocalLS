package com.bitpoint.homeservercontrol;

import android.app.Activity;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

/** Compare actual card bounds, including nested cards, rather than declared margins. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=30,qualifiers="ru-rRU-w411dp-h890dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class SpacingConsistencyTest {
 @Test public void nestedDashboardCardsUseTheSameGutterAsGraphsAndTransfers()throws Exception{
  for(int theme:new int[]{0,1,4})for(int density:new int[]{160,320})for(int[] size:new int[][]{{360,640},{411,890},{890,411},{1280,800}}){
   Context app=RuntimeEnvironment.getApplication();Configuration conf=new Configuration(app.getResources().getConfiguration());conf.densityDpi=density;conf.fontScale=1;
   ThemeCatalog.select(app,theme);Context c=new ContextThemeWrapper(app.createConfigurationContext(conf),ThemeCatalog.style(app));float d=c.getResources().getDisplayMetrics().density;
   View root=LayoutInflater.from(c).inflate(R.layout.activity_main,null);ServerHomeLayout server=root.findViewById(R.id.serverHome);DashboardLayout home=root.findViewById(R.id.dashboardScroll);
   try(var screen=Robolectric.buildActivity(Activity.class).setup()){
    screen.get().setContentView(root);server.setVisibility(View.GONE);home.setVisibility(View.VISIBLE);IconButtons.decorate(root);
    int w=Math.round(size[0]*d),h=Math.round(size[1]*d);root.measure(exact(w),exact(h));root.layout(0,0,w,h);
    View charts=root.findViewById(R.id.dashboardCharts),transfer=ReflectionHelpers.getField(home,"transfer"),recent=ReflectionHelpers.getField(home,"recent");
    Rect speed=bounds(home,((ViewGroup)charts).getChildAt(0)),latency=bounds(home,((ViewGroup)charts).getChildAt(1));int gutter=latency.left-speed.right;
    assertTrue("Compact gutters stay usable and regular gutters never grow excessively",gutter>=Math.round(4*d)&&gutter<=Math.round(8*d));
    Rect disk=bounds(home,ReflectionHelpers.getField(home,"storage")),battery=bounds(home,ReflectionHelpers.getField(home,"battery")),connection=bounds(home,root.findViewById(R.id.remoteConnectionCard));
    boolean columns=disk.bottom<=battery.top;
    assertEquals("Metrics match the graph gutter",gutter,columns?battery.top-disk.bottom:battery.left-disk.right);
    assertEquals("Connection has the same gutter before metrics",gutter,disk.top-connection.bottom);
    assertEquals("Metrics align to the connection card's start",connection.left,disk.left);
    if(transfer.getVisibility()==View.VISIBLE){View ribbon=ReflectionHelpers.getField(home,"info");assertEquals("Ribbon follows graphs with the same gutter",gutter,ribbon.getTop()-charts.getBottom());assertEquals("Transfer follows ribbon with the same gutter",gutter,transfer.getTop()-ribbon.getBottom());assertEquals(gutter,recent.getTop()-transfer.getBottom());assertEquals(gutter,root.findViewById(R.id.applicationToolsCard).getTop()-recent.getBottom());}
    if(theme==1&&density==160)capture(root,size[0]+"x"+size[1]+"-client");
   }finally{server.close();}
  }
 }
 @Test public void serverColumnsAndResourceCardsKeepIdenticalGutters()throws Exception{
  for(int density:new int[]{160,320})for(int[] size:new int[][]{{360,728},{411,890},{890,411},{1280,800}}){
   Context app=RuntimeEnvironment.getApplication();Configuration conf=new Configuration(app.getResources().getConfiguration());conf.densityDpi=density;conf.fontScale=1;
   Context c=new ContextThemeWrapper(app.createConfigurationContext(conf),com.bitpoint.homeservercontrol.ui.R.style.Theme_LocalLS_Ocean);float d=c.getResources().getDisplayMetrics().density;
   View root=LayoutInflater.from(c).inflate(R.layout.activity_main,null);ServerHomeLayout home=root.findViewById(R.id.serverHome);
   try(var screen=Robolectric.buildActivity(Activity.class).setup()){
    screen.get().setContentView(root);root.findViewById(R.id.dashboardScroll).setVisibility(View.GONE);home.setVisibility(View.VISIBLE);root.findViewById(R.id.homeClientTab).setVisibility(View.GONE);IconButtons.decorate(root);
    int w=Math.round(size[0]*d),h=Math.round(size[1]*d);root.measure(exact(w),exact(h));root.layout(0,0,w,h);
    int gutter=ReflectionHelpers.getField(home,"gap");ViewGroup resources=ReflectionHelpers.getField(home,"resources");assertEquals(gutter,resources.getChildAt(1).getLeft()-resources.getChildAt(0).getRight());
    java.util.Map<Integer,java.util.List<View>> columns=new java.util.HashMap<>();
    for(int i=0;i<home.getChildCount();i++){View card=home.getChildAt(i);if(card.getVisibility()==View.VISIBLE&&card.getHeight()>0)columns.computeIfAbsent(card.getLeft(),k->new java.util.ArrayList<>()).add(card);}
    for(var column:columns.values()){column.sort(java.util.Comparator.comparingInt(View::getTop));for(int i=1;i<column.size();i++)assertEquals("Every adjacent server card shares the same gutter",gutter,column.get(i).getTop()-column.get(i-1).getBottom());}
    if(density==160)capture(root,size[0]+"x"+size[1]+"-server");
   }finally{home.close();}
  }
 }
 private Rect bounds(ViewGroup boundary,View child){Rect rect=new Rect();child.getDrawingRect(rect);boundary.offsetDescendantRectToMyCoords(child,rect);return rect;}
 private void capture(View root,String name)throws Exception{File folder=new File("build/ui-previews/spacing-20261026");folder.mkdirs();Bitmap image=Bitmap.createBitmap(root.getWidth(),root.getHeight(),Bitmap.Config.ARGB_8888);root.draw(new Canvas(image));try(var out=new FileOutputStream(new File(folder,name+".png"))){assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,out));}finally{image.recycle();}}
 private static int exact(int n){return View.MeasureSpec.makeMeasureSpec(n,View.MeasureSpec.EXACTLY);}
}
