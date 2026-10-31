package com.bitpoint.homeservercontrol;
import android.graphics.*;
import android.graphics.drawable.Drawable;
final class BatteryGaugeDrawable extends Drawable {
 private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final int available,unknown;int level=-1;
 BatteryGaugeDrawable(android.content.Context c){available=ThemeCatalog.color(c,com.bitpoint.homeservercontrol.ui.R.attr.hscSuccess);unknown=ThemeCatalog.color(c,com.bitpoint.homeservercontrol.ui.R.attr.hscTextSecondary);}
 void level(int value){level=value;invalidateSelf();}
 @Override public void draw(Canvas canvas){Rect b=getBounds();float s=b.height()/24f;RectF body=new RectF(b.left+s,b.top+3*s,b.right-4*s,b.bottom-3*s);paint.setColor(level>=0?available:unknown);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2*s);canvas.drawRoundRect(body,3*s,3*s,paint);paint.setStyle(Paint.Style.FILL);canvas.drawRect(b.right-3*s,b.top+9*s,b.right-s,b.bottom-9*s,paint);if(level>=0){body.inset(3*s,3*s);body.right=body.left+body.width()*Math.max(0,Math.min(100,level))/100f;canvas.drawRoundRect(body,s,s,paint);}}
 @Override public void setAlpha(int a){paint.setAlpha(a);}@Override public void setColorFilter(ColorFilter f){paint.setColorFilter(f);}@Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
