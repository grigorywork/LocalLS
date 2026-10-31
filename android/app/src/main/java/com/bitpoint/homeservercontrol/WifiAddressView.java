package com.bitpoint.homeservercontrol;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Collections;
import java.util.List;

/** Compact, read-only address of this device; it never changes connections or server state. */
public final class WifiAddressView extends LinearLayout {
    private final TextView value;
    private List<String> shown=Collections.emptyList();
    private boolean rendered,compact;
    void compact(){compact=true;rendered=false;value.setTextSize(23);value.setPadding(0,0,0,0);value.setIncludeFontPadding(false);value.setMaxLines(1);value.setEllipsize(android.text.TextUtils.TruncateAt.END);value.setTypeface(null,android.graphics.Typeface.BOLD);refresh();}

    public WifiAddressView(Context context){this(context,null);}
    public WifiAddressView(Context context,AttributeSet attrs){
        super(context,attrs);setOrientation(VERTICAL);
        value=new TextView(context);value.setId(R.id.currentWifiIpValue);value.setTextSize(14);
        value.setTextColor(ThemeCatalog.color(context,com.bitpoint.homeservercontrol.ui.R.attr.hscTextPrimary));
        int padding=Math.round(4*getResources().getDisplayMetrics().density);value.setPadding(0,padding,0,padding);
        addView(value,new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT));refresh();
    }
    void refresh(){
        List<String> now=LocalServerAddresses.current(getContext());
        if(rendered && shown.equals(now))return;
        shown=now;rendered=true;
        String addresses=now.isEmpty()?getContext().getString(R.string.current_wifi_ip_missing):android.text.TextUtils.join(", ",now);
        value.setText(compact?addresses:getContext().getString(R.string.current_wifi_ip_value,addresses));
    }
}
