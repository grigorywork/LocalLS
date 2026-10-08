package com.bitpoint.homeservercontrol;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

public class SettingsActivity extends BaseActivity {
    private EditText portInput, userInput, passwordInput, agentPortInput, agentTokenInput;
    private CheckBox savePasswordCheck, saveAgentTokenCheck, backgroundMonitorCheck;

    private int secretGeneration;
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ConfigMigrator.migrate(this);
        setContentView(R.layout.activity_settings);
        portInput = findViewById(R.id.settingsPortInput);
        userInput = findViewById(R.id.settingsUserInput);
        passwordInput = findViewById(R.id.settingsPasswordInput);
        agentPortInput = findViewById(R.id.settingsAgentPortInput);
        agentTokenInput = findViewById(R.id.settingsAgentTokenInput);
        savePasswordCheck = findViewById(R.id.settingsSavePasswordCheck);
        saveAgentTokenCheck = findViewById(R.id.settingsSaveAgentTokenCheck);
        backgroundMonitorCheck = findViewById(R.id.settingsBackgroundMonitorCheck);
        load();

        findViewById(R.id.settingsBackButton).setOnClickListener(v -> finish());
        findViewById(R.id.settingsSaveButton).setOnClickListener(v -> save());
        findViewById(R.id.agentWizardButton).setOnClickListener(v -> showAgentWizard());
    }

    private void load() {
        SharedPreferences prefs = getSharedPreferences(ServerConfig.PREFS, MODE_PRIVATE);
        portInput.setText(String.valueOf(prefs.getInt(ServerConfig.KEY_PORT, ServerConfig.DEFAULT_PORT)));
        userInput.setText(prefs.getString(ServerConfig.KEY_USER, ServerConfig.DEFAULT_USER));
        agentPortInput.setText(String.valueOf(prefs.getInt(ServerConfig.KEY_AGENT_PORT, ServerConfig.DEFAULT_AGENT_PORT)));

        boolean savePassword = prefs.getBoolean(ServerConfig.KEY_SAVE_PASSWORD, false);
        savePasswordCheck.setChecked(savePassword);


        boolean saveToken = prefs.getBoolean(ServerConfig.KEY_SAVE_AGENT_TOKEN, false);
        saveAgentTokenCheck.setChecked(saveToken);

        backgroundMonitorCheck.setChecked(prefs.getBoolean(ServerConfig.KEY_BACKGROUND_MONITOR, false));
        final int generation = ++secretGeneration;
        SecretWorker.execute(() -> {
            String password = savePassword ? SecurePrefs.loadPassword(this) : "";
            String token = saveToken ? SecurePrefs.loadAgentToken(this) : "";
            handler.post(() -> {
                if (isDestroyed() || isFinishing() || generation != secretGeneration) return;
                if (passwordInput.length() == 0 && savePasswordCheck.isChecked()) passwordInput.setText(password);
                if (agentTokenInput.length() == 0 && saveAgentTokenCheck.isChecked()) agentTokenInput.setText(token);
            });
        });
    }

    private void save() {
        int port = parsePort(portInput, 1, 65535);
        int agentPort = parsePort(agentPortInput, 1, 65535);
        String user = userInput.getText().toString().trim();
        if (port <= 0 || agentPort <= 0 || TextUtils.isEmpty(user)) {
            Toast.makeText(this, "Проверь порт, пользователя и порт агента", Toast.LENGTH_SHORT).show();
            return;
        }

        SharedPreferences prefs = getSharedPreferences(ServerConfig.PREFS, MODE_PRIVATE);
        prefs.edit()
                .putInt(ServerConfig.KEY_PORT, port)
                .putString(ServerConfig.KEY_USER, user)
                .putInt(ServerConfig.KEY_AGENT_PORT, agentPort)
                .putBoolean(ServerConfig.KEY_SAVE_PASSWORD, savePasswordCheck.isChecked())
                .putBoolean(ServerConfig.KEY_SAVE_AGENT_TOKEN, saveAgentTokenCheck.isChecked())
                .putBoolean(ServerConfig.KEY_BACKGROUND_MONITOR, backgroundMonitorCheck.isChecked())
                .remove(ServerConfig.KEY_MONITOR_HAS_STATE)
                .apply();

        final boolean keepPassword = savePasswordCheck.isChecked(), keepToken = saveAgentTokenCheck.isChecked();
        final String password = passwordInput.getText().toString(), token = agentTokenInput.getText().toString();
        ++secretGeneration;
        SecretWorker.execute(() -> {
            boolean saved = false;
            try {
                if (keepPassword) SecurePrefs.savePassword(this, password); else SecurePrefs.clearPassword(this);
                if (keepToken) SecurePrefs.saveAgentToken(this, token); else SecurePrefs.clearAgentToken(this);
                saved = true;
            } catch (Exception e) { /* Never display raw Keystore errors. */ }
            final boolean success = saved;
            if (!success) prefs.edit().putBoolean(ServerConfig.KEY_SAVE_PASSWORD, false)
                    .putBoolean(ServerConfig.KEY_SAVE_AGENT_TOKEN, false).apply();
            handler.post(() -> { if (isUiActive()) Toast.makeText(this, success ? "Настройки сохранены"
                    : "Не удалось сохранить секрет в Android Keystore", Toast.LENGTH_SHORT).show(); });
        });
        MonitoringScheduler.setEnabled(this, backgroundMonitorCheck.isChecked());
    }

    @Override protected void onDestroy() { ++secretGeneration; handler.removeCallbacksAndMessages(null); super.onDestroy(); }

    private int parsePort(EditText input, int min, int max) {
        try {
            int p = Integer.parseInt(input.getText().toString().trim());
            return p >= min && p <= max ? p : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    private void showAgentWizard() {
        new AlertDialog.Builder(this)
                .setTitle("Настройка серверного агента")
                .setMessage("Агент нужен только для кнопок Start / Stop / Restart, когда SSH уже недоступен.\n\n"
                        + "1. Скопируй папку серверного агента из комплекта проекта на серверное устройство.\n\n"
                        + "2. В Termux открой эту папку и выполни:\n./install.sh\n\n"
                        + "3. Скрипт покажет случайный токен. Введи его здесь и включи сохранение в Keystore.\n\n"
                        + "4. Запусти агент вручную:\npython ~/home-server-agent/agent.py\n\n"
                        + "5. Проверь кнопкой «Проверить агент» на Dashboard. Только после успешной ручной проверки можно включать install-boot.sh.\n\n"
                        + "Агент не должен запускать второй sshd сам по себе и его порт 8787 нельзя открывать в WAN.")
                .setPositiveButton("Понятно", null)
                .show();
    }
}
