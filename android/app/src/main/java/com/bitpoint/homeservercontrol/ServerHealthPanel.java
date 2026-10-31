package com.bitpoint.homeservercontrol;

import android.content.Context;
import android.view.Gravity;
import android.widget.*;
import java.util.*;

/** Local service responsiveness only; no fabricated samples or persisted credentials. */
final class ServerHealthPanel extends LinearLayout {
    final TextView state,value,scope;
    final MetricChartView chart;
    private final List<Float> samples=new ArrayList<>();
    private long last;
    ServerHealthPanel(Context c){
        super(c);setOrientation(VERTICAL);setGravity(Gravity.CENTER_VERTICAL);
        state=label("Нет замеров",11,false);value=label("—",22,true);scope=label("Локальный SSH",11,false);
        chart=new MetricChartView(c);chart.setMinimumHeight(0);chart.setLabels("мс","60 с");addView(chart,new LayoutParams(-1,0,1));
    }
    private TextView label(String text,int size,boolean bold){TextView v=new TextView(getContext());v.setText(text);v.setTextSize(size);v.setIncludeFontPadding(false);v.setMaxLines(1);v.setEllipsize(android.text.TextUtils.TruncateAt.END);v.setTextColor(ThemeCatalog.color(getContext(),bold?com.bitpoint.homeservercontrol.ui.R.attr.hscTextPrimary:com.bitpoint.homeservercontrol.ui.R.attr.hscTextSecondary));if(bold)v.setTypeface(null,android.graphics.Typeface.BOLD);addView(v,new LayoutParams(-1,-2));return v;}
    void sample(long time,float millis,boolean running){
        if(!running){samples.clear();last=0;state.setText("Сервер выключен");value.setText("—");}
        else{
            if(last>0&&time-last>10000)samples.clear();last=time;
            samples.add(Float.isFinite(millis)&&millis>=0?millis:Float.NaN);while(samples.size()>13)samples.remove(0);
            boolean valid=Float.isFinite(millis)&&millis>=0,all=valid&&samples.size()>=2;for(Float v:samples)all&=Float.isFinite(v);
            state.setText(!valid?"Нет ответа":all?"Без сбоев в окне":"Собираем замеры");value.setText(valid?(millis<1?"<1 мс":String.format(java.util.Locale.getDefault(),"%.0f мс",millis)):"—");
        }
        state.setTextColor(ThemeCatalog.color(getContext(),running&&samples.size()>=2&&!samples.contains(Float.NaN)?com.bitpoint.homeservercontrol.ui.R.attr.hscSuccess:com.bitpoint.homeservercontrol.ui.R.attr.hscTextSecondary));
        chart.setValues(new ArrayList<>(samples),0,Float.NaN);setContentDescription("Локальный SSH. "+state.getText()+". "+value.getText());
    }
    @Override protected void onMeasure(int ws,int hs){
        int h=MeasureSpec.getSize(hs);float d=getResources().getDisplayMetrics().density;
        state.setVisibility(h>=130*d?VISIBLE:GONE);scope.setVisibility(h>=110*d?VISIBLE:GONE);value.setVisibility(h>=45*d?VISIBLE:GONE);chart.setVisibility(h>=65*d?VISIBLE:GONE);
        super.onMeasure(ws,hs);
        // Use the actual plot edge, including its responsive numeric-axis gutter.
        int inset=(int)Math.ceil(chart.plotLeftInset());
        if(state.getPaddingLeft()!=inset){
            state.setPadding(inset,0,0,0);value.setPadding(inset,0,0,0);scope.setPadding(inset,0,0,0);
            super.onMeasure(ws,hs);
        }
    }
}
