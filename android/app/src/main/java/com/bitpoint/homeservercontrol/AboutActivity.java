package com.bitpoint.homeservercontrol;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.TextView;

/** Author and installed build information, reachable from both client and server settings. */
public final class AboutActivity extends BaseActivity {
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);setContentView(R.layout.activity_about);
        SettingsUi.surface(findViewById(R.id.aboutCard));
        try{
            PackageInfo info=getPackageManager().getPackageInfo(getPackageName(),0);
            ((TextView)findViewById(R.id.aboutVersion)).setText(getString(R.string.about_version,info.versionName,info.versionCode));
        }catch(PackageManager.NameNotFoundException impossible){throw new IllegalStateException("Installed package metadata missing",impossible);}
        findViewById(R.id.settingsBackButton).setOnClickListener(v->finish());
    }
}
