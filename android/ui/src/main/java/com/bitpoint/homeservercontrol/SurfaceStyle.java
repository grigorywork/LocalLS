package com.bitpoint.homeservercontrol;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.TextView;

/** One quiet line colour for card edges, section headings and list separators. */
final class SurfaceStyle {
    static int lineColor(Context c){
        int stroke=ThemeCatalog.color(c,com.bitpoint.homeservercontrol.ui.R.attr.hscStroke);
        return Color.argb(150,Color.red(stroke),Color.green(stroke),Color.blue(stroke));
    }
    static Drawable rule(Context c,float start,float end){return new Rule(c,start,end);}
    static void heading(TextView view){
        float d=view.getResources().getDisplayMetrics().density;
        view.setIncludeFontPadding(false);view.setPadding(0,0,0,Math.round(4*d));
        view.setMinHeight(Math.round(22*d));view.setBackground(rule(view.getContext(),0,0));
    }
    static String quantity(String value){return value.replace(".0 "," ").replace(",0 "," ");}
    static boolean owned(Drawable drawable){return drawable instanceof Panel;}
    static Drawable panel(Context c){return new Panel(c);}
    private static final class Panel extends GradientDrawable {
        private final float density;
        Panel(Context c){density=c.getResources().getDisplayMetrics().density;
            if(ThemeCatalog.glass(c)){
                setOrientation(Orientation.TL_BR);setColors(ThemeCatalog.lightGlass(c)?new int[]{0xCCFFFFFF,0xCCFFFFFF,0xCCFFFFFF}:new int[]{0xC02D343F,0x9811151D,0xB8212732});
            }else setColor(ThemeCatalog.color(c,com.bitpoint.homeservercontrol.ui.R.attr.hscPanel));
            setStroke(Math.max(1,Math.round(.75f*density)),lineColor(c));setCornerRadius(9*density);
        }
        @Override public boolean getPadding(Rect out){out.set(Math.round(10*density),Math.round(8*density),Math.round(10*density),Math.round(8*density));return true;}
    }
    private static final class Rule extends Drawable {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float density,start,end;private final int color;private int alpha=255;
        Rule(Context c,float start,float end){density=c.getResources().getDisplayMetrics().density;this.start=start;this.end=end;color=lineColor(c);}
        @Override public void draw(Canvas canvas){
            Rect b=getBounds();boolean rtl=getLayoutDirection()==View.LAYOUT_DIRECTION_RTL;
            float left=b.left+(rtl?end:start)*density,right=b.right-(rtl?start:end)*density;
            paint.setColor(color);paint.setAlpha(Color.alpha(color)*alpha/255);
            if(right>left)canvas.drawRect(left,b.bottom-Math.max(1,.75f*density),right,b.bottom,paint);
        }
        @Override public boolean onLayoutDirectionChanged(int direction){invalidateSelf();return true;}
        @Override public void setAlpha(int value){alpha=Math.max(0,Math.min(255,value));invalidateSelf();}
        @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
}
