package com.bitpoint.homeservercontrol;

import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.*;
import android.graphics.drawable.Drawable;
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
@Config(sdk=30,qualifiers="ru-rRU-w411dp-h890dp-mdpi",shadows=RouteIdentityTest.Secrets.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class BottomNavigationContrastTest {
 private Context app;
 @Before public void setup(){app=RuntimeEnvironment.getApplication();app.getSharedPreferences(ServerConfig.PREFS,0).edit().clear().putInt(ServerConfig.KEY_CONFIG_VERSION,3).commit();app.getSharedPreferences(EmbeddedServerService.PREFS,0).edit().clear().commit();}
 @Test public void transparentNavigationRemainsReadableAcrossThemesGlassFontsAndViewports()throws Exception{
  for(int theme:new int[]{4,0,1})for(boolean glass:new boolean[]{false,true})for(boolean outline:new boolean[]{false,true})for(float font:new float[]{1,2}){
   ThemeCatalog.select(app,theme);ThemeCatalog.glass(app,glass);ThemeCatalog.lightOutline(app,outline);IconShapeCatalog.select(app,0);
   Configuration cfg=new Configuration(app.getResources().getConfiguration());cfg.fontScale=font;
   Context c=new ContextThemeWrapper(app.createConfigurationContext(cfg),ThemeCatalog.style(app));ThemeCatalog.applyOverlays(c);
   View root=LayoutInflater.from(c).inflate(R.layout.activity_main,null);ServerHomeLayout server=root.findViewById(R.id.serverHome);
   try{
    IconButtons.decorate(root);ThemeCatalog.surfaces(root);
    for(int[] size:new int[][]{{360,640},{640,360},{1280,800},{800,1280}}){
     layout(root,size[0],size[1]);
     if(theme==4&&!outline&&font==1&&(size[0]==360||size[0]==1280))capture(root,(glass?"light-glass":"light")+"-"+size[0]+"x"+size[1]);
     for(int id:new int[]{R.id.checkButton,R.id.filesButton,R.id.transferQueueButton,R.id.toolsMenuButton}){
      CenteredActionButton b=root.findViewById(id);int background=ThemeCatalog.color(c,com.bitpoint.homeservercontrol.ui.R.attr.hscPanel);int base=ThemeCatalog.color(c,com.bitpoint.homeservercontrol.ui.R.attr.hscBackground);background=composite(background,base);
      assertTrue("Readable navigation caption "+ThemeCatalog.IDS[theme]+" glass="+glass+" "+b.getText(),contrast(b.getCurrentTextColor(),background)>=4.5);
      assertTrue("Touch target",b.getWidth()>=48&&b.getHeight()>=48);assertNotNull(b.getLayout());for(int line=0;line<b.getLayout().getLineCount();line++)assertEquals("Complete caption",0,b.getLayout().getEllipsisCount(line));
      assertTrue("Caption and icon fit vertically",b.contentBounds().top>=0&&b.contentBounds().bottom<=b.getHeight());
      Drawable icon=ReflectionHelpers.getField(b,"tileIcon");assertNotNull(icon);assertIconContrast(icon,b,background);
      android.graphics.Rect bounds=new android.graphics.Rect();b.getDrawingRect(bounds);((ViewGroup)root).offsetDescendantRectToMyCoords(b,bounds);assertTrue("Navigation visible in viewport",bounds.top>=0&&bounds.bottom<=root.getHeight());
     }
    }
   }finally{server.close();}
  }
 }
 @Test public void filledShapesAndRepeatedDecorationKeepNavigationContrast(){
  ThemeCatalog.select(app,4);
  for(boolean glass:new boolean[]{false,true})for(int shape=0;shape<4;shape++){
   ThemeCatalog.glass(app,glass);IconShapeCatalog.select(app,shape);Context c=new ContextThemeWrapper(app,ThemeCatalog.style(app));ThemeCatalog.applyOverlays(c);
   View root=LayoutInflater.from(c).inflate(R.layout.activity_main,null);ServerHomeLayout server=root.findViewById(R.id.serverHome);
   try{
    for(int repeat=0;repeat<2;repeat++){
     IconButtons.decorate(root);ThemeCatalog.surfaces(root);layout(root,411,890);
     for(int id:new int[]{R.id.checkButton,R.id.filesButton,R.id.transferQueueButton,R.id.toolsMenuButton}){
      CenteredActionButton b=root.findViewById(id);Drawable background=b.getBackground();background.setBounds(0,0,b.getWidth(),b.getHeight());background.setState(b.getDrawableState());
      Bitmap bitmap=Bitmap.createBitmap(b.getWidth(),b.getHeight(),Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);canvas.drawColor(composite(ThemeCatalog.color(c,com.bitpoint.homeservercontrol.ui.R.attr.hscPanel),ThemeCatalog.color(c,com.bitpoint.homeservercontrol.ui.R.attr.hscBackground)));background.draw(canvas);int color=bitmap.getPixel(bitmap.getWidth()/2,bitmap.getHeight()/2);bitmap.recycle();
      assertTrue("Shape "+shape+" glass="+glass+" repeat="+repeat+" "+b.getText()+" fg="+Integer.toHexString(b.getCurrentTextColor())+" bg="+Integer.toHexString(color)+" caption contrast",contrast(b.getCurrentTextColor(),color)>=4.5);assertIconContrast(ReflectionHelpers.getField(b,"tileIcon"),b,color);
      assertTrue("Retired shape cannot restore a filled navigation button",background instanceof android.graphics.drawable.ColorDrawable);assertEquals(0,Color.alpha(((android.graphics.drawable.ColorDrawable)background).getColor()));
      b.setEnabled(false);background.setState(b.getDrawableState());Bitmap disabled=Bitmap.createBitmap(b.getWidth(),b.getHeight(),Bitmap.Config.ARGB_8888);Canvas disabledCanvas=new Canvas(disabled);disabledCanvas.drawColor(composite(ThemeCatalog.color(c,com.bitpoint.homeservercontrol.ui.R.attr.hscPanel),ThemeCatalog.color(c,com.bitpoint.homeservercontrol.ui.R.attr.hscBackground)));background.draw(disabledCanvas);int disabledColor=disabled.getPixel(disabled.getWidth()/2,disabled.getHeight()/2);disabled.recycle();assertTrue("Disabled label remains readable",contrast(b.getCurrentTextColor(),disabledColor)>=4.5);assertIconContrast(ReflectionHelpers.getField(b,"tileIcon"),b,disabledColor);b.setEnabled(true);
     }
    }
   }finally{server.close();}
  }
 }
 @Test public void changingShapeWithoutRestartRestoresTheMatchingForeground(){
  ThemeCatalog.select(app,4);
  for(boolean glass:new boolean[]{false,true}){
   ThemeCatalog.glass(app,glass);IconShapeCatalog.select(app,0);
   try(var screen=Robolectric.buildActivity(MainActivity.class).setup()){
    MainActivity a=screen.get();View root=((ViewGroup)a.findViewById(android.R.id.content)).getChildAt(0);
    for(int shape:new int[]{0,1,2,3,0,2}){
     IconShapeCatalog.select(app,shape);a.polishControls();layout(root,411,890);
     for(int id:new int[]{R.id.checkButton,R.id.filesButton,R.id.transferQueueButton,R.id.toolsMenuButton}){
      CenteredActionButton b=a.findViewById(id);Drawable background=b.getBackground();background.setBounds(0,0,b.getWidth(),b.getHeight());background.setState(b.getDrawableState());
      Bitmap bitmap=Bitmap.createBitmap(b.getWidth(),b.getHeight(),Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);canvas.drawColor(composite(ThemeCatalog.color(a,com.bitpoint.homeservercontrol.ui.R.attr.hscPanel),ThemeCatalog.color(a,com.bitpoint.homeservercontrol.ui.R.attr.hscBackground)));background.draw(canvas);int color=bitmap.getPixel(bitmap.getWidth()/2,bitmap.getHeight()/2);bitmap.recycle();
      assertTrue("Changing shape without restart: "+shape+" "+b.getText(),contrast(b.getCurrentTextColor(),color)>=4.5);assertIconContrast(ReflectionHelpers.getField(b,"tileIcon"),b,color);
     }
    }
   }
  }
 }
 @Test public void lightRoleTabsRemainReadableAfterSwitchingAndPolishing(){
  ThemeCatalog.select(app,4);app.getSharedPreferences(EmbeddedServerService.PREFS,0).edit().putBoolean("show_client",true).commit();
  for(boolean glass:new boolean[]{false,true}){
   ThemeCatalog.glass(app,glass);
   try(var screen=Robolectric.buildActivity(MainActivity.class).setup()){
    MainActivity a=screen.get();View root=((ViewGroup)a.findViewById(android.R.id.content)).getChildAt(0);
    for(int selected:new int[]{R.id.homeServerTab,R.id.homeClientTab,R.id.homeServerTab}){
     a.findViewById(selected).performClick();a.polishControls();layout(root,411,890);
     for(int id:new int[]{R.id.homeServerTab,R.id.homeClientTab}){
      Button b=a.findViewById(id);int fill=b.getBackgroundTintList().getColorForState(b.getDrawableState(),0);fill=composite(fill,ThemeCatalog.color(a,com.bitpoint.homeservercontrol.ui.R.attr.hscBackground));
      assertTrue("Role caption readable glass="+glass+" selected="+b.isSelected()+" "+b.getText(),contrast(b.getCurrentTextColor(),fill)>=4.5);
     }
    }
   }
  }
 }
 // Render the vector at enlarged size: a 1.5px horizontal stroke at mdpi can have
 // no fully covered pixels. Check the actual tint on fully covered interior pixels.
 private void assertIconContrast(Drawable icon,View button,int background){
  Rect original=new Rect(icon.getBounds());Bitmap bitmap=Bitmap.createBitmap(72,72,Bitmap.Config.ARGB_8888);
  try{icon.setBounds(0,0,72,72);icon.setState(button.getDrawableState());icon.draw(new Canvas(bitmap));boolean visible=false;
   for(int y=0;y<72&&!visible;y++)for(int x=0;x<72;x++){int color=bitmap.getPixel(x,y);if(Color.alpha(color)>240){assertTrue("Navigation glyph has sufficient contrast",contrast(color,background)>=4.5);visible=true;break;}}
   assertTrue("Icon contains visible strokes",visible);
  }finally{icon.setBounds(original);bitmap.recycle();}
 }
 private int composite(int fg,int bg){float a=Color.alpha(fg)/255f;return Color.rgb(Math.round(Color.red(fg)*a+Color.red(bg)*(1-a)),Math.round(Color.green(fg)*a+Color.green(bg)*(1-a)),Math.round(Color.blue(fg)*a+Color.blue(bg)*(1-a)));}
 private double linear(int n){double v=n/255d;return v<=.04045?v/12.92:Math.pow((v+.055)/1.055,2.4);}
 private double luminance(int c){return .2126*linear(Color.red(c))+.7152*linear(Color.green(c))+.0722*linear(Color.blue(c));}
 private double contrast(int a,int b){double x=luminance(a),y=luminance(b);return (Math.max(x,y)+.05)/(Math.min(x,y)+.05);}
 private void force(View v){v.forceLayout();if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)force(((ViewGroup)v).getChildAt(i));}
 private void layout(View v,int w,int h){force(v);v.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));v.layout(0,0,w,h);}
 private void capture(View v,String name)throws Exception{Bitmap bitmap=Bitmap.createBitmap(v.getWidth(),v.getHeight(),Bitmap.Config.ARGB_8888);v.draw(new Canvas(bitmap));File dir=new File("build/ui-previews/navigation-1.29-20261027");dir.mkdirs();try(var out=new FileOutputStream(new File(dir,name+".png"))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}finally{bitmap.recycle();}}
}
