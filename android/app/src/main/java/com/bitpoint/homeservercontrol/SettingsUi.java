package com.bitpoint.homeservercontrol;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Readable settings sections, using the same surfaces and palette as the home screen. */
final class SettingsUi {
    static int dp(Context c,float value){return Math.round(value*c.getResources().getDisplayMetrics().density);}
    static void surface(View view){view.setBackground(SurfaceStyle.panel(view.getContext()));}
    static LinearLayout section(LinearLayout parent,String title){
        Context c=parent.getContext();LinearLayout section=new LinearLayout(c);section.setOrientation(LinearLayout.VERTICAL);
        surface(section);section.setPadding(dp(c,12),dp(c,8),dp(c,12),dp(c,8));
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.topMargin=dp(c,8);parent.addView(section,params);
        TextView heading=new TextView(c);heading.setText(title);heading.setTextSize(18);heading.setTypeface(null,Typeface.BOLD);
        heading.setTextColor(ThemeCatalog.color(c,com.bitpoint.homeservercontrol.ui.R.attr.hscTextPrimary));heading.setIncludeFontPadding(false);
        heading.setPadding(0,dp(c,4),0,dp(c,8));heading.setGravity(Gravity.CENTER_VERTICAL);section.addView(heading,new LinearLayout.LayoutParams(-1,-2));return section;
    }
    static void note(TextView view){
        Context c=view.getContext();view.setTextSize(14);view.setTextColor(ThemeCatalog.color(c,com.bitpoint.homeservercontrol.ui.R.attr.hscTextSecondary));
        view.setIncludeFontPadding(false);view.setLineSpacing(dp(c,2),1);view.setPadding(0,dp(c,4),0,dp(c,8));
    }
}
