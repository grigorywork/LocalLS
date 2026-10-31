package com.bitpoint.homeservercontrol;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Transparent action row with a quiet inset separator and visible press/keyboard focus feedback. */
final class ListActionDrawable extends Drawable {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private final int divider,accent;
    private int alpha=255;
    private final boolean outlined,separator;
    private final float separatorStart,separatorEnd;
    ListActionDrawable(Context context){this(context,false);}
    ListActionDrawable(Context context,boolean outlined){
        this(context,outlined,true);
    }
    ListActionDrawable(Context context,boolean outlined,boolean separator){
        this(context,outlined,separator,52,12);
    }
    ListActionDrawable(Context context,boolean outlined,boolean separator,float start,float end){
        this.outlined=outlined;this.separator=separator;
        separatorStart=start;separatorEnd=end;
        density=context.getResources().getDisplayMetrics().density;
        divider=SurfaceStyle.lineColor(context);
        accent=ThemeCatalog.color(context,com.bitpoint.homeservercontrol.ui.R.attr.hscAccent);
    }
    @Override public boolean isStateful(){return true;}
    @Override protected boolean onStateChange(int[] state){invalidateSelf();return true;}
    // A persisted button tint belongs to the old filled controls, never to a list row.
    @Override public void setTintList(ColorStateList ignored){}
    @Override public void draw(Canvas canvas){
        Rect bounds=getBounds();boolean enabled=false,active=false;
        for(int state:getState()){enabled|=state==android.R.attr.state_enabled;active|=state==android.R.attr.state_pressed||state==android.R.attr.state_focused;}
        if(enabled&&active){paint.setColor(accent);paint.setAlpha(24*alpha/255);canvas.drawRect(bounds,paint);}
        if(outlined){paint.setColor(accent);paint.setAlpha((enabled?100:45)*alpha/255);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Math.max(1,density));RectF rect=new RectF(bounds);rect.inset(density,density);canvas.drawRoundRect(rect,8*density,8*density,paint);paint.setStyle(Paint.Style.FILL);return;}
        if(!separator)return;
        paint.setColor(divider);paint.setAlpha(Color.alpha(divider)*(enabled?255:136)*alpha/65025);
        float start=bounds.left+separatorStart*density,end=bounds.right-separatorEnd*density;
        if(getLayoutDirection()==android.view.View.LAYOUT_DIRECTION_RTL){start=bounds.left+separatorEnd*density;end=bounds.right-separatorStart*density;}
        if(end>start)canvas.drawRect(start,bounds.bottom-Math.max(1,.75f*density),end,bounds.bottom,paint);
    }
    @Override public boolean onLayoutDirectionChanged(int direction){invalidateSelf();return true;}
    @Override public void getOutline(Outline outline){outline.setEmpty();}
    @Override public void setAlpha(int value){alpha=Math.max(0,Math.min(255,value));invalidateSelf();}
    @Override public int getAlpha(){return alpha;}
    @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
