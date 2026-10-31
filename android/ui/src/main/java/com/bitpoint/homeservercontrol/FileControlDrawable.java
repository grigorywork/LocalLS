package com.bitpoint.homeservercontrol;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;

/** File actions share the chosen translucent palette, outline and outer button geometry. */
final class FileControlDrawable extends GradientDrawable {
    FileControlDrawable(Context c,boolean selected){
        boolean light=ThemeCatalog.lightGlass(c);int accent=ThemeCatalog.color(c,com.bitpoint.homeservercontrol.ui.R.attr.hscAccent);
        int[] colors=selected?new int[]{(accent&0xFFFFFF)|0x50000000,(accent&0xFFFFFF)|0x28000000}:light?new int[]{0xCCFFFFFF,0xCCFFFFFF}:new int[]{0xC02D343F,0x9811151D};
        setOrientation(Orientation.TL_BR);setColors(colors);float d=c.getResources().getDisplayMetrics().density;
        setStroke(Math.max(1,Math.round(.75f*d)),selected?accent:SurfaceStyle.lineColor(c));
        setCornerRadius(9*d);
    }
    @Override public void setTintList(ColorStateList ignored){}
    @Override public boolean isStateful(){return true;}
    @Override protected boolean onStateChange(int[] state){boolean enabled=false,pressed=false;for(int s:state){enabled|=s==android.R.attr.state_enabled;pressed|=s==android.R.attr.state_pressed||s==android.R.attr.state_focused;}setAlpha(enabled?(pressed?220:255):120);return true;}
}
