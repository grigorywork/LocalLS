package com.bitpoint.homeservercontrol;

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

/** Multiline truncation must be visible rather than silently clipping the remaining file name. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=30,qualifiers="ru-rRU-w411dp-h890dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class FileLabelRenderingTest {
 @Test public void longFileNamesStayWithinTwoLinesAndShowEllipsisAcrossThemesAndFonts()throws Exception{
  String original="Very_long_document_name_".repeat(8)+"final_revision_2026.dwg";
  for(int theme:new int[]{1,0,2,3,4})for(float font:new float[]{1,2})for(int dpi:new int[]{160,320})for(int width:new int[]{320,280,411,600}){
   Context app=RuntimeEnvironment.getApplication();ThemeCatalog.select(app,theme);Configuration conf=new Configuration(app.getResources().getConfiguration());conf.fontScale=font;conf.densityDpi=dpi;Context c=new ContextThemeWrapper(app.createConfigurationContext(conf),ThemeCatalog.style(app));float d=c.getResources().getDisplayMetrics().density;
   FileEntryRow row=new FileEntryRow(c);row.bind(new FileNodes.Node("share",original,original,false,12345),false,v->fail("Binding or rendering must not select files"));int w=Math.round(width*d);row.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));row.layout(0,0,row.getMeasuredWidth(),row.getMeasuredHeight());
   if(theme==1&&dpi==160&&width==320)capture(row,"row-font-"+(int)font);
   TextView name=ReflectionHelpers.getField(row,"name"),detail=ReflectionHelpers.getField(row,"detail");assertEquals("Rendering never changes the real file name",original,name.getText().toString());assertTrue("Full name remains available to accessibility",row.getChildAt(0).getContentDescription().toString().contains(original));
   assertTrue("At most two visible filename lines",name.getLayout().getLineCount()<=2);int hidden=0;for(int line=0;line<name.getLayout().getLineCount();line++)hidden+=name.getLayout().getEllipsisCount(line);
   assertTrue("Clipped file name must have a visible ellipsis theme="+theme+" font="+font+" width="+width+" lines="+name.getLayout().getLineCount(),hidden>0);
   assertTrue("Filename layout stays inside its own measured height",name.getLayout().getHeight()<=name.getHeight()-name.getCompoundPaddingTop()-name.getCompoundPaddingBottom());assertTrue("Filename and details never overlap",name.getBottom()<=detail.getTop());
   Button more=(Button)row.getChildAt(row.getChildCount()-1);assertTrue("Actions stay inside the file row",more.getRight()<=row.getWidth()-row.getPaddingRight());assertTrue("Menu keeps its touch target",more.getHeight()>=Math.round(48*d));
  }
 }
 private void capture(View row,String name)throws Exception{Bitmap b=Bitmap.createBitmap(row.getWidth(),row.getHeight(),Bitmap.Config.ARGB_8888);b.eraseColor(ThemeCatalog.color(row.getContext(),com.bitpoint.homeservercontrol.ui.R.attr.hscBackground));row.draw(new Canvas(b));File folder=new File("build/ui-previews/file-labels-1.23-20261026");folder.mkdirs();try(var out=new FileOutputStream(new File(folder,name+".png"))){assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,out));}finally{b.recycle();}}
}
