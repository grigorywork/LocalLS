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

@RunWith(RobolectricTestRunner.class)
@Config(sdk=30,qualifiers="ru-rRU-w411dp-h890dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class UiAlignmentPolishTest {
 @Test public void serverHeaderEdgesMatchStatusCardIncludingCompactTablets()throws Exception{
  for(int density:new int[]{160,320})for(int[] size:new int[][]{{960,600},{1280,800},{960,500},{760,600}}){
   Context c=context(density,1);float d=c.getResources().getDisplayMetrics().density;
   View root=LayoutInflater.from(c).inflate(R.layout.activity_main,null);ServerHomeLayout home=root.findViewById(R.id.serverHome);
   try(var screen=Robolectric.buildActivity(Activity.class).setup()){
    screen.get().setContentView(root);root.findViewById(R.id.dashboardScroll).setVisibility(View.GONE);home.setVisibility(View.VISIBLE);root.findViewById(R.id.homeClientTab).setVisibility(View.GONE);IconButtons.decorate(root);layout(root,Math.round(size[0]*d),Math.round(size[1]*d));
    Rect header=bounds((ViewGroup)root,root.findViewById(R.id.homeServerTab)),status=bounds((ViewGroup)root,root.findViewById(R.id.localServerCard));
    assertTrue("Header and status never overlap at "+size[0]+"x"+size[1],header.bottom<=status.top);
    if(root.findViewById(R.id.homeRoleTabs).getWidth()<root.getWidth()){
     assertEquals("Header left edge matches its column",status.left,header.left);assertEquals("Header right edge at "+size[0]+"x"+size[1],status.right,header.right);
     int gap=ReflectionHelpers.getField(home,"gap");assertEquals("Header follows the same gutter",gap,status.top-header.bottom);
    }
    if(density==160)capture(root,"server-"+size[0]+"x"+size[1]);
   }finally{home.close();}
  }
 }
 @Test public void chartEmptyStatesStayReadableWithoutEllipsisAtPhoneWidths()throws Exception{
  for(int density:new int[]{160,320})for(float font:new float[]{1,2})for(int[] size:new int[][]{{320,568},{360,640},{411,890},{890,411},{1280,800}}){
   Context c=context(density,font);float d=c.getResources().getDisplayMetrics().density;View root=LayoutInflater.from(c).inflate(R.layout.activity_main,null);ServerHomeLayout server=root.findViewById(R.id.serverHome);
   try(var screen=Robolectric.buildActivity(Activity.class).setup()){
    screen.get().setContentView(root);root.findViewById(R.id.dashboardScroll).setVisibility(View.VISIBLE);server.setVisibility(View.GONE);IconButtons.decorate(root);layout(root,Math.round(size[0]*d),Math.round(size[1]*d));
    for(int id:new int[]{R.id.dashboardSpeedSummary,R.id.dashboardLatencySummary}){
     TextView label=root.findViewById(id);TextView title=(TextView)((ViewGroup)label.getParent()).getChildAt(0);assertTrue("Chart title fits without cropping",title.getPaint().measureText(title.getText().toString())<=title.getWidth()-title.getCompoundPaddingLeft()-title.getCompoundPaddingRight()+1);assertTrue("Summary keeps readable type",label.getTextSize()>=12*d);assertEquals("Full chart state at "+size[0]+"x"+size[1]+" font="+font+": "+label.getText(),0,label.getLayout().getEllipsisCount(0));
    }
    if(density==160&&font==1&&(size[0]==411||size[0]==1280))capture(root,"client-"+size[0]+"x"+size[1]);
   }finally{server.close();}
  }
 }
 private Context context(int density,float font){Context app=RuntimeEnvironment.getApplication();Configuration conf=new Configuration(app.getResources().getConfiguration());conf.densityDpi=density;conf.fontScale=font;return new ContextThemeWrapper(app.createConfigurationContext(conf),com.bitpoint.homeservercontrol.ui.R.style.Theme_LocalLS_Ocean);}
 private Rect bounds(ViewGroup root,View child){Rect r=new Rect();child.getDrawingRect(r);root.offsetDescendantRectToMyCoords(child,r);return r;}
 private void layout(View root,int w,int h){root.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));root.layout(0,0,w,h);}
 private void capture(View root,String name)throws Exception{File folder=new File("build/ui-previews/alignment-polish-20261026");folder.mkdirs();Bitmap b=Bitmap.createBitmap(root.getWidth(),root.getHeight(),Bitmap.Config.ARGB_8888);root.draw(new Canvas(b));try(var out=new FileOutputStream(new File(folder,name+".png"))){assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,out));}finally{b.recycle();}}
}
