package com.bitpoint.homeservercontrol;

import android.content.*;import android.content.res.Configuration;import android.graphics.*;import android.view.*;import android.widget.*;
import java.io.*;import java.util.*;
import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.*;import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

/** Regression for real-density phone viewports: never drop reference sections to fit preferred heights. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=30,qualifiers="ru-rRU-w411dp-h890dp-mdpi") @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ReferenceLayoutTest {
 @Test public void completeServerReferenceFitsPhonesAtRealPixelDensities()throws Exception{
  for(int density:new int[]{160,320,480})for(int[] size:new int[][]{{360,728},{360,744},{360,800},{393,780},{393,852},{411,818},{411,890}})render(density,size[0],size[1],false);
 }
 @Test public void completeServerReferenceFitsTabletColumns()throws Exception{render(160,1280,800,true);render(320,960,600,true);}
 private void render(int density,int width,int height,boolean tablet)throws Exception{
  Context app=RuntimeEnvironment.getApplication();Configuration config=new Configuration(app.getResources().getConfiguration());config.densityDpi=density;config.fontScale=1;config.screenWidthDp=width;config.screenHeightDp=height;
  Context context=new ContextThemeWrapper(app.createConfigurationContext(config),com.bitpoint.homeservercontrol.ui.R.style.Theme_LocalLS_Ocean);float d=context.getResources().getDisplayMetrics().density;assertEquals(density/160f,d,.001f);
  View root=LayoutInflater.from(context).inflate(R.layout.activity_main,null);ServerHomeLayout home=root.findViewById(R.id.serverHome);
  try(var controller=Robolectric.buildActivity(android.app.Activity.class).setup()){controller.get().setContentView(root);
   root.findViewById(R.id.dashboardScroll).setVisibility(View.GONE);home.setVisibility(View.VISIBLE);root.findViewById(R.id.homeClientTab).setVisibility(View.GONE);((TextView)root.findViewById(R.id.homeServerTab)).setText("Сервер\nЭтот телефон");IconButtons.decorate(root);((Button)root.findViewById(R.id.serverPowerButton)).setText("Остановить");root.findViewById(R.id.homeServerTab).setBackground(SurfaceStyle.panel(context));
   List<ServerDeviceRow> rows=ReflectionHelpers.getField(home,"deviceRows");ReflectionHelpers.setField(home,"allowedCount",3);String[] names={"Телефон","Ноутбук","Планшет"};for(int i=0;i<3;i++)rows.get(i).bind(names[i],i<2);
   ((TextView)root.findViewById(R.id.currentWifiIpValue)).setText("192.168.1.151");((TextView)root.findViewById(R.id.serverAccessSummary)).setText("Порт 8022 · localls");((TextView)root.findViewById(R.id.localServerSummary)).setText("● Сервер работает");((TextView)root.findViewById(R.id.serverPanelBattery)).setText("35%");((BatteryGaugeDrawable)ReflectionHelpers.getField(home,"gauge")).level(35);((TextView)root.findViewWithTag("charging")).setText("Заряжается");((TextView)root.findViewWithTag("disk_detail")).setText("свободно из 256 ГБ");((TextView)ReflectionHelpers.getField(home,"deviceTitle")).setText("Устройства с доступом");((TextView)root.findViewById(R.id.serverPanelDeviceCount)).setText("3 из 7");((TextView)root.findViewById(R.id.serverPanelSpace)).setText("137 ГБ");((TextView)root.findViewById(R.id.serverReceiveSummary)).setText("Загрузки / LocalLS");((TextView)root.findViewById(R.id.serverPanelReceipt)).setText("Фото.jpg\n12 МБ · Получено сейчас");
   int w=Math.round(width*d),h=Math.round(height*d);root.measure(exact(w),exact(h));root.layout(0,0,w,h);
   for(int id:new int[]{R.id.serverPowerButton,R.id.serverQrToggle,R.id.serverLocalFilesButton,R.id.serverReceiveButton,R.id.localServerQuickButton,R.id.serverBackgroundButton}){View action=root.findViewById(id);assertTrue("Full 48dp action at usable height "+height+"dp: "+id+" actual "+action.getHeight(),action.getHeight()>=Math.round(48*d));}
   for(ServerDeviceRow row:rows){assertEquals("Device row must remain visible at "+width+"dp / "+density+"dpi",View.VISIBLE,row.getVisibility());assertTrue(row.getHeight()>=Math.round(48*d));}
   for(String field:new String[]{"uptime","deviceTitle","receipt","folderTitle"}){View v=ReflectionHelpers.getField(home,field);assertTrue(field+" must remain visible",v.isShown());assertTrue(field+" must have text height",v.getHeight()>0);}
   if(tablet){View tabs=root.findViewById(R.id.homeRoleTabs),access=ReflectionHelpers.getField(home,"access"),status=ReflectionHelpers.getField(home,"status");assertTrue("Tablet host header only spans its left column",tabs.getWidth()<root.getWidth()/2+Math.round(16*d));assertTrue("Tablet status follows the left header",status.getTop()>=tabs.getBottom());assertTrue("QR starts beside the header",access.getTop()<tabs.getBottom());}
   Rect last=new Rect();View footer=ReflectionHelpers.getField(home,"footer");footer.getDrawingRect(last);((ViewGroup)root).offsetDescendantRectToMyCoords(footer,last);assertTrue("Reference must use available height, not leave blank bottom: "+last+" / "+h,h-last.bottom<Math.round(20*d));
   for(int id:new int[]{R.id.localServerSummary,R.id.currentWifiIpValue,R.id.serverAccessSummary,R.id.serverPanelBattery,R.id.serverPanelSpace,R.id.serverReceiveSummary})assertInsideParent(root.findViewById(id));
   assertInsideParent(root.findViewWithTag("charging"));assertInsideParent(root.findViewWithTag("disk_detail"));
   assertNoClipping(home,home);View access=ReflectionHelpers.getField(home,"access");Rect before=new Rect(access.getLeft(),access.getTop(),access.getRight(),access.getBottom());
   if(density==320){Bitmap bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);root.draw(new Canvas(bitmap));File dir=new File("build/ui-previews/reference-correction-20261024");dir.mkdirs();try(var out=new FileOutputStream(new File(dir,(tablet?"tablet-":"phone-")+width+".png"))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}bitmap.recycle();}
   // Visibility alone must not reorder or expand cards; QR content is exercised by ApprovedDesignTest.
   ((TextView)root.findViewById(R.id.serverPanelQrCaption)).setText("Сканируйте QR");((Button)root.findViewById(R.id.serverQrToggle)).setText("Скрыть QR");root.measure(exact(w),exact(h));root.layout(0,0,w,h);assertEquals(before,new Rect(access.getLeft(),access.getTop(),access.getRight(),access.getBottom()));
  }finally{home.close();}
 }
 private void assertInsideParent(View view){ViewGroup parent=(ViewGroup)view.getParent();assertTrue("Content cropped by card: "+view+" at "+view.getBottom()+" / "+parent.getHeight(),view.getTop()>=parent.getPaddingTop()&&view.getBottom()<=parent.getHeight()-parent.getPaddingBottom());if(view instanceof TextView){TextView label=(TextView)view;assertTrue("Text height is clipped: "+label.getText(),label.getHeight()>=label.getLineHeight()+label.getPaddingTop()+label.getPaddingBottom());}}
 private void assertNoClipping(View v,ViewGroup boundary){if(v.getVisibility()!=View.VISIBLE)return;Rect rect=new Rect();v.getDrawingRect(rect);boundary.offsetDescendantRectToMyCoords(v,rect);assertTrue("Outside server viewport "+v+" "+rect,rect.top>=0&&rect.left>=0&&rect.bottom<=boundary.getHeight()&&rect.right<=boundary.getWidth());if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)assertNoClipping(((ViewGroup)v).getChildAt(i),boundary);}
 private static int exact(int n){return View.MeasureSpec.makeMeasureSpec(n,View.MeasureSpec.EXACTLY);}
}
