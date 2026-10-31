package com.bitpoint.homeservercontrol;

import com.bitpoint.homeservercontrol.ui.R;

import android.content.Context;
import android.util.TypedValue;

/** Semantic colours shared by XML, charts and dynamically created views. */
final class ThemeCatalog {
    static final String KEY = "appearance_theme";
    static final String[] IDS = {"purple", "ocean", "emerald", "amber", "light", "ruby", "turquoise", "silver"};
    static final String[] NAMES = {"Фиолетовая", "Океан", "Изумруд", "Янтарь", "Светлый фон", "Красная", "Бирюзовая", "Серебристая"};
    private static final int[] STYLES = {R.style.Theme_HomeServerControl,
            R.style.Theme_LocalLS_Ocean, R.style.Theme_LocalLS_Emerald,
            R.style.Theme_LocalLS_Amber, R.style.Theme_LocalLS_Light, R.style.Theme_LocalLS_Ruby, R.style.Theme_LocalLS_Turquoise, R.style.Theme_LocalLS_Silver};

    static final String GLASS_KEY="appearance_glass", OUTLINE_KEY="appearance_outline";
    private ThemeCatalog() {}
    static boolean glass(Context c){return c.getSharedPreferences(ServerConfig.PREFS,0).getBoolean(GLASS_KEY,false);}
    static boolean lightOutline(Context c){return c.getSharedPreferences(ServerConfig.PREFS,0).getBoolean(OUTLINE_KEY,false);}
    static void glass(Context c,boolean enabled){c.getSharedPreferences(ServerConfig.PREFS,0).edit().putBoolean(GLASS_KEY,enabled).apply();}
    static void lightOutline(Context c,boolean enabled){c.getSharedPreferences(ServerConfig.PREFS,0).edit().putBoolean(OUTLINE_KEY,enabled).apply();}
    static boolean lightGlass(Context c){return glass(c)&&index(c)==4;}
    static int signature(Context c){return style(c)*31+(glass(c)?2:0)+(lightOutline(c)?1:0);}
    static void applyOverlays(Context c){if(glass(c))c.getTheme().applyStyle(lightGlass(c)?R.style.ThemeOverlay_LocalLS_LightGlass:R.style.ThemeOverlay_LocalLS_Glass,true);if(lightOutline(c))c.getTheme().applyStyle(R.style.ThemeOverlay_LocalLS_LightOutline,true);}
    static void surfaces(android.view.View v){
        Context c=v.getContext();if(!glass(c)&&!lightOutline(c))return;
        if(v instanceof android.view.ViewGroup){
            if(v.getBackground() instanceof android.graphics.drawable.GradientDrawable&&!SurfaceStyle.owned(v.getBackground())){
                int l=v.getPaddingLeft(),t=v.getPaddingTop(),r=v.getPaddingRight(),b=v.getPaddingBottom();v.setBackground(SurfaceStyle.panel(c));v.setPadding(l,t,r,b);
            }
            android.view.ViewGroup group=(android.view.ViewGroup)v;for(int i=0;i<group.getChildCount();i++)surfaces(group.getChildAt(i));
        }
    }

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
        if(attribute==R.attr.hscStroke&&lightOutline(context))return 0xFFBDC5D2;
        if(lightGlass(context)){
            if(attribute==R.attr.hscBackground)return 0xFFF3F4F6;
            if(attribute==R.attr.hscPanel)return 0xCCFFFFFF;
            if(attribute==R.attr.hscTextPrimary)return 0xFF20242B;
            if(attribute==R.attr.hscTextSecondary)return 0xFF59616D;
            if(attribute==R.attr.hscStroke)return 0xFFCAD0D8;
            if(attribute==R.attr.hscSecondaryButton)return 0xCCFFFFFF;
            if(attribute==R.attr.hscSuccess)return 0xFF176E42;
        }else if(glass(context)){
            if(attribute==R.attr.hscBackground)return android.graphics.Color.BLACK;
            if(attribute==R.attr.hscPanel)return 0xB8141820;
            if(attribute==R.attr.hscTextPrimary)return 0xFFF5F7FA;
            if(attribute==R.attr.hscTextSecondary)return 0xFFB3BBC7;
            if(attribute==R.attr.hscStroke)return 0xFF687484;
            if(attribute==R.attr.hscSecondaryButton)return 0x98252B37;
            if(attribute==R.attr.hscSuccess)return 0xFF51E58A;
        }
        TypedValue value = new TypedValue();
        if (!context.getTheme().resolveAttribute(attribute, value, true))
            throw new IllegalArgumentException("Missing theme colour");
        return value.resourceId != 0 ? context.getColor(value.resourceId) : value.data;
    }
}
