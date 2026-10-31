package com.bitpoint.homeservercontrol;

import android.os.Bundle;
import android.widget.*;

/** The same appearance choices remain reachable from a server-only phone. */
public final class AppearanceActivity extends BaseActivity {
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);LinearLayout root=new SettingsColumn(this);root.setOrientation(LinearLayout.VERTICAL);
        Button back=button(root,R.id.settingsBackButton,"Оформление");back.setOnClickListener(v->finish());
        LinearLayout panel=SettingsUi.section(root,"Оформление");
        Button theme=button(panel,R.id.themeChooserButton,"Цветовая тема: "+ThemeCatalog.NAMES[ThemeCatalog.index(this)]);theme.setOnClickListener(v->AppearanceSettings.chooseTheme(this,this::recreate));
        Switch glass=new Switch(this);glass.setId(R.id.appearanceGlassSwitch);glass.setText("Эффект стекла");glass.setTextSize(16);glass.setTextColor(ThemeCatalog.color(this,com.bitpoint.homeservercontrol.ui.R.attr.hscTextPrimary));glass.setMinHeight(SettingsUi.dp(this,56));glass.setPadding(SettingsUi.dp(this,12),0,SettingsUi.dp(this,12),0);panel.addView(glass,new LinearLayout.LayoutParams(-1,-2));
        Button outline=button(panel,R.id.appearanceOutlineButton,"Обводка");AppearanceSettings.bind(this,glass,outline,this::recreate);
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(ThemeCatalog.color(this,com.bitpoint.homeservercontrol.ui.R.attr.hscBackground));scroll.addView(root);setContentView(scroll);
    }
    private Button button(LinearLayout parent,int id,String title){Button b=new Button(this);b.setId(id);b.setText(title);b.setTextSize(16);b.setMinHeight(SettingsUi.dp(this,56));b.setTag(com.bitpoint.homeservercontrol.ui.R.id.compact_action_marker,true);parent.addView(b,new LinearLayout.LayoutParams(-1,-2));return b;}
}
