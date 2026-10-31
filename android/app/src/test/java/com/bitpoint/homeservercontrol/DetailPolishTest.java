package com.bitpoint.homeservercontrol;

import android.app.Activity;import android.content.*;import android.content.res.Configuration;import android.graphics.*;import android.os.Bundle;import android.text.Layout;import android.view.*;import android.widget.*;
import java.io.*;import java.util.*;
import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.*;import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=30,qualifiers="ru-rRU-w411dp-h890dp-mdpi",shadows=RouteIdentityTest.Secrets.class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class DetailPolishTest {
 @Before public void setup(){var app=RuntimeEnvironment.getApplication();app.getSharedPreferences(ServerConfig.PREFS,0).edit().clear().putInt(ServerConfig.KEY_CONFIG_VERSION,3).putString(ServerConfig.KEY_LOCAL_HOST,"192.0.2.9").putString("keep_identity","encrypted-key-placeholder").commit();app.getSharedPreferences(EmbeddedServerService.PREFS,0).edit().clear().commit();RouteIdentityTest.Secrets.store.clear();ThemeCatalog.select(app,1);EmbeddedServerService.running=false;EmbeddedServerService.starting=false;EmbeddedServerService.launchRequested=0;}
 @Test public void percentageItselfIsCenteredAndStorageSubtitleIsFlushAcrossThemesDensitiesAndRoles()throws Exception{
  Context app=RuntimeEnvironment.getApplication();
  for(int theme=0;theme<ThemeCatalog.IDS.length;theme++)for(int[] size:new int[][]{{320,728,160},{411,890,320},{1280,800,160}}){
   ThemeCatalog.select(app,theme);Configuration config=new Configuration(app.getResources().getConfiguration());config.densityDpi=size[2];config.fontScale=1;Context c=new ContextThemeWrapper(app.createConfigurationContext(config),ThemeCatalog.style(app));float d=c.getResources().getDisplayMetrics().density;
   View root=LayoutInflater.from(c).inflate(R.layout.activity_main,null);ServerHomeLayout server=root.findViewById(R.id.serverHome);DashboardLayout dashboard=root.findViewById(R.id.dashboardScroll);
   try(var screen=Robolectric.buildActivity(Activity.class).setup()){
    screen.get().setContentView(root);IconButtons.decorate(root);
    for(boolean own:new boolean[]{true,false}){
     server.setVisibility(own?View.VISIBLE:View.GONE);dashboard.setVisibility(own?View.GONE:View.VISIBLE);root.findViewById(R.id.homeClientTab).setVisibility(own?View.GONE:View.VISIBLE);
     TextView value=root.findViewById(own?R.id.serverPanelBattery:R.id.batteryValue);
     for(String level:new String[]{"35%","100%","—"}){
      value.setText(level);measure(root,Math.round(size[0]*d),Math.round(size[1]*d));Layout text=value.getLayout();assertNotNull(text);ViewGroup row=(ViewGroup)value.getParent();float left=value.getLeft()+value.getCompoundPaddingLeft()+text.getLineLeft(0),right=value.getLeft()+value.getCompoundPaddingLeft()+text.getLineRight(0);
      if(!own){CompactMetricCard card=(CompactMetricCard)row;if(ReflectionHelpers.<Boolean>getField(card,"rich")){assertEquals("Approved percentage starts at the leading inset",card.getPaddingLeft(),left,1.1f*d);assertTrue("Full percentage does not overlap the right artwork",right+d<=card.artwork.getLeft());assertTrue("Heading badge stays above the full percentage",card.glyph.getBottom()+d<=value.getTop());assertEquals("Artwork follows the trailing inset",card.getWidth()-card.getPaddingRight(),card.artwork.getRight());}else{assertEquals("Compact percentage is aligned to the trailing inset",row.getWidth()-row.getPaddingRight(),right,1.1f*d);assertTrue("Badge never overlaps the full value",card.glyph.getRight()+d<=left);}assertEquals("Battery "+level+" size="+Arrays.toString(size)+" width="+value.getWidth()+" paint="+value.getPaint().measureText(level)+" layout="+text.getWidth(),0,text.getEllipsisCount(0));}
      else{      assertEquals("Percentage text, including 100%, is centered independently of the icon",row.getWidth()/2f,(left+right)/2,1.1f*d);View glyph=row.getChildAt(1);if(!(glyph.getRight()+d<=left))capture(root,"failure-metric");assertTrue("Icon must not overlap the number theme="+theme+" size="+Arrays.toString(size)+" own="+own+" level="+level+" glyph="+glyph.getLeft()+","+glyph.getRight()+" textLeft="+left+" row="+row.getWidth()+" paint="+value.getPaint().measureText(level),glyph.getRight()+d<=left);assertEquals("Glyph is vertically centered",row.getHeight()/2f,(glyph.getTop()+glyph.getBottom())/2f,1.1f*d);assertEquals(0,text.getEllipsisCount(0));}
     }
     if(own){TextView detail=root.findViewWithTag("disk_detail");assertEquals(0,detail.getPaddingStart());assertEquals(root.findViewById(R.id.serverPanelSpace).getLeft(),detail.getLeft());}
     if(theme==1&&size[0]!=320)capture(root,(own?"server-":"client-")+(size[0]==1280?"tablet":"phone"));
    }
   }finally{server.close();}
  }
 }
 @Test public void applicationSettingsAreReadableBoundedAndSaveOnlyAfterExplicitAction()throws Exception{
  var app=RuntimeEnvironment.getApplication();var prefs=app.getSharedPreferences(ServerConfig.PREFS,0);var before=prefs.getAll();
  try(var screen=Robolectric.buildActivity(SettingsActivity.class).setup()){
   SettingsActivity a=screen.get();View root=root(a);
   for(int[] size:new int[][]{{320,568},{411,890},{1280,800}}){measure(root,size[0],size[1]);View card=a.findViewById(R.id.settingsShareCard);assertTrue("Tablet form has a bounded width",card.getWidth()<=720);for(int id:new int[]{R.id.settingsBackgroundMonitorCheck,R.id.shareImmediatelyCheck,R.id.settingsSaveButton}){TextView row=a.findViewById(id);assertTrue(row.getHeight()>=56);assertTrue(row.getTextSize()>=16);assertEquals(0,row.getLayout().getEllipsisCount(row.getLayout().getLineCount()-1));}if(size[0]!=320)capture(root,size[0]==1280?"app-settings-tablet":"app-settings-phone");}
   assertEquals(before,prefs.getAll());((CheckBox)a.findViewById(R.id.shareImmediatelyCheck)).setChecked(true);assertFalse(prefs.getBoolean(ShareOutbox.AUTO,false));a.findViewById(R.id.settingsSaveButton).performClick();assertTrue(prefs.getBoolean(ShareOutbox.AUTO,false));assertEquals(before.get(ServerConfig.KEY_LOCAL_HOST),prefs.getString(ServerConfig.KEY_LOCAL_HOST,""));assertEquals(before.get("keep_identity"),prefs.getString("keep_identity",""));
  }
 }
 @Test public void serverSettingsKeepSavedKeyAndFieldsWhileManualImportIsCollapsed()throws Exception{
  var app=RuntimeEnvironment.getApplication();String key=SshKeys.generate().getString(SshKeys.PUBLIC);var prefs=app.getSharedPreferences(EmbeddedServerService.PREFS,0);prefs.edit().putString("allowed_key",key).putInt("port",9123).putString("user","localls").commit();var before=prefs.getAll();
  try(var screen=Robolectric.buildActivity(EmbeddedServerActivity.class).setup()){
   EmbeddedServerActivity a=screen.get();View root=root(a);measure(root,411,890);TextView input=a.findViewById(R.id.embeddedServerKey);assertEquals(key,input.getText().toString());assertFalse("Manual key editor stays out of the normal flow",input.isShown());assertEquals("9123",((TextView)a.findViewById(R.id.embeddedServerPort)).getText().toString());
   Button manual=find(root,"Публичный ключ (.pub)");assertNotNull(manual);manual.performClick();assertTrue(input.isShown());assertTrue(manual.isSelected());manual.performClick();assertFalse(input.isShown());assertFalse(manual.isSelected());assertEquals(before,prefs.getAll());capture(root,"server-settings-phone");measure(root,1280,800);capture(root,"server-settings-tablet");assertEquals(before,prefs.getAll());
  }
 }
 @Test public void longerBackgroundInstructionsAreAvailableOnDemandAndLargeTextStillFits()throws Exception{
  float font=RuntimeEnvironment.getFontScale();try{RuntimeEnvironment.setFontScale(2);
   try(var screen=Robolectric.buildActivity(EmbeddedServerActivity.class).setup()){
    View root=root(screen.get());measure(root,320,568);verifyRows(root);Button help=find(root,"Работа в фоне");assertNotNull(help);help.performClick();android.app.AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();assertNotNull(dialog);assertTrue(Shadows.shadowOf(dialog).getMessage().toString().contains("перезагрузки"));dialog.dismiss();
   }
   try(var screen=Robolectric.buildActivity(SettingsActivity.class).setup()){View root=root(screen.get());measure(root,320,568);verifyRows(root);}
  }finally{RuntimeEnvironment.setFontScale(font);}
 }
 private void verifyRows(View view){if(view.getVisibility()!=View.VISIBLE)return;if(view instanceof TextView&&view instanceof Button){TextView t=(TextView)view;assertTrue(t.getHeight()>=56);assertNotNull(t.getLayout());int last=t.getLayout().getLineCount()-1;assertEquals(t.getText().length(),t.getLayout().getLineEnd(last));assertEquals(0,t.getLayout().getEllipsisCount(last));assertTrue(t.getLayout().getHeight()<=t.getHeight()-t.getCompoundPaddingTop()-t.getCompoundPaddingBottom());}if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)verifyRows(((ViewGroup)view).getChildAt(i));}
 private Button find(View v,String text){if(v instanceof Button&&text.contentEquals(((Button)v).getText()))return (Button)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){Button b=find(((ViewGroup)v).getChildAt(i),text);if(b!=null)return b;}return null;}
 private View root(Activity a){return ((ViewGroup)a.findViewById(android.R.id.content)).getChildAt(0);}
 private void measure(View v,int w,int h){v.measure(View.MeasureSpec.makeMeasureSpec(w,1073741824),View.MeasureSpec.makeMeasureSpec(h,1073741824));v.layout(0,0,w,h);}
 private void capture(View root,String name)throws Exception{Bitmap b=Bitmap.createBitmap(root.getWidth(),root.getHeight(),Bitmap.Config.ARGB_8888);root.draw(new Canvas(b));File dir=new File("build/ui-previews/detail-polish-r6-20261025");dir.mkdirs();try(var out=new FileOutputStream(new File(dir,name+".png"))){assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,out));}b.recycle();}
}
