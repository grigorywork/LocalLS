package com.bitpoint.homeservercontrol;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

/** The number is centered in the card; the leading glyph never shifts its text column. */
final class MetricValueRow extends FrameLayout {
    private final ImageView glyph;
    private final TextView value;private int preferredWidth,preferredHeight;
    MetricValueRow(Context c,TextView value,Drawable image,int width,int height){
        super(c);this.value=value;preferredWidth=width;preferredHeight=height;value.setCompoundDrawables(null,null,null,null);value.setGravity(Gravity.CENTER);
        value.setIncludeFontPadding(false);addView(value,new LayoutParams(-1,-1));
        glyph=new ImageView(c);glyph.setImageDrawable(image);glyph.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(glyph,new LayoutParams(SettingsUi.dp(c,width),SettingsUi.dp(c,height),Gravity.START|Gravity.CENTER_VERTICAL));
    }
    void glyphSize(int width,int height){preferredWidth=width;preferredHeight=height;}
    @Override protected void onMeasure(int width,int height){
        int desired=SettingsUi.dp(getContext(),preferredWidth);
        float textWidth=Math.max(value.getPaint().measureText("100%"),value.getPaint().measureText(value.getText().toString()));
        int room=Math.max(SettingsUi.dp(getContext(),12),(int)((MeasureSpec.getSize(width)-textWidth)/2)-SettingsUi.dp(getContext(),8));
        LayoutParams p=(LayoutParams)glyph.getLayoutParams();int glyphWidth=Math.min(desired,room),glyphHeight=Math.round(SettingsUi.dp(getContext(),preferredHeight)*glyphWidth/(float)desired);
        if(p.width!=glyphWidth||p.height!=glyphHeight){p.width=glyphWidth;p.height=glyphHeight;glyph.setLayoutParams(p);}
        super.onMeasure(width,height);
    }
}
