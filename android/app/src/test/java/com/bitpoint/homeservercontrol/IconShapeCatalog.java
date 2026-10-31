package com.bitpoint.homeservercontrol;

import android.content.Context;

/** Test-only legacy preference fixture: production no longer reads or exposes this setting. */
final class IconShapeCatalog {
    static final String KEY = "icon_shape";
    static int index(Context context) {
        return Math.max(0, Math.min(3, context.getSharedPreferences(ServerConfig.PREFS, Context.MODE_PRIVATE).getInt(KEY, 0)));
    }
    static void select(Context context, int index) {
        context.getSharedPreferences(ServerConfig.PREFS, Context.MODE_PRIVATE).edit().putInt(KEY, Math.max(0, Math.min(3,index))).apply();
    }
}
