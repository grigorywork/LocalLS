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

    private static String key(String host, int port) {
        return ServerConfig.KEY_TRUSTED_FINGERPRINT_PREFIX + host + ":" + port;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(ServerConfig.PREFS, Context.MODE_PRIVATE);
    }
}
