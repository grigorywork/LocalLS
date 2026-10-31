package com.bitpoint.homeservercontrol;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.Drawable;

/** A quiet 28dp visible strip inside a full 48dp action target; deliberately no stroke. */
final class StripActionDrawable extends Drawable {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final float density;private final int color;private int alpha=255;
    StripActionDrawable(Context c){density=c.getResources().getDisplayMetrics().density;color=ThemeCatalog.color(c,com.bitpoint.homeservercontrol.ui.R.attr.hscTextSecondary);}
    @Override public void draw(Canvas canvas){boolean pressed=false,enabled=false;for(int s:getState()){pressed|=s==android.R.attr.state_pressed||s==android.R.attr.state_focused;enabled|=s==android.R.attr.state_enabled;}RectF r=new RectF(getBounds());float inset=Math.max(0,(r.height()-28*density)/2);r.inset(0,inset);paint.setColor(color);paint.setAlpha(alpha*(enabled?(pressed?52:20):10)/255);canvas.drawRoundRect(r,6*density,6*density,paint);}
    @Override public boolean isStateful(){return true;}@Override protected boolean onStateChange(int[] s){invalidateSelf();return true;}
    @Override public void setTintList(ColorStateList ignored){}@Override public void setAlpha(int a){alpha=a;invalidateSelf();}@Override public int getAlpha(){return alpha;}@Override public void setColorFilter(ColorFilter f){paint.setColorFilter(f);}@Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
