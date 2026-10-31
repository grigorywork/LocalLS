package com.bitpoint.homeservercontrol;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.LinearLayout;

/** Comfortable form width on tablets, with the same readable rows on phones. */
public final class SettingsColumn extends LinearLayout {
    public SettingsColumn(Context c){super(c);setOrientation(VERTICAL);}
    public SettingsColumn(Context c,AttributeSet a){super(c,a);}
    @Override protected void onMeasure(int width,int height){
        int edge=Math.max(SettingsUi.dp(getContext(),16),(MeasureSpec.getSize(width)-SettingsUi.dp(getContext(),720))/2);
        setPadding(edge,SettingsUi.dp(getContext(),16),edge,SettingsUi.dp(getContext(),16));super.onMeasure(width,height);
    }
}
