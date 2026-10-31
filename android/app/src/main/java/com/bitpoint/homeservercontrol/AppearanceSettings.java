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
        outline.setText("Обводка: "+(ThemeCatalog.lightOutline(owner)?"Светлая":"По теме"));
        outline.setOnClickListener(v->new AlertDialog.Builder(owner).setTitle("Обводка")
            .setSingleChoiceItems(new String[]{"По теме","Светлая"},ThemeCatalog.lightOutline(owner)?1:0,(dialog,which)->{ThemeCatalog.lightOutline(owner,which==1);dialog.dismiss();refresh.run();})
            .setNegativeButton("Назад",null).show());
    }
    static void chooseTheme(BaseActivity owner,Runnable refresh){
        new AlertDialog.Builder(owner).setTitle("Цветовая тема")
            .setSingleChoiceItems(ThemeCatalog.NAMES,ThemeCatalog.index(owner),(dialog,which)->{ThemeCatalog.select(owner,which);dialog.dismiss();refresh.run();})
            .setNegativeButton("Назад",null).show();
    }
    private AppearanceSettings(){}
}
