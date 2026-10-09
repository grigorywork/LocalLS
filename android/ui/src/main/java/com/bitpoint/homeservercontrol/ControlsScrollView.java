package com.bitpoint.homeservercontrol;
import android.content.Context;
import android.util.AttributeSet;
import android.widget.ScrollView;
/** Leaves room for the file list while allowing large-font controls to scroll. */
public final class ControlsScrollView extends ScrollView {
    public ControlsScrollView(Context context, AttributeSet attrs) { super(context, attrs); }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        if (MeasureSpec.getMode(heightSpec) != MeasureSpec.UNSPECIFIED) {
            int limit = Math.round(MeasureSpec.getSize(heightSpec) * 0.55f);
            heightSpec = MeasureSpec.makeMeasureSpec(limit, MeasureSpec.AT_MOST);
        }
        super.onMeasure(widthSpec, heightSpec);
    }
}
