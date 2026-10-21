package com.bitpoint.homeservercontrol;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Collections;
import java.util.List;

/** This device's address only; it never changes a connection, trust pin or server state. */
public final class WifiAddressView extends LinearLayout {
    private final TextView value;
    private final Button copy;
    private List<String> shown=Collections.emptyList();
    private boolean rendered;

    public WifiAddressView(Context context){this(context,null);}
    public WifiAddressView(Context context,AttributeSet attrs){
        super(context,attrs);setOrientation(VERTICAL);
        TextView title=new TextView(context);title.setText(R.string.current_wifi_ip);title.setTextSize(16);
        title.setTextColor(ThemeCatalog.color(context,com.bitpoint.homeservercontrol.ui.R.attr.hscTextPrimary));
        title.setTypeface(null,Typeface.BOLD);title.setGravity(Gravity.CENTER);addView(title);
        value=new TextView(context);value.setId(R.id.currentWifiIpValue);value.setTextSize(20);
        value.setGravity(Gravity.CENTER);value.setTextIsSelectable(true);
        addView(value,new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT));
        TextView hint=new TextView(context);hint.setText(R.string.current_wifi_ip_hint);hint.setTextSize(14);
        hint.setTextColor(ThemeCatalog.color(context,com.bitpoint.homeservercontrol.ui.R.attr.hscTextSecondary));
        hint.setGravity(Gravity.CENTER);addView(hint);
        copy=new Button(context);copy.setId(R.id.copyCurrentWifiIp);copy.setTag("file_toolbar");
        copy.setText(R.string.copy_current_wifi_ip);copy.setAllCaps(false);copy.setGravity(Gravity.CENTER);
        copy.setMinimumHeight(Math.round(48*getResources().getDisplayMetrics().density));
        addView(copy,new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT));
        copy.setOnClickListener(v->copyAddress());refresh();
    }
    void refresh(){render(LocalServerAddresses.current(getContext()));}
    private void render(List<String> values){
        if(rendered && shown.equals(values))return;
        shown=values;rendered=true;
        value.setText(values.isEmpty()?getContext().getString(R.string.current_wifi_ip_missing):android.text.TextUtils.join("\n",values));
        StatusBadge.apply(value,values.isEmpty()?StatusBadge.Tone.NEUTRAL:StatusBadge.Tone.SUCCESS);
        copy.setEnabled(!values.isEmpty());
    }
    private void copyAddress(){
        List<String> now=LocalServerAddresses.current(getContext());
        if(!shown.equals(now)){render(now);message(R.string.current_wifi_ip_changed);return;}
        if(now.isEmpty())return;
        if(now.size()==1){copyVerified(now.get(0));return;}
        new AlertDialog.Builder(getContext()).setTitle(R.string.choose_current_wifi_ip)
                .setItems(now.toArray(new String[0]),(dialog,index)->copyVerified(now.get(index)))
                .setNegativeButton(android.R.string.cancel,null).show();
    }
    private void copyVerified(String ip){
        List<String> now=LocalServerAddresses.current(getContext());
        if(!now.contains(ip)){render(now);message(R.string.current_wifi_ip_changed);return;}
        ClipboardManager clipboard=(ClipboardManager)getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if(clipboard!=null){clipboard.setPrimaryClip(ClipData.newPlainText(getContext().getString(R.string.current_wifi_ip),ip));message(R.string.current_wifi_ip_copied);}
    }
    private void message(int text){Toast.makeText(getContext(),text,Toast.LENGTH_SHORT).show();}
}
