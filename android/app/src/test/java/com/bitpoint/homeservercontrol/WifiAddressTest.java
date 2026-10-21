package com.bitpoint.homeservercontrol;

import android.content.*;
import android.net.*;
import android.os.Looper;
import android.widget.TextView;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.*;
import org.robolectric.util.ReflectionHelpers;
import java.net.InetAddress;
import java.time.Duration;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=30)
public class WifiAddressTest {
    private Context app;private ShadowConnectivityManager manager;
    @Before public void setup(){
        app=RuntimeEnvironment.getApplication();manager=Shadows.shadowOf((ConnectivityManager)app.getSystemService(Context.CONNECTIVITY_SERVICE));
        manager.clearAllNetworks();
        EmbeddedServerService.running=false;EmbeddedServerService.starting=false;EmbeddedServerService.launchRequested=0;
    }
    private Network network(int id,int[] transports,String... addresses)throws Exception{
        Network network=ShadowNetwork.newInstance(id);
        manager.addNetwork(network,ShadowNetworkInfo.newInstance(NetworkInfo.DetailedState.CONNECTED,ConnectivityManager.TYPE_WIFI,0,true,true));
        NetworkCapabilities caps=ShadowNetworkCapabilities.newInstance();
        for(int transport:transports)Shadows.shadowOf(caps).addTransportType(transport);
        manager.setNetworkCapabilities(network,caps);properties(network,addresses);return network;
    }
    private void properties(Network network,String... addresses)throws Exception{
        LinkProperties properties=new LinkProperties();
        for(String ip:addresses){
            InetAddress value=InetAddress.getByName(ip);
            LinkAddress address=ReflectionHelpers.callConstructor(LinkAddress.class,ReflectionHelpers.ClassParameter.from(InetAddress.class,value),ReflectionHelpers.ClassParameter.from(int.class,value instanceof java.net.Inet4Address?24:64));
            ReflectionHelpers.callInstanceMethod(properties,"addLinkAddress",ReflectionHelpers.ClassParameter.from(LinkAddress.class,address));
        }
        manager.setLinkProperties(network,properties);
    }
    private String clipboard(){return ((ClipboardManager)app.getSystemService(Context.CLIPBOARD_SERVICE)).getPrimaryClip().getItemAt(0).getText().toString();}
    private void sentinel(){((ClipboardManager)app.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("existing","keep"));}
    @Test public void mainScreenShowsOwnWifiIpBeforeStartingServerAndCopiesOnlyAddress()throws Exception{
        network(1,new int[]{NetworkCapabilities.TRANSPORT_WIFI},"192.0.2.14");
        app.getSharedPreferences(ServerConfig.PREFS,0).edit().putString(ServerConfig.KEY_LOCAL_HOST,"198.51.100.20").commit();
        try(var c=Robolectric.buildActivity(MainActivity.class).create()){
            MainActivity a=c.get();assertEquals("192.0.2.14",((TextView)a.findViewById(R.id.currentWifiIpValue)).getText().toString());
            a.findViewById(R.id.copyCurrentWifiIp).performClick();assertEquals("192.0.2.14",clipboard());
            assertEquals("198.51.100.20",app.getSharedPreferences(ServerConfig.PREFS,0).getString(ServerConfig.KEY_LOCAL_HOST,""));
            assertFalse(EmbeddedServerService.running);
        }
    }
    @Test public void wifiChangeRefreshesVisibleScreenAndDisconnectRemovesOldIp()throws Exception{
        Network wifi=network(2,new int[]{NetworkCapabilities.TRANSPORT_WIFI},"192.0.2.21");
        try(var c=Robolectric.buildActivity(MainActivity.class).create().start().resume()){
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            properties(wifi,"198.51.100.21");Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
            assertEquals("198.51.100.21",((TextView)c.get().findViewById(R.id.currentWifiIpValue)).getText().toString());
            manager.removeNetwork(wifi);Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
            assertFalse(c.get().findViewById(R.id.copyCurrentWifiIp).isEnabled());
            assertFalse(((TextView)c.get().findViewById(R.id.currentWifiIpValue)).getText().toString().contains("198.51.100.21"));
        }
    }
    @Test public void copyRechecksAddressInsteadOfSilentlyCopyingStaleValue()throws Exception{
        Network wifi=network(3,new int[]{NetworkCapabilities.TRANSPORT_WIFI},"192.0.2.31");
        try(var c=Robolectric.buildActivity(EmbeddedServerActivity.class).create()){
            sentinel();properties(wifi,"198.51.100.31");
            c.get().findViewById(R.id.copyCurrentWifiIp).performClick();assertEquals("keep",clipboard());
            assertEquals("198.51.100.31",((TextView)c.get().findViewById(R.id.currentWifiIpValue)).getText().toString());
            c.get().findViewById(R.id.copyCurrentWifiIp).performClick();assertEquals("198.51.100.31",clipboard());
        }
    }
    @Test public void cellularVpnAndUnusableAddressesAreNotShownAsWifiIp()throws Exception{
        network(4,new int[]{NetworkCapabilities.TRANSPORT_CELLULAR},"198.51.100.44");
        network(5,new int[]{NetworkCapabilities.TRANSPORT_WIFI,NetworkCapabilities.TRANSPORT_VPN},"198.51.100.55");
        network(6,new int[]{NetworkCapabilities.TRANSPORT_WIFI},"127.0.0.1","169.254.4.5","0.0.0.0","fe80::1","192.0.2.60");
        assertEquals(Collections.singletonList("192.0.2.60"),LocalServerAddresses.current(app));
    }
    @Test public void multipleWifiAddressesRequireChoiceAndCopyOnlyChosenCurrentIp()throws Exception{
        Network wifi=network(7,new int[]{NetworkCapabilities.TRANSPORT_WIFI},"192.0.2.71","192.0.2.72");
        try(var c=Robolectric.buildActivity(EmbeddedServerActivity.class).create()){
            sentinel();c.get().findViewById(R.id.copyCurrentWifiIp).performClick();
            var dialog=ShadowAlertDialog.getLatestAlertDialog();assertNotNull(dialog);assertEquals("keep",clipboard());
            dialog.getListView().performItemClick(dialog.getListView().getChildAt(1),1,1);assertEquals("192.0.2.72",clipboard());
            c.get().findViewById(R.id.copyCurrentWifiIp).performClick();dialog=ShadowAlertDialog.getLatestAlertDialog();
            properties(wifi,"192.0.2.71");sentinel();dialog.getListView().performItemClick(dialog.getListView().getChildAt(1),1,1);
            assertEquals("keep",clipboard());
        }
    }
    @Test public void missingWifiDoesNotCopyOldClipboardOrStartServer(){
        sentinel();try(var c=Robolectric.buildActivity(EmbeddedServerActivity.class).create()){
            assertFalse(c.get().findViewById(R.id.copyCurrentWifiIp).isEnabled());
            c.get().findViewById(R.id.copyCurrentWifiIp).performClick();assertEquals("keep",clipboard());
            assertFalse(EmbeddedServerService.running);
        }
    }
}
