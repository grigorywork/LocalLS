package com.bitpoint.homeservercontrol;

import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.Toast;
import android.view.View;

/** App preferences only: connection and local-server settings live in their role blocks. */
public class SettingsActivity extends BaseActivity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_settings);
        for(int id:new int[]{R.id.settingsMonitorCard,R.id.settingsShareCard,R.id.settingsServerOptions})SettingsUi.surface(findViewById(id));
        findViewById(R.id.settingsServerButton).setOnClickListener(v->startActivity(new android.content.Intent(this,EmbeddedServerActivity.class)));
        CheckBox monitor=findViewById(R.id.settingsBackgroundMonitorCheck);
        monitor.setChecked(getSharedPreferences(ServerConfig.PREFS,MODE_PRIVATE).getBoolean(ServerConfig.KEY_BACKGROUND_MONITOR,false));
        ((CheckBox)findViewById(R.id.shareImmediatelyCheck)).setChecked(getSharedPreferences(ServerConfig.PREFS,0).getBoolean(ShareOutbox.AUTO,false));
        alignExplanation(R.id.settingsBackgroundMonitorCheck,R.id.settingsMonitorDetail);alignExplanation(R.id.shareImmediatelyCheck,R.id.shareImmediatelyExplanation);
        renderRole();
        findViewById(R.id.settingsSaveButton).setOnClickListener(v -> {
            if(DeviceRole.serverOnly(this))return;
            getSharedPreferences(ServerConfig.PREFS,MODE_PRIVATE).edit()
                .putBoolean(ShareOutbox.AUTO,((CheckBox)findViewById(R.id.shareImmediatelyCheck)).isChecked())
                .putBoolean(ServerConfig.KEY_BACKGROUND_MONITOR,monitor.isChecked()).remove(ServerConfig.KEY_MONITOR_HAS_STATE).apply();
            boolean scheduled=MonitoringScheduler.setEnabled(this,monitor.isChecked());
            Toast.makeText(this,monitor.isChecked() && !scheduled?"Не удалось включить мониторинг":"Настройки сохранены",Toast.LENGTH_SHORT).show();
        });
        findViewById(R.id.settingsBackButton).setOnClickListener(v -> finish());
        findViewById(R.id.aboutAppButton).setOnClickListener(v -> startActivity(new android.content.Intent(this,AboutActivity.class)));
    }
    private void alignExplanation(int control,int explanation){CheckBox box=findViewById(control);findViewById(explanation).setPaddingRelative(box.getCompoundPaddingStart(),0,0,SettingsUi.dp(this,8));}
    private void renderRole(){boolean hidden=DeviceRole.serverOnly(this);
        findViewById(R.id.settingsClientOptions).setVisibility(hidden?View.GONE:View.VISIBLE);findViewById(R.id.settingsServerOptions).setVisibility(hidden?View.VISIBLE:View.GONE);
        findViewById(R.id.shareImmediatelyCheck).setVisibility(hidden?View.GONE:View.VISIBLE);findViewById(R.id.shareImmediatelyExplanation).setVisibility(hidden?View.GONE:View.VISIBLE);
        findViewById(R.id.settingsBackgroundMonitorCheck).setVisibility(hidden?View.GONE:View.VISIBLE);
        findViewById(R.id.settingsSaveButton).setVisibility(hidden?View.GONE:View.VISIBLE);
    }
    @Override protected void onResume(){super.onResume();renderRole();}
}
