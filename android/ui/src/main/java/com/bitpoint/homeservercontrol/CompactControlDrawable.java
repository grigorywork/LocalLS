package com.bitpoint.homeservercontrol;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.Drawable;

/** A complete, inset rounded frame for standalone controls. Never draws list separators. */
final class CompactControlDrawable extends Drawable {
 private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
 private final float density;private final int fill,stroke,accent;private int alpha=255;
 CompactControlDrawable(Context c){
  density=c.getResources().getDisplayMetrics().density;
  int panel=ThemeCatalog.color(c,com.bitpoint.homeservercontrol.ui.R.attr.hscPanel);
  fill=ThemeCatalog.index(c)==4?panel:blend(panel,ThemeCatalog.color(c,com.bitpoint.homeservercontrol.ui.R.attr.hscSecondaryButton),.28f);
  stroke=ThemeCatalog.glass(c)||ThemeCatalog.lightOutline(c)?SurfaceStyle.lineColor(c):blend(ThemeCatalog.color(c,com.bitpoint.homeservercontrol.ui.R.attr.hscStroke),ThemeCatalog.color(c,com.bitpoint.homeservercontrol.ui.R.attr.hscAccent),.16f);
  accent=ThemeCatalog.color(c,com.bitpoint.homeservercontrol.ui.R.attr.hscAccent);
 }
 private static int blend(int a,int b,float k){return Color.rgb(Math.round(Color.red(a)*(1-k)+Color.red(b)*k),Math.round(Color.green(a)*(1-k)+Color.green(b)*k),Math.round(Color.blue(a)*(1-k)+Color.blue(b)*k));}
 private RectF frame(){RectF r=new RectF(getBounds());float inset=density+Math.max(1,.75f*density)/2;r.inset(inset,inset);return r;}
 @Override public boolean isStateful(){return true;}
 @Override protected boolean onStateChange(int[] state){invalidateSelf();return true;}
 @Override public void setTintList(ColorStateList ignored){}
 @Override public void draw(Canvas canvas){
  boolean enabled=false,active=false;for(int state:getState()){enabled|=state==android.R.attr.state_enabled;active|=state==android.R.attr.state_pressed||state==android.R.attr.state_focused;}
  RectF r=frame();paint.setStyle(Paint.Style.FILL);paint.setColor(active&&enabled?blend(fill,accent,.14f):fill);paint.setAlpha(Color.alpha(fill)*alpha*(enabled?255:100)/(255*255));drawFrame(canvas,r);
  paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Math.max(1,.75f*density));paint.setColor(active&&enabled?accent:stroke);paint.setAlpha(alpha*(enabled?230:100)/255);drawFrame(canvas,r);paint.setStyle(Paint.Style.FILL);
 }
 private void drawFrame(Canvas canvas,RectF r){canvas.drawRoundRect(r,8*density,8*density,paint);}
 @Override public void getOutline(Outline out){Rect b=getBounds();int inset=Math.max(1,Math.round(density));out.setRoundRect(b.left+inset,b.top+inset,b.right-inset,b.bottom-inset,8*density);out.setAlpha(alpha/255f);}
 @Override public void setAlpha(int value){alpha=Math.max(0,Math.min(255,value));invalidateSelf();}
 @Override public int getAlpha(){return alpha;}
 @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
 @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
