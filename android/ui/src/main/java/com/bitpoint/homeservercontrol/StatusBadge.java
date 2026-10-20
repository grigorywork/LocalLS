package com.bitpoint.homeservercontrol;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.widget.TextView;

/** A status remains readable without colour vision: its text always names the state. */
final class StatusBadge {
    enum Tone { SUCCESS, WARNING, ERROR, NEUTRAL }
    static void apply(TextView view,Tone tone) {
        int panel=ThemeCatalog.color(view.getContext(),com.bitpoint.homeservercontrol.ui.R.attr.hscBackground);
        boolean dark=(Color.red(panel)*299+Color.green(panel)*587+Color.blue(panel)*114)<128000;
        int foreground,background;
        switch(tone) {
            case SUCCESS:foreground=Color.parseColor(dark?"#94F0B3":"#185A30");background=Color.parseColor(dark?"#153D2A":"#E5F5EB");break;
            case WARNING:foreground=Color.parseColor(dark?"#FFE199":"#735000");background=Color.parseColor(dark?"#443716":"#FFF4D6");break;
            case ERROR:foreground=Color.parseColor(dark?"#FFB7BF":"#9B1C31");background=Color.parseColor(dark?"#4A1E25":"#FDE9EA");break;
            default:foreground=ThemeCatalog.color(view.getContext(),com.bitpoint.homeservercontrol.ui.R.attr.hscTextSecondary);background=ThemeCatalog.color(view.getContext(),com.bitpoint.homeservercontrol.ui.R.attr.hscPanel);
        }
        view.setTextColor(foreground);
        int token=tone.ordinal()+(dark?10:20);
        if(Integer.valueOf(token).equals(view.getTag(com.bitpoint.homeservercontrol.ui.R.id.statusBadgeStyle)))return;
        view.setTag(com.bitpoint.homeservercontrol.ui.R.id.statusBadgeStyle,token);
        float density=view.getResources().getDisplayMetrics().density;
        GradientDrawable shape=new GradientDrawable();shape.setColor(background);shape.setCornerRadius(10*density);shape.setStroke(Math.max(1,Math.round(density)),foreground);
        view.setBackground(shape);view.setTextColor(foreground);view.setTypeface(view.getTypeface(),Typeface.BOLD);
        int padding=Math.round(12*density);view.setPadding(padding,padding,padding,padding);
        view.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);
    }
}
