package com.bitpoint.homeservercontrol;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.Gravity;
import android.widget.*;

/** Approved number-first card, with a compact fallback and disjoint measured content areas. */
final class CompactMetricCard extends LinearLayout {
    final TextView heading,value,detail,footer,capacity;
    final ImageView glyph,artwork;
    final ProgressBar indicator;
    private final Drawable progressImage,dividerImage,clearImage;
    private final String title,compactTitle;
    private boolean details=true,inline,stacked,centered,rich,splitFooter;
    private int contentHeight,bodyHeight,headingHeight,valueHeight,detailHeight,footerHeight,iconSize,artSize;

    CompactMetricCard(Context context,String title,TextView number,Drawable image,int accent){
        super(context);setOrientation(VERTICAL);setBackground(SurfaceStyle.panel(context));setPadding(dp(10),dp(8),dp(10),dp(8));
        this.title=title;compactTitle=title.replace(" сервера","");boolean batteryCard=image instanceof BatteryGaugeDrawable;
        heading=label(title,12,true);value=number;value.setTextSize(28);value.setTypeface(null,Typeface.BOLD);
        value.setCompoundDrawables(null,null,null,null);value.setPadding(0,0,0,0);value.setIncludeFontPadding(false);value.setMaxLines(1);
        addView(value,new LayoutParams(-1,-2));
        glyph=new ImageView(context);glyph.setImageDrawable(image);glyph.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);glyph.setPadding(dp(4),dp(4),dp(4),dp(4));
        GradientDrawable badge=new GradientDrawable();badge.setColor(Color.argb(24,Color.red(accent),Color.green(accent),Color.blue(accent)));badge.setCornerRadius(dp(8));glyph.setBackground(badge);addView(glyph,new LayoutParams(dp(32),dp(32)));
        detail=label("Нет данных",11,false);indicator=new ProgressBar(context,null,android.R.attr.progressBarStyleHorizontal);indicator.setMax(100);indicator.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);addView(indicator,new LayoutParams(-1,dp(5)));
        GradientDrawable track=new GradientDrawable();track.setColor(ThemeCatalog.color(context,com.bitpoint.homeservercontrol.ui.R.attr.hscStroke));track.setCornerRadius(dp(4));
        GradientDrawable fill=new GradientDrawable();fill.setColor(accent);fill.setCornerRadius(dp(4));
        LayerDrawable layers=new LayerDrawable(new Drawable[]{track,new ClipDrawable(fill,Gravity.START,ClipDrawable.HORIZONTAL)});layers.setId(0,android.R.id.background);layers.setId(1,android.R.id.progress);progressImage=layers;dividerImage=SurfaceStyle.rule(context,0,0);clearImage=new ColorDrawable(Color.TRANSPARENT);indicator.setProgressDrawable(layers);
        footer=label("",11,false);capacity=label("",11,false);
        artwork=new ImageView(context);artwork.setImageDrawable(new MetricArtwork(context,accent,batteryCard));artwork.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);addView(artwork,new LayoutParams(-2,-2));
    }
    private TextView label(String text,int size,boolean bold){TextView t=new TextView(getContext());t.setText(text);t.setTextSize(size);t.setTextColor(ThemeCatalog.color(getContext(),bold?com.bitpoint.homeservercontrol.ui.R.attr.hscTextPrimary:com.bitpoint.homeservercontrol.ui.R.attr.hscTextSecondary));t.setIncludeFontPadding(false);t.setMaxLines(1);t.setGravity(Gravity.CENTER_VERTICAL);t.setEllipsize(android.text.TextUtils.TruncateAt.END);if(bold)t.setTypeface(null,Typeface.BOLD);addView(t,new LayoutParams(-1,-2));return t;}
    private int dp(float value){return SettingsUi.dp(getContext(),value);}
    void density(boolean details,boolean foot){this.details=details;detail.setVisibility(details?VISIBLE:GONE);footer.setVisibility(foot?(footer.getText().length()==0?INVISIBLE:VISIBLE):GONE);}
    void stacked(boolean value){stacked=value;}
    void centered(boolean value){centered=value;}
    boolean isInline(){return inline;}
    void artworkLevel(int level){Drawable image=artwork.getDrawable();if(image instanceof BatteryGaugeDrawable)((BatteryGaugeDrawable)image).level(level);else image.setLevel(level<0?0:Math.max(0,Math.min(100,level))+1);}
    int preferredHeight(int width){
        int available=Math.max(1,width-getPaddingLeft()-getPaddingRight());rich=details;String caption=rich?title:compactTitle;if(!caption.contentEquals(heading.getText()))heading.setText(caption);iconSize=dp(rich?28:32);headingHeight=Math.max(dp(rich?28:20),heading.getLineHeight());valueHeight=Math.max(dp(32),value.getLineHeight());
        detailHeight=details?Math.max(dp(14),detail.getLineHeight()):0;footerHeight=footer.getVisibility()!=GONE?Math.max(dp(14),Math.max(footer.getLineHeight(),capacity.getLineHeight())):0;
        if(rich){
            inline=false;artSize=Math.min(dp(88),Math.max(dp(44),available*32/100));bodyHeight=Math.max(artSize,valueHeight+dp(2)+detailHeight);splitFooter=available<dp(280);
            return getPaddingTop()+headingHeight+dp(6)+bodyHeight+dp(6)+dp(5)+(footerHeight>0?dp(4)+footerHeight*(splitFooter?2:1):0)+getPaddingBottom();
        }
        int textSpace=available-iconSize-dp(8);
        inline=!stacked&&textSpace>=Math.min(heading.getPaint().measureText(heading.getText().toString()),textSpace-dp(2))+Math.max(Math.min(value.getPaint().measureText(value.getText().toString()),textSpace-dp(2)),value.getPaint().measureText("100%"))+dp(12);
        bodyHeight=inline?Math.max(iconSize,valueHeight):Math.max(iconSize,headingHeight+valueHeight);
        return getPaddingTop()+bodyHeight+dp(6)+dp(5)+(footerHeight>0?dp(4)+footerHeight:0)+getPaddingBottom();
    }
    private void fit(TextView text,int width){width=Math.max(1,width-dp(2));float actual=text.getPaint().measureText(text.getText().toString());if(actual>width&&actual>0)text.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,text.getTextSize()*width/actual);}
    @Override protected void onMeasure(int ws,int hs){
        int w=MeasureSpec.getSize(ws),wanted=preferredHeight(w),h=resolveSize(wanted,hs);contentHeight=wanted;int available=Math.max(1,w-getPaddingLeft()-getPaddingRight());
        for(int i=0;i<getChildCount();i++)getChildAt(i).forceLayout();
        artwork.setVisibility(rich?VISIBLE:GONE);capacity.setVisibility(rich&&footerHeight>0?(capacity.getText().length()==0?INVISIBLE:VISIBLE):GONE);
        if(rich){
            value.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);int textWidth=Math.max(1,available-artSize-dp(8));
            fit(value,textWidth);fit(detail,textWidth);fit(heading,available-iconSize-dp(8));
            int footerWidth=splitFooter?available:Math.max(1,(available-dp(8))/2);fit(footer,footerWidth);fit(capacity,footerWidth);
            value.measure(exact(textWidth),exact(valueHeight));detail.measure(exact(textWidth),exact(detailHeight));heading.measure(exact(available-iconSize-dp(8)),exact(headingHeight));glyph.measure(exact(iconSize),exact(iconSize));artwork.measure(exact(artSize),exact(artSize));
            footer.measure(exact(footerWidth),exact(footerHeight));capacity.measure(exact(footerWidth),exact(footerHeight));
            indicator.setProgressDrawable(clearImage);indicator.setBackground(dividerImage);
        }else{
            value.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);int textSpace=Math.max(1,available-iconSize-dp(8));fit(value,textSpace-dp(2));fit(heading,textSpace-dp(2));fit(detail,available-dp(2));fit(footer,available-dp(2));
            int numberWidth=inline?Math.min(textSpace,Math.max(dp(48),(int)Math.ceil(Math.max(value.getPaint().measureText(value.getText().toString()),value.getPaint().measureText("100%")))+dp(2))):textSpace;
            value.measure(exact(numberWidth),exact(valueHeight));glyph.measure(exact(iconSize),exact(iconSize));heading.measure(exact(inline?Math.max(1,textSpace-numberWidth-dp(8)):textSpace),exact(headingHeight));detail.measure(exact(available),exact(0));footer.measure(exact(available),exact(footerHeight));
            indicator.setBackground(null);indicator.setProgressDrawable(progressImage);
        }
        indicator.measure(exact(available),exact(dp(5)));setMeasuredDimension(w,h);
    }
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
        int left=getPaddingLeft(),right=getWidth()-getPaddingRight(),top=getPaddingTop()+(centered?Math.max(0,(getHeight()-contentHeight)/2):0);
        if(rich){
            glyph.layout(left,top+(headingHeight-iconSize)/2,left+iconSize,top+(headingHeight+iconSize)/2);heading.layout(left+iconSize+dp(8),top,right,top+headingHeight);int body=top+headingHeight+dp(6),valueTop=body+(bodyHeight-valueHeight-dp(2)-detailHeight)/2;
            value.layout(left,valueTop,left+value.getMeasuredWidth(),valueTop+valueHeight);detail.layout(left,valueTop+valueHeight+dp(2),left+detail.getMeasuredWidth(),valueTop+valueHeight+dp(2)+detailHeight);
            artwork.layout(right-artSize,body+(bodyHeight-artSize)/2,right,body+(bodyHeight+artSize)/2);int y=body+bodyHeight+dp(6);indicator.layout(left,y,right,y+dp(5));y+=dp(9);
            if(footerHeight>0){footer.layout(left,y,left+footer.getMeasuredWidth(),y+footerHeight);int cy=splitFooter?y+footerHeight:y;capacity.setGravity((splitFooter?Gravity.START:Gravity.END)|Gravity.CENTER_VERTICAL);capacity.layout(right-capacity.getMeasuredWidth(),cy,right,cy+footerHeight);}return;
        }
        int textStart=left+iconSize+dp(8);glyph.layout(left,top+(bodyHeight-iconSize)/2,left+iconSize,top+(bodyHeight+iconSize)/2);int headingTop=inline?top+(bodyHeight-headingHeight)/2:top;heading.layout(textStart,headingTop,textStart+heading.getMeasuredWidth(),headingTop+headingHeight);int valueTop=inline?top:top+headingHeight;value.layout(right-value.getMeasuredWidth(),valueTop,right,valueTop+valueHeight);int y=top+bodyHeight+dp(6);indicator.layout(left,y,right,y+dp(5));y+=dp(9);if(footerHeight>0)footer.layout(left,y,right,y+footerHeight);
    }
    private static int exact(int value){return MeasureSpec.makeMeasureSpec(Math.max(0,value),MeasureSpec.EXACTLY);}
    private static final class MetricArtwork extends Drawable {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final int accent,muted,panel;private final BatteryGaugeDrawable battery;
        MetricArtwork(Context c,int accent,boolean isBattery){battery=isBattery?new BatteryGaugeDrawable(c):null;this.accent=accent;panel=ThemeCatalog.color(c,com.bitpoint.homeservercontrol.ui.R.attr.hscPanel);muted=ThemeCatalog.color(c,com.bitpoint.homeservercontrol.ui.R.attr.hscTextSecondary);}
        @Override protected boolean onLevelChange(int level){invalidateSelf();return true;}
        @Override public void draw(Canvas c){Rect b=getBounds();if(battery!=null){int height=Math.round(b.width()*.62f);battery.level(getLevel()==0?-1:getLevel()-1);battery.setBounds(b.left,b.centerY()-height/2,b.right,b.centerY()+(height+1)/2);battery.draw(c);return;}float s=Math.min(b.width()/72f,b.height()/72f),x=b.exactCenterX()-36*s,y=b.exactCenterY()-36*s;int save=c.save();c.translate(x,y);c.scale(s,s);paint.setStrokeWidth(1.6f);
            for(int i=0;i<3;i++){float top=9+i*20;paint.setColor(getLevel()==0?muted:accent);paint.setStyle(i==1&&getLevel()>0?Paint.Style.FILL:Paint.Style.STROKE);c.drawRoundRect(new RectF(3,top,69,top+14),4,4,paint);paint.setStyle(Paint.Style.FILL);paint.setColor(i==1&&getLevel()>0?panel:accent);c.drawCircle(61,top+7,1.8f,paint);}c.restoreToCount(save);}
        @Override public void setAlpha(int alpha){paint.setAlpha(alpha);}@Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);}@Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
}
