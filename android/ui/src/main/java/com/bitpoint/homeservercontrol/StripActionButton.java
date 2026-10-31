package com.bitpoint.homeservercontrol;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.widget.Button;

/** Centers the horizontal icon/caption together, independently of TextView's compound slots. */
public final class StripActionButton extends Button {
    public StripActionButton(Context c){super(c);}
    @Override protected void onDraw(Canvas canvas){
        Drawable icon=getCompoundDrawablesRelative()[0];String text=getText().toString();TextPaint paint=getPaint();paint.setColor(getCurrentTextColor());
        float gap=icon==null?0:getCompoundDrawablePadding(),iw=icon==null?0:icon.getBounds().width();
        int available=Math.max(1,(int)(getWidth()-getPaddingLeft()-getPaddingRight()-iw-gap));
        int width=Math.max(1,Math.min(available,(int)Math.ceil(paint.measureText(text))));
        StaticLayout label=StaticLayout.Builder.obtain(text,0,text.length(),paint,width)
                .setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(false)
                .setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE).setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
                .setMaxLines(2).build();
        float x=getPaddingLeft()+(getWidth()-getPaddingLeft()-getPaddingRight()-iw-gap-width)/2f;
        if(icon!=null){int saved=canvas.save();canvas.translate(x,(getHeight()-icon.getBounds().height())/2f);icon.setState(getDrawableState());icon.draw(canvas);canvas.restoreToCount(saved);}
        int saved=canvas.save();canvas.translate(x+iw+gap,(getHeight()-label.getHeight())/2f);label.draw(canvas);canvas.restoreToCount(saved);
    }
}
