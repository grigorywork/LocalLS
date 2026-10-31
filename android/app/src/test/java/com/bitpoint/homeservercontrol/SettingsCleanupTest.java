package com.bitpoint.homeservercontrol;

import android.app.*;import android.content.*;import android.graphics.*;import android.os.Looper;import android.view.*;import android.widget.*;
import java.io.*;import java.util.*;import java.util.concurrent.*;
import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.*;import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=30,qualifiers="ru-rRU-w411dp-h890dp-mdpi",shadows=RouteIdentityTest.Secrets.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class SettingsCleanupTest {
 private Context app;
 @Before public void setup(){app=RuntimeEnvironment.getApplication();app.getSharedPreferences(ServerConfig.PREFS,0).edit().clear().putInt(ServerConfig.KEY_CONFIG_VERSION,3).putString(ServerConfig.KEY_LOCAL_HOST,"192.0.2.44").putString(ServerConfig.KEY_USER,"localls").putInt(ServerConfig.KEY_PORT,8022).commit();app.getSharedPreferences(EmbeddedServerService.PREFS,0).edit().clear().commit();EmbeddedServerService.running=false;RouteIdentityTest.Secrets.store.clear();}
 @Test public void settingsHaveNoRetiredEntriesAndRemainReadableOnPhoneAndTablet()throws Exception{
  float saved=RuntimeEnvironment.getFontScale();try{
   for(int theme:new int[]{0,4})for(boolean glass:new boolean[]{false,true})for(float font:new float[]{1,2}){
    ThemeCatalog.select(app,theme);ThemeCatalog.glass(app,glass);RuntimeEnvironment.setFontScale(font);
    try(var main=Robolectric.buildActivity(MainActivity.class).setup();var network=Robolectric.buildActivity(NetworkActivity.class).setup();var settings=Robolectric.buildActivity(SettingsActivity.class).setup()){
     MainActivity a=main.get();a.findViewById(R.id.toolsMenuButton).performClick();
     for(String name:new String[]{"drawerVpnHeader","drawerVpnPane","drawerSettingsHeader","drawerSettingsPane"})assertEquals("Removed entry "+name,0,a.getResources().getIdentifier(name,"id",a.getPackageName()));
     for(String name:new String[]{"networkVpnHeader","networkModeGroup","networkAutoFallbackCheck","networkIperfTargetInput","networkTestButton","tailscaleWizardButton"})assertEquals(0,network.get().getResources().getIdentifier(name,"id",a.getPackageName()));
     assertEquals("Поиск сервера",((TextView)a.findViewById(R.id.networkButton)).getText().toString());assertNotNull(settings.get().findViewById(R.id.aboutAppButton));assertNotNull(a.findViewById(R.id.connectionCheckButton));
     for(int[] size:new int[][]{{360,640},{640,360},{1280,800},{800,1280}}){
      ReflectionHelpers.callInstanceMethod(a,"closeToolsDrawer");layout(root(a),size[0],size[1]);ReflectionHelpers.callInstanceMethod(a,"openToolsDrawer");layout(root(a),size[0],size[1]);View pane=a.findViewById(R.id.advancedPanel);pane.animate().cancel();pane.setTranslationX(0);Rect paneBounds=new Rect();pane.getDrawingRect(paneBounds);((ViewGroup)root(a)).offsetDescendantRectToMyCoords(pane,paneBounds);assertTrue("Open menu is inside the viewport "+paneBounds,paneBounds.width()>=48&&paneBounds.left>=0&&paneBounds.right<=size[0]&&paneBounds.top>=0&&paneBounds.bottom<=size[1]);for(int id:new int[]{R.id.appSettingsButton,R.id.drawerAppearanceHeader,R.id.diagnosticsButton,R.id.drawerToolsHeader}){TextView caption=a.findViewById(id);assertEquals("Menu words must stay intact: "+caption.getText(),1,caption.getLayout().getLineCount());assertTrue("Menu font remains readable",caption.getTextSize()>=16*font-.1f);}layout(root(network.get()),size[0],size[1]);layout(root(settings.get()),size[0],size[1]);check(root(network.get()));check(root(settings.get()));String visible=visibleText(root(a));assertFalse(visible.contains("Tailscale"));assertFalse(visible.contains("Termux"));assertFalse(visible.contains("Realme Agent"));
      if(font==1&&((theme==4&&!glass)||(theme==0&&glass))&&(size[0]==360||size[0]==1280)){
       String suffix=(theme==4?"light":"dark")+"-"+size[0]+"x"+size[1];capture(root(a),"menu-"+suffix);capture(root(network.get()),"search-"+suffix);capture(root(settings.get()),"settings-"+suffix);
      }
     }
    }
   }
  }finally{RuntimeEnvironment.setFontScale(saved);}
 }
 @Test public void oneAddressEditorPreservesLegacyRouteAndInactiveKeys()throws Exception{
  for(String mode:new String[]{ServerConfig.MODE_LOCAL,ServerConfig.MODE_VPN}){
   setup();var prefs=app.getSharedPreferences(ServerConfig.PREFS,0);String local="192.0.2.44",vpn="100.64.0.44",next="192.0.2.45";
   prefs.edit().putString(ServerConfig.KEY_MODE,mode).putString(ServerConfig.KEY_VPN_HOST,vpn).putBoolean(ServerConfig.KEY_AUTO_FALLBACK,true).putString("iperf_target","preserve-old-value").commit();
   String active=ConnectionSelector.selectedHost(app),inactive=ConnectionSelector.alternateHost(app);var identity=SshKeys.generate();var other=SshKeys.generate();String pin="SHA256:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",otherPin="SHA256:BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB";
   SshKeys.save(app,active,8022,"localls",identity);SshKeys.save(app,inactive,8022,"localls",other);HostTrustStore.trust(app,active,8022,pin);HostTrustStore.trust(app,inactive,8022,otherPin);
   try(var screen=Robolectric.buildActivity(NetworkActivity.class).setup()){
    EditText field=screen.get().findViewById(R.id.networkLocalHostInput);assertEquals(active,field.getText().toString());field.setText(next);screen.recreate();assertEquals("Draft persists with its original endpoint",next,((EditText)screen.get().findViewById(R.id.networkLocalHostInput)).getText().toString());screen.get().findViewById(R.id.networkSaveButton).performClick();
   }
   assertEquals(mode,prefs.getString(ServerConfig.KEY_MODE,""));assertEquals(next,ConnectionSelector.selectedHost(app));assertEquals(inactive,ConnectionSelector.alternateHost(app));assertEquals(otherPin,HostTrustStore.get(app,inactive,8022));assertEquals(pin,HostTrustStore.get(app,next,8022));assertEquals(other.getString(SshKeys.PUBLIC),SshKeys.info(app,inactive,8022,"localls").getString(SshKeys.PUBLIC));assertEquals(identity.getString(SshKeys.PUBLIC),SshKeys.info(app,next,8022,"localls").getString(SshKeys.PUBLIC));assertTrue(prefs.getBoolean(ServerConfig.KEY_AUTO_FALLBACK,false));assertEquals("preserve-old-value",prefs.getString("iperf_target",""));
  }
 }
 @Test public void staleDiscoveryAndStaleEditorCannotReplaceNewProfile(){
  var prefs=app.getSharedPreferences(ServerConfig.PREFS,0);
  try(var screen=Robolectric.buildActivity(NetworkActivity.class).setup()){
   NetworkActivity a=screen.get();EditText field=a.findViewById(R.id.networkLocalHostInput);CompletableFuture<Void> old=new CompletableFuture<>();ReflectionHelpers.setField(a,"requestTask",old);int generation=ReflectionHelpers.getField(a,"requestGeneration");((TextView)a.findViewById(R.id.networkResultText)).setText("Ищу устройства…");field.setText("192.0.2.55");assertTrue(old.isCancelled());assertTrue(ReflectionHelpers.<Integer>getField(a,"requestGeneration")>generation);assertFalse(((TextView)a.findViewById(R.id.networkResultText)).getText().toString().startsWith("Ищу устройства"));
   prefs.edit().putString(ServerConfig.KEY_LOCAL_HOST,"192.0.2.99").putString("identity_marker","preserve").commit();var before=new HashMap<>(prefs.getAll());a.findViewById(R.id.networkSaveButton).performClick();assertEquals(before,prefs.getAll());screen.recreate();assertEquals("Draft survives rotation","192.0.2.55",((EditText)screen.get().findViewById(R.id.networkLocalHostInput)).getText().toString());screen.get().findViewById(R.id.networkSaveButton).performClick();assertEquals("A rotated stale editor cannot overwrite a new profile",before,prefs.getAll());
  }
 }
 private String visibleText(View v){if(!v.isShown())return "";String s=v instanceof TextView?((TextView)v).getText().toString():"";if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)s+="\n"+visibleText(((ViewGroup)v).getChildAt(i));return s;}
 private void check(View v){if(v.getVisibility()!=View.VISIBLE)return;if(v instanceof TextView){TextView t=(TextView)v;assertTrue("Visible width",t.getWidth()>=48);assertNotNull(t.getLayout());assertTrue("Complete caption: "+t.getText(),t.getLayout().getHeight()<=t.getHeight()-t.getCompoundPaddingTop()-t.getCompoundPaddingBottom());for(int i=0;i<t.getLayout().getLineCount();i++)assertEquals(0,t.getLayout().getEllipsisCount(i));if(v instanceof Button)assertTrue(v.getHeight()>=48);}if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)check(((ViewGroup)v).getChildAt(i));}
 private View root(Activity a){return ((ViewGroup)a.findViewById(android.R.id.content)).getChildAt(0);}
 private void force(View v){v.forceLayout();if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)force(((ViewGroup)v).getChildAt(i));}
 private void layout(View v,int w,int h){force(v);v.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));v.layout(0,0,w,h);}
 private void capture(View v,String name)throws Exception{Bitmap b=Bitmap.createBitmap(v.getWidth(),v.getHeight(),Bitmap.Config.ARGB_8888);v.draw(new Canvas(b));File folder=new File("build/ui-previews/settings-1.31-20261028");folder.mkdirs();try(var out=new FileOutputStream(new File(folder,name+".png"))){assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,out));}finally{b.recycle();}}
}
