package com.bitpoint.homeservercontrol;

import android.os.Bundle;
import android.widget.*;
import java.util.*;

/** Receipt metadata never exposes a private key or executes a path supplied by a client. */
public class ServerReceiptActivity extends BaseActivity {
    @Override protected void onCreate(Bundle saved){
        super.onCreate(saved);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);int pad=Math.round(16*getResources().getDisplayMetrics().density);root.setPadding(pad,pad,pad,pad);
        TextView title=new TextView(this);title.setText("Полученные файлы");title.setTextSize(22);title.setTextColor(ThemeCatalog.color(this,com.bitpoint.homeservercontrol.ui.R.attr.hscTextPrimary));root.addView(title);
        TextView explanation=new TextView(this);explanation.setText("Последние 100 поступлений в папку раздачи. Автосохранение в выбранную папку выполняется отдельно.");explanation.setTextColor(ThemeCatalog.color(this,com.bitpoint.homeservercontrol.ui.R.attr.hscTextSecondary));root.addView(explanation);
        ListView list=new ListView(this);root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        TextView empty=new TextView(this);empty.setText("Поступлений пока нет");empty.setTextColor(ThemeCatalog.color(this,com.bitpoint.homeservercontrol.ui.R.attr.hscTextSecondary));root.addView(empty);list.setEmptyView(empty);
        Button back=new Button(this);back.setText("Назад");back.setOnClickListener(v->finish());root.addView(back);setContentView(root);
        new Thread(()->{List<ServerReceiptStore.Entry> entries;try(ServerReceiptStore journal=new ServerReceiptStore(this)){entries=journal.list();}catch(RuntimeException error){entries=Collections.emptyList();}final List<ServerReceiptStore.Entry> rows=entries;runOnUiThread(()->{if(isDestroyed()||isFinishing())return;List<String> labels=new ArrayList<>();for(var row:rows)labels.add(row.name()+"\n"+ServerTelemetry.bytes(row.bytes)+" · "+ServerReceiptStore.sender(this,row.sender)+"\n"+android.text.format.DateFormat.format("dd.MM HH:mm",row.received));list.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,labels){@Override public android.view.View getView(int position,android.view.View recycled,android.view.ViewGroup parent){TextView view=(TextView)super.getView(position,recycled,parent);view.setTextColor(ThemeCatalog.color(ServerReceiptActivity.this,com.bitpoint.homeservercontrol.ui.R.attr.hscTextPrimary));FileTypeIcons.apply(view,rows.get(position).name(),false,28);return view;}});});},"LocalLS-receipt-list").start();
    }
}
