package com.bitpoint.homeservercontrol;

import com.bitpoint.homeservercontrol.ui.R;

import android.content.Context;
import android.util.TypedValue;

/** Semantic colours shared by XML, charts and dynamically created views. */
final class ThemeCatalog {
    static final String KEY = "appearance_theme";
    static final String[] IDS = {"purple", "ocean", "emerald", "amber", "light"};
    static final String[] NAMES = {"Фиолетовая", "Океан", "Изумруд", "Янтарь", "Светлая"};
    private static final int[] STYLES = {R.style.Theme_HomeServerControl,
            R.style.Theme_LocalLS_Ocean, R.style.Theme_LocalLS_Emerald,
            R.style.Theme_LocalLS_Amber, R.style.Theme_LocalLS_Light};

    private ThemeCatalog() {}

    static int index(Context context) {
        String id = context.getSharedPreferences(ServerConfig.PREFS, Context.MODE_PRIVATE).getString(KEY, IDS[0]);
        for (int i = 0; i < IDS.length; i++) if (IDS[i].equals(id)) return i;
        return 0;
    }

    static int style(Context context) { return STYLES[index(context)]; }

    static void select(Context context, int index) {
        if (index < 0 || index >= IDS.length) return;
        context.getSharedPreferences(ServerConfig.PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY, IDS[index]).apply();
    }

    static int color(Context context, int attribute) {
        TypedValue value = new TypedValue();
        if (!context.getTheme().resolveAttribute(attribute, value, true))
            throw new IllegalArgumentException("Missing theme colour");
        return value.resourceId != 0 ? context.getColor(value.resourceId) : value.data;
    }
}
