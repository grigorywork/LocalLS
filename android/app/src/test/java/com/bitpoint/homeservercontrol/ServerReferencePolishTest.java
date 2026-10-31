package com.bitpoint.homeservercontrol;

import android.content.*;import android.graphics.*;import android.graphics.drawable.Drawable;import android.view.*;import android.widget.*;
import java.io.*;import java.util.*;
import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.*;import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

/** Native-pixel checks for the exact defects reported in the approved server reference. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=30,qualifiers="ru-rRU-w411dp-h890dp-mdpi") @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ServerReferencePolishTest {
 @Test public void fullHeaderAndTextAlignedRulesHaveOneColourAndNoExtraLastRowAcrossThemesAndShapes()throws Exception{
  Context app=RuntimeEnvironment.getApplication();List<ServerDevices.Device> devices=new ArrayList<>();for(String name:new String[]{"Телефон","Ноутбук","Планшет"})devices.add(new ServerDevices.Device(SshKeys.generate().getString(SshKeys.PUBLIC),name,0));
  Set<String> online=new HashSet<>();for(int i=0;i<2;i++)online.add(EmbeddedSftpServer.publicFingerprint(devices.get(i).key));
  for(int theme=0;theme<ThemeCatalog.IDS.length;theme++)for(int shape=0;shape<4;shape++){
   ThemeCatalog.select(app,theme);IconShapeCatalog.select(app,shape);var preferences=app.getSharedPreferences(ServerConfig.PREFS,0).getAll();var keys=app.getSharedPreferences(EmbeddedServerService.PREFS,0).getAll();Context c=new ContextThemeWrapper(app,ThemeCatalog.style(app));View root=LayoutInflater.from(c).inflate(R.layout.activity_main,null);ServerHomeLayout home=root.findViewById(R.id.serverHome);var screen=Robolectric.buildActivity(android.app.Activity.class).setup();screen.get().setContentView(root);
   try{
    root.findViewById(R.id.dashboardScroll).setVisibility(View.GONE);home.setVisibility(View.VISIBLE);root.findViewById(R.id.homeClientTab).setVisibility(View.GONE);IconButtons.decorate(root);IconButtons.decorate(root);
    ReflectionHelpers.callInstanceMethod(home,"render",ReflectionHelpers.ClassParameter.from(ServerTelemetry.class,new ServerTelemetry(35,137L*1073741824,256L*1073741824,1080000,1)),ReflectionHelpers.ClassParameter.from(List.class,devices),ReflectionHelpers.ClassParameter.from(Set.class,online),ReflectionHelpers.ClassParameter.from(List.class,Arrays.asList(new ServerReceiptStore.Entry("Фото.jpg","fixture",12L*1024*1024,System.currentTimeMillis()))));
    ((TextView)root.findViewById(R.id.currentWifiIpValue)).setText("192.168.1.151");((TextView)root.findViewById(R.id.serverAccessSummary)).setText("Порт 8022 · localls");
    measure(root,411,890);CenteredActionButton power=root.findViewById(R.id.serverPowerButton);assertTrue("Power icon keeps its centered tile layout",power.hasTileIcon());assertEquals("Power caption shares the icon centre",android.text.Layout.Alignment.ALIGN_CENTER,power.getLayout().getAlignment());assertEquals(power.getHeight()/2f,power.contentBounds().centerY(),1f);assertEquals("Readable server title in every theme",ThemeCatalog.color(c,com.bitpoint.homeservercontrol.ui.R.attr.hscTextPrimary),((TextView)root.findViewById(R.id.homeServerTab)).getCurrentTextColor());assertEquals("Readable footer caption in every theme",ThemeCatalog.color(c,com.bitpoint.homeservercontrol.ui.R.attr.hscTextSecondary),((TextView)root.findViewById(R.id.serverBackgroundButton)).getCurrentTextColor());assertTrue("Inline QR retains its readable pixel size: "+root.findViewById(R.id.serverQrButton).getHeight(),root.findViewById(R.id.serverQrButton).getHeight()>=144);View header=ReflectionHelpers.getField(home,"deviceHeader");int expected=pixel(SurfaceStyle.lineColor(c));Bitmap headerPixels=background(header);
    try{for(int x=0;x<headerPixels.getWidth();x++)assertEquals("Header rule continues beneath the counter",expected,headerPixels.getPixel(x,headerPixels.getHeight()-1));}finally{headerPixels.recycle();}
    assertTrue("Headings keep the reference top inset",((View)ReflectionHelpers.getField(home,"deviceTitle")).getPaddingTop()>=6);
    assertNull("No second rule under just the title",((View)ReflectionHelpers.getField(home,"deviceTitle")).getBackground());
    List<ServerDeviceRow> rows=ReflectionHelpers.getField(home,"deviceRows");int stateColumn=-1;
    for(int i=0;i<rows.size();i++){
     ServerDeviceRow row=rows.get(i);TextView name=ReflectionHelpers.getField(row,"name"),state=ReflectionHelpers.getField(row,"state");assertTrue(row.isShown());assertTrue(row.getHeight()>=48);if(stateColumn<0)stateColumn=state.getLeft();assertEquals("Online and offline indicators have one column",stateColumn,state.getLeft());
     Bitmap pixels=background(row);try{for(int x=0;x<pixels.getWidth();x++)assertEquals("Only intermediate rows have a rule, starting at their text",i<2&&x>=name.getLeft()?expected:0,pixels.getPixel(x,pixels.getHeight()-1));}finally{pixels.recycle();}
    }
    Button files=root.findViewById(R.id.serverLocalFilesButton);Bitmap line=background(files);try{for(int x=0;x<line.getWidth();x++)assertEquals("Folder rule begins under the title",x>=files.getCompoundPaddingLeft()?expected:0,line.getPixel(x,line.getHeight()-1));}finally{line.recycle();}
    for(int id:new int[]{R.id.serverReceiveButton,R.id.localServerQuickButton,R.id.serverBackgroundButton}){Bitmap clean=background(root.findViewById(id));try{for(int x=0;x<clean.getWidth();x++)assertEquals("No orphan footer or last-row line",0,clean.getPixel(x,clean.getHeight()-1));}finally{clean.recycle();}}
    assertNull(((Button)root.findViewById(R.id.localServerQuickButton)).getCompoundDrawablesRelative()[2]);assertNotNull(((Button)root.findViewById(R.id.serverBackgroundButton)).getCompoundDrawablesRelative()[2]);
    assertEquals(preferences,app.getSharedPreferences(ServerConfig.PREFS,0).getAll());assertEquals(keys,app.getSharedPreferences(EmbeddedServerService.PREFS,0).getAll());capture(root,"server-"+ThemeCatalog.IDS[theme]+"-shape-"+shape,411,890);if(shape==0)capture(root,"tablet-"+ThemeCatalog.IDS[theme],1280,800);
   }finally{home.close();screen.close();}
  }
 }
 @Test public void compactControlGeometryChangesTheWholeFrameWhileIllustrationStaysSquare(){
  Context app=RuntimeEnvironment.getApplication();for(int theme=0;theme<ThemeCatalog.IDS.length;theme++){
   ThemeCatalog.select(app,theme);Context c=new ContextThemeWrapper(app,ThemeCatalog.style(app));int[] square=null;
   for(int shape=0;shape<4;shape++){
    IconShapeCatalog.select(app,shape);Drawable control=new CompactControlDrawable(c);Bitmap b=draw(control,168,48);try{
     assertEquals("Empty outside contour",0,Color.alpha(b.getPixel(0,0)));assertTrue(Color.alpha(b.getPixel(84,24))>0);assertEquals("Unified rounded corner stays clear for every legacy preference",0,Color.alpha(b.getPixel(2,2)));
     Outline o=new Outline();control.getOutline(o);assertTrue(o.canClip());assertTrue("Outline clips on Android 7 through 15",o.getRect(new Rect()));assertEquals(8,o.getRadius(),.01);
    }finally{b.recycle();}
    Bitmap illustration=draw(new CompactControlDrawable(c),144,144);try{int[] pixels=new int[144*144];illustration.getPixels(pixels,0,144,0,0,144,144);if(square==null)square=pixels;else assertArrayEquals("QR placeholder never becomes an oval or clips its shield",square,pixels);}finally{illustration.recycle();}
   }
  }
 }
 @Test public void shieldUsesCurrentThemeForItsFrameKeyAndOrbitsInsteadOfHardcodedBlue(){
  Context app=RuntimeEnvironment.getApplication();for(int theme=0;theme<ThemeCatalog.IDS.length;theme++){
   ThemeCatalog.select(app,theme);Context c=new ContextThemeWrapper(app,ThemeCatalog.style(app));BatteryGaugeDrawable gauge=new BatteryGaugeDrawable(c);for(int level:new int[]{-1,35}){gauge.level(level);Bitmap battery=draw(gauge,34,24);try{int color=ThemeCatalog.color(c,level<0?com.bitpoint.homeservercontrol.ui.R.attr.hscTextSecondary:com.bitpoint.homeservercontrol.ui.R.attr.hscSuccess),count=0;for(int y=0;y<24;y++)for(int x=0;x<34;x++)if(Color.alpha(battery.getPixel(x,y))>245&&close(battery.getPixel(x,y),color))count++;assertTrue("Battery outline follows this theme",count>30);}finally{battery.recycle();}}Bitmap b=draw(c.getDrawable(com.bitpoint.homeservercontrol.ui.R.drawable.ic_qr_shield),128,128);
   try{
    int[] palette={ThemeCatalog.color(c,com.bitpoint.homeservercontrol.ui.R.attr.hscAccent),ThemeCatalog.color(c,com.bitpoint.homeservercontrol.ui.R.attr.hscIllustrationAccent),ThemeCatalog.color(c,com.bitpoint.homeservercontrol.ui.R.attr.hscTextPrimary),ThemeCatalog.color(c,com.bitpoint.homeservercontrol.ui.R.attr.hscPanel)};int accent=0,key=0;
    for(int y=0;y<128;y++)for(int x=0;x<128;x++){int pixel=b.getPixel(x,y);if(Color.alpha(pixel)<245)continue;boolean matched=false;for(int a:palette)for(int color:palette)matched|=onSegment(pixel,a,color);assertTrue("Foreign colour in themed shield "+ThemeCatalog.IDS[theme]+" at "+x+","+y,matched);if(close(pixel,palette[0]))accent++;if(close(pixel,palette[2]))key++;}
    assertTrue("Visible shield outline",accent>100);assertTrue("Visible key",key>80);
   }finally{b.recycle();}
  }
 }
 private int pixel(int color){Bitmap b=Bitmap.createBitmap(1,1,Bitmap.Config.ARGB_8888);new Canvas(b).drawColor(color);int value=b.getPixel(0,0);b.recycle();return value;}
 private boolean onSegment(int pixel,int from,int to){float r=Color.red(to)-Color.red(from),g=Color.green(to)-Color.green(from),b=Color.blue(to)-Color.blue(from),length=r*r+g*g+b*b;if(length==0)return close(pixel,from);float k=Math.max(0,Math.min(1,((Color.red(pixel)-Color.red(from))*r+(Color.green(pixel)-Color.green(from))*g+(Color.blue(pixel)-Color.blue(from))*b)/length));return Math.abs(Color.red(pixel)-Color.red(from)-k*r)<=4&&Math.abs(Color.green(pixel)-Color.green(from)-k*g)<=4&&Math.abs(Color.blue(pixel)-Color.blue(from)-k*b)<=4;}
 private boolean close(int a,int b){return Math.abs(Color.red(a)-Color.red(b))<=4&&Math.abs(Color.green(a)-Color.green(b))<=4&&Math.abs(Color.blue(a)-Color.blue(b))<=4;}
 private Bitmap background(View view){Drawable d=view.getBackground();assertNotNull(d);d.setState(new int[]{android.R.attr.state_enabled});return draw(d,view.getWidth(),view.getHeight());}
 private Bitmap draw(Drawable d,int w,int h){Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);d.setBounds(0,0,w,h);d.setState(new int[]{android.R.attr.state_enabled});d.draw(new Canvas(b));return b;}
 private void measure(View root,int w,int h){root.measure(View.MeasureSpec.makeMeasureSpec(w,1073741824),View.MeasureSpec.makeMeasureSpec(h,1073741824));root.layout(0,0,w,h);}
 private void capture(View root,String name,int w,int h)throws Exception{measure(root,w,h);Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);root.draw(new Canvas(b));File dir=new File("build/ui-previews/reference-r5-20261025");dir.mkdirs();try(var out=new FileOutputStream(new File(dir,name+".png"))){assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,out));}b.recycle();}
}
