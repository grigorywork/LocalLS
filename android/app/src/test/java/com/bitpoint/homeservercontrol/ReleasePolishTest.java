package com.bitpoint.homeservercontrol;

import android.app.*;import android.content.*;import android.content.res.Configuration;import android.graphics.*;import android.graphics.drawable.*;import android.view.*;import android.widget.*;
import java.io.*;import java.util.*;
import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=30,qualifiers="ru-rRU-w411dp-h890dp-mdpi",shadows=RouteIdentityTest.Secrets.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ReleasePolishTest {
 private Context app;
 @Before public void setup(){app=RuntimeEnvironment.getApplication();app.getSharedPreferences(ServerConfig.PREFS,0).edit().clear().putInt(ServerConfig.KEY_CONFIG_VERSION,3).putString(ServerConfig.KEY_LOCAL_HOST,"192.0.2.44").putString("identity_marker","preserve").commit();app.getSharedPreferences(EmbeddedServerService.PREFS,0).edit().clear().putString("server_key_marker","preserve").commit();EmbeddedServerService.running=false;RouteIdentityTest.Secrets.store.clear();}
 @Test public void retiredShapeValuesHaveNoUiOrGeometryEffects(){
  for(int theme:new int[]{0,1,4})for(boolean glass:new boolean[]{false,true}){
   ThemeCatalog.select(app,theme);ThemeCatalog.glass(app,glass);Context c=new ContextThemeWrapper(app,ThemeCatalog.style(app));ThemeCatalog.applyOverlays(c);int[] previous=null;
   for(int legacy=0;legacy<4;legacy++){
    IconShapeCatalog.select(app,legacy);Button button=new Button(c);button.setText("Действие");button.setLayoutParams(new LinearLayout.LayoutParams(200,64));IconButtons.decorate(button);Drawable bg=button.getBackground();bg.setBounds(0,0,200,64);bg.setState(button.getDrawableState());
    Bitmap bitmap=Bitmap.createBitmap(200,64,Bitmap.Config.ARGB_8888);bg.draw(new Canvas(bitmap));int[] pixels=new int[200*64];bitmap.getPixels(pixels,0,200,0,0,200,64);bitmap.recycle();if(previous==null)previous=pixels;else assertArrayEquals("Legacy shape cannot change the whole frame",previous,pixels);
    Outline outline=new Outline();bg.getOutline(outline);assertEquals(12,outline.getRadius(),0);
   }
   var before=new HashMap<>(app.getSharedPreferences(ServerConfig.PREFS,0).getAll());try(var screen=Robolectric.buildActivity(AppearanceActivity.class).setup()){assertEquals(0,screen.get().getResources().getIdentifier("iconShapeButton","id",app.getPackageName()));assertFalse(texts(root(screen.get())).contains("Форма кнопок"));}assertEquals(before,app.getSharedPreferences(ServerConfig.PREFS,0).getAll());
  }
 }
 @Test public void lightGlassUsesUniformNeutralSurfacesAcrossPhoneAndTablet()throws Exception{
  ThemeCatalog.select(app,4);
  for(boolean glass:new boolean[]{false,true})for(float font:new float[]{1,2}){
   ThemeCatalog.glass(app,glass);Configuration config=new Configuration(app.getResources().getConfiguration());config.fontScale=font;Context c=new ContextThemeWrapper(app.createConfigurationContext(config),ThemeCatalog.style(app));ThemeCatalog.applyOverlays(c);
   int bg=ThemeCatalog.color(c,com.bitpoint.homeservercontrol.ui.R.attr.hscBackground);assertTrue("Neutral background",Math.max(Color.red(bg),Math.max(Color.green(bg),Color.blue(bg)))-Math.min(Color.red(bg),Math.min(Color.green(bg),Color.blue(bg)))<=4);
   GradientDrawable surface=(GradientDrawable)SurfaceStyle.panel(c);if(glass){int[] colors=surface.getColors();assertNotNull(colors);for(int color:colors){assertEquals("Flat glass without diagonal tint",0xCCFFFFFF,color);}}
   View view=LayoutInflater.from(c).inflate(R.layout.activity_main,null);ServerHomeLayout server=view.findViewById(R.id.serverHome);
   try{
    IconButtons.decorate(view);ThemeCatalog.surfaces(view);
    for(int[] size:new int[][]{{360,640},{640,360},{1280,800},{800,1280}}){layout(view,size[0],size[1]);for(int id:new int[]{R.id.checkButton,R.id.filesButton,R.id.transferQueueButton,R.id.toolsMenuButton}){CenteredActionButton b=view.findViewById(id);assertTrue(b.getHeight()>=48);assertTrue(b.contentBounds().top>=0&&b.contentBounds().bottom<=b.getHeight());}if(font==1&&(size[0]==360||size[0]==1280))capture(view,(glass?"light-glass":"light")+"-"+size[0]+"x"+size[1]);}
   }finally{server.close();}
  }
 }
 @Test public void aboutShowsAuthorInstalledVersionAndOpensFromBothSettingsRoles()throws Exception{
  var prefs=app.getSharedPreferences(ServerConfig.PREFS,0);var before=new HashMap<>(prefs.getAll());
  try(var screen=Robolectric.buildActivity(AboutActivity.class).setup()){
   AboutActivity a=screen.get();assertEquals("Автор — MSS9LN",((TextView)a.findViewById(R.id.aboutAuthor)).getText().toString());var info=a.getPackageManager().getPackageInfo(a.getPackageName(),0);assertEquals(a.getString(R.string.about_version,info.versionName,info.versionCode),((TextView)a.findViewById(R.id.aboutVersion)).getText().toString());assertTrue(((TextView)a.findViewById(R.id.aboutGreeting)).getText().toString().startsWith("Привет!"));assertFalse(a.getPackageManager().getActivityInfo(new ComponentName(a,AboutActivity.class),0).exported);a.findViewById(R.id.settingsBackButton).performClick();assertTrue(a.isFinishing());
  }
  assertEquals(before,prefs.getAll());
  for(boolean serverOnly:new boolean[]{false,true}){
   app.getSharedPreferences(EmbeddedServerService.PREFS,0).edit().putBoolean("server_role",serverOnly).putBoolean("show_client",!serverOnly).commit();
   try(var screen=Robolectric.buildActivity(SettingsActivity.class).setup()){View b=screen.get().findViewById(R.id.aboutAppButton);assertEquals(View.VISIBLE,b.getVisibility());b.performClick();assertEquals(AboutActivity.class.getName(),Shadows.shadowOf(screen.get()).getNextStartedActivity().getComponent().getClassName());}
  }
  try(var screen=Robolectric.buildActivity(EmbeddedServerActivity.class).setup()){screen.get().findViewById(R.id.aboutAppButton).performClick();assertEquals(AboutActivity.class.getName(),Shadows.shadowOf(screen.get()).getNextStartedActivity().getComponent().getClassName());}
 }
 @Test public void aboutFitsEveryThemeWithLargeTextAndBothOrientations()throws Exception{
  float saved=RuntimeEnvironment.getFontScale();try{
   for(int theme:new int[]{0,1,4})for(boolean glass:new boolean[]{false,true})for(float font:new float[]{1,2}){
    ThemeCatalog.select(app,theme);ThemeCatalog.glass(app,glass);RuntimeEnvironment.setFontScale(font);
    try(var screen=Robolectric.buildActivity(AboutActivity.class).setup()){
     View view=root(screen.get());for(int[] size:new int[][]{{360,640},{640,360},{1280,800},{800,1280}}){layout(view,size[0],size[1]);checkText(view);checkHorizontalBounds((ViewGroup)view,view);assertTrue(screen.get().findViewById(R.id.settingsBackButton).getHeight()>=48);if(font==1&&((theme==4&&!glass)||(theme==0&&glass))&&(size[0]==360||size[0]==1280))capture(view,"about-"+(theme==4?"light":"dark")+"-"+size[0]+"x"+size[1]);}
    }
   }
  }finally{RuntimeEnvironment.setFontScale(saved);}
 }
 private void checkText(View v){if(v.getVisibility()!=View.VISIBLE)return;if(v instanceof TextView){TextView t=(TextView)v;assertTrue("About text has a visible width: "+t.getText()+" width="+t.getWidth(),t.getWidth()>=48);assertNotNull(t.getLayout());assertTrue("Full about caption "+t.getText(),t.getLayout().getHeight()<=t.getHeight()-t.getCompoundPaddingTop()-t.getCompoundPaddingBottom());for(int i=0;i<t.getLayout().getLineCount();i++)assertEquals(0,t.getLayout().getEllipsisCount(i));}if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)checkText(((ViewGroup)v).getChildAt(i));}
 private void checkHorizontalBounds(ViewGroup root,View v){if(v.getVisibility()!=View.VISIBLE)return;Rect bounds=new Rect();v.getDrawingRect(bounds);root.offsetDescendantRectToMyCoords(v,bounds);assertTrue("About content inside horizontal viewport: "+bounds,bounds.left>=0&&bounds.right<=root.getWidth());if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)checkHorizontalBounds(root,((ViewGroup)v).getChildAt(i));}
 private String texts(View v){String result=v instanceof TextView?((TextView)v).getText().toString():"";if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)result+="\n"+texts(((ViewGroup)v).getChildAt(i));return result;}
 private View root(Activity a){return ((ViewGroup)a.findViewById(android.R.id.content)).getChildAt(0);}
 private void force(View v){v.forceLayout();if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)force(((ViewGroup)v).getChildAt(i));}
 private void layout(View v,int w,int h){force(v);v.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));v.layout(0,0,w,h);}
 private void capture(View v,String name)throws Exception{Bitmap b=Bitmap.createBitmap(v.getWidth(),v.getHeight(),Bitmap.Config.ARGB_8888);v.draw(new Canvas(b));File dir=new File("build/ui-previews/polish-1.30-20261027");dir.mkdirs();try(var out=new FileOutputStream(new File(dir,name+".png"))){assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,out));}finally{b.recycle();}}
}
