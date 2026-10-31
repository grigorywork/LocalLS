package com.bitpoint.homeservercontrol;

import android.content.*;import android.graphics.*;import android.view.*;import android.widget.*;
import java.io.*;
import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=30,qualifiers="ru-rRU-w411dp-h890dp-mdpi",shadows=RouteIdentityTest.Secrets.class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ServerContourTest {
 @Test public void standaloneServerActionsHaveClosedFramesAndNoFooterUnderlinesAcrossThemesAndShapes()throws Exception{
  var app=RuntimeEnvironment.getApplication();
  for(int theme=0;theme<ThemeCatalog.IDS.length;theme++)for(int shape=0;shape<4;shape++){
   ThemeCatalog.select(app,theme);IconShapeCatalog.select(app,shape);Context c=new ContextThemeWrapper(app,ThemeCatalog.style(app));View root=LayoutInflater.from(c).inflate(R.layout.activity_main,null);ServerHomeLayout home=root.findViewById(R.id.serverHome);
   try{
    root.findViewById(R.id.dashboardScroll).setVisibility(View.GONE);home.setVisibility(View.VISIBLE);root.findViewById(R.id.homeClientTab).setVisibility(View.GONE);((Button)root.findViewById(R.id.homeServerTab)).setText("Сервер\nЭтот телефон");IconButtons.decorate(root);IconButtons.decorate(root);
    root.measure(exact(411),exact(890));root.layout(0,0,411,890);
    for(int id:new int[]{R.id.localServerQuickButton,R.id.serverBackgroundButton}){
     Button button=root.findViewById(id);Bitmap bg=Bitmap.createBitmap(button.getWidth(),button.getHeight(),Bitmap.Config.ARGB_8888);
     try{button.getBackground().setBounds(0,0,bg.getWidth(),bg.getHeight());button.getBackground().draw(new Canvas(bg));for(int y=0;y<bg.getHeight();y++)for(int x=0;x<bg.getWidth();x++)assertEquals("Standalone footer must have no divider or frame "+id,0,Color.alpha(bg.getPixel(x,y)));}finally{bg.recycle();}
    }
    for(int id:new int[]{R.id.serverPowerButton,R.id.serverQrButton}){
     View button=root.findViewById(id);assertTrue("Dedicated full contour "+id,button.getBackground() instanceof CompactControlDrawable);assertClosedFrame(button);
    }
    View strip=root.findViewById(R.id.serverQrToggle);assertTrue("QR action follows approved borderless strip",strip.getBackground() instanceof StripActionDrawable);assertTrue(strip.getHeight()>=48);Bitmap stripPixels=Bitmap.createBitmap(strip.getWidth(),strip.getHeight(),Bitmap.Config.ARGB_8888);try{strip.getBackground().setBounds(0,0,stripPixels.getWidth(),stripPixels.getHeight());strip.getBackground().setState(new int[]{android.R.attr.state_enabled});strip.getBackground().draw(new Canvas(stripPixels));assertEquals("No top frame",0,Color.alpha(stripPixels.getPixel(stripPixels.getWidth()/2,0)));assertEquals("No bottom frame",0,Color.alpha(stripPixels.getPixel(stripPixels.getWidth()/2,stripPixels.getHeight()-1)));assertTrue("Visible quiet strip",Color.alpha(stripPixels.getPixel(stripPixels.getWidth()/2,stripPixels.getHeight()/2))>0);}finally{stripPixels.recycle();}
    if(shape==0&&(theme==0||theme==1||theme==3)){
     Bitmap image=Bitmap.createBitmap(411,890,Bitmap.Config.ARGB_8888);root.draw(new Canvas(image));File dir=new File("build/ui-previews/contours-20261025");dir.mkdirs();try(var out=new FileOutputStream(new File(dir,"server-"+ThemeCatalog.IDS[theme]+".png"))){assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,out));}image.recycle();
    }
   }finally{home.close();}
  }
 }
 private void assertClosedFrame(View view){
  var drawable=view.getBackground();int w=view.getWidth(),h=view.getHeight();assertTrue(w>=48&&h>=48);drawable.setBounds(0,0,w,h);drawable.setState(new int[]{android.R.attr.state_enabled});Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
  try{
   drawable.draw(new Canvas(b));assertEquals(0,Color.alpha(b.getPixel(0,0)));assertEquals(0,Color.alpha(b.getPixel(w-1,0)));assertEquals(0,Color.alpha(b.getPixel(0,h-1)));assertEquals(0,Color.alpha(b.getPixel(w-1,h-1)));
   assertTrue(Color.alpha(b.getPixel(w/2,1))>0);assertTrue(Color.alpha(b.getPixel(w/2,h-2))>0);assertTrue(Color.alpha(b.getPixel(1,h/2))>0);assertTrue(Color.alpha(b.getPixel(w-2,h/2))>0);
   Outline outline=new Outline();drawable.getOutline(outline);assertTrue("Round frame supports clipping",outline.canClip());
   int normal=b.getPixel(w/2,h/2);b.eraseColor(Color.TRANSPARENT);drawable.setState(new int[]{android.R.attr.state_enabled,android.R.attr.state_pressed});drawable.draw(new Canvas(b));assertNotEquals("Visible pressed feedback",normal,b.getPixel(w/2,h/2));assertEquals("Press stays inside rounded corners",0,Color.alpha(b.getPixel(0,0)));
   b.eraseColor(Color.TRANSPARENT);drawable.setState(new int[]{});drawable.draw(new Canvas(b));assertTrue("Disabled control remains distinct",Color.alpha(b.getPixel(w/2,h/2))<255);drawable.setState(new int[]{android.R.attr.state_enabled});
  }finally{b.recycle();}
 }
 @Test public void diagnosticsIdentifiesThisUiWithoutChangingApplicationVersion(){
  var app=RuntimeEnvironment.getApplication();app.getSharedPreferences(ServerConfig.PREFS,0).edit().clear().putInt(ServerConfig.KEY_CONFIG_VERSION,3).commit();RouteIdentityTest.Secrets.store.clear();
  try(var activity=Robolectric.buildActivity(DiagnosticsActivity.class).setup()){
   TextView text=activity.get().findViewById(R.id.diagnosticsText);assertTrue(text.getText().toString(),text.getText().toString().contains("UI-R8"));assertEquals("UI-R8 · 25.10.2026",activity.get().getString(R.string.ui_build_revision));
  }
 }
 private static int exact(int v){return View.MeasureSpec.makeMeasureSpec(v,View.MeasureSpec.EXACTLY);}
}
