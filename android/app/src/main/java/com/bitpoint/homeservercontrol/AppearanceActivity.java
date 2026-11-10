package com.bitpoint.homeservercontrol;

import android.os.Bundle;
import android.widget.*;

/** The same appearance choices remain reachable from a server-only phone. */
public final class AppearanceActivity extends BaseActivity {
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);LinearLayout root=new SettingsColumn(this);root.setOrientation(LinearLayout.VERTICAL);
        Button back=button(root,R.id.settingsBackButton,AppText.tr("Оформление"));back.setOnClickListener(v->finish());
        LinearLayout panel=SettingsUi.section(root,AppText.tr("Оформление"));
        Button theme=button(panel,R.id.themeChooserButton,AppText.tr("Цветовая тема: ")+AppText.tr(ThemeCatalog.NAMES[ThemeCatalog.index(this)==4?0:ThemeCatalog.index(this)]));theme.setOnClickListener(v->AppearanceSettings.chooseTheme(this,this::recreate));
        Button language=button(panel,android.view.View.generateViewId(),AppText.tr("Язык / Language"));language.setOnClickListener(v->new android.app.AlertDialog.Builder(this).setTitle("Язык / Language").setSingleChoiceItems(new String[]{"Русский","English"},"en".equals(AppLanguage.get(this))?1:0,(d,i)->{AppLanguage.select(this,i==1?"en":"ru");d.dismiss();recreate();}).setNegativeButton(android.R.string.cancel,null).show());
        Switch light=new Switch(this);light.setText(AppText.tr("Светлый фон"));light.setTextSize(16);light.setTextColor(ThemeCatalog.color(this,com.bitpoint.homeservercontrol.ui.R.attr.hscTextPrimary));light.setPadding(SettingsUi.dp(this,12),0,SettingsUi.dp(this,12),0);light.setMinHeight(SettingsUi.dp(this,56));light.setChecked(ThemeCatalog.light(this));panel.addView(light,new LinearLayout.LayoutParams(-1,-2));light.setOnCheckedChangeListener((v,checked)->{ThemeCatalog.light(this,checked);recreate();});
        Switch glass=new Switch(this);glass.setId(R.id.appearanceGlassSwitch);glass.setText(AppText.tr("Эффект стекла"));glass.setTextSize(16);glass.setTextColor(ThemeCatalog.color(this,com.bitpoint.homeservercontrol.ui.R.attr.hscTextPrimary));glass.setMinHeight(SettingsUi.dp(this,56));glass.setPadding(SettingsUi.dp(this,12),0,SettingsUi.dp(this,12),0);panel.addView(glass,new LinearLayout.LayoutParams(-1,-2));
        Button outline=button(panel,R.id.appearanceOutlineButton,AppText.tr("Обводка"));AppearanceSettings.bind(this,glass,outline,this::recreate);
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(ThemeCatalog.color(this,com.bitpoint.homeservercontrol.ui.R.attr.hscBackground));scroll.addView(root);setContentView(scroll);
    }
    private Button button(LinearLayout parent,int id,String title){Button b=new Button(this);b.setId(id);b.setText(title);b.setTextSize(16);b.setMinHeight(SettingsUi.dp(this,56));b.setTag(com.bitpoint.homeservercontrol.ui.R.id.compact_action_marker,true);parent.addView(b,new LinearLayout.LayoutParams(-1,-2));return b;}
}
