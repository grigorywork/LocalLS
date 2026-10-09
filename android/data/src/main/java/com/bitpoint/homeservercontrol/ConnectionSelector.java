package com.bitpoint.homeservercontrol;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

final class ConnectionSelector {
    private ConnectionSelector() {}

    static String selectedHost(Context context) {
        SharedPreferences prefs = prefs(context);
        String mode = prefs.getString(ServerConfig.KEY_MODE, ServerConfig.MODE_LOCAL);
        return ServerConfig.MODE_VPN.equals(mode)
                ? trim(prefs.getString(ServerConfig.KEY_VPN_HOST, ""))
                : trim(prefs.getString(ServerConfig.KEY_LOCAL_HOST, ServerConfig.DEFAULT_LOCAL_HOST));
    }

    static String alternateHost(Context context) {
        SharedPreferences prefs = prefs(context);
        String mode = prefs.getString(ServerConfig.KEY_MODE, ServerConfig.MODE_LOCAL);
        return ServerConfig.MODE_VPN.equals(mode)
                ? trim(prefs.getString(ServerConfig.KEY_LOCAL_HOST, ServerConfig.DEFAULT_LOCAL_HOST))
                : trim(prefs.getString(ServerConfig.KEY_VPN_HOST, ""));
    }

    static String hostForFiles(Context context) {
        String selected = selectedHost(context);
        if (!TextUtils.isEmpty(selected)) return selected;
        if (!autoFallback(context)) return selected;
        return trim(prefs(context).getString(ServerConfig.KEY_LAST_WORKING_HOST, ""));
    }

    static void rememberWorkingHost(Context context, String host) {
        if (TextUtils.isEmpty(host)) return;
        prefs(context).edit().putString(ServerConfig.KEY_LAST_WORKING_HOST, host.trim()).apply();
    }

    static boolean autoFallback(Context context) {
        return prefs(context).getBoolean(ServerConfig.KEY_AUTO_FALLBACK, false);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(ServerConfig.PREFS, Context.MODE_PRIVATE);
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
