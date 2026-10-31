package com.bitpoint.homeservercontrol;

import android.view.*;
import android.widget.*;

/** Full-width action lists; input fields, navigation and the static dashboards keep their layout. */
final class CompactActions {
    private CompactActions(){}
    static boolean marked(Button button){return Boolean.TRUE.equals(button.getTag(com.bitpoint.homeservercontrol.ui.R.id.compact_action_marker));}
    private static boolean command(View view){
        if(!(view instanceof Button)||view instanceof CompoundButton)return false;
        Object tag=view.getTag();
        return tag==null||"qr_action".equals(tag)||"menu_action".equals(tag)||"compact_action".equals(tag)||"drawer_header".equals(tag);
    }
    private static void mark(Button button){
        button.setTag(com.bitpoint.homeservercontrol.ui.R.id.compact_action_marker,true);
        // Keep the real button, its semantic tag, id, callbacks, enabled and visibility states.
        String full=button.getText().toString(),shorter=full;
        if(full.equals("Скопировать данные подключения"))shorter="Копировать адрес";
        if(full.equals("Экспортировать публичный ключ .pub"))shorter="Экспорт .pub";
        if(full.equals("Сохранять в «Загрузки / LocalLS»"))shorter="Загрузки / LocalLS";
        if(full.equals("Скопировать диагностику запуска"))shorter="Копировать отчёт";
        if(!shorter.equals(full)){button.setContentDescription(full);button.setText(shorter);}
        button.setMinWidth(0);button.setMinimumWidth(0);button.setMaxWidth(Integer.MAX_VALUE);
        ViewGroup.LayoutParams params=button.getLayoutParams();
        params.height=ViewGroup.LayoutParams.WRAP_CONTENT;
        if(params instanceof LinearLayout.LayoutParams){
            LinearLayout.LayoutParams linear=(LinearLayout.LayoutParams)params;
            linear.width=ViewGroup.LayoutParams.MATCH_PARENT;linear.weight=0;
            linear.setMargins(0,0,0,0);linear.setMarginStart(0);linear.setMarginEnd(0);
        }
        button.setLayoutParams(params);
    }
    static void arrange(View view){
        if(view instanceof DashboardLayout||view instanceof ServerHomeLayout||view instanceof FileBrowserLayout||view instanceof AdapterView)return;
        if(view instanceof ActionGridLayout){
            ActionGridLayout group=(ActionGridLayout)view;boolean commands=group.getChildCount()>0;
            for(int i=0;i<group.getChildCount();i++)commands&=command(group.getChildAt(i));
            if(commands){
                group.setMaximumColumns(1);group.setSpacingDp(0,0);
                group.setMaximumContentWidthDp(0);group.setMaximumCellWidthDp(0);
                for(int i=0;i<group.getChildCount();i++)mark((Button)group.getChildAt(i));
            }
            return;
        }
        if(!(view instanceof ViewGroup))return;
        ViewGroup parent=(ViewGroup)view;
        if(parent instanceof LinearLayout&&((LinearLayout)parent).getOrientation()==LinearLayout.HORIZONTAL&&parent.getChildCount()>0){
            boolean actions=true;for(int i=0;i<parent.getChildCount();i++)actions&=command(parent.getChildAt(i));
            if(actions)((LinearLayout)parent).setOrientation(LinearLayout.VERTICAL);
        }
        for(int i=0;i<parent.getChildCount();i++){
            View child=parent.getChildAt(i);
            if(command(child)&&parent instanceof LinearLayout&&((LinearLayout)parent).getOrientation()==LinearLayout.VERTICAL)mark((Button)child);
            else arrange(child);
        }
    }
}
