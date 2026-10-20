package com.bitpoint.homeservercontrol;

import android.content.Context;
import android.util.AttributeSet;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.widget.Button;

/** Draws icon and caption as one vertically/horizontally centered unit. Drawer rows stay native. */
public final class CenteredActionButton extends Button {
    private Drawable tileIcon;
    public CenteredActionButton(Context c,AttributeSet a) {super(c,a);}
    public CenteredActionButton(Context c) {super(c);}
    void setTileIcon(Drawable icon) {tileIcon=icon;setCompoundDrawables(null,null,null,null);requestLayout();invalidate();}
    boolean hasTileIcon() {return tileIcon!=null;}
    RectF contentBounds() {
        Layout layout=getLayout();if(tileIcon==null || layout==null)return new RectF();
        float height=tileIcon.getBounds().height()+getCompoundDrawablePadding()+layout.getHeight();
        float top=getPaddingTop()+(getHeight()-getPaddingTop()-getPaddingBottom()-height)/2f;
        return new RectF(getPaddingLeft(),top,getWidth()-getPaddingRight(),top+height);
    }
    @Override protected void onMeasure(int width,int height) {
        super.onMeasure(width,height);
        if(tileIcon!=null && getLayout()!=null) {
            int wanted=tileIcon.getBounds().height()+getCompoundDrawablePadding()+getLayout().getHeight()+getPaddingTop()+getPaddingBottom();
            setMeasuredDimension(getMeasuredWidthAndState(),resolveSizeAndState(Math.max(wanted,getMeasuredHeight()),height,0));
        }
    }
    @Override protected void onDraw(Canvas canvas) {
        if(tileIcon==null || getLayout()==null) {super.onDraw(canvas);return;}
        RectF bounds=contentBounds();int saved=canvas.save();
        tileIcon.setState(getDrawableState());
        canvas.translate((getWidth()-tileIcon.getBounds().width())/2f,bounds.top);tileIcon.draw(canvas);canvas.restoreToCount(saved);
        saved=canvas.save();
        canvas.translate((getWidth()-getLayout().getWidth())/2f,bounds.top+tileIcon.getBounds().height()+getCompoundDrawablePadding());
        getPaint().setColor(getCurrentTextColor());getPaint().drawableState=getDrawableState();getLayout().draw(canvas);canvas.restoreToCount(saved);
    }
}
