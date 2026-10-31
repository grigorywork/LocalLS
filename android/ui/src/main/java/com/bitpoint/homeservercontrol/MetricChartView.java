package com.bitpoint.homeservercontrol;

import com.bitpoint.homeservercontrol.ui.R;

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
    private final Paint.FontMetrics labelMetrics = new Paint.FontMetrics();
    private String unit = "", windowLabel = "";
    private final List<Float> values = new ArrayList<>();
    private float fixedMin = Float.NaN;
    private float fixedMax = Float.NaN;

    public MetricChartView(Context context) { super(context); init(); }
    public MetricChartView(Context context, AttributeSet attrs) { super(context, attrs); init(); }
    public MetricChartView(Context context, AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); init(); }

    private void init() {
        gridPaint.setColor(ThemeCatalog.color(getContext(), R.attr.hscStroke));
        gridPaint.setStrokeWidth(dp(.6f));gridPaint.setAlpha(105);
        gridPaint.setPathEffect(new android.graphics.DashPathEffect(new float[]{dp(3),dp(3)},0));
        linePaint.setColor(ThemeCatalog.color(getContext(), R.attr.hscAccent));
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(dp(2));
        pointPaint.setColor(ThemeCatalog.color(getContext(), R.attr.hscAccent));
        pointPaint.setStyle(Paint.Style.FILL);
        labelPaint.setColor(ThemeCatalog.color(getContext(), R.attr.hscTextSecondary));
        labelPaint.setTextSize(getResources().getDimension(R.dimen.text_chart));
        labelPaint.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL));
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
        float w = getWidth(), h = getHeight();
        boolean readable=h>=dp(100)&&w>=dp(140);
        float min = Float.isNaN(fixedMin) ? findMin() : fixedMin;
        float max = Float.isNaN(fixedMax) ? findMax() : fixedMax;
        // An empty chart still needs its empty-state label, including before the first sample.
        if (Float.isNaN(min)) min=0;
        if (Float.isNaN(max)) max=1;
        if (max <= min) max = min + 1f;
        float left = plotLeftInset(), right = w-dp(8);
        labelPaint.getFontMetrics(labelMetrics);
        float top = dp(unit.isEmpty()?8:h<dp(100)?12:20);
        float bottomInset = unit.isEmpty()?dp(8):Math.max(dp(h<dp(100)?14:20),labelMetrics.descent-labelMetrics.ascent+dp(4));
        float bottom=h-bottomInset;
        if (right <= left || bottom <= top) return;
        for (int i=0;i<=4;i++) {float y=top+(bottom-top)*i/4f;canvas.drawLine(left,y,right,y,gridPaint);}
        if (!unit.isEmpty()) {
            boolean showNow=w>=dp(220);float nowWidth=labelPaint.measureText("сейчас");
            float available=w-dp(16)-(showNow?nowWidth+dp(12):0);
            String full="Последние "+windowLabel,window=labelPaint.measureText(full)<=available?full:windowLabel;
            if(showNow&&labelPaint.measureText(window)>available){showNow=false;available=w-dp(16);window=labelPaint.measureText(full)<=available?full:windowLabel;}
            float baseline=h-dp(3)-Math.max(0,labelMetrics.descent);
            canvas.drawText(window,dp(8),baseline,labelPaint);
            if(showNow)canvas.drawText("сейчас",right-nowWidth,baseline,labelPaint);
        }
        int valid=0;for(Float v:values)if(v!=null&&!Float.isNaN(v))valid++;
        if(valid==0){
            String empty="Нет замеров";float previous=labelPaint.getTextSize();labelPaint.setTextSize(dp(readable?12:10));labelPaint.getFontMetrics(labelMetrics);
            canvas.drawText(empty,left+(right-left-labelPaint.measureText(empty))/2,(top+bottom-labelMetrics.ascent-labelMetrics.descent)/2,labelPaint);
            labelPaint.setTextSize(previous);return;
        }
        if(readable){float previous=labelPaint.getTextSize();labelPaint.setTextSize(dp(10));for(int i=0;i<3;i++){float value=max-(max-min)*i/2f;String number=String.format(java.util.Locale.getDefault(),max>=10?"%.0f":"%.1f",value);canvas.drawText(number,left-dp(5)-labelPaint.measureText(number),top+(bottom-top)*i/2f+dp(3),labelPaint);}labelPaint.setTextSize(previous);}

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
            // A continuous curve keeps dense histories legible.
        }
        // Close each valid segment independently so missing samples never become fake readings.
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        int accent = linePaint.getColor();
        fill.setShader(new android.graphics.LinearGradient(0,top,0,bottom,new int[]{(accent & 0x00ffffff)|0x55000000,accent & 0x00ffffff},null,android.graphics.Shader.TileMode.CLAMP));
        Path area=new Path();boolean active=false;float end=left;
        for(int i=0;i<values.size();i++){
            Float value=values.get(i);
            if(value==null||Float.isNaN(value)){if(active){area.lineTo(end,bottom);area.close();canvas.drawPath(area,fill);area.reset();active=false;}continue;}
            float x=left+(right-left)*i/Math.max(1,values.size()-1);float y=bottom-Math.max(0f,Math.min(1f,(value-min)/(max-min)))*(bottom-top);
            if(!active){area.moveTo(x,bottom);area.lineTo(x,y);active=true;}else area.lineTo(x,y);end=x;
        }
        if(active){area.lineTo(end,bottom);area.close();canvas.drawPath(area,fill);}
        canvas.drawPath(path, linePaint);
    }

    /** Left edge of the plot, also used to align labels beside a responsive chart. */
    public float plotLeftInset() {
        if (getMeasuredHeight()<dp(100)||getMeasuredWidth()<dp(140)) return dp(8);
        float min=Float.isNaN(fixedMin)?findMin():fixedMin;
        float max=Float.isNaN(fixedMax)?findMax():fixedMax;
        if(Float.isNaN(min))min=0;
        if(Float.isNaN(max))max=1;
        if(max<=min)max=min+1;
        float previous=labelPaint.getTextSize(),axisWidth=0;
        labelPaint.setTextSize(dp(10));
        for(int i=0;i<3;i++){
            float value=max-(max-min)*i/2f;
            String number=String.format(java.util.Locale.getDefault(),max>=10?"%.0f":"%.1f",value);
            axisWidth=Math.max(axisWidth,labelPaint.measureText(number));
        }
        labelPaint.setTextSize(previous);
        return Math.max(dp(28),axisWidth+dp(9));
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
