package com.bitpoint.homeservercontrol;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
@Config(sdk=30,qualifiers="ru-rRU-w360dp-h640dp-mdpi")
public class HomeServerMetricsTest {
 @Test public void batteryComesFromServerBatteryBroadcastAndUnknownRemainsUnknown(){
  Context app=RuntimeEnvironment.getApplication();
  for(int level:new int[]{73,0,100,-1}){
   app.sendStickyBroadcast(new Intent(Intent.ACTION_BATTERY_CHANGED).putExtra(BatteryManager.EXTRA_LEVEL,level).putExtra(BatteryManager.EXTRA_SCALE,100));
   ServerTelemetry data=DeviceTelemetry.read(app);assertEquals(level,data.battery);assertTrue(data.free>=0);assertTrue(data.total>=data.free);
  }
  for(int state:new int[]{BatteryManager.BATTERY_STATUS_UNKNOWN,BatteryManager.BATTERY_STATUS_CHARGING,BatteryManager.BATTERY_STATUS_DISCHARGING}){
   app.sendStickyBroadcast(new Intent(Intent.ACTION_BATTERY_CHANGED).putExtra(BatteryManager.EXTRA_STATUS,state));assertEquals(state==BatteryManager.BATTERY_STATUS_UNKNOWN?-1:state==BatteryManager.BATTERY_STATUS_CHARGING?1:0,DeviceTelemetry.read(app).charging);
  }
 }
 @Test public void homeHasFourPrimaryActionsAndServerMetricsResetWhenRouteChanges(){
  var app=RuntimeEnvironment.getApplication();app.getSharedPreferences(ServerConfig.PREFS,0).edit().clear().putInt(ServerConfig.KEY_CONFIG_VERSION,3).commit();app.getSharedPreferences(EmbeddedServerService.PREFS,0).edit().clear().commit();
  try(var screen=Robolectric.buildActivity(MainActivity.class).setup()){
   MainActivity a=screen.get();ViewGroup actions=a.findViewById(R.id.applicationToolsCard);assertEquals(4,actions.getChildCount());
   for(int id:new int[]{R.id.checkButton,R.id.filesButton,R.id.transferQueueButton,R.id.toolsMenuButton})assertSame(actions,a.findViewById(id).getParent());
   for(int id:new int[]{R.id.dashboardSpeedChart,R.id.dashboardLatencyChart})assertTrue(a.findViewById(id).isShown());
   a.findViewById(R.id.toolsMenuButton).performClick();for(int id:new int[]{R.id.attachFilesButton,R.id.networkButton,R.id.historyButton,R.id.diagnosticsButton,R.id.appSettingsButton})assertTrue(a.findViewById(id).isShown());
   ServerStats stats=new ServerStats();stats.authenticated=true;stats.reachable=true;stats.hostKeyTrusted=true;stats.sftpOnly=true;new ServerTelemetry(73,2147483648L,4294967296L,3660).apply(stats);
   ReflectionHelpers.callInstanceMethod(a,"renderStats",ClassParameter.from(ServerStats.class,stats),ClassParameter.from(String.class,"192.0.2.2"),ClassParameter.from(int.class,8022),ClassParameter.from(String.class,"localls"));
   assertEquals("73%",((TextView)a.findViewById(R.id.batteryValue)).getText().toString());assertEquals("2 ГБ",((TextView)a.findViewById(R.id.diskValue)).getText().toString());assertEquals("Свободное место сервера: 2.0 ГБ",a.findViewById(R.id.diskValue).getContentDescription());
   ReflectionHelpers.callInstanceMethod(a,"invalidateConnectionStats");assertFalse(((TextView)a.findViewById(R.id.batteryValue)).getText().toString().contains("73%"));assertFalse(((TextView)a.findViewById(R.id.diskValue)).getText().toString().contains("2.0"));assertEquals("—",((TextView)a.findViewById(R.id.batteryValue)).getText().toString());assertEquals("—",((TextView)a.findViewById(R.id.diskValue)).getText().toString());assertEquals("Заряд батареи сервера: —",a.findViewById(R.id.batteryValue).getContentDescription());assertEquals("Свободное место сервера: —",a.findViewById(R.id.diskValue).getContentDescription());
  }
 }
}
