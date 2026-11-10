package com.bitpoint.homeservercontrol;

import android.content.Context;
import android.graphics.*;
import android.text.method.SingleLineTransformationMethod;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;

/** Read-only, single-line information; icon and caption are centered together in each third. */
final class StatusRibbon extends LinearLayout {
    final TextView first,second,third;
    private final ImageView stateIcon;
    private final Paint rule=new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean trusted;
    StatusRibbon(Context c,TextView first,TextView second,boolean server){
        super(c);this.first=first;this.second=second;setOrientation(HORIZONTAL);setGravity(Gravity.CENTER_VERTICAL);setBackground(SurfaceStyle.panel(c));setPadding(dp(6),0,dp(6),0);setWillNotDraw(false);
        group(first,com.bitpoint.homeservercontrol.ui.R.drawable.ic_action_history);
        group(second,com.bitpoint.homeservercontrol.ui.R.drawable.ic_action_restart);
        third=new TextView(c);third.setText(server?AppText.tr("В сети: 0"):AppText.tr("Не проверен"));
        stateIcon=group(third,server?com.bitpoint.homeservercontrol.ui.R.drawable.ic_device_phone:com.bitpoint.homeservercontrol.ui.R.drawable.ic_status_shield_check);
        first.setTransformationMethod(new CompactText());second.setTransformationMethod(new CompactText());rule.setColor(SurfaceStyle.lineColor(c));
    }
    private ImageView group(TextView text,int icon){
        text.setTextSize(TypedValue.COMPLEX_UNIT_PX,dp(11));text.setTextColor(ThemeCatalog.color(getContext(),com.bitpoint.homeservercontrol.ui.R.attr.hscTextSecondary));text.setIncludeFontPadding(false);text.setMaxLines(1);text.setEllipsize(android.text.TextUtils.TruncateAt.END);text.setPadding(0,0,0,0);text.setCompoundDrawables(null,null,null,null);text.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout group=new LinearLayout(getContext());group.setGravity(Gravity.CENTER);group.setPadding(dp(3),0,dp(3),0);ImageView glyph=new ImageView(getContext());glyph.setImageResource(icon);glyph.setColorFilter(ThemeCatalog.color(getContext(),com.bitpoint.homeservercontrol.ui.R.attr.hscAccent));glyph.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);group.addView(glyph,new LayoutParams(dp(14),dp(14)));LayoutParams label=new LayoutParams(-2,-2);label.leftMargin=dp(4);group.addView(text,label);addView(group,new LayoutParams(0,-1,1));return glyph;
    }
    void trust(ServerStats stats){
        trusted=stats.authenticated&&stats.hostKeyTrusted&&!stats.hostKeyMismatch&&!stats.hostKeyNeedsTrust;
        third.setText(stats.hostKeyMismatch?AppText.tr("Ключ изменён"):trusted?AppText.tr("Ключ проверен"):AppText.tr("Не проверен"));
        int color=ThemeCatalog.color(getContext(),stats.hostKeyMismatch?com.bitpoint.homeservercontrol.ui.R.attr.hscError:trusted?com.bitpoint.homeservercontrol.ui.R.attr.hscSuccess:com.bitpoint.homeservercontrol.ui.R.attr.hscTextSecondary);third.setTextColor(color);stateIcon.setColorFilter(color);third.setContentDescription(stats.hostKeyMismatch?AppText.tr("Ключ сервера отличается от сохранённого"):trusted?AppText.tr("Ключ сервера совпадает с сохранённым, вход подтверждён"):AppText.tr("Ключ сервера не подтверждён"));
    }
    void online(int count){third.setText(AppText.tr("В сети: ")+Math.max(0,count));third.setContentDescription(AppText.tr("Сейчас подключено устройств: ")+Math.max(0,count));}
    private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
    int preferredHeight(){return dp(28);}
    @Override protected void onMeasure(int width,int height){
        int available=Math.max(1,(MeasureSpec.getSize(width)-getPaddingLeft()-getPaddingRight())/3-dp(24));
        for(TextView label:new TextView[]{first,second,third}){
            if(label==third&&trusted)label.setText(available<dp(82)?AppText.tr("Проверен"):AppText.tr("Ключ проверен"));
            CharSequence text=label.getTransformationMethod()==null?label.getText():label.getTransformationMethod().getTransformation(label.getText(),label);
            label.setTextSize(TypedValue.COMPLEX_UNIT_PX,dp(11));float wanted=label.getPaint().measureText(text.toString());if(wanted>available&&wanted>0)label.setTextSize(TypedValue.COMPLEX_UNIT_PX,Math.max(dp(9),dp(11)*available/wanted));
        }
        super.onMeasure(width,MeasureSpec.makeMeasureSpec(resolveSize(preferredHeight(),height),MeasureSpec.EXACTLY));
    }
    @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);float width=(getWidth()-getPaddingLeft()-getPaddingRight())/3f;for(int i=1;i<3;i++){float x=getPaddingLeft()+width*i;canvas.drawRect(x,getHeight()/2f-dp(7),x+Math.max(1,dp(.75f)),getHeight()/2f+dp(7),rule);}}
    private static final class CompactText extends SingleLineTransformationMethod {
        @Override public CharSequence getTransformation(CharSequence source,View view){
            String value=source==null?"—":source.toString();if(value.contains(AppText.tr("Показатели"))||value.contains(AppText.tr("недоступ")))return AppText.tr("Нет данных");
            if(value.contains(AppText.tr("устарели")))return AppText.tr("Давно");
            if(value.equals(AppText.tr("Сейчас"))||value.equals(AppText.tr("Обновлено сейчас")))return AppText.tr("Сейчас");
            for(String prefix:new String[]{AppText.tr("Работает "),AppText.tr("Обновлено "),AppText.tr("Время работы:")})if(value.startsWith(prefix)){value=value.substring(prefix.length()).trim();break;}
            return value.replace(AppText.tr(" сейчас"),AppText.tr("Сейчас"));
        }
    }
}
