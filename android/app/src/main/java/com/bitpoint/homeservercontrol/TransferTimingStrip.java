package com.bitpoint.homeservercontrol;

import android.content.Context;
import android.view.*;
import android.widget.TextView;

/** Small metadata below progress: one line on wide screens, two or three on phones. */
final class TransferTimingStrip extends ViewGroup {
    final TextView amount,queue,file;
    private int rows,rowHeight;
    TransferTimingStrip(Context c,TextView amount,TextView queue,TextView file){
        super(c);this.amount=amount;this.queue=queue;this.file=file;
        for(TextView t:new TextView[]{amount,queue,file}){if(t.getParent()!=null)((ViewGroup)t.getParent()).removeView(t);t.setCompoundDrawablesRelative(null,null,null,null);t.setPadding(0,0,0,0);t.setSingleLine(false);t.setMaxLines(3);t.setEllipsize(null);t.setIncludeFontPadding(false);addView(t);}
        amount.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);queue.setGravity(Gravity.CENTER);file.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
    }
    private int gap(){return Math.round(8*getResources().getDisplayMetrics().density);}
    private int width(TextView t){return t.length()==0?0:(int)Math.ceil(t.getPaint().measureText(t.getText().toString()))+2;}
    int preferredHeight(int available){
        int a=width(amount),q=width(queue),f=width(file);rows=queue.getVisibility()!=VISIBLE&&file.length()==0?1:2*Math.max(a,f)+q+2*gap()<=available?1:a+f+gap()<=available?2:3;
        rowHeight=0;for(TextView label:new TextView[]{amount,queue,file}){label.measure(MeasureSpec.makeMeasureSpec(Math.max(1,Math.min(available,width(label))),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));rowHeight=Math.max(rowHeight,label.getMeasuredHeight());}return rows*rowHeight+(rows-1)*2;
    }
    @Override protected void onMeasure(int ws,int hs){int w=MeasureSpec.getSize(ws),height=preferredHeight(w);for(TextView t:new TextView[]{amount,queue,file})t.measure(MeasureSpec.makeMeasureSpec(Math.min(w,width(t)),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(rowHeight,MeasureSpec.EXACTLY));setMeasuredDimension(w,resolveSize(height,hs));}
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
        int w=getWidth();place(amount,0,0);
        if(rows==1){place(queue,(w-queue.getMeasuredWidth())/2,0);place(file,w-file.getMeasuredWidth(),0);}
        else if(rows==2){place(file,w-file.getMeasuredWidth(),0);place(queue,(w-queue.getMeasuredWidth())/2,rowHeight+2);}
        else{place(file,w-file.getMeasuredWidth(),rowHeight+2);place(queue,(w-queue.getMeasuredWidth())/2,2*(rowHeight+2));}
    }
    private void place(TextView v,int x,int y){v.layout(x,y,x+v.getMeasuredWidth(),y+rowHeight);}
}
