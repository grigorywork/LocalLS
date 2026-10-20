package com.bitpoint.homeservercontrol;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.NetworkCapabilities;
import java.net.*;
import java.util.*;

/** Local display only. No discovery broadcasts, external services or persistent address storage. */
final class LocalServerAddresses {
    static List<String> current(Context context) {
        LinkedHashSet<String> addresses=new LinkedHashSet<>();
        try {
            ConnectivityManager manager=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
            for(android.net.Network network:manager.getAllNetworks()) {
                NetworkCapabilities capabilities=manager.getNetworkCapabilities(network);
                if(capabilities==null || !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                        || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN))continue;
                LinkProperties properties=manager.getLinkProperties(network);
                if(properties!=null)for(LinkAddress address:properties.getLinkAddresses())add(addresses,address.getAddress());
            }
        } catch(Exception ignored) { }
        // Some hotspot vendors do not expose the AP network through ConnectivityManager.
        if(addresses.isEmpty())try {
            Enumeration<NetworkInterface> interfaces=NetworkInterface.getNetworkInterfaces();
            while(interfaces.hasMoreElements()) {
                NetworkInterface network=interfaces.nextElement();String name=network.getName();
                if(!network.isUp() || network.isLoopback() || !(name.startsWith("wlan") || name.startsWith("ap") || name.startsWith("swlan")))continue;
                Enumeration<InetAddress> values=network.getInetAddresses();while(values.hasMoreElements())add(addresses,values.nextElement());
            }
        } catch(Exception ignored) { }
        return new ArrayList<>(addresses);
    }
    private static void add(Set<String> result,InetAddress value) {
        if(value instanceof Inet4Address && !value.isLoopbackAddress() && !value.isLinkLocalAddress()
                && !value.isAnyLocalAddress() && !value.isMulticastAddress())result.add(value.getHostAddress());
    }
}
