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
    private EditText localHostInput, vpnHostInput, portInput, userInput;
    private CheckBox autoRefreshCheck;
    private RadioGroup modeGroup;
    private RadioButton localModeButton, vpnModeButton;

    private TextView statusText, statusDetail, ipValue, portValue, userValue,
            latencyValue, diskValue, uptimeValue, batteryValue, fingerprintValue, logText;
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
    private final Runnable localServerRefresh=new Runnable() {
        public void run() {refreshLocalServer();if(isUiActive())handler.postDelayed(this,1000);}
    };
    private Runnable savedProfileCheck;
    private Object drawerBackCallback; // Avoid resolving the API 33 interface on Android 7–12.

    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private DrawerSections drawerSections;
    private WifiSetupController wifiSetup;
    private volatile int uiGeneration, secretGeneration;
    private java.util.concurrent.Future<?> checkTask;
    private boolean loadingConfig;
    private int loadedConfigSignature;
    private boolean checking = false;
    private boolean firstResume = true;

    private String lastSeenFingerprint = "";
    private String lastSeenHost = "";
    private int lastSeenPort = -1;

    private final Runnable autoRefreshRunnable = new Runnable() {
        @Override public void run() {
            if (isUiActive() && autoRefreshCheck != null && autoRefreshCheck.isChecked() && clientEnabled()) {
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
        ((Switch)findViewById(R.id.showClientSwitch)).setOnCheckedChangeListener((v,checked)->{
            getSharedPreferences(EmbeddedServerService.PREFS,0).edit().putBoolean("show_client",checked).apply();renderRole();handler.removeCallbacks(autoRefreshRunnable);if(checked && autoRefreshCheck.isChecked())handler.post(autoRefreshRunnable);else if(!checked)cancelNetworkWork();
        });
        renderRole();
        drawerSections = new DrawerSections(this,
                new int[]{R.id.drawerSettingsHeader, R.id.drawerWifiHeader,
                        R.id.drawerAppearanceHeader, R.id.drawerToolsHeader, R.id.drawerVpnHeader},
                new int[]{R.id.drawerSettingsPane, R.id.drawerWifiPane,
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
        StatusBadge.apply(statusText,StatusBadge.Tone.WARNING);
        if(activeHost().isEmpty()) {
            findViewById(R.id.drawerConnectionPane).setVisibility(View.VISIBLE);
            statusText.setText("НЕ НАСТРОЕНО");statusDetail.setText("Укажи адрес другого устройства в разделе «Адрес и ключ».");
        }
    }


    @Override
    protected void onResume() {
        super.onResume();
        if (liveSpeedPanel != null) liveSpeedPanel.start();
        if (wifiSetup != null) wifiSetup.start();
        handler.removeCallbacks(autoRefreshRunnable);
        if (autoRefreshCheck.isChecked()) handler.postDelayed(autoRefreshRunnable, 15000);
        renderDashboardLatency();
        handler.removeCallbacks(localServerRefresh);handler.post(localServerRefresh);
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

    private boolean clientEnabled(){var p=getSharedPreferences(EmbeddedServerService.PREFS,0);return !p.getBoolean("server_role",false)||p.getBoolean("show_client",false);}
    private void renderRole(){
        var p=getSharedPreferences(EmbeddedServerService.PREFS,0);boolean server=p.getBoolean("server_role",false),show=p.getBoolean("show_client",false);
        Switch toggle=findViewById(R.id.showClientSwitch);toggle.setVisibility(server?View.VISIBLE:View.GONE);
        if(toggle.isChecked()!=show)toggle.setChecked(show);
        findViewById(R.id.remoteConnectionCard).setVisibility(server&&!show?View.GONE:View.VISIBLE);
    }
    private void refreshLocalServer() {
        renderRole();
        ((WifiAddressView)findViewById(R.id.currentWifiAddress)).refresh();
        TextView summary=findViewById(R.id.localServerSummary);
        summary.setText(EmbeddedServerService.status);
        StatusBadge.apply(summary,EmbeddedServerService.running?(EmbeddedServerService.waitingForClient?StatusBadge.Tone.WARNING:StatusBadge.Tone.SUCCESS):
            EmbeddedServerService.starting || EmbeddedServerService.launchRequested!=0?StatusBadge.Tone.WARNING:
            EmbeddedServerService.diagnostic.isEmpty()?StatusBadge.Tone.NEUTRAL:StatusBadge.Tone.ERROR);
    }

    private int configSignature() {
        SharedPreferences prefs = getSharedPreferences(ServerConfig.PREFS, MODE_PRIVATE);
        return java.util.Objects.hash(prefs.getString(ServerConfig.KEY_LOCAL_HOST, ""),
                prefs.getString(ServerConfig.KEY_VPN_HOST, ""), prefs.getString(ServerConfig.KEY_MODE, ""),
                prefs.getInt(ServerConfig.KEY_PORT, ServerConfig.DEFAULT_PORT), prefs.getString(ServerConfig.KEY_USER, ""),
                prefs.getInt(ServerConfig.KEY_AGENT_PORT, ServerConfig.DEFAULT_AGENT_PORT),
                prefs.getBoolean(ServerConfig.KEY_SAVE_PASSWORD, false), prefs.getBoolean(ServerConfig.KEY_SAVE_AGENT_TOKEN, false),
                prefs.getBoolean(ServerConfig.KEY_BACKGROUND_MONITOR, false), prefs.getString(ServerConfig.KEY_PASSWORD_CIPHER, ""),
                prefs.getString(ServerConfig.KEY_AGENT_TOKEN_CIPHER, ""),
                prefs.getString(SshKeys.MODE,"password"),prefs.getLong(SshKeys.REVISION,0L),prefs.getBoolean(AgentTls.LEGACY,false));
    }

    private void invalidateConnectionStats() {
        StatusBadge.apply(statusText,StatusBadge.Tone.WARNING);
        statusText.setText(com.bitpoint.homeservercontrol.ui.R.string.status_ab27bd0a2936);
        statusText.setTextColor(ThemeCatalog.color(this, com.bitpoint.homeservercontrol.ui.R.attr.hscWarning));
        statusDetail.setText(com.bitpoint.homeservercontrol.ui.R.string.status_a7e8f235c5f7);
        lastSeenFingerprint = ""; lastSeenHost = ""; lastSeenPort = -1;
        trustKeyButton.setVisibility(View.GONE);
        fingerprintValue.setVisibility(View.GONE);
        latencyValue.setText(com.bitpoint.homeservercontrol.ui.R.string.status_36c28e09c94a); diskValue.setText(com.bitpoint.homeservercontrol.ui.R.string.status_e1faaf5992f7); uptimeValue.setText(com.bitpoint.homeservercontrol.ui.R.string.status_cbe1cb69c8a9);
    }

    private void bindViews() {
        localHostInput = findViewById(R.id.localHostInput);
        vpnHostInput = findViewById(R.id.vpnHostInput);
        portInput = findViewById(R.id.portInput);
        userInput = findViewById(R.id.userInput);

        autoRefreshCheck = findViewById(R.id.autoRefreshCheck);
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
        batteryValue = findViewById(R.id.batteryValue);
        fingerprintValue = findViewById(R.id.fingerprintValue);
        logText = findViewById(R.id.logText);

        checkButton = findViewById(R.id.checkButton);
        trustKeyButton = findViewById(R.id.trustKeyButton);
    }

    private void wireActions() {
        findViewById(R.id.drawerScrim).setOnClickListener(v -> closeToolsDrawer());
        findViewById(R.id.drawerConnectionHeader).setOnClickListener(v -> {
            View pane=findViewById(R.id.drawerConnectionPane);
            pane.setVisibility(pane.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE);
        });
        findViewById(R.id.themeChooserButton).setOnClickListener(v -> chooseTheme());
        findViewById(R.id.iconShapeButton).setOnClickListener(v -> chooseIconShape());
        findViewById(R.id.localServerQuickButton).setOnClickListener(v -> startActivity(new Intent(this,EmbeddedServerActivity.class)));
        findViewById(R.id.appSettingsButton).setOnClickListener(v -> startActivity(new Intent(this,SettingsActivity.class)));
        findViewById(R.id.drawerVpnSettingsButton).setOnClickListener(v -> startActivity(new Intent(this,NetworkActivity.class).putExtra("expand_vpn",true)));
        findViewById(R.id.openVpnClientButton).setOnClickListener(v -> openVpnClient());
        findViewById(R.id.vpnHelpButton).setOnClickListener(v -> showVpnHelp());
        ((Switch)findViewById(R.id.drawerVpnSwitch)).setOnCheckedChangeListener((button,checked) -> {
            if(updatingVpnSwitch)return;
            if(checked && VpnController.state(this)==VpnController.State.OTHER_VPN) {
                refreshVpnStatus();new AlertDialog.Builder(this).setTitle("Другой VPN уже включён")
                    .setMessage("Переключиться на Tailscale?").setNegativeButton("Отмена",null)
                    .setPositiveButton("Переключить",(d,w) -> requestVpn(true)).show();
            } else requestVpn(checked);
        });
        findViewById(R.id.sshKeySetupButton).setOnClickListener(v -> {
            if(saveConfig())startActivity(new Intent(this,SecurityActivity.class));
        });
        findViewById(R.id.saveButton).setOnClickListener(v -> {
            if(!saveConfig())return;
            if(savedProfileCheck!=null)handler.removeCallbacks(savedProfileCheck);
            final int generation=uiGeneration;
            savedProfileCheck=() -> {if(isUiActive() && generation==uiGeneration)checkServer(false);};
            handler.postDelayed(savedProfileCheck,750);
        });
        checkButton.setOnClickListener(v -> checkServer(true));
        findViewById(R.id.copyButton).setOnClickListener(v -> copyConnection());
        findViewById(R.id.filesButton).setOnClickListener(v -> openFiles(false));
        findViewById(R.id.attachFilesButton).setOnClickListener(v -> openFiles(attachedFiles.isEmpty()));
        findViewById(R.id.networkButton).setOnClickListener(v -> startActivity(new Intent(this,NetworkActivity.class)));
        findViewById(R.id.settingsButton).setOnClickListener(v -> {openToolsDrawer();drawerSections.open(R.id.drawerSettingsPane);});
        findViewById(R.id.historyButton).setOnClickListener(v -> startActivity(new Intent(this,HistoryActivity.class)));
        findViewById(R.id.diagnosticsButton).setOnClickListener(v -> startActivity(new Intent(this,DiagnosticsActivity.class)));
        findViewById(R.id.exportButton).setOnClickListener(v -> exportDiagnostics());
        modeGroup.setOnCheckedChangeListener((group,id) -> {
            if(!loadingConfig){cancelNetworkWork();invalidateConnectionStats();}renderConnectionSummary();
        });
        android.text.TextWatcher watcher=new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s,int start,int count,int after) { }
            public void onTextChanged(CharSequence s,int start,int before,int count) {
                if(!loadingConfig){cancelNetworkWork();invalidateConnectionStats();renderConnectionSummary();}
            }
            public void afterTextChanged(android.text.Editable value) { }
        };
        localHostInput.addTextChangedListener(watcher);vpnHostInput.addTextChangedListener(watcher);
        portInput.addTextChangedListener(watcher);userInput.addTextChangedListener(watcher);
        trustKeyButton.setOnClickListener(v -> trustLastFingerprint());
        findViewById(R.id.forgetKeyButton).setOnClickListener(v -> forgetFingerprint());
        findViewById(R.id.profileTransferButton).setOnClickListener(v -> startActivity(new Intent(this,ProfileTransferActivity.class)));
        findViewById(R.id.importEndpointButton).setOnClickListener(v -> showEndpointImport());
        autoRefreshCheck.setOnCheckedChangeListener((button,checked) -> {
            handler.removeCallbacks(autoRefreshRunnable);if(checked)handler.post(autoRefreshRunnable);
        });
    }

    private void loadConfig() {
        loadingConfig=true;
        SharedPreferences prefs=getSharedPreferences(ServerConfig.PREFS,MODE_PRIVATE);
        localHostInput.setText(prefs.getString(ServerConfig.KEY_LOCAL_HOST,ServerConfig.DEFAULT_LOCAL_HOST));
        vpnHostInput.setText(prefs.getString(ServerConfig.KEY_VPN_HOST,ServerConfig.DEFAULT_VPN_HOST));
        portInput.setText(String.valueOf(prefs.getInt(ServerConfig.KEY_PORT,ServerConfig.DEFAULT_PORT)));
        userInput.setText(prefs.getString(ServerConfig.KEY_USER,ServerConfig.DEFAULT_USER));
        if(ServerConfig.MODE_VPN.equals(prefs.getString(ServerConfig.KEY_MODE,ServerConfig.MODE_LOCAL)))vpnModeButton.setChecked(true);
        else localModeButton.setChecked(true);
        if(prefs.getBoolean(ServerConfig.KEY_BACKGROUND_MONITOR,false))MonitoringScheduler.setEnabled(this,true);
        loadingConfig=false;loadedConfigSignature=configSignature();
    }

    private void showEndpointImport() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        input.setHint("ssh://user@192.168.1.82:8022");
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Адрес подключения")
                .setMessage("Вставь SSH/SFTP-адрес без пароля и пути. Он заполнит активную точку Local/VPN. Затем сохрани и проверь подключение.")
                .setView(input).setNegativeButton("Отмена", null).setPositiveButton("Заполнить", null).create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                ConnectionEndpoint endpoint = ConnectionEndpoint.parse(input.getText().toString(),
                        userInput.getText().toString().trim(), parsePort());
                (vpnModeButton.isChecked() ? vpnHostInput : localHostInput).setText(endpoint.host);
                userInput.setText(endpoint.user);
                portInput.setText(String.valueOf(endpoint.port));
                dialog.dismiss();
            } catch (IllegalArgumentException error) { input.setError(error.getMessage()); }
        });
    }

    private boolean saveConfig() {
        String localHost = localHostInput.getText().toString().trim();
        String vpnHost = vpnHostInput.getText().toString().trim();
        String user = userInput.getText().toString().trim();
        int port = parsePort();

        localHostInput.setError(null);
        vpnHostInput.setError(null);
        userInput.setError(null);
        portInput.setError(null);
        boolean invalid = false;
        if ((!localHost.isEmpty() || !vpnModeButton.isChecked()) && !ConnectionEndpoint.validHost(localHost)) {
            localHostInput.setError("Укажи IP или имя сервера без порта и пути"); invalid = true;
        }
        if ((!vpnHost.isEmpty() || vpnModeButton.isChecked()) && !ConnectionEndpoint.validHost(vpnHost)) {
            vpnHostInput.setError("Укажи IP или имя сервера в VPN"); invalid = true;
        }
        if (!ConnectionEndpoint.validUser(user)) {
            userInput.setError("Укажи имя пользователя SSH, например u0_a123"); invalid = true;
        }
        if (port <= 0) { portInput.setError("Порт SSH: от 1 до 65535"); invalid = true; }
        if (invalid) {
            Toast.makeText(this, "Исправь отмеченные поля подключения", Toast.LENGTH_SHORT).show();
            return false;
        }

        String mode = vpnModeButton.isChecked() ? ServerConfig.MODE_VPN : ServerConfig.MODE_LOCAL;
        SharedPreferences prefs = getSharedPreferences(ServerConfig.PREFS, MODE_PRIVATE);
        String oldHost=ConnectionSelector.selectedHost(this),newHost=vpnModeButton.isChecked()?vpnHost:localHost;
        if(port==prefs.getInt(ServerConfig.KEY_PORT,ServerConfig.DEFAULT_PORT) && user.equals(prefs.getString(ServerConfig.KEY_USER,ServerConfig.DEFAULT_USER))){
            try{if(SshKeys.moveRoute(this,oldHost,newHost,port,user))appendLog("Адрес изменён. Прежний ключ и fingerprint сервера сохранены.");}
            catch(Exception e){Toast.makeText(this,"Не удалось сохранить доступ для нового адреса. Старое подключение сохранено.",Toast.LENGTH_LONG).show();return false;}
        }
        prefs.edit()
                .putString(ServerConfig.KEY_LOCAL_HOST, localHost)
                .putString(ServerConfig.KEY_VPN_HOST, vpnHost)
                .putString(ServerConfig.KEY_MODE, mode)
                .putInt(ServerConfig.KEY_PORT, port)
                .putString(ServerConfig.KEY_USER, user)
                .putBoolean(ServerConfig.KEY_SAVE_PASSWORD, false)
                .apply();

        appendLog("Подключение сохранено.");
        Toast.makeText(this,"Сохранено",Toast.LENGTH_SHORT).show();
        renderConnectionSummary();
        return true;
    }

    private String activeHost() {
        return (vpnModeButton.isChecked() ? vpnHostInput : localHostInput).getText().toString().trim();
    }

    private int parsePort() {
        return parsePortValue(portInput);
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
        final String password = "";
        final int port = parsePort();

        if (!ConnectionEndpoint.validHost(primaryHost) || !ConnectionEndpoint.validUser(user) || port <= 0) {
            if (userInitiated) Toast.makeText(this, "Проверь активный адрес, порт и пользователя", Toast.LENGTH_SHORT).show();
            return;
        }

        checking = true;
        StatusBadge.apply(statusText,StatusBadge.Tone.WARNING);
        checkButton.setEnabled(false);
        statusText.setText(com.bitpoint.homeservercontrol.ui.R.string.status_32dfa9c36e2b);
        statusText.setTextColor(ThemeCatalog.color(this, com.bitpoint.homeservercontrol.ui.R.attr.hscWarning));
        statusDetail.setText(getString(com.bitpoint.homeservercontrol.ui.R.string.server_endpoint_format, primaryHost, port));
        appendLog("Проверяю " + primaryHost + ":" + port + "…");

        final int generation = uiGeneration;
        checkTask = executor.submit(() -> {
            String usedHost = primaryHost;
            String trustedFingerprint = HostTrustStore.get(this, usedHost, port);
            ServerStats stats = SshClient.probe(this, usedHost, port, user, password, trustedFingerprint);

            if (!stats.reachable && ConnectionSelector.autoFallback(this)) {
                String alternate = ConnectionSelector.alternateHost(this);
                if (!TextUtils.isEmpty(alternate) && !alternate.equals(primaryHost)) {
                    String altTrusted = HostTrustStore.get(this, alternate, port);
                    ServerStats alternateStats = SshClient.probe(this, alternate, port, user, password, altTrusted);
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
        batteryValue.setText(getString(com.bitpoint.homeservercontrol.ui.R.string.status_3ca1619dfe27, stats.battery));
        fingerprintValue.setText(getString(com.bitpoint.homeservercontrol.ui.R.string.status_8229b14b80f9, stats.hostFingerprint));
        fingerprintValue.setVisibility(stats.hostFingerprint != null && !"—".equals(stats.hostFingerprint)
                ? View.VISIBLE : View.GONE);

        MetricHistoryStore.addProbe(this, stats.reachable, stats.latencyMs, stats.disk);
        renderDashboardLatency();
        if (stats.authenticated && stats.hostKeyTrusted && !"—".equals(stats.disk)) {
            DiskHistoryStore.add(this, stats.disk);
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
            if (stats.sftpOnly) statusDetail.setText("SFTP работает. Командная оболочка отключена; системные метрики недоступны.");
            else statusDetail.setText(com.bitpoint.homeservercontrol.ui.R.string.status_0870869f22e7);
            appendLog("Сервер онлайн. SSH-вход успешен.");
        } else if (stats.reachable) {
            statusText.setText(com.bitpoint.homeservercontrol.ui.R.string.status_18bf0e4021be);
            statusText.setTextColor(ThemeCatalog.color(this, com.bitpoint.homeservercontrol.ui.R.attr.hscWarning));
            statusDetail.setText("SSH-порт доступен, авторизация не выполнена.\n" + stats.error);
            appendLog(stats.error);
        } else {
            statusText.setText(com.bitpoint.homeservercontrol.ui.R.string.status_a267c32f1c34);
            statusText.setTextColor(ThemeCatalog.color(this, com.bitpoint.homeservercontrol.ui.R.attr.hscError));
            statusDetail.setText(stats.error);
            appendLog(stats.error);
        }
        StatusBadge.apply(statusText,stats.authenticated && stats.hostKeyTrusted?StatusBadge.Tone.SUCCESS:
            stats.hostKeyMismatch || !stats.reachable?StatusBadge.Tone.ERROR:StatusBadge.Tone.WARNING);
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
        if (!ConnectionEndpoint.validHost(host) || !ConnectionEndpoint.validUser(user) || port < 1) {
            Toast.makeText(this,"Проверь адрес, пользователя и порт",Toast.LENGTH_SHORT).show(); return;
        }
        String authority = user+"@"+(host.contains(":") ? "["+host+"]" : host)+":"+port;
        new AlertDialog.Builder(this).setTitle("Скопировать адрес")
                .setItems(new String[]{"sftp://"+authority,"ssh://"+authority},(d,which) -> {
                    ClipboardManager clipboard=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
                    clipboard.setPrimaryClip(ClipData.newPlainText("LocalLS",(which==0?"sftp://":"ssh://")+authority));
                    Toast.makeText(this,"Адрес без секретов скопирован",Toast.LENGTH_SHORT).show();
                }).show();
    }

    private boolean validSshInputs(String host, int port, String user, String password) {
        if (!ConnectionEndpoint.validHost(host) || port <= 0 || !ConnectionEndpoint.validUser(user)) {
            Toast.makeText(this, "Проверь адрес, порт и пользователя", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (TextUtils.isEmpty(HostTrustStore.get(this, host, port))) {
            Toast.makeText(this, "Сначала проверь сервер и подтверди fingerprint", Toast.LENGTH_LONG).show();
            return false;
        }
        return true;
    }

    private void openFiles(boolean pickAttachment) {
        String host = activeHost();
        int port = parsePort();
        String user = userInput.getText().toString().trim();
        String password = "";
        if (!validSshInputs(host, port, user, password)) return;

        // Сохраняем несекретные поля, чтобы файловый экран использовал тот же режим/адрес.
        if (!saveConfig()) return;
        Intent intent = new Intent(this, FilesActivity.class).putExtra("own_server",false);
        intent.putExtra(FileAttachments.PICK_ON_OPEN, pickAttachment);
        FileAttachments.put(intent, attachedFiles);
        try { startActivity(intent); }
        catch (RuntimeException e) {
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
        params.width = DrawerLayoutPolicy.width(width, getResources().getConfiguration().fontScale,
                getResources().getDisplayMetrics().density);
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
                        + "4. Дома текущий локальный IP сервера остаётся доступен с обычным Tailscale без exit node. Для доступа из другой сети выбери профиль VPN / Tailscale.\n\n"
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
        report.append(batteryValue.getText()).append("\n");
        report.append(fingerprintValue.getText()).append("\n\n");
        report.append("История памяти\n").append(DiskHistoryStore.render(this, 20)).append("\n\n");
        report.append("Журнал\n").append(logText.getText()).append("\n");
        report.append("\nПриватный SSH-ключ и токен агента не экспортируются.\n");
        pendingReport = SecretRedactor.redact(report.toString(),
                "", "");

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

    private void appendLog(String message) {
        String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        String existing = logText.getText().toString();
        String safe = SecretRedactor.redact(message,
                "", "");
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
        handler.removeCallbacks(localServerRefresh);
        if (savedProfileCheck != null) handler.removeCallbacks(savedProfileCheck);
        handler.removeCallbacks(vpnRefresh);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
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
