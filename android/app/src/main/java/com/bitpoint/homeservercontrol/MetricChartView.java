package com.bitpoint.homeservercontrol;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class MetricChartView extends View {
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private String unit = "", windowLabel = "";
    private final List<Float> values = new ArrayList<>();
    private float fixedMin = Float.NaN;
    private float fixedMax = Float.NaN;

    public MetricChartView(Context context) { super(context); init(); }
    public MetricChartView(Context context, AttributeSet attrs) { super(context, attrs); init(); }
    public MetricChartView(Context context, AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); init(); }

    private void init() {
        gridPaint.setColor(ThemeCatalog.color(getContext(), R.attr.hscStroke));
        gridPaint.setStrokeWidth(dp(1));
        linePaint.setColor(ThemeCatalog.color(getContext(), R.attr.hscAccent));
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(dp(2));
        pointPaint.setColor(ThemeCatalog.color(getContext(), R.attr.hscAccent));
        pointPaint.setStyle(Paint.Style.FILL);
        labelPaint.setColor(ThemeCatalog.color(getContext(), R.attr.hscTextSecondary));
        labelPaint.setTextSize(11 * getResources().getDisplayMetrics().scaledDensity);
        setMinimumHeight((int) dp(120));
    }

    void setLabels(String unit, String windowLabel) {
        if (this.unit.equals(unit) && this.windowLabel.equals(windowLabel)) return;
        this.unit = unit;
        this.windowLabel = windowLabel;
        invalidate();
    }

    void setValues(List<Float> input, float min, float max) {
        if (values.equals(input) && Float.compare(fixedMin, min)==0 && Float.compare(fixedMax, max)==0) return;
        values.clear();
        if (input != null) values.addAll(input);
        fixedMin = min;
        fixedMax = max;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        float left = dp(8), right = w - dp(8), top = dp(unit.isEmpty() ? 8 : 20),
                bottom = h - dp(unit.isEmpty() ? 8 : 20);
        if (right <= left || bottom <= top) return;

        for (int i = 0; i <= 4; i++) {
            float y = top + (bottom - top) * i / 4f;
            canvas.drawLine(left, y, right, y, gridPaint);
        }

        float min = Float.isNaN(fixedMin) ? findMin() : fixedMin;
        float max = Float.isNaN(fixedMax) ? findMax() : fixedMax;
        if (Float.isNaN(min) || Float.isNaN(max)) return;
        if (max <= min) max = min + 1f;
        if (!unit.isEmpty()) {
            canvas.drawText(String.format(java.util.Locale.getDefault(), "%.1f %s", max, unit), left, dp(12), labelPaint);
            canvas.drawText("−" + windowLabel, left, h - dp(3), labelPaint);
            canvas.drawText("сейчас", right - labelPaint.measureText("сейчас"), h - dp(3), labelPaint);
        }

        int valid = 0;
        for (Float v : values) if (v != null && !Float.isNaN(v)) valid++;
        if (valid == 0) return;

        path.reset();
        boolean started = false;
        int n = Math.max(1, values.size() - 1);
        for (int i = 0; i < values.size(); i++) {
            Float value = values.get(i);
            if (value == null || Float.isNaN(value)) {
                started = false;
                continue;
            }
            float x = left + (right - left) * i / n;
            float normalized = (value - min) / (max - min);
            normalized = Math.max(0f, Math.min(1f, normalized));
            float y = bottom - normalized * (bottom - top);
            if (!started) {
                path.moveTo(x, y);
                started = true;
            } else {
                path.lineTo(x, y);
            }
            canvas.drawCircle(x, y, dp(2.2f), pointPaint);
        }
        canvas.drawPath(path, linePaint);
    }

    private float findMin() {
        float min = Float.NaN;
        for (Float v : values) {
            if (v == null || Float.isNaN(v)) continue;
            if (Float.isNaN(min) || v < min) min = v;
        }
        if (!Float.isNaN(min) && min > 0) min = Math.max(0f, min * 0.85f);
        return min;
    }

    private float findMax() {
        float max = Float.NaN;
        for (Float v : values) {
            if (v == null || Float.isNaN(v)) continue;
            if (Float.isNaN(max) || v > max) max = v;
        }
        if (!Float.isNaN(max)) max = max <= 0 ? 1f : max * 1.15f;
        return max;
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
