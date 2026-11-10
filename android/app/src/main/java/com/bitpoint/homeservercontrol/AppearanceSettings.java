package com.bitpoint.homeservercontrol;

import android.app.AlertDialog;
import android.widget.Button;
import android.widget.Switch;

/** Appearance writes only its own keys; server identities and connection preferences are untouched. */
final class AppearanceSettings {
    static void bind(BaseActivity owner,Switch glass,Button outline,Runnable refresh){
        glass.setSaveEnabled(false); // Persisted appearance wins over an older Activity view-state bundle.
        glass.setChecked(ThemeCatalog.glass(owner));
        glass.setOnCheckedChangeListener((v,checked)->{ThemeCatalog.glass(owner,checked);refresh.run();});
        outline.setText(AppText.tr("Обводка: ")+(ThemeCatalog.lightOutline(owner)?AppText.tr("Светлая"):AppText.tr("По теме")));
        outline.setOnClickListener(v->new AlertDialog.Builder(owner).setTitle(AppText.tr("Обводка"))
            .setSingleChoiceItems(new String[]{AppText.tr("По теме"),AppText.tr("Светлая")},ThemeCatalog.lightOutline(owner)?1:0,(dialog,which)->{ThemeCatalog.lightOutline(owner,which==1);dialog.dismiss();refresh.run();})
            .setNegativeButton(AppText.tr("Назад"),null).show());
    }
    static void chooseTheme(BaseActivity owner,Runnable refresh){
        if(ThemeCatalog.index(owner)==4&&!owner.getSharedPreferences(ServerConfig.PREFS,0).contains(ThemeCatalog.LIGHT_KEY))ThemeCatalog.light(owner,true);
        final int[] choices={0,1,2,3,5,6,7};String[] labels=new String[choices.length];int selected=0;
        for(int i=0;i<choices.length;i++){labels[i]=AppText.tr(ThemeCatalog.NAMES[choices[i]]);if(choices[i]==ThemeCatalog.index(owner))selected=i;}
        new AlertDialog.Builder(owner).setTitle(AppText.tr("Цветовая тема"))
            .setSingleChoiceItems(labels,selected,(dialog,which)->{ThemeCatalog.select(owner,choices[which]);dialog.dismiss();refresh.run();})
            .setNegativeButton(AppText.tr("Назад"),null).show();
    }
    private AppearanceSettings(){}
}
