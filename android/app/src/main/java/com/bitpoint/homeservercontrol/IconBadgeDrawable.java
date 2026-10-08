package com.bitpoint.homeservercontrol;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/** A selectable frame around the function glyph, not a launcher mask. */
final class IconBadgeDrawable extends Drawable {
    private final Drawable glyph;
    private final int shape;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
    IconBadgeDrawable(Drawable glyph, int shape) {
        this.glyph = glyph; this.shape = shape;
        fill.setColor(0x18ffffff);
        outline.setColor(0x33000000); outline.setStyle(Paint.Style.STROKE);
    }
    @Override public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        float stroke = Math.max(1f,bounds.width()/24f);
        outline.setStrokeWidth(stroke);
        RectF box = new RectF(bounds);box.inset(stroke/2,stroke/2);
        if (shape == 1) {
            canvas.drawOval(box,fill);canvas.drawOval(box,outline);
        } else {
            float radius = shape == 2 ? box.width()/5 : 0;
            canvas.drawRoundRect(box,radius,radius,fill);canvas.drawRoundRect(box,radius,radius,outline);
        }
        int pad=Math.round(bounds.width()*.17f);
        glyph.setBounds(bounds.left+pad,bounds.top+pad,bounds.right-pad,bounds.bottom-pad);
        glyph.draw(canvas);
    }
    @Override public void setAlpha(int alpha) { glyph.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter filter) { glyph.setColorFilter(filter); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
