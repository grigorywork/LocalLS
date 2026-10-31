package com.bitpoint.homeservercontrol;

import android.app.AlertDialog;
import android.content.Context;
import android.view.View;
import android.widget.*;

final class QueueActions {
    private QueueActions(){}
    static ActionGridLayout row(Context c){
        ActionGridLayout row=new ActionGridLayout(c);row.setMaximumColumns(2);row.setSpacingDp(6,4);row.setTag("queue_actions");
        for(boolean clear:new boolean[]{false,true}){
            Button button=new Button(c);button.setTag("queue_action");button.setText(clear?"Очистить список":"Отменить все");button.setTextSize(12);button.setAllCaps(false);button.setMinHeight(SettingsUi.dp(c,48));button.setMinimumHeight(SettingsUi.dp(c,48));button.setMinWidth(0);button.setMinimumWidth(0);button.setPadding(SettingsUi.dp(c,6),0,SettingsUi.dp(c,6),0);button.setTextColor(ThemeCatalog.color(c,com.bitpoint.homeservercontrol.ui.R.attr.hscTextPrimary));
            button.setBackground(new ButtonOutlineDrawable(c,button.getBackground()));button.setOnClickListener(v->confirm(c,clear));row.addView(button,new LinearLayout.LayoutParams(-1,-2));
        }return row;
    }
    static void enabled(ActionGridLayout row,boolean pending,boolean any){for(int n=0;n<row.getChildCount();n++)row.getChildAt(n).setEnabled(!TransferForegroundService.clearing&&(n==0?pending:any));}
    static void confirm(Context c,boolean clear){
        new AlertDialog.Builder(c).setTitle(clear?"Очистить список?":"Отменить все передачи?")
            .setMessage(clear?"Передачи остановятся, список и локальные временные копии очистятся. Исходники и готовые файлы сохранятся.":"Текущие и ожидающие передачи будут отменены. Исходники и готовые файлы сохранятся.")
            .setNegativeButton("Назад",null).setPositiveButton(clear?"Очистить":"Отменить все",(d,w)->{if(clear)TransferForegroundService.clear(c);else TransferForegroundService.cancel(c);}).show();
    }
}
