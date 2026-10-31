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
    private int maximumColumns = Integer.MAX_VALUE;
    private int horizontalGapDp,verticalGapDp=4;
    private int maximumContentWidthDp;
    private int maximumCellWidthDp;
    private int[] rowHeights = new int[0];
    public ActionGridLayout(Context context) { super(context); setBaselineAligned(false); }
    public ActionGridLayout(Context context, AttributeSet attrs) { super(context, attrs); setBaselineAligned(false); }
    public void setMaximumColumns(int maximum) { int next=Math.max(1,maximum);if(maximumColumns!=next){maximumColumns=next;requestLayout();} }
    public void setSpacingDp(int horizontal,int vertical){horizontalGapDp=Math.max(0,horizontal);verticalGapDp=Math.max(0,vertical);requestLayout();}
    public void setMaximumContentWidthDp(int maximum){maximumContentWidthDp=Math.max(0,maximum);requestLayout();}
    public void setMaximumCellWidthDp(int maximum){maximumCellWidthDp=Math.max(0,maximum);requestLayout();}
    private int cellLimit(int available){return maximumCellWidthDp==0?available:Math.min(available,columns*Math.round(maximumCellWidthDp*getResources().getDisplayMetrics().density)+(columns-1)*columnGap());}
    private int contentWidth(int available){return maximumContentWidthDp==0?available:Math.min(available,Math.round(maximumContentWidthDp*getResources().getDisplayMetrics().density));}
    private int columnGap(){return Math.round(horizontalGapDp*getResources().getDisplayMetrics().density);}
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
        int available = contentWidth(Math.max(1, width - getPaddingLeft() - getPaddingRight()));
        columns = Math.max(1, Math.min(maximumColumns, Math.min(visible.size(), (available+columnGap()) / (minCell+columnGap()))));
        if(!visible.isEmpty()){int rows=(visible.size()+columns-1)/columns;columns=(visible.size()+rows-1)/rows;}
        available=cellLimit(available);
        rowHeights = new int[(visible.size() + columns - 1) / columns];
        int state = 0;
        for (int i = 0; i < visible.size(); i++) {
            View child = visible.get(i);
            LayoutParams lp = (LayoutParams) child.getLayoutParams();
            int childWidth = Math.max(1, (available-(columns-1)*columnGap()) / columns - lp.leftMargin - lp.rightMargin);
            child.measure(MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY),
                    getChildMeasureSpec(heightSpec, getPaddingTop() + getPaddingBottom() + lp.topMargin + lp.bottomMargin, lp.height));
            rowHeights[i / columns] = Math.max(rowHeights[i / columns], child.getMeasuredHeight() + lp.topMargin + lp.bottomMargin);
            state = combineMeasuredStates(state, child.getMeasuredState());
        }
        // Keep buttons in each row aligned after measuring their wrapped captions.
        for (int i = 0; i < visible.size(); i++) {
            View child = visible.get(i);
            if (!(child instanceof Button)) continue;
            LayoutParams lp = (LayoutParams) child.getLayoutParams();
            child.measure(MeasureSpec.makeMeasureSpec(child.getMeasuredWidth(), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(rowHeights[i / columns] - lp.topMargin - lp.bottomMargin,
                            MeasureSpec.EXACTLY));
        }
        int height = getPaddingTop() + getPaddingBottom();
        for (int row : rowHeights) height += row;
        height += Math.max(0, rowHeights.length - 1) * rowGap();
        setMeasuredDimension(resolveSizeAndState(width, widthSpec, state), resolveSizeAndState(height, heightSpec, state << MEASURED_HEIGHT_STATE_SHIFT));
    }
    private int rowGap() { return Math.round(verticalGapDp * getResources().getDisplayMetrics().density); }
    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        List<View> visible = children();
        int available = cellLimit(contentWidth(getWidth() - getPaddingLeft() - getPaddingRight()));
        int y = getPaddingTop();
        for (int i = 0; i < visible.size(); i++) {
            if (i > 0 && i % columns == 0) y += rowHeights[i / columns - 1] + rowGap();
            View child = visible.get(i);
            LayoutParams lp = (LayoutParams) child.getLayoutParams();
            int column = getLayoutDirection() == LAYOUT_DIRECTION_RTL ? columns - 1 - i % columns : i % columns;
            int cellWidth=(available-(columns-1)*columnGap())/columns;
            int start=getLayoutDirection()==LAYOUT_DIRECTION_RTL?getWidth()-getPaddingRight()-available:getPaddingLeft();
            int x = start + column * (cellWidth+columnGap()) + lp.leftMargin;
            int childTop = y + lp.topMargin;
            child.layout(x, childTop, x + child.getMeasuredWidth(), childTop + child.getMeasuredHeight());
        }
    }
}
