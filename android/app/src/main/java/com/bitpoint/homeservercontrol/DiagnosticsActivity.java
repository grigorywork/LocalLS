package com.bitpoint.homeservercontrol;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

public class DiagnosticsActivity extends BaseActivity {
    private TextView diagnosticsText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ConfigMigrator.migrate(this);
        setContentView(R.layout.activity_diagnostics);
        diagnosticsText = findViewById(R.id.diagnosticsText);
        findViewById(R.id.diagnosticsRefreshButton).setOnClickListener(v -> refresh());
        findViewById(R.id.diagnosticsCopyButton).setOnClickListener(v -> copy());
        findViewById(R.id.diagnosticsClearTransfersButton).setOnClickListener(v -> {
            TransferLogStore.clear(this);
            refresh();
            Toast.makeText(this, "История передач очищена", Toast.LENGTH_SHORT).show();
        });
        findViewById(R.id.diagnosticsBackButton).setOnClickListener(v -> finish());
        refresh();
    }

    private void refresh() {
        SharedPreferences prefs = getSharedPreferences(ServerConfig.PREFS, MODE_PRIVATE);
        String local = prefs.getString(ServerConfig.KEY_LOCAL_HOST, ServerConfig.DEFAULT_LOCAL_HOST);
        String vpn = prefs.getString(ServerConfig.KEY_VPN_HOST, "");
        int port = prefs.getInt(ServerConfig.KEY_PORT, ServerConfig.DEFAULT_PORT);
        String mode = prefs.getString(ServerConfig.KEY_MODE, ServerConfig.MODE_LOCAL);
        boolean auto = prefs.getBoolean(ServerConfig.KEY_AUTO_FALLBACK, false);
        String last = prefs.getString(ServerConfig.KEY_LAST_WORKING_HOST, "");

        StringBuilder report = new StringBuilder();
        report.append("HOME SERVER CONTROL — ДИАГНОСТИКА\n\n");
        report.append("Версия приложения: ").append(appVersion()).append('\n');
        report.append("Android: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        report.append("Устройство: ").append(DeviceProfile.summary(this)).append('\n');
        report.append("Схема настроек: ").append(prefs.getInt(ServerConfig.KEY_CONFIG_VERSION, 0))
                .append('/').append(ConfigMigrator.CURRENT_VERSION).append("\n\n");

        report.append("Режим: ").append(ServerConfig.MODE_VPN.equals(mode) ? "VPN / Tailscale" : "локальный Wi‑Fi").append('\n');
        report.append("Локальный хост: ").append(safe(local)).append('\n');
        report.append("VPN-хост: ").append(safe(vpn)).append('\n');
        report.append("Активный хост: ").append(safe(ConnectionSelector.selectedHost(this))).append('\n');
        report.append("Автопереход: ").append(auto ? "включён" : "выключен").append('\n');
        report.append("Последний рабочий хост: ").append(safe(last)).append('\n');
        report.append("SSH-порт: ").append(port).append('\n');
        report.append("Пользователь: ").append(safe(prefs.getString(ServerConfig.KEY_USER, ServerConfig.DEFAULT_USER))).append("\n\n");

        report.append("Пароль сохранён в Keystore: ")
                .append(prefs.getBoolean(ServerConfig.KEY_SAVE_PASSWORD, false) ? "да" : "нет").append('\n');
        report.append("Токен агента сохранён в Keystore: ")
                .append(prefs.getBoolean(ServerConfig.KEY_SAVE_AGENT_TOKEN, false) ? "да" : "нет").append('\n');
        report.append("Low-RAM профиль: ").append(DeviceProfile.isLowRam(this) ? "да" : "нет").append('\n');
        report.append("Фоновый мониторинг: ")
                .append(prefs.getBoolean(ServerConfig.KEY_BACKGROUND_MONITOR, false) ? "включён" : "выключен").append('\n');
        report.append("Fingerprint Local: ").append(hasFingerprint(local, port) ? "сохранён" : "нет").append('\n');
        report.append("Fingerprint VPN: ").append(hasFingerprint(vpn, port) ? "сохранён" : "нет").append("\n\n");

        report.append("ПОСЛЕДНИЕ ПЕРЕДАЧИ\n").append(TransferLogStore.render(this));
        diagnosticsText.setText(report.toString());
    }

    private boolean hasFingerprint(String host, int port) {
        return host != null && !host.trim().isEmpty() && !HostTrustStore.get(this, host.trim(), port).isEmpty();
    }

    private String appVersion() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            return info.versionName == null ? "—" : info.versionName;
        } catch (Exception e) {
            return "—";
        }
    }

    private void copy() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("Home Server diagnostics", diagnosticsText.getText()));
        Toast.makeText(this, "Диагностика скопирована без паролей и токенов", Toast.LENGTH_SHORT).show();
    }

    private static String safe(String value) {
        return value == null || value.trim().isEmpty() ? "—" : value.trim();
    }
}
