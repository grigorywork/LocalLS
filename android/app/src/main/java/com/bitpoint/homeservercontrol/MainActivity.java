package com.bitpoint.homeservercontrol;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Switch;
import android.widget.FrameLayout;
import android.view.Gravity;
import android.net.Uri;
import android.os.SystemClock;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends BaseActivity {
    private EditText localHostInput, vpnHostInput, portInput, userInput, passwordInput;
    private EditText iperfTargetInput, agentPortInput, agentTokenInput;
    private CheckBox savePasswordCheck, autoRefreshCheck, saveAgentTokenCheck, backgroundMonitorCheck;
    private RadioGroup modeGroup;
    private RadioButton localModeButton, vpnModeButton;

    private TextView statusText, statusDetail, ipValue, portValue, userValue,
            latencyValue, diskValue, uptimeValue, sshdValue, batteryValue,
            temperatureValue, chargingValue, iperfValue, fingerprintValue,
            commandHelpText, iperfOutputText, agentOutputText, historyValue, logText;
    private Button checkButton, trustKeyButton;

    private static final int REQ_EXPORT_REPORT = 601;
    private static final int REQ_NOTIFICATIONS = 602;
    private String pendingReport = "";
    private LiveSpeedPanel liveSpeedPanel;
    private final ArrayList<Uri> attachedFiles = new ArrayList<>();
    private boolean updatingVpnSwitch;
    private Boolean requestedVpn;
    private long vpnRequestDeadline;
    private final Runnable vpnRefresh = () -> refreshVpnStatus();
    private Object drawerBackCallback; // Avoid resolving the API 33 interface on Android 7–12.

    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private DrawerSections drawerSections;
    private WifiSetupController wifiSetup;
    private AlertDialog pendingAgentDialog;
    private volatile int uiGeneration, secretGeneration;
    private int quickGeneration, iperfGeneration, agentGeneration;
    private java.util.concurrent.Future<?> checkTask, quickTask, iperfTask, agentTask;
    private boolean loadingConfig;
    private int loadedConfigSignature;
    private boolean checking = false;
    private boolean firstResume = true;

    private String lastSeenFingerprint = "";
    private String lastSeenHost = "";
    private int lastSeenPort = -1;

    private final Runnable autoRefreshRunnable = new Runnable() {
        @Override public void run() {
            if (isUiActive() && autoRefreshCheck != null && autoRefreshCheck.isChecked()) {
                checkServer(false);
                handler.postDelayed(this, 15_000);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ConfigMigrator.migrate(this);
        setContentView(R.layout.activity_main);
        bindViews();
        liveSpeedPanel = new LiveSpeedPanel(findViewById(R.id.dashboardSpeedChart), findViewById(R.id.dashboardSpeedSummary));
        loadConfig();
        wireActions();
        drawerSections = new DrawerSections(this,
                new int[]{R.id.drawerConnectionHeader, R.id.drawerSettingsHeader, R.id.drawerWifiHeader,
                        R.id.drawerAppearanceHeader, R.id.drawerToolsHeader, R.id.drawerVpnHeader},
                new int[]{R.id.drawerConnectionPane, R.id.drawerSettingsPane, R.id.drawerWifiPane,
                        R.id.drawerAppearancePane, R.id.drawerToolsPane, R.id.drawerVpnPane});
        wifiSetup = new WifiSetupController(this);
        findViewById(R.id.drawerVpnHeader).setOnClickListener(v -> {
            drawerSections.togglePane(R.id.drawerVpnPane); refreshVpnStatus();
        });
        renderConnectionSummary();
        if (savedInstanceState == null || !savedInstanceState.containsKey(FileAttachments.EXTRA))
            for (Uri uri : FileAttachments.from(getIntent())) FileAttachments.add(attachedFiles, uri);
        if (savedInstanceState != null) {
            ArrayList<Uri> restored = savedInstanceState.getParcelableArrayList(FileAttachments.EXTRA);
            if (restored != null) for (Uri uri : restored) FileAttachments.add(attachedFiles, uri);
        }
        renderAttachmentEntry();
    }


    @Override
    protected void onResume() {
        super.onResume();
        if (liveSpeedPanel != null) liveSpeedPanel.start();
        if (wifiSetup != null) wifiSetup.start();
        handler.removeCallbacks(autoRefreshRunnable);
        if (autoRefreshCheck.isChecked()) handler.postDelayed(autoRefreshRunnable, 15000);
        renderDashboardLatency();
        refreshVpnStatus();
        if (firstResume) {
            firstResume = false;
            return;
        }
        if (loadedConfigSignature != configSignature()) {
            cancelNetworkWork(); invalidateConnectionStats(); loadConfig();
        }
        renderConnectionSummary();
    }

    private int configSignature() {
        SharedPreferences prefs = getSharedPreferences(ServerConfig.PREFS, MODE_PRIVATE);
        return java.util.Objects.hash(prefs.getString(ServerConfig.KEY_LOCAL_HOST, ""),
                prefs.getString(ServerConfig.KEY_VPN_HOST, ""), prefs.getString(ServerConfig.KEY_MODE, ""),
                prefs.getInt(ServerConfig.KEY_PORT, ServerConfig.DEFAULT_PORT), prefs.getString(ServerConfig.KEY_USER, ""),
                prefs.getInt(ServerConfig.KEY_AGENT_PORT, ServerConfig.DEFAULT_AGENT_PORT),
                prefs.getBoolean(ServerConfig.KEY_SAVE_PASSWORD, false), prefs.getBoolean(ServerConfig.KEY_SAVE_AGENT_TOKEN, false),
                prefs.getBoolean(ServerConfig.KEY_BACKGROUND_MONITOR, false), prefs.getString(ServerConfig.KEY_PASSWORD_CIPHER, ""),
                prefs.getString(ServerConfig.KEY_AGENT_TOKEN_CIPHER, ""));
    }

    private void invalidateConnectionStats() {
        statusText.setText(com.bitpoint.homeservercontrol.ui.R.string.status_ab27bd0a2936);
        statusText.setTextColor(ThemeCatalog.color(this, com.bitpoint.homeservercontrol.ui.R.attr.hscWarning));
        statusDetail.setText(com.bitpoint.homeservercontrol.ui.R.string.status_a7e8f235c5f7);
        lastSeenFingerprint = ""; lastSeenHost = ""; lastSeenPort = -1;
        trustKeyButton.setVisibility(View.GONE);
        fingerprintValue.setVisibility(View.GONE);
        latencyValue.setText(com.bitpoint.homeservercontrol.ui.R.string.status_36c28e09c94a); diskValue.setText(com.bitpoint.homeservercontrol.ui.R.string.status_e1faaf5992f7); uptimeValue.setText(com.bitpoint.homeservercontrol.ui.R.string.status_cbe1cb69c8a9);
        sshdValue.setText(com.bitpoint.homeservercontrol.ui.R.string.status_ce3ecbddec73); batteryValue.setText(com.bitpoint.homeservercontrol.ui.R.string.status_8d23eea1be14); temperatureValue.setText(com.bitpoint.homeservercontrol.ui.R.string.status_196fa3c8ea8d);
        chargingValue.setText(com.bitpoint.homeservercontrol.ui.R.string.status_22a0cf38a169); iperfValue.setText(com.bitpoint.homeservercontrol.ui.R.string.status_c80b4e6b81e1);
    }

    private void bindViews() {
        localHostInput = findViewById(R.id.localHostInput);
        vpnHostInput = findViewById(R.id.vpnHostInput);
        portInput = findViewById(R.id.portInput);
        userInput = findViewById(R.id.userInput);
        passwordInput = findViewById(R.id.passwordInput);
        iperfTargetInput = findViewById(R.id.iperfTargetInput);
        agentPortInput = findViewById(R.id.agentPortInput);
        agentTokenInput = findViewById(R.id.agentTokenInput);

        savePasswordCheck = findViewById(R.id.savePasswordCheck);
        autoRefreshCheck = findViewById(R.id.autoRefreshCheck);
        saveAgentTokenCheck = findViewById(R.id.saveAgentTokenCheck);
        backgroundMonitorCheck = findViewById(R.id.backgroundMonitorCheck);
        modeGroup = findViewById(R.id.modeGroup);
        localModeButton = findViewById(R.id.localModeButton);
        vpnModeButton = findViewById(R.id.vpnModeButton);

        statusText = findViewById(R.id.statusText);
        statusDetail = findViewById(R.id.statusDetail);
        ipValue = findViewById(R.id.ipValue);
        portValue = findViewById(R.id.portValue);
        userValue = findViewById(R.id.userValue);
        latencyValue = findViewById(R.id.latencyValue);
        diskValue = findViewById(R.id.diskValue);
        uptimeValue = findViewById(R.id.uptimeValue);
        sshdValue = findViewById(R.id.sshdValue);
        batteryValue = findViewById(R.id.batteryValue);
        temperatureValue = findViewById(R.id.temperatureValue);
        chargingValue = findViewById(R.id.chargingValue);
        iperfValue = findViewById(R.id.iperfValue);
        fingerprintValue = findViewById(R.id.fingerprintValue);
        commandHelpText = findViewById(R.id.commandHelpText);
        iperfOutputText = findViewById(R.id.iperfOutputText);
        agentOutputText = findViewById(R.id.agentOutputText);
        historyValue = findViewById(R.id.historyValue);
        logText = findViewById(R.id.logText);

        checkButton = findViewById(R.id.checkButton);
        trustKeyButton = findViewById(R.id.trustKeyButton);
    }

    private void wireActions() {
        findViewById(R.id.advancedToggleButton).setOnClickListener(v -> openToolsDrawer());
        findViewById(R.id.drawerScrim).setOnClickListener(v -> closeToolsDrawer());
        findViewById(R.id.themeChooserButton).setOnClickListener(v -> chooseTheme());
        findViewById(R.id.serverSetupButton).setOnClickListener(v -> showServerMenu());
        findViewById(R.id.restartServerButton).setOnClickListener(v -> {
            if (agentTokenInput.getText().length() == 0) {
                showServerMenu();
                findServerView(R.id.agentCredentialsPanel).setVisibility(View.VISIBLE);
                agentOutputText.setText(com.bitpoint.homeservercontrol.ui.R.string.status_a6920f312e45);
            } else confirmAgentAction("restart", "Перезапустить SSH-службу? Активные SSH/SFTP-подключения прервутся.");
        });
        findViewById(R.id.localServerQuickButton).setOnClickListener(v -> startActivity(
                new Intent(this, ServerSetupActivity.class).putExtra("auto_prepare", true)));
        findViewById(R.id.currentServerSettingsButton).setOnClickListener(v -> showServerMenu());
        findViewById(R.id.appSettingsButton).setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.iconShapeButton).setOnClickListener(v -> chooseIconShape());
        findViewById(R.id.serverWizardMenuButton).setOnClickListener(v -> {
            if (serverMenuDialog != null) serverMenuDialog.dismiss();
            startActivity(new Intent(this, ServerSetupActivity.class));
        });
        findViewById(R.id.agentCredentialsToggleButton).setOnClickListener(v -> {
            View fields = findServerView(R.id.agentCredentialsPanel);
            fields.setVisibility(fields.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
        });
        findViewById(R.id.saveAgentCredentialsButton).setOnClickListener(v -> saveConfig());
        findViewById(R.id.drawerVpnSettingsButton).setOnClickListener(v -> startActivity(
                new Intent(this, NetworkActivity.class).putExtra("expand_vpn", true)));
        findViewById(R.id.drawerVpnSaveButton).setOnClickListener(v -> saveConfig());
        findViewById(R.id.openVpnClientButton).setOnClickListener(v -> openVpnClient());
        findViewById(R.id.vpnHelpButton).setOnClickListener(v -> showVpnHelp());
        ((Switch) findViewById(R.id.drawerVpnSwitch)).setOnCheckedChangeListener((button, checked) -> {
            if (updatingVpnSwitch) return;
            if (checked && VpnController.state(this) == VpnController.State.OTHER_VPN) {
                refreshVpnStatus();
                new AlertDialog.Builder(this).setTitle("Другой VPN уже включён")
                        .setMessage("Android использует один VPN одновременно. Переключиться на Tailscale?")
                        .setNegativeButton("Отмена", null)
                        .setPositiveButton("Переключить", (d, w) -> requestVpn(true)).show();
            } else requestVpn(checked);
        });
        findViewById(R.id.saveButton).setOnClickListener(v -> saveConfig());
        checkButton.setOnClickListener(v -> checkServer(true));
        findViewById(R.id.copyButton).setOnClickListener(v -> copyConnection());
        findViewById(R.id.solidButton).setOnClickListener(v -> openSolidExplorer());
        findViewById(R.id.filesButton).setOnClickListener(v -> openFiles(false));
        findViewById(R.id.attachFilesButton).setOnClickListener(v -> openFiles(attachedFiles.isEmpty()));
        findViewById(R.id.networkButton).setOnClickListener(v -> startActivity(new Intent(this, NetworkActivity.class)));
        findViewById(R.id.settingsButton).setOnClickListener(v -> {
            openToolsDrawer(); drawerSections.open(R.id.drawerSettingsPane);
        });
        findViewById(R.id.historyButton).setOnClickListener(v -> startActivity(new Intent(this, HistoryActivity.class)));
        findViewById(R.id.diagnosticsButton).setOnClickListener(v -> startActivity(new Intent(this, DiagnosticsActivity.class)));
        findViewById(R.id.exportButton).setOnClickListener(v -> exportDiagnostics());
        findViewById(R.id.clearHistoryButton).setOnClickListener(v -> {
            DiskHistoryStore.clear(this);
            historyValue.setText(DiskHistoryStore.render(this, 8));
            appendLog("История памяти очищена.");
        });

        modeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (!loadingConfig) { cancelNetworkWork(); invalidateConnectionStats(); }
            renderConnectionSummary();
            String mode = checkedId == R.id.vpnModeButton ? "VPN / Tailscale" : "локальный Wi-Fi";
            appendLog("Режим подключения: " + mode + ".");
        });

        android.text.TextWatcher profileWatcher = new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence text, int start, int count, int after) { }
            public void onTextChanged(CharSequence text, int start, int before, int count) {
                if (!loadingConfig) { cancelNetworkWork(); invalidateConnectionStats(); renderConnectionSummary(); }
            }
            public void afterTextChanged(android.text.Editable text) { }
        };
        localHostInput.addTextChangedListener(profileWatcher);
        vpnHostInput.addTextChangedListener(profileWatcher);
        portInput.addTextChangedListener(profileWatcher);
        userInput.addTextChangedListener(profileWatcher);
        passwordInput.addTextChangedListener(profileWatcher);
        trustKeyButton.setOnClickListener(v -> trustLastFingerprint());
        findViewById(R.id.forgetKeyButton).setOnClickListener(v -> forgetFingerprint());

        findViewById(R.id.quickSshdButton).setOnClickListener(v -> runQuickCommand(
                "pgrep -a sshd 2>/dev/null || true",
                "pgrep -a sshd — ищет процесс SSH-сервера. Если видишь PID и sshd, сервер запущен."));
        findViewById(R.id.quickIpButton).setOnClickListener(v -> runQuickCommand(
                "ip addr show wlan0 2>/dev/null | grep 'inet ' || true",
                "ip addr show wlan0 | grep 'inet ' — показывает IP-адрес сервера в текущей Wi-Fi сети."));
        findViewById(R.id.quickDiskButton).setOnClickListener(v -> runQuickCommand(
                "df -h \"$HOME/storage/shared\" 2>/dev/null",
                "df -h — показывает общий объём, занятое и свободное место. -h выводит размеры в ГБ/МБ."));
        findViewById(R.id.quickUptimeButton).setOnClickListener(v -> runQuickCommand(
                "uptime",
                "uptime — показывает, сколько времени система работает без перезагрузки и текущую нагрузку."));

        findViewById(R.id.runIperfButton).setOnClickListener(v -> runIperfTest());

        findViewById(R.id.agentStatusButton).setOnClickListener(v -> runAgentAction("status", false));
        findViewById(R.id.agentStartButton).setOnClickListener(v -> runAgentAction("start", false));
        findViewById(R.id.agentRestartButton).setOnClickListener(v -> confirmAgentAction("restart", "Перезапустить SSH-сервер?"));
        findViewById(R.id.agentStopButton).setOnClickListener(v -> confirmAgentAction("stop", "Остановить SSH-сервер? SFTP/WinSCP отключатся."));

        backgroundMonitorCheck.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (loadingConfig) return;
            getSharedPreferences(ServerConfig.PREFS, MODE_PRIVATE).edit()
                    .putBoolean(ServerConfig.KEY_BACKGROUND_MONITOR, isChecked)
                    .remove(ServerConfig.KEY_MONITOR_HAS_STATE)
                    .apply();
            boolean scheduled = MonitoringScheduler.setEnabled(this, isChecked);
            if (isChecked) {
                requestNotificationPermissionIfNeeded();
                appendLog(scheduled
                        ? "Фоновый мониторинг включён: проверка примерно каждые 15 минут."
                        : "Не удалось включить фоновый мониторинг.");
            } else {
                appendLog("Фоновый мониторинг выключен.");
            }
        });

        autoRefreshCheck.setOnCheckedChangeListener((buttonView, isChecked) -> {
            handler.removeCallbacks(autoRefreshRunnable);
            if (isChecked) {
                appendLog("Автопроверка включена: 15 секунд.");
                handler.post(autoRefreshRunnable);
            } else {
                appendLog("Автопроверка выключена.");
            }
        });
    }

    private void loadConfig() {
        loadingConfig = true;
        SharedPreferences prefs = getSharedPreferences(ServerConfig.PREFS, MODE_PRIVATE);
        localHostInput.setText(prefs.getString(ServerConfig.KEY_LOCAL_HOST, ServerConfig.DEFAULT_LOCAL_HOST));
        vpnHostInput.setText(prefs.getString(ServerConfig.KEY_VPN_HOST, ServerConfig.DEFAULT_VPN_HOST));
        portInput.setText(String.valueOf(prefs.getInt(ServerConfig.KEY_PORT, ServerConfig.DEFAULT_PORT)));
        userInput.setText(prefs.getString(ServerConfig.KEY_USER, ServerConfig.DEFAULT_USER));
        iperfTargetInput.setText(prefs.getString(ServerConfig.KEY_IPERF_TARGET, ""));
        agentPortInput.setText(String.valueOf(prefs.getInt(ServerConfig.KEY_AGENT_PORT, ServerConfig.DEFAULT_AGENT_PORT)));

        String mode = prefs.getString(ServerConfig.KEY_MODE, ServerConfig.MODE_LOCAL);
        if (ServerConfig.MODE_VPN.equals(mode)) vpnModeButton.setChecked(true);
        else localModeButton.setChecked(true);

        boolean savePassword = prefs.getBoolean(ServerConfig.KEY_SAVE_PASSWORD, false);
        savePasswordCheck.setChecked(savePassword);


        boolean saveAgentToken = prefs.getBoolean(ServerConfig.KEY_SAVE_AGENT_TOKEN, false);
        saveAgentTokenCheck.setChecked(saveAgentToken);


        boolean backgroundMonitor = prefs.getBoolean(ServerConfig.KEY_BACKGROUND_MONITOR, false);
        backgroundMonitorCheck.setChecked(backgroundMonitor);
        historyValue.setText(DiskHistoryStore.render(this, 8));
        if (backgroundMonitor) MonitoringScheduler.setEnabled(this, true);
        loadingConfig = false;
        loadedConfigSignature = configSignature();
        final int generation = ++secretGeneration;
        final String originalPassword = passwordInput.getText().toString();
        final String originalToken = agentTokenInput.getText().toString();
        SecretWorker.execute(() -> {
            String storedPassword = savePassword ? SecurePrefs.loadPassword(this) : "";
            String storedToken = saveAgentToken ? SecurePrefs.loadAgentToken(this) : "";
            handler.post(() -> {
                if (isDestroyed() || isFinishing() || generation != secretGeneration) return;
                if (savePassword && savePasswordCheck.isChecked() && originalPassword.contentEquals(passwordInput.getText()))
                    passwordInput.setText(storedPassword);
                if (saveAgentToken && saveAgentTokenCheck.isChecked() && originalToken.contentEquals(agentTokenInput.getText()))
                    agentTokenInput.setText(storedToken);
            });
        });
    }

    private boolean saveConfig() {
        String localHost = localHostInput.getText().toString().trim();
        String vpnHost = vpnHostInput.getText().toString().trim();
        String user = userInput.getText().toString().trim();
        int port = parsePort();
        int agentPort = parseAgentPort();

        if (TextUtils.isEmpty(localHost) || TextUtils.isEmpty(user) || port <= 0 || agentPort <= 0) {
            Toast.makeText(this, "Проверь локальный IP, порт, пользователя и порт агента", Toast.LENGTH_SHORT).show();
            return false;
        }

        String mode = vpnModeButton.isChecked() ? ServerConfig.MODE_VPN : ServerConfig.MODE_LOCAL;
        SharedPreferences prefs = getSharedPreferences(ServerConfig.PREFS, MODE_PRIVATE);
        prefs.edit()
                .putString(ServerConfig.KEY_LOCAL_HOST, localHost)
                .putString(ServerConfig.KEY_VPN_HOST, vpnHost)
                .putString(ServerConfig.KEY_MODE, mode)
                .putInt(ServerConfig.KEY_PORT, port)
                .putString(ServerConfig.KEY_USER, user)
                .putString(ServerConfig.KEY_IPERF_TARGET, iperfTargetInput.getText().toString().trim())
                .putInt(ServerConfig.KEY_AGENT_PORT, agentPort)
                .putBoolean(ServerConfig.KEY_SAVE_PASSWORD, savePasswordCheck.isChecked())
                .putBoolean(ServerConfig.KEY_SAVE_AGENT_TOKEN, saveAgentTokenCheck.isChecked())
                .putBoolean(ServerConfig.KEY_BACKGROUND_MONITOR, backgroundMonitorCheck.isChecked())
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
            } catch (Exception e) { /* Keystore errors never include secrets in UI/logs. */ }
            final boolean success = saved;
            if (!success) prefs.edit().putBoolean(ServerConfig.KEY_SAVE_PASSWORD, false)
                    .putBoolean(ServerConfig.KEY_SAVE_AGENT_TOKEN, false).apply();
            handler.post(() -> {
                if (!isUiActive()) return;
                appendLog(success ? "Настройки сохранены." : "Не удалось сохранить секрет в Android Keystore.");
                Toast.makeText(this, success ? "Сохранено" : "Не удалось сохранить секрет в Android Keystore",
                        Toast.LENGTH_SHORT).show();
            });
        });
        renderConnectionSummary();
        return true;
    }

    private String activeHost() {
        return (vpnModeButton.isChecked() ? vpnHostInput : localHostInput).getText().toString().trim();
    }

    private int parsePort() {
        return parsePortValue(portInput);
    }

    private int parseAgentPort() {
        return parsePortValue(agentPortInput);
    }

    private int parsePortValue(EditText input) {
        try {
            int port = Integer.parseInt(input.getText().toString().trim());
            return (port >= 1 && port <= 65535) ? port : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    private void checkServer(boolean userInitiated) {
        if (checking) return;
        final String primaryHost = activeHost();
        final String user = userInput.getText().toString().trim();
        final String password = passwordInput.getText().toString();
        final int port = parsePort();

        if (TextUtils.isEmpty(primaryHost) || TextUtils.isEmpty(user) || port <= 0) {
            if (userInitiated) Toast.makeText(this, "Проверь активный адрес, порт и пользователя", Toast.LENGTH_SHORT).show();
            return;
        }

        checking = true;
        checkButton.setEnabled(false);
        statusText.setText(com.bitpoint.homeservercontrol.ui.R.string.status_32dfa9c36e2b);
        statusText.setTextColor(ThemeCatalog.color(this, com.bitpoint.homeservercontrol.ui.R.attr.hscWarning));
        statusDetail.setText(getString(com.bitpoint.homeservercontrol.ui.R.string.server_endpoint_format, primaryHost, port));
        appendLog("Проверяю " + primaryHost + ":" + port + "…");

        final int generation = uiGeneration;
        checkTask = executor.submit(() -> {
            String usedHost = primaryHost;
            String trustedFingerprint = HostTrustStore.get(this, usedHost, port);
            ServerStats stats = SshClient.probe(usedHost, port, user, password, trustedFingerprint);

            if (!stats.reachable && ConnectionSelector.autoFallback(this)) {
                String alternate = ConnectionSelector.alternateHost(this);
                if (!TextUtils.isEmpty(alternate) && !alternate.equals(primaryHost)) {
                    String altTrusted = HostTrustStore.get(this, alternate, port);
                    ServerStats alternateStats = SshClient.probe(alternate, port, user, password, altTrusted);
                    if (alternateStats.reachable) {
                        usedHost = alternate;
                        stats = alternateStats;
                    }
                }
            }


            final String finalHost = usedHost;
            final ServerStats finalStats = stats;
            handler.post(() -> {
                if (!isUiActive() || generation != uiGeneration) return;
                checking = false;
                checkButton.setEnabled(true);
                if (!primaryHost.equals(finalHost)) {
                    appendLog("Основной маршрут недоступен. Использован резервный: " + finalHost + ".");
                }
                if (finalStats.reachable) ConnectionSelector.rememberWorkingHost(this, finalHost);
                renderStats(finalStats, finalHost, port, user);
            });
        });
    }

    private void renderStats(ServerStats stats, String host, int port, String user) {
        ipValue.setText(getString(com.bitpoint.homeservercontrol.ui.R.string.status_a09644a994b3, ("—".equals(stats.ip) ? host : stats.ip)));
        portValue.setText(getString(com.bitpoint.homeservercontrol.ui.R.string.status_ff7a04dd6c26, port));
        userValue.setText(getString(com.bitpoint.homeservercontrol.ui.R.string.status_617bb5a0a088, user));
        latencyValue.setText(stats.reachable ? getString(com.bitpoint.homeservercontrol.ui.R.string.status_latency_format, stats.latencyMs) : getString(com.bitpoint.homeservercontrol.ui.R.string.status_36c28e09c94a));
        diskValue.setText(getString(com.bitpoint.homeservercontrol.ui.R.string.status_b375486b730b, stats.disk));
        uptimeValue.setText(getString(com.bitpoint.homeservercontrol.ui.R.string.status_3597244eb434, stats.uptime));
        sshdValue.setText(getString(com.bitpoint.homeservercontrol.ui.R.string.status_62e5a47d617d, stats.sshd));
        batteryValue.setText(getString(com.bitpoint.homeservercontrol.ui.R.string.status_3ca1619dfe27, stats.battery));
        temperatureValue.setText(getString(com.bitpoint.homeservercontrol.ui.R.string.status_ca1c6f5dfef8, stats.temperature));
        chargingValue.setText(getString(com.bitpoint.homeservercontrol.ui.R.string.status_a64c88b40f51, stats.charging));
        iperfValue.setText(getString(com.bitpoint.homeservercontrol.ui.R.string.status_cde09eb6dd81, stats.iperf));
        fingerprintValue.setText(getString(com.bitpoint.homeservercontrol.ui.R.string.status_8229b14b80f9, stats.hostFingerprint));
        fingerprintValue.setVisibility(stats.hostFingerprint != null && !"—".equals(stats.hostFingerprint)
                ? View.VISIBLE : View.GONE);

        MetricHistoryStore.addProbe(this, stats.reachable, stats.latencyMs, stats.disk);
        renderDashboardLatency();
        if (stats.authenticated && stats.hostKeyTrusted && !"—".equals(stats.disk)) {
            DiskHistoryStore.add(this, stats.disk);
            historyValue.setText(DiskHistoryStore.render(this, 8));
        }

        lastSeenFingerprint = stats.hostFingerprint == null ? "" : stats.hostFingerprint;
        lastSeenHost = host;
        lastSeenPort = port;
        trustKeyButton.setVisibility(stats.hostKeyNeedsTrust ? View.VISIBLE : View.GONE);

        if (stats.hostKeyMismatch) {
            statusText.setText(com.bitpoint.homeservercontrol.ui.R.string.status_de99b03c323c);
            statusText.setTextColor(ThemeCatalog.color(this, com.bitpoint.homeservercontrol.ui.R.attr.hscError));
            statusDetail.setText(stats.error);
            appendLog(stats.error);
        } else if (stats.hostKeyNeedsTrust) {
            statusText.setText(com.bitpoint.homeservercontrol.ui.R.string.status_d2b35fb33f9d);
            statusText.setTextColor(ThemeCatalog.color(this, com.bitpoint.homeservercontrol.ui.R.attr.hscWarning));
            statusDetail.setText(com.bitpoint.homeservercontrol.ui.R.string.status_2ff061084808);
            appendLog("Получен новый SSH fingerprint. Команды пока заблокированы.");
        } else if (stats.authenticated && stats.hostKeyTrusted) {
            statusText.setText(com.bitpoint.homeservercontrol.ui.R.string.status_7141047c824d);
            statusText.setTextColor(ThemeCatalog.color(this, com.bitpoint.homeservercontrol.ui.R.attr.hscSuccess));
            statusDetail.setText(com.bitpoint.homeservercontrol.ui.R.string.status_0870869f22e7);
            appendLog("Сервер онлайн. SSH-вход успешен.");
        } else if (stats.reachable) {
            statusText.setText(com.bitpoint.homeservercontrol.ui.R.string.status_18bf0e4021be);
            statusText.setTextColor(ThemeCatalog.color(this, com.bitpoint.homeservercontrol.ui.R.attr.hscWarning));
            statusDetail.setText(stats.error);
            appendLog(stats.error);
        } else {
            statusText.setText(com.bitpoint.homeservercontrol.ui.R.string.status_a267c32f1c34);
            statusText.setTextColor(ThemeCatalog.color(this, com.bitpoint.homeservercontrol.ui.R.attr.hscError));
            statusDetail.setText(stats.error);
            appendLog(stats.error);
        }
    }

    private void trustLastFingerprint() {
        if (TextUtils.isEmpty(lastSeenFingerprint) || "—".equals(lastSeenFingerprint) || TextUtils.isEmpty(lastSeenHost) || lastSeenPort <= 0) {
            Toast.makeText(this, "Сначала выполни проверку сервера", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Доверять SSH-ключу?")
                .setMessage("Fingerprint:\n" + lastSeenFingerprint + "\n\nПодтверждай только если это твой сервер.")
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Доверять", (dialog, which) -> {
                    HostTrustStore.trust(this, lastSeenHost, lastSeenPort, lastSeenFingerprint);
                    trustKeyButton.setVisibility(View.GONE);
                    appendLog("SSH fingerprint сохранён для " + lastSeenHost + ":" + lastSeenPort + ".");
                    checkServer(true);
                })
                .show();
    }

    private void forgetFingerprint() {
        String host = activeHost();
        int port = parsePort();
        if (TextUtils.isEmpty(host) || port <= 0) return;
        HostTrustStore.clear(this, host, port);
        appendLog("Сохранённый fingerprint забыт для " + host + ":" + port + ".");
        fingerprintValue.setText(com.bitpoint.homeservercontrol.ui.R.string.status_3450e8b60cd1);
        fingerprintValue.setVisibility(View.GONE);
        Toast.makeText(this, "Fingerprint удалён", Toast.LENGTH_SHORT).show();
    }

    private void runQuickCommand(String command, String explanation) {
        final String host = activeHost();
        final int port = parsePort();
        final String user = userInput.getText().toString().trim();
        final String password = passwordInput.getText().toString();
        final String trusted = HostTrustStore.get(this, host, port);

        if (!validSshInputs(host, port, user, password)) return;
        commandHelpText.setText(getString(com.bitpoint.homeservercontrol.ui.R.string.command_running_format, explanation));
        final int screen = uiGeneration, request = ++quickGeneration;
        if (quickTask != null) quickTask.cancel(true);
        quickTask = executor.submit(() -> {
            SshCommandResult result = SshClient.runCommand(host, port, user, password, trusted, command, 8000);
            handler.post(() -> {
                if (!isUiActive() || screen != uiGeneration || request != quickGeneration) return;
                String text = result.success ? result.output : "Ошибка: " + result.error;
                if (TextUtils.isEmpty(text)) text = "(команда ничего не вывела)";
                commandHelpText.setText(getString(com.bitpoint.homeservercontrol.ui.R.string.command_result_format, explanation, text));
                appendLog(result.success ? "Быстрая команда выполнена." : "Команда не выполнена: " + result.error);
            });
        });
    }

    private void runIperfTest() {
        final String target = iperfTargetInput.getText().toString().trim();
        if (!target.matches("[A-Za-z0-9._:-]{1,253}")) {
            Toast.makeText(this, "Укажи корректный IP/имя ПК", Toast.LENGTH_SHORT).show();
            return;
        }

        final String host = activeHost();
        final int port = parsePort();
        final String user = userInput.getText().toString().trim();
        final String password = passwordInput.getText().toString();
        final String trusted = HostTrustStore.get(this, host, port);
        if (!validSshInputs(host, port, user, password)) return;

        getSharedPreferences(ServerConfig.PREFS, MODE_PRIVATE).edit()
                .putString(ServerConfig.KEY_IPERF_TARGET, target).apply();
        iperfOutputText.setText(com.bitpoint.homeservercontrol.ui.R.string.status_5af958668837);
        appendLog("iperf3: сервер → " + target + ".");

        String command = "iperf3 -c '" + target + "' -P 4 -t 5 --format m";
        final int screen = uiGeneration, request = ++iperfGeneration;
        if (iperfTask != null) iperfTask.cancel(true);
        iperfTask = executor.submit(() -> {
            SshCommandResult result = SshClient.runCommand(host, port, user, password, trusted, command, 15_000);
            handler.post(() -> {
                if (!isUiActive() || screen != uiGeneration || request != iperfGeneration) return;
                if (result.success) {
                    iperfOutputText.setText(getString(com.bitpoint.homeservercontrol.ui.R.string.status_f1ebc5d72f11, result.output));
                    double speed = MetricHistoryStore.parseIperfMbps(result.output);
                    if (!Double.isNaN(speed)) {
                        MetricHistoryStore.addSpeed(this, speed);
                        appendLog(String.format(Locale.getDefault(), "iperf3 завершён: %.1f Мбит/с.", speed));
                    } else {
                        appendLog("iperf3 завершён.");
                    }
                } else {
                    iperfOutputText.setText(getString(com.bitpoint.homeservercontrol.ui.R.string.status_02ff59b1ca7d, result.error));
                    appendLog("iperf3 ошибка: " + result.error);
                }
            });
        });
    }

    private boolean validSshInputs(String host, int port, String user, String password) {
        if (TextUtils.isEmpty(host) || port <= 0 || TextUtils.isEmpty(user) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, "Проверь адрес/порт/логин/пароль", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (TextUtils.isEmpty(HostTrustStore.get(this, host, port))) {
            Toast.makeText(this, "Сначала проверь сервер и подтверди fingerprint", Toast.LENGTH_LONG).show();
            return false;
        }
        return true;
    }

    private AlertDialog serverMenuDialog;
    private View serverManagementPanel;

    private View findServerView(int id) {
        return serverManagementPanel != null ? serverManagementPanel.findViewById(id) : findViewById(id);
    }

    private void showServerMenu() {
        if (serverMenuDialog != null && serverMenuDialog.isShowing()) return;
        serverManagementPanel = findViewById(R.id.serverManagementPanel);
        android.view.ViewGroup originalParent = (android.view.ViewGroup) serverManagementPanel.getParent();
        int position = originalParent.indexOfChild(serverManagementPanel);
        originalParent.removeView(serverManagementPanel);
        serverManagementPanel.setVisibility(View.VISIBLE);
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        int padding = Math.round(12 * getResources().getDisplayMetrics().density);
        scroll.setPadding(padding, padding / 3, padding, padding / 3);
        scroll.addView(serverManagementPanel);
        serverMenuDialog = new AlertDialog.Builder(this).setTitle("Сервер")
                .setView(scroll).setNegativeButton("Закрыть", null).create();
        serverMenuDialog.setOnDismissListener(dialog -> {
            scroll.removeView(serverManagementPanel);
            serverManagementPanel.setVisibility(View.GONE);
            originalParent.addView(serverManagementPanel, position);
        });
        serverMenuDialog.show();
    }

    private void confirmAgentAction(String action, String message) {
        pendingAgentDialog = new AlertDialog.Builder(this)
                .setTitle("Управление SSH")
                .setMessage(message)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Продолжить", (dialog, which) -> runAgentAction(action, true))
                .show();
    }

    private void runAgentAction(String action, boolean confirmed) {
        if (("stop".equals(action) || "restart".equals(action)) && !confirmed) return;
        final String host = activeHost();
        final int port = parseAgentPort();
        final String token = agentTokenInput.getText().toString();
        if (TextUtils.isEmpty(host) || port <= 0 || TextUtils.isEmpty(token)) {
            Toast.makeText(this, "Для агента нужны адрес, порт и токен", Toast.LENGTH_SHORT).show();
            return;
        }

        agentOutputText.setText(getString(com.bitpoint.homeservercontrol.ui.R.string.status_7bb857aaa20b, action));
        final int screen = uiGeneration, request = ++agentGeneration;
        if (agentTask != null) agentTask.cancel(true);
        agentTask = executor.submit(() -> {
            AgentResult result = AgentClient.request(host, port, token, action);
            handler.post(() -> {
                if (!isUiActive() || screen != uiGeneration || request != agentGeneration) return;
                if (result.success) {
                    agentOutputText.setText(getString(com.bitpoint.homeservercontrol.ui.R.string.status_422c9b125018, result.body));
                    appendLog("Агент /" + action + ": успешно.");
                    Toast.makeText(this, "restart".equals(action) ? "SSH-служба перезапущена" : "Команда выполнена", Toast.LENGTH_SHORT).show();
                    if (!"status".equals(action)) handler.postDelayed(() -> checkServer(false), 1200);
                } else {
                    agentOutputText.setText(getString(com.bitpoint.homeservercontrol.ui.R.string.status_f685119c53fb, result.error));
                    appendLog("Агент /" + action + ": " + result.error);
                }
            });
        });
    }

    private void renderConnectionSummary() {
        String host = activeHost();
        int port = parsePort();
        String user = userInput.getText().toString().trim();
        ipValue.setText(getString(com.bitpoint.homeservercontrol.ui.R.string.status_a09644a994b3, (TextUtils.isEmpty(host) ? "—" : host)));
        portValue.setText(port > 0
                ? getString(com.bitpoint.homeservercontrol.ui.R.string.status_ff7a04dd6c26, port)
                : getString(com.bitpoint.homeservercontrol.ui.R.string.status_port_unavailable));
        userValue.setText(getString(com.bitpoint.homeservercontrol.ui.R.string.status_617bb5a0a088, user));
    }

    private void copyConnection() {
        String host = activeHost();
        int port = parsePort();
        String user = userInput.getText().toString().trim();
        String text = "Протокол: SFTP\nХост: " + host + "\nПорт: " + port + "\nПользователь: " + user;
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("SFTP server", text));
        appendLog("Данные SFTP скопированы в буфер.");
        Toast.makeText(this, "Данные SFTP скопированы", Toast.LENGTH_SHORT).show();
    }

    private void openFiles(boolean pickAttachment) {
        String host = activeHost();
        int port = parsePort();
        String user = userInput.getText().toString().trim();
        String password = passwordInput.getText().toString();
        if (!validSshInputs(host, port, user, password)) return;

        // Сохраняем несекретные поля, чтобы файловый экран использовал тот же режим/адрес.
        if (!saveConfig()) return;
        Intent intent = new Intent(this, FilesActivity.class);
        intent.putExtra(FileAttachments.PICK_ON_OPEN, pickAttachment);
        FileAttachments.put(intent, attachedFiles);
        SessionPassword.put(password);
        try { startActivity(intent); }
        catch (RuntimeException e) {
            SessionPassword.take();
            attachedFiles.clear();
            renderAttachmentEntry();
            Toast.makeText(this, "Нет доступа к вложениям. Прикрепи файлы заново через выбор файлов Android.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        attachedFiles.clear();
        renderAttachmentEntry();
        appendLog("Открыт встроенный SFTP-браузер.");
    }

    private void renderAttachmentEntry() {
        ((Button) findViewById(R.id.attachFilesButton)).setText(attachedFiles.isEmpty()
                ? "Отправить файл" : "Вложений: " + attachedFiles.size() + " • Отправить");
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        for (Uri uri : FileAttachments.from(intent)) FileAttachments.add(attachedFiles, uri);
        renderAttachmentEntry();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putParcelableArrayList(FileAttachments.EXTRA, new ArrayList<>(attachedFiles));
        super.onSaveInstanceState(state);
    }

    private void openToolsDrawer() {
        View panel = findViewById(R.id.advancedPanel);
        if (panel.getVisibility() == View.VISIBLE) { closeToolsDrawer(); return; }
        View root = findViewById(R.id.dashboardRoot);
        int width = root.getWidth() > 0 ? root.getWidth() : getResources().getDisplayMetrics().widthPixels;
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) panel.getLayoutParams();
        params.width = width / 2;
        params.gravity = Gravity.RIGHT;
        panel.setLayoutParams(params);
        panel.animate().cancel();
        panel.setTranslationX(params.width);
        panel.setVisibility(View.VISIBLE);
        findViewById(R.id.drawerScrim).setVisibility(View.VISIBLE);
        findViewById(R.id.dashboardScroll).setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        panel.animate().translationX(0).setDuration(220).start();
        if (Build.VERSION.SDK_INT >= 33) {
            drawerBackCallback = (android.window.OnBackInvokedCallback) this::closeToolsDrawer;
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    (android.window.OnBackInvokedCallback) drawerBackCallback);
        }
        refreshVpnStatus();
    }

    private void closeToolsDrawer() {
        if (Build.VERSION.SDK_INT >= 33 && drawerBackCallback != null) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(
                    (android.window.OnBackInvokedCallback) drawerBackCallback);
            drawerBackCallback = null;
        }
        View panel = findViewById(R.id.advancedPanel);
        panel.animate().cancel();
        panel.setVisibility(View.GONE);
        findViewById(R.id.drawerScrim).setVisibility(View.GONE);
        findViewById(R.id.dashboardScroll).setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        handler.removeCallbacks(vpnRefresh);
        android.view.inputmethod.InputMethodManager keyboard = (android.view.inputmethod.InputMethodManager)
                getSystemService(INPUT_METHOD_SERVICE);
        if (keyboard != null) keyboard.hideSoftInputFromWindow(panel.getWindowToken(), 0);
    }

    @Override
    @android.annotation.SuppressLint("GestureBackNavigation") // API 24–32; API 33+ uses the registered callback above.
    public void onBackPressed() {
        if (findViewById(R.id.advancedPanel).getVisibility() == View.VISIBLE) closeToolsDrawer();
        else super.onBackPressed();
    }

    private void chooseIconShape() {
        new AlertDialog.Builder(this).setTitle("Форма значков функций")
                .setSingleChoiceItems(IconShapeCatalog.NAMES, IconShapeCatalog.index(this), (dialog, selected) -> {
                    IconShapeCatalog.select(this, selected);
                    refreshIconStyle();
                    if (serverManagementPanel != null) IconButtons.decorate(serverManagementPanel);
                    dialog.dismiss();
                }).setNegativeButton("Назад", null).show();
    }

    private void chooseTheme() {
        new AlertDialog.Builder(this).setTitle("Цветовая тема")
                .setSingleChoiceItems(ThemeCatalog.NAMES, ThemeCatalog.index(this), (dialog, selected) -> {
                    ThemeCatalog.select(this, selected);
                    dialog.dismiss();
                    recreate();
                }).setNegativeButton("Назад", null).show();
    }

    private void requestVpn(boolean connect) {
        VpnController.Request result = VpnController.request(this, connect);
        if (result == VpnController.Request.SENT) {
            requestedVpn = connect;
            vpnRequestDeadline = SystemClock.elapsedRealtime() + 20_000;
            refreshVpnStatus();
            return;
        }
        requestedVpn = null;
        refreshVpnStatus();
        new AlertDialog.Builder(this).setTitle("VPN / Tailscale")
                .setMessage(result == VpnController.Request.NOT_INSTALLED
                        ? "Установи Tailscale на телефон, серверное устройство и компьютер. Войди в одну сеть и разреши VPN на телефоне. После настройки тумблер сможет включать и выключать подключение."
                        : "Открой Tailscale и заверши настройку. Эта версия клиента или политика Android не разрешила команду подключения.")
                .setNegativeButton("Назад", null)
                .setPositiveButton(result == VpnController.Request.NOT_INSTALLED ? "Скачать" : "Открыть",
                        (dialog, which) -> openVpnClient()).show();
    }

    private void refreshVpnStatus() {
        Switch toggle = findViewById(R.id.drawerVpnSwitch);
        if (toggle == null) return;
        handler.removeCallbacks(vpnRefresh);
        VpnController.State state = VpnController.state(this);
        boolean connected = state == VpnController.State.TAILSCALE;
        String message = connected ? "VPN Tailscale включён" : state == VpnController.State.OTHER_VPN
                ? "Включён другой VPN" : "VPN выключен";
        if (requestedVpn != null) {
            if (requestedVpn == connected) requestedVpn = null;
            else if (SystemClock.elapsedRealtime() >= vpnRequestDeadline) {
                requestedVpn = null;
                message = "Открой Tailscale и проверь подключение";
            } else message = requestedVpn ? "Подключение…" : "Отключение…";
        }
        updatingVpnSwitch = true;
        toggle.setChecked(connected);
        toggle.setEnabled(requestedVpn == null);
        updatingVpnSwitch = false;
        ((TextView) findViewById(R.id.drawerVpnStatus)).setText(message);
        ((Button) findViewById(R.id.themeChooserButton)).setText(getString(com.bitpoint.homeservercontrol.ui.R.string.status_8851ca39fa37, ThemeCatalog.NAMES[ThemeCatalog.index(this)]));
        if (isUiActive() && ((findViewById(R.id.advancedPanel).getVisibility() == View.VISIBLE
                && findViewById(R.id.drawerVpnPane).getVisibility() == View.VISIBLE) || requestedVpn != null))
            handler.postDelayed(vpnRefresh, 1000);
    }

    private void openVpnClient() {
        Intent intent = getPackageManager().getLaunchIntentForPackage(VpnController.PACKAGE);
        if (intent == null) intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://tailscale.com/download/android"));
        try { startActivity(intent); }
        catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(this, "Установи Tailscale из магазина приложений", Toast.LENGTH_LONG).show();
        }
    }

    private void showVpnHelp() {
        new AlertDialog.Builder(this).setTitle("Общая сеть телефона и ПК")
                .setMessage("1. Установи Tailscale на серверное устройство, телефон и компьютер.\n\n"
                        + "2. Войди на устройствах в одну сеть Tailscale и включи их подключения.\n\n"
                        + "3. Укажи 100.x.x.x или MagicDNS-имя сервера в VPN-профиле; порт 8022 и логин остаются прежними. На ПК используй тот же адрес в SFTP-клиенте.\n\n"
                        + "4. Дома локальный IP 192.168.1.82 остаётся доступен с обычным Tailscale без exit node. Для доступа из другой сети выбери профиль VPN / Tailscale.\n\n"
                        + "Если используешь exit node, включи Allow LAN access в настройках Tailscale на каждом устройстве.\n\n"
                        + "Тумблер управляет VPN этого телефона. Другие устройства подключаются в своих клиентах.")
                .setPositiveButton("Понятно", null).show();
    }

    private void exportDiagnostics() {
        StringBuilder report = new StringBuilder();
        report.append("LocalLS v0.9\n");
        report.append("Создано: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date())).append("\n\n");
        report.append("Подключение\n");
        report.append("Режим: ").append(vpnModeButton.isChecked() ? "VPN/Tailscale" : "Local").append("\n");
        report.append("Хост: ").append(activeHost()).append("\n");
        report.append("Порт: ").append(parsePort()).append("\n");
        report.append("Пользователь: ").append(userInput.getText().toString().trim()).append("\n\n");
        report.append("Состояние\n");
        report.append(statusText.getText()).append("\n");
        report.append(statusDetail.getText()).append("\n");
        report.append(latencyValue.getText()).append("\n");
        report.append(diskValue.getText()).append("\n");
        report.append(uptimeValue.getText()).append("\n");
        report.append(sshdValue.getText()).append("\n");
        report.append(batteryValue.getText()).append("\n");
        report.append(temperatureValue.getText()).append("\n");
        report.append(chargingValue.getText()).append("\n");
        report.append(iperfValue.getText()).append("\n");
        report.append(fingerprintValue.getText()).append("\n\n");
        report.append("История памяти\n").append(DiskHistoryStore.render(this, 20)).append("\n\n");
        report.append("Журнал\n").append(logText.getText()).append("\n");
        report.append("\nПароль SSH и токен агента намеренно не экспортируются.\n");
        pendingReport = SecretRedactor.redact(report.toString(),
                passwordInput.getText().toString(), agentTokenInput.getText().toString());

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE, "home-server-diagnostics.txt");
        startActivityForResult(intent, REQ_EXPORT_REPORT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == WifiSetupController.ADD_NETWORK) { wifiSetup.onAddResult(resultCode); return; }
        if (requestCode != REQ_EXPORT_REPORT || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        final Uri destination = data.getData();
        final String report = pendingReport;
        pendingReport = "";
        executor.execute(() -> {
            boolean saved = false;
            try (OutputStream out = getContentResolver().openOutputStream(destination, "w")) {
                if (out != null) { out.write(report.getBytes(StandardCharsets.UTF_8)); out.flush(); saved = true; }
            } catch (Exception e) { /* Do not expose provider errors. */ }
            final boolean success = saved;
            handler.post(() -> { if (isUiActive()) Toast.makeText(this,
                    success ? "Отчёт сохранён" : "Не удалось сохранить отчёт", Toast.LENGTH_SHORT).show(); });
        });
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == WifiSetupController.PERMISSION) { wifiSetup.onPermissionResult(grantResults); return; }
        if (requestCode == REQ_NOTIFICATIONS && grantResults.length > 0
                && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Мониторинг продолжит работать, но уведомления Android запрещены", Toast.LENGTH_LONG).show();
        }
    }

    private void openSolidExplorer() {
        Intent launch = getPackageManager().getLaunchIntentForPackage("pl.solidexplorer2");
        if (launch != null) {
            startActivity(launch);
            appendLog("Открыт Solid Explorer.");
        } else {
            Toast.makeText(this, "Solid Explorer не найден", Toast.LENGTH_LONG).show();
        }
    }

    private void appendLog(String message) {
        String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        String existing = logText.getText().toString();
        String safe = SecretRedactor.redact(message,
                passwordInput.getText().toString(), agentTokenInput.getText().toString());
        String next = time + "  " + safe + "\n" + existing;
        if (next.length() > 6000) next = next.substring(0, 6000);
        logText.setText(next);
    }

    private void renderDashboardLatency() {
        MetricChartView chart = findViewById(R.id.dashboardLatencyChart);
        TextView summary = findViewById(R.id.dashboardLatencySummary);
        if (chart == null || summary == null) return;
        List<Float> recent = new ArrayList<>();
        for (MetricHistoryStore.Sample sample : MetricHistoryStore.read(this)) {
            if (!Float.isNaN(sample.latencyMs)) recent.add(sample.latencyMs);
        }
        while (recent.size() > 20) recent.remove(0);
        float peak = 0;
        for (Float value : recent) peak = Math.max(peak, value);
        summary.setText(recent.isEmpty() ? "Нет замеров"
                : String.format(Locale.getDefault(), "Последний: %.0f мс", recent.get(recent.size() - 1)));
        while (recent.size() < 20) recent.add(0, Float.NaN);
        chart.setLabels("мс", "20 замеров");
        chart.setValues(recent, 0, Math.max(10f, peak * 1.2f));
    }

    private void cancelNetworkWork() {
        ++uiGeneration;
        if (checkTask != null) checkTask.cancel(true);
        if (quickTask != null) quickTask.cancel(true);
        if (iperfTask != null) iperfTask.cancel(true);
        if (agentTask != null) agentTask.cancel(true);
        checking = false;
        if (checkButton != null) checkButton.setEnabled(true);
        if (statusText != null && "● ПРОВЕРКА…".contentEquals(statusText.getText())) {
            statusText.setText(com.bitpoint.homeservercontrol.ui.R.string.status_f7ed201ba529);
            statusDetail.setText(com.bitpoint.homeservercontrol.ui.R.string.status_7fad5c3e34a2);
        }
    }

    @Override
    protected void onPause() {
        if (liveSpeedPanel != null) liveSpeedPanel.stop();
        if (wifiSetup != null) wifiSetup.stop();
        cancelNetworkWork();
        handler.removeCallbacks(autoRefreshRunnable);
        handler.removeCallbacks(vpnRefresh);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (serverMenuDialog != null) serverMenuDialog.dismiss();
        if (pendingAgentDialog != null) pendingAgentDialog.dismiss();
        if (wifiSetup != null) wifiSetup.destroy();
        ++secretGeneration;
        if (Build.VERSION.SDK_INT >= 33 && drawerBackCallback != null)
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(
                    (android.window.OnBackInvokedCallback) drawerBackCallback);
        if (liveSpeedPanel != null) liveSpeedPanel.stop();
        handler.removeCallbacksAndMessages(null);
        executor.shutdownNow();
        super.onDestroy();
    }
}
