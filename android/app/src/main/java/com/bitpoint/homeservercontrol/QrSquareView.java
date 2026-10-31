package com.bitpoint.homeservercontrol;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;
import com.journeyapps.barcodescanner.Size;

/** Square image/camera viewport, capped so the primary QR fits a phone screen. */
final class QrSquareView extends FrameLayout {
    QrSquareView(Context context){super(context);}
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        int availableHeight=Math.max(160,getResources().getConfiguration().screenHeightDp-120);
        int maximum=Math.round(Math.min(320,availableHeight)*getResources().getDisplayMetrics().density);
        int side=Math.min(MeasureSpec.getSize(widthSpec),maximum);
        if(MeasureSpec.getMode(heightSpec)!=MeasureSpec.UNSPECIFIED)side=Math.min(side,MeasureSpec.getSize(heightSpec));
        for(int i=0;i<getChildCount();i++){
            View child=getChildAt(i);
            if(child instanceof DecoratedBarcodeView){
                int frame=Math.round(side*.8f);((DecoratedBarcodeView)child).getBarcodeView().setFramingRectSize(new Size(frame,frame));
            }
            child.measure(MeasureSpec.makeMeasureSpec(side,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(side,MeasureSpec.EXACTLY));
        }
        setMeasuredDimension(side,side);
    }
}
