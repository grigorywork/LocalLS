package com.bitpoint.homeservercontrol;

import android.content.Context;
import android.view.*;
import android.widget.TextView;

/** Tablet actions sit beside the title; phone actions keep a full accessible row. */
final class QueueHeaderLayout extends ViewGroup {
    private final TextView title;
    private final View actions;
    private boolean wide;
    private int line;
    QueueHeaderLayout(Context c,TextView title,View actions){super(c);this.title=title;this.actions=actions;addView(title);addView(actions);}
    private int dp(int n){return SettingsUi.dp(getContext(),n);}
    @Override protected void onMeasure(int ws,int hs){
        int w=MeasureSpec.getSize(ws);wide=w>=dp(700);int right=wide?Math.min(dp(440),w*2/3):w;
        actions.measure(MeasureSpec.makeMeasureSpec(right,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));
        title.measure(MeasureSpec.makeMeasureSpec(wide?Math.max(1,w-right-dp(12)):w,MeasureSpec.AT_MOST),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));
        line=wide?Math.max(title.getMeasuredHeight(),actions.getMeasuredHeight()):title.getMeasuredHeight();
        setMeasuredDimension(w,wide?line:line+dp(4)+actions.getMeasuredHeight());
    }
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){title.layout(0,wide?(line-title.getMeasuredHeight())/2:0,title.getMeasuredWidth(),wide?(line+title.getMeasuredHeight())/2:title.getMeasuredHeight());int x=wide?getWidth()-actions.getMeasuredWidth():0,y=wide?(line-actions.getMeasuredHeight())/2:line+dp(4);actions.layout(x,y,x+actions.getMeasuredWidth(),y+actions.getMeasuredHeight());}
}
