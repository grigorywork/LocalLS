package com.bitpoint.homeservercontrol;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import java.net.Inet4Address;
import java.net.Inet6Address;

/** Tailscale's public Android broadcast API. No imitation VPN or remote commands. */
final class VpnController {
    static final String PACKAGE = "com.tailscale.ipn";
    enum State { OFF, TAILSCALE, OTHER_VPN }
    enum Request { SENT, NOT_INSTALLED, UNSUPPORTED, FAILED }

    private VpnController() {}

    static Intent command(boolean connect) {
        return new Intent(PACKAGE + (connect ? ".CONNECT_VPN" : ".DISCONNECT_VPN"))
                .setPackage(PACKAGE);
    }

    static boolean installed(Context context) {
        try { context.getPackageManager().getApplicationInfo(PACKAGE, 0); return true; }
        catch (PackageManager.NameNotFoundException e) { return false; }
    }

    static Request request(Context context, boolean connect) {
        if (!installed(context)) return Request.NOT_INSTALLED;
        Intent intent = command(connect);
        if (context.getPackageManager().queryBroadcastReceivers(intent, 0).isEmpty()) return Request.UNSUPPORTED;
        try { context.sendBroadcast(intent); return Request.SENT; }
        catch (RuntimeException e) { return Request.FAILED; }
    }

    static State state(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return State.OFF;
        boolean other = false;
        try {
            int uid = installed(context) ? context.getPackageManager().getApplicationInfo(PACKAGE, 0).uid : -1;
            for (Network network : cm.getAllNetworks()) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(network);
                if (caps == null || !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue;
                other = true;
                if (Build.VERSION.SDK_INT >= 30 && caps.getOwnerUid() >= 0) {
                    if (uid >= 0 && caps.getOwnerUid() == uid) return State.TAILSCALE;
                    continue;
                }
                LinkProperties links = cm.getLinkProperties(network);
                // API 24–29 do not expose owner UID. Tailscale has documented CGNAT/ULA addresses.
                if (uid >= 0 && links != null) for (LinkAddress link : links.getLinkAddresses()) {
                    byte[] address = link.getAddress().getAddress();
                    if (link.getAddress() instanceof Inet4Address && (address[0] & 255) == 100
                            && (address[1] & 192) == 64) return State.TAILSCALE;
                    if (link.getAddress() instanceof Inet6Address && (address[0] & 255) == 0xfd
                            && (address[1] & 255) == 0x7a && (address[2] & 255) == 0x11
                            && (address[3] & 255) == 0x5c && (address[4] & 255) == 0xa1
                            && (address[5] & 255) == 0xe0) return State.TAILSCALE;
                }
            }
        } catch (Exception ignored) { /* Permission/policy changes must not crash UI. */ }
        return other ? State.OTHER_VPN : State.OFF;
    }
}
