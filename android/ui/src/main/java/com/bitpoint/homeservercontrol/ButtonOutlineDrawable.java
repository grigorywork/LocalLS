package com.bitpoint.homeservercontrol;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;

/** A single rounded geometry and semantic fill, independent of the icon colour. */
final class ButtonOutlineDrawable extends Drawable {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private final int base,background,outline;private final boolean lightOutline;
    private int alpha=255;
    private ColorStateList tint;

    ButtonOutlineDrawable(Context context,Drawable original){
        lightOutline=ThemeCatalog.lightOutline(context);outline=SurfaceStyle.lineColor(context);density=context.getResources().getDisplayMetrics().density;
        boolean secondary=original instanceof RippleDrawable && ((RippleDrawable)original).getDrawable(0) instanceof GradientDrawable;
        base=original instanceof ButtonOutlineDrawable?((ButtonOutlineDrawable)original).base:
                ThemeCatalog.color(context,secondary?com.bitpoint.homeservercontrol.ui.R.attr.hscSecondaryButton:com.bitpoint.homeservercontrol.ui.R.attr.hscButton);
        background=ThemeCatalog.color(context,com.bitpoint.homeservercontrol.ui.R.attr.hscBackground);
    }
    @Override public void setTintList(ColorStateList value){tint=value;invalidateSelf();}
    @Override public boolean isStateful(){return true;}
    @Override protected boolean onStateChange(int[] state){invalidateSelf();return true;}
    private static int blend(int from,int to,float amount){
        return Color.rgb(Math.round(Color.red(from)*(1-amount)+Color.red(to)*amount),
                Math.round(Color.green(from)*(1-amount)+Color.green(to)*amount),
                Math.round(Color.blue(from)*(1-amount)+Color.blue(to)*amount));
    }
    private RectF rect(){RectF bounds=new RectF(getBounds());bounds.inset(density,density);return bounds;}
    private void color(int value){paint.setColor(value);paint.setAlpha(Color.alpha(value)*alpha/255);}
    @Override public void draw(Canvas canvas){
        boolean pressed=false,enabled=false;
        for(int state:getState()){pressed|=state==android.R.attr.state_pressed;enabled|=state==android.R.attr.state_enabled;}
        int fill=tint==null?base:tint.getColorForState(getState(),base);
        if(!enabled)fill=blend(fill,background,.55f);else if(pressed)fill=blend(fill,Color.WHITE,.14f);
        RectF bounds=rect();paint.setStyle(Paint.Style.FILL);color(fill);draw(canvas,bounds);
        if(lightOutline){
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(density);
            color(lightOutline?outline:blend(fill,Color.WHITE,.12f));draw(canvas,bounds);
        }
    }
    private void draw(Canvas canvas,RectF bounds){
        canvas.drawRoundRect(bounds,12*density,12*density,paint);
    }
    @Override public void getOutline(Outline outline){
        RectF bounds=rect();int left=(int)Math.ceil(bounds.left),top=(int)Math.ceil(bounds.top),right=(int)Math.floor(bounds.right),bottom=(int)Math.floor(bounds.bottom);
        outline.setRoundRect(left,top,right,bottom,12*density);
        outline.setAlpha(alpha/255f);
    }
    @Override public void setAlpha(int value){alpha=Math.max(0,Math.min(255,value));invalidateSelf();}
    @Override public int getAlpha(){return alpha;}
    @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
