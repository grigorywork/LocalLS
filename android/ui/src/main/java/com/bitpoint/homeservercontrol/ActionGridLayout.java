package com.bitpoint.homeservercontrol;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.LinearLayout;
import java.util.ArrayList;
import java.util.List;

/** Keeps action tiles aligned and adds rows when larger text needs more space. */
public final class ActionGridLayout extends LinearLayout {
    private int columns = 1;
    private int[] rowHeights = new int[0];
    public ActionGridLayout(Context context) { super(context); setBaselineAligned(false); }
    public ActionGridLayout(Context context, AttributeSet attrs) { super(context, attrs); setBaselineAligned(false); }
    private List<View> children() {
        List<View> visible = new ArrayList<>();
        for (int i = 0; i < getChildCount(); i++) if (getChildAt(i).getVisibility() != GONE) visible.add(getChildAt(i));
        return visible;
    }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        List<View> visible = children();
        int minCell = Math.round(64 * getResources().getDisplayMetrics().density);
        for (View view : visible) {
            LayoutParams lp = (LayoutParams) view.getLayoutParams();
            lp.resolveLayoutDirection(getLayoutDirection());
            if (view instanceof Button) {
                Button button = (Button) view;
                for (String word : button.getText().toString().split("\\s+")) {
                    int wordWidth = (int) Math.ceil(button.getPaint().measureText(word));
                    minCell = Math.max(minCell, wordWidth + button.getCompoundPaddingLeft()
                            + button.getCompoundPaddingRight() + lp.leftMargin + lp.rightMargin + 2);
                }
            } else if (view instanceof TextView) {
                TextView label = (TextView) view;
                minCell = Math.max(minCell, (int) Math.ceil(label.getPaint().measureText(label.getText().toString()))
                        + label.getCompoundPaddingLeft() + label.getCompoundPaddingRight() + lp.leftMargin + lp.rightMargin);
            }
        }
        int width = MeasureSpec.getMode(widthSpec) == MeasureSpec.UNSPECIFIED
                ? minCell * visible.size() + getPaddingLeft() + getPaddingRight() : MeasureSpec.getSize(widthSpec);
        int available = Math.max(1, width - getPaddingLeft() - getPaddingRight());
        columns = Math.max(1, Math.min(visible.size(), available / minCell));
        rowHeights = new int[(visible.size() + columns - 1) / columns];
        int state = 0;
        for (int i = 0; i < visible.size(); i++) {
            View child = visible.get(i);
            LayoutParams lp = (LayoutParams) child.getLayoutParams();
            int childWidth = Math.max(1, available / columns - lp.leftMargin - lp.rightMargin);
            child.measure(MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY),
                    getChildMeasureSpec(heightSpec, getPaddingTop() + getPaddingBottom() + lp.topMargin + lp.bottomMargin, lp.height));
            rowHeights[i / columns] = Math.max(rowHeights[i / columns], child.getMeasuredHeight() + lp.topMargin + lp.bottomMargin);
            state = combineMeasuredStates(state, child.getMeasuredState());
        }
        int height = getPaddingTop() + getPaddingBottom();
        for (int row : rowHeights) height += row;
        height += Math.max(0, rowHeights.length - 1) * rowGap();
        setMeasuredDimension(resolveSizeAndState(width, widthSpec, state), resolveSizeAndState(height, heightSpec, state << MEASURED_HEIGHT_STATE_SHIFT));
    }
    private int rowGap() { return Math.round(4 * getResources().getDisplayMetrics().density); }
    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        List<View> visible = children();
        int available = getWidth() - getPaddingLeft() - getPaddingRight();
        int y = getPaddingTop();
        for (int i = 0; i < visible.size(); i++) {
            if (i > 0 && i % columns == 0) y += rowHeights[i / columns - 1] + rowGap();
            View child = visible.get(i);
            LayoutParams lp = (LayoutParams) child.getLayoutParams();
            int column = getLayoutDirection() == LAYOUT_DIRECTION_RTL ? columns - 1 - i % columns : i % columns;
            int x = getPaddingLeft() + column * (available / columns) + lp.leftMargin;
            int childTop = y + lp.topMargin;
            child.layout(x, childTop, x + child.getMeasuredWidth(), childTop + child.getMeasuredHeight());
        }
    }
}
