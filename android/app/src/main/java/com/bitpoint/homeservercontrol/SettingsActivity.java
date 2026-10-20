package com.bitpoint.homeservercontrol;

import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.Toast;

/** App preferences only: connection and local-server settings live in their role blocks. */
public class SettingsActivity extends BaseActivity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_settings);
        CheckBox monitor=findViewById(R.id.settingsBackgroundMonitorCheck);
        monitor.setChecked(getSharedPreferences(ServerConfig.PREFS,MODE_PRIVATE).getBoolean(ServerConfig.KEY_BACKGROUND_MONITOR,false));
        findViewById(R.id.settingsSaveButton).setOnClickListener(v -> {
            getSharedPreferences(ServerConfig.PREFS,MODE_PRIVATE).edit()
                .putBoolean(ServerConfig.KEY_BACKGROUND_MONITOR,monitor.isChecked()).remove(ServerConfig.KEY_MONITOR_HAS_STATE).apply();
            boolean scheduled=MonitoringScheduler.setEnabled(this,monitor.isChecked());
            Toast.makeText(this,monitor.isChecked() && !scheduled?"Не удалось включить мониторинг":"Настройки сохранены",Toast.LENGTH_SHORT).show();
        });
        findViewById(R.id.settingsBackButton).setOnClickListener(v -> finish());
    }
}
