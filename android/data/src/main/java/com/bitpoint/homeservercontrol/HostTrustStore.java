package com.bitpoint.homeservercontrol;

import android.content.Context;
import android.content.SharedPreferences;

final class HostTrustStore {
    private HostTrustStore() {}

    static String get(Context context, String host, int port) {
        return prefs(context).getString(key(host, port), "");
    }

    static void trust(Context context, String host, int port, String fingerprint) {
        prefs(context).edit().putString(key(host, port), fingerprint).apply();
    }

    static void clear(Context context, String host, int port) {
        prefs(context).edit().remove(key(host, port)).apply();
    }

    /** Local trusted addresses only; never probes or discovers network devices. */
    static java.util.List<String> hostsForFingerprint(Context context,int port,String fingerprint) {
        java.util.Set<String> hosts=new java.util.TreeSet<>();
        if(fingerprint==null || fingerprint.isEmpty())return new java.util.ArrayList<>(hosts);
        String prefix=ServerConfig.KEY_TRUSTED_FINGERPRINT_PREFIX,suffix=":"+port;
        for(java.util.Map.Entry<String,?> entry:prefs(context).getAll().entrySet()) {
            String name=entry.getKey();
            if(!name.startsWith(prefix) || !name.endsWith(suffix) || !fingerprint.equals(entry.getValue()))continue;
            String host=name.substring(prefix.length(),name.length()-suffix.length());
            if(ConnectionEndpoint.validHost(host))hosts.add(host);
        }
        return new java.util.ArrayList<>(hosts);
    }

    private static String key(String host, int port) {
        return ServerConfig.KEY_TRUSTED_FINGERPRINT_PREFIX + host + ":" + port;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(ServerConfig.PREFS, Context.MODE_PRIVATE);
    }
}
