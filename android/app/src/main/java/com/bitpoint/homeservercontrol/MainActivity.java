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
    private Button checkButton, connectionCheckButton, trustKeyButton;

    private static final int REQ_EXPORT_REPORT = 601;
    private static final int REQ_NOTIFICATIONS = 602;
    private static final int REQ_SERVER_NOTIFICATIONS = 603;
    private boolean serverHomeSelected;
    private String pendingReport = "";
    private LiveSpeedPanel liveSpeedPanel;
    private final ArrayList<Uri> attachedFiles = new ArrayList<>();
    private final Runnable localServerRefresh=new Runnable() {
        public void run() {refreshLocalServer();refreshHomeTransfers();if(isUiActive())handler.postDelayed(this,1000);}
    };
    private final Runnable pairedMetricsRefresh=new Runnable(){
        public void run(){
            if(!isUiActive())return;
            if(clientEnabled()&&!serverHomeSelected&&!autoRefreshCheck.isChecked()){
                FileConnection route=FileConnection.current(MainActivity.this);
                if(!route.pin.isEmpty()&&SshKeys.hasStoredKey(MainActivity.this,route.host,route.port,route.user))checkServer(false);
            }
            handler.postDelayed(this,30000);
        }
    };
    private boolean readingHome;
    private void refreshHomeTransfers(){
        if(readingHome||serverHomeSelected||!isUiActive())return;readingHome=true;final int generation=uiGeneration;
        executor.execute(()->{String history="Передач пока нет";QueueEstimate estimate=null;boolean any=false;try{
            java.util.List<String> rows=new java.util.ArrayList<>();
            java.util.List<org.json.JSONObject> jobs=new TransferJournal(this).list();any=!jobs.isEmpty();estimate=QueueEstimate.read(this,jobs,TransferForegroundService.state,TransferSpeedStore.queueSpeed());
            for(org.json.JSONObject job:jobs){
                String status=job.optString("status");if(!java.util.Arrays.asList("done","failed","cancelled").contains(status))continue;
                org.json.JSONArray sources=job.getJSONArray("sources");for(int i=0;i<sources.length()&&rows.size()<3;i++){org.json.JSONObject f=sources.getJSONObject(i);rows.add(("done".equals(status)?"✓ ":"failed".equals(status)?"! ":"× ")+f.optString("name")+" · "+(f.optBoolean("directory")?"Папка":ServerTelemetry.bytes(f.optLong("size")))+" · "+("done".equals(status)?("upload".equals(job.optString("action"))?"Отправлено":"Получено"):"failed".equals(status)?"Ошибка":"Отменено"));}if(rows.size()==3)break;
            }if(!rows.isEmpty())history=android.text.TextUtils.join("\n",rows);
        }catch(Exception e){history="Журнал временно недоступен";}
        final String result=history;final QueueEstimate queueEstimate=estimate;final boolean hasEntries=any;handler.post(()->{readingHome=false;if(!isUiActive()||generation!=uiGeneration)return;
            TransferForegroundService.State state=TransferForegroundService.state;
            int percent=state.total>0?(int)Math.min(100,state.bytes*100d/state.total):0;String detail=state.active?(state.total>0?percent+"% · "+ServerTelemetry.bytes(state.bytes)+" из "+ServerTelemetry.bytes(state.total):state.text):state.text;
            ((DashboardLayout)findViewById(R.id.dashboardScroll)).transfers(state.active?TransferForegroundService.currentName:"Передача не выполняется",detail,percent,result);
            ((DashboardLayout)findViewById(R.id.dashboardScroll)).transferTiming(TransferSpeedStore.queueSpeed(),state.bytes,state.total);
            if(queueEstimate!=null)((DashboardLayout)findViewById(R.id.dashboardScroll)).queueSummary(queueEstimate,hasEntries);
        });});
    }
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
    private Boolean lastClientRole;

    private String lastSeenFingerprint = "";
    private String lastSeenHost = "";
    private int lastSeenPort = -1;

    private final Runnable autoRefreshRunnable = new Runnable() {
        @Override public void run() {
            if (isUiActive() && autoRefreshCheck != null && autoRefreshCheck.isChecked() && clientEnabled() && !serverHomeSelected) {
                checkServer(false);
                handler.postDelayed(this, 15_000);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ConfigMigrator.migrate(this);
        TransferForegroundService.restore(this);
        if(savedInstanceState!=null)pendingReport=savedInstanceState.getString("pending_report","");
        setContentView(R.layout.activity_main);
        boolean savedServerRole=getSharedPreferences(EmbeddedServerService.PREFS,0).getBoolean("server_role",false);
        serverHomeSelected=savedInstanceState!=null?savedInstanceState.getBoolean("server_home",savedServerRole):savedServerRole;
        bindViews();
        liveSpeedPanel = new LiveSpeedPanel(findViewById(R.id.dashboardSpeedChart), findViewById(R.id.dashboardSpeedSummary));
        loadConfig();
        wireActions();
        ((Switch)findViewById(R.id.showClientSwitch)).setOnCheckedChangeListener((v,checked)->{
            getSharedPreferences(EmbeddedServerService.PREFS,0).edit().putBoolean("show_client",checked).apply();renderRole();handler.removeCallbacks(autoRefreshRunnable);if(checked && autoRefreshCheck.isChecked())handler.post(autoRefreshRunnable);else if(!checked)cancelNetworkWork();
        });
        refreshLocalServer();
        drawerSections = new DrawerSections(this,
                new int[]{R.id.drawerWifiHeader,
                        R.id.drawerAppearanceHeader, R.id.drawerToolsHeader},
                new int[]{R.id.drawerWifiPane,
                        R.id.drawerAppearancePane, R.id.drawerToolsPane});
        wifiSetup = new WifiSetupController(this);
        findViewById(R.id.connectionActionsGrid).setBackground(SurfaceStyle.panel(this));findViewById(R.id.connectionActionsGrid).setPadding(0,0,0,0);
        findViewById(R.id.connectionCheckCard).setBackground(SurfaceStyle.panel(this));
        renderConnectionSummary();
        if (savedInstanceState == null || !savedInstanceState.containsKey(FileAttachments.EXTRA))
            for (Uri uri : FileAttachments.from(getIntent())) FileAttachments.add(attachedFiles, uri);
        if (savedInstanceState != null) {
            ArrayList<Uri> restored = savedInstanceState.getParcelableArrayList(FileAttachments.EXTRA);
            if (restored != null) for (Uri uri : restored) FileAttachments.add(attachedFiles, uri);
        }
        routeSharedIntent(getIntent(),savedInstanceState==null);
        renderAttachmentEntry();
        StatusBadge.apply(statusText,StatusBadge.Tone.WARNING);
        if(activeHost().isEmpty()) {
            statusText.setText("НЕ НАСТРОЕНО");statusDetail.setText("Укажи адрес другого устройства в разделе «Адрес и ключ».");
        }
        if(getIntent().getBooleanExtra("localls.openAppearance",false)){getIntent().removeExtra("localls.openAppearance");handler.post(()->{if(!serverHomeSelected&&clientEnabled()){openToolsDrawer();drawerSections.open(R.id.drawerAppearancePane);}});}
    }


    @Override
    protected void onResume() {
        super.onResume();
        if (liveSpeedPanel != null && !serverHomeSelected) liveSpeedPanel.start();
        if (wifiSetup != null) wifiSetup.start();
        handler.removeCallbacks(autoRefreshRunnable);
        if (autoRefreshCheck.isChecked()) handler.postDelayed(autoRefreshRunnable, 15000);
        handler.removeCallbacks(pairedMetricsRefresh);handler.postDelayed(pairedMetricsRefresh,500);
        renderDashboardLatency();
        handler.removeCallbacks(localServerRefresh);handler.post(localServerRefresh);
        if (firstResume) {
            firstResume = false;
            return;
        }
        if (loadedConfigSignature != configSignature()) {
            cancelNetworkWork(); invalidateConnectionStats(); loadConfig();
        }
        renderConnectionSummary();
    }

    private boolean clientEnabled(){return !DeviceRole.serverOnly(this);}
    private void renderRole(){
        var p=getSharedPreferences(EmbeddedServerService.PREFS,0);boolean server=p.getBoolean("server_role",false),show=p.getBoolean("show_client",false);
        Switch toggle=findViewById(R.id.showClientSwitch);toggle.setVisibility(View.GONE);
        if(toggle.isChecked()!=show)toggle.setChecked(show);
        findViewById(R.id.remoteConnectionCard).setVisibility(server&&!show?View.GONE:View.VISIBLE);
        boolean enabled=clientEnabled();findViewById(R.id.networkButton).setVisibility(enabled?View.VISIBLE:View.GONE);
        for(int id:new int[]{R.id.checkButton,R.id.filesButton,R.id.attachFilesButton})
            findViewById(id).setVisibility(enabled?View.VISIBLE:View.GONE);
        findViewById(R.id.homeClientTab).setVisibility(enabled?View.VISIBLE:View.GONE);
        if(!enabled && !serverHomeSelected){serverHomeSelected=true;closeDashboardSheet();closeToolsDrawer();cancelNetworkWork();}
        renderHomeSelection();
        if(lastClientRole==null || lastClientRole!=enabled){lastClientRole=enabled;
            if(getSharedPreferences(ServerConfig.PREFS,0).getBoolean(ServerConfig.KEY_BACKGROUND_MONITOR,false))MonitoringScheduler.setEnabled(this,enabled);
        }
    }
    private void selectHome(boolean server){
        if(!server && !clientEnabled())return;
        closeDashboardSheet();closeToolsDrawer();serverHomeSelected=server;
        if(server){cancelNetworkWork();handler.removeCallbacks(autoRefreshRunnable);}
        else if(autoRefreshCheck.isChecked()){handler.removeCallbacks(autoRefreshRunnable);handler.postDelayed(autoRefreshRunnable,15000);}
        renderHomeSelection();
    }
    private void renderHomeSelection(){
        if(liveSpeedPanel!=null){if(serverHomeSelected)liveSpeedPanel.stop();else if(isUiActive())liveSpeedPanel.start();}
        if(!serverHomeSelected)((ServerHomeLayout)findViewById(R.id.serverHome)).suspend();
        findViewById(R.id.serverHome).setVisibility(serverHomeSelected?View.VISIBLE:View.GONE);
        findViewById(R.id.dashboardScroll).setVisibility(serverHomeSelected?View.GONE:View.VISIBLE);
        for(int id:new int[]{R.id.homeServerTab,R.id.homeClientTab}){
            View tab=findViewById(id);boolean selected=(id==R.id.homeServerTab)==serverHomeSelected;
            tab.setSelected(selected);tab.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    id==R.id.homeServerTab&&!clientEnabled()?ThemeCatalog.color(this,com.bitpoint.homeservercontrol.ui.R.attr.hscPanel):ThemeCatalog.color(this,selected?com.bitpoint.homeservercontrol.ui.R.attr.hscButton:com.bitpoint.homeservercontrol.ui.R.attr.hscSecondaryButton)));
            if(id==R.id.homeServerTab){Button title=(Button)tab;title.setText(clientEnabled()||getResources().getConfiguration().fontScale>1.3f?"Сервер":"Сервер\nЭтот телефон");title.setTextColor(ThemeCatalog.color(this,com.bitpoint.homeservercontrol.ui.R.attr.hscTextPrimary));}
            IconButtons.dashboardTab((Button)tab);
        }
    }
    private void refreshLocalServer() {
        renderRole();
        ((ServerHomeLayout)findViewById(R.id.serverHome)).refresh();
        ((WifiAddressView)findViewById(R.id.currentWifiAddress)).refresh();
        TextView summary=findViewById(R.id.localServerSummary);
        summary.setText(EmbeddedServerService.running?"● Сервер работает":
            EmbeddedServerService.launchPending()?"Запуск сервера…":EmbeddedServerService.status);
        boolean savedKey=!getSharedPreferences(EmbeddedServerService.PREFS,0).getString("allowed_key","").isEmpty();
        ((TextView)findViewById(R.id.serverAccessSummary)).setText("Порт "+(EmbeddedServerService.running?EmbeddedServerService.boundPort:getSharedPreferences(EmbeddedServerService.PREFS,0).getInt("port",8022))+" · "+getSharedPreferences(EmbeddedServerService.PREFS,0).getString("user","localls")+(EmbeddedServerService.running&&EmbeddedServerService.waitingForClient?" · Вход закрыт":" · Доступ по ключу"));
        StatusBadge.apply(summary,EmbeddedServerService.running?(EmbeddedServerService.waitingForClient?StatusBadge.Tone.WARNING:StatusBadge.Tone.SUCCESS):
            EmbeddedServerService.launchPending()?StatusBadge.Tone.WARNING:
            EmbeddedServerService.diagnostic.isEmpty()?StatusBadge.Tone.NEUTRAL:StatusBadge.Tone.ERROR);
        TextView receive=findViewById(R.id.serverReceiveSummary);
        receive.setText(ReceiveFolder.label(this));receive.setContentDescription("Папка входящих: "+ReceiveFolder.label(this)+". "+AutoReceive.status);
        ((Button)findViewById(R.id.serverBackgroundButton)).setText(EmbeddedServerService.running?"Работает в фоне":"Работа в фоне");
        Button power=findViewById(R.id.serverPowerButton);
        boolean pending=EmbeddedServerService.launchPending();
        power.setText(EmbeddedServerService.running?"Остановить":pending?"Запуск…":"Включить");
        power.setEnabled(Build.VERSION.SDK_INT>=26 && !pending);
    }
    private void toggleOwnServer(){
        if(EmbeddedServerService.running){stopService(new Intent(this,EmbeddedServerService.class));return;}
        if(Build.VERSION.SDK_INT<26 || EmbeddedServerService.launchPending())return;
        try{
            EmbeddedServerService.start(this);
            if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},REQ_SERVER_NOTIFICATIONS);
        }catch(RuntimeException error){Toast.makeText(this,EmbeddedServerService.status,Toast.LENGTH_LONG).show();}
        refreshLocalServer();
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
        TextView connectionResult=findViewById(R.id.connectionCheckStatus);
        connectionResult.setText(R.string.ssh_check_changed);
        connectionResult.setTextColor(ThemeCatalog.color(this,com.bitpoint.homeservercontrol.ui.R.attr.hscTextSecondary));
        StatusBadge.apply(statusText,StatusBadge.Tone.WARNING);
        statusText.setText(com.bitpoint.homeservercontrol.ui.R.string.status_ab27bd0a2936);
        statusText.setTextColor(ThemeCatalog.color(this, com.bitpoint.homeservercontrol.ui.R.attr.hscWarning));
        statusDetail.setText(com.bitpoint.homeservercontrol.ui.R.string.status_a7e8f235c5f7);
        lastSeenFingerprint = ""; lastSeenHost = ""; lastSeenPort = -1;
        trustKeyButton.setVisibility(View.GONE);
        fingerprintValue.setVisibility(View.GONE);
        ((DashboardLayout)findViewById(R.id.dashboardScroll)).resetMetrics();
        batteryValue.setText("—");batteryValue.setContentDescription("Заряд батареи сервера: —");
        diskValue.setContentDescription("Свободное место сервера: —");
        latencyValue.setText(com.bitpoint.homeservercontrol.ui.R.string.status_36c28e09c94a); diskValue.setText("—"); uptimeValue.setText(com.bitpoint.homeservercontrol.ui.R.string.status_cbe1cb69c8a9);
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
        connectionCheckButton = findViewById(R.id.connectionCheckButton);
        trustKeyButton = findViewById(R.id.trustKeyButton);
    }

    private void wireActions() {
        findViewById(R.id.homeServerTab).setOnClickListener(v->selectHome(true));
        findViewById(R.id.homeClientTab).setOnClickListener(v->selectHome(false));
        findViewById(R.id.serverPowerButton).setOnClickListener(v->toggleOwnServer());
        findViewById(R.id.serverLocalFilesButton).setOnClickListener(v->startActivity(new Intent(this,FilesActivity.class).putExtra("own_server",true)));
        findViewById(R.id.serverReceiveButton).setOnClickListener(v->startActivity(new Intent(this,EmbeddedServerActivity.class).putExtra("open_receive_folder",true)));
        findViewById(R.id.serverBackgroundButton).setOnClickListener(v->{try{
            startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));
        }catch(RuntimeException error){Toast.makeText(this,"Открой настройки Android → LocalLS → Батарея",Toast.LENGTH_LONG).show();}});
        findViewById(R.id.drawerScrim).setOnClickListener(v -> closeToolsDrawer());
        findViewById(R.id.drawerConnectionHeader).setOnClickListener(v -> {
            showDashboardSheet(R.id.connectionSheet);
        });
        findViewById(R.id.connectionCloseButton).setOnClickListener(v->closeDashboardSheet());
        findViewById(R.id.detailsCloseButton).setOnClickListener(v->closeDashboardSheet());
        findViewById(R.id.connectionDetailsButton).setOnClickListener(v->showDashboardSheet(R.id.connectionDetailsSheet));
        findViewById(R.id.scanQrButton).setOnClickListener(v->startActivity(new Intent(this,QrPairingActivity.class)));
        findViewById(R.id.transferQueueButton).setOnClickListener(v->startActivity(new Intent(this,TransferQueueActivity.class)));
        findViewById(R.id.serverQrButton).setOnClickListener(v->startActivity(new Intent(this,QrPairingActivity.class).putExtra("server",true)));
        findViewById(R.id.toolsMenuButton).setOnClickListener(v->openToolsDrawer());
        findViewById(R.id.themeChooserButton).setOnClickListener(v -> chooseTheme());
        AppearanceSettings.bind(this,findViewById(R.id.appearanceGlassSwitch),findViewById(R.id.appearanceOutlineButton),this::refreshAppearance);
        findViewById(R.id.localServerQuickButton).setOnClickListener(v -> startActivity(new Intent(this,EmbeddedServerActivity.class)));
        findViewById(R.id.appSettingsButton).setOnClickListener(v -> startActivity(new Intent(this,SettingsActivity.class)));
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
        connectionCheckButton.setOnClickListener(v -> checkServer(true));
        findViewById(R.id.copyButton).setOnClickListener(v -> copyConnection());
        findViewById(R.id.filesButton).setOnClickListener(v -> openFiles(false));
        findViewById(R.id.attachFilesButton).setOnClickListener(v -> openFiles(attachedFiles.isEmpty()));
        findViewById(R.id.networkButton).setOnClickListener(v -> startActivity(new Intent(this,NetworkActivity.class)));
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

    private void setCheckButtonsEnabled(boolean enabled) {
        if(checkButton!=null)checkButton.setEnabled(enabled);
        if(connectionCheckButton!=null)connectionCheckButton.setEnabled(enabled);
    }

    private void checkServer(boolean userInitiated) {
        if (checking) return;
        final String primaryHost = activeHost();
        final String user = userInput.getText().toString().trim();
        final String password = "";
        final int port = parsePort();

        if (!ConnectionEndpoint.validHost(primaryHost) || !ConnectionEndpoint.validUser(user) || port <= 0) {
            if (userInitiated) {
                TextView result=findViewById(R.id.connectionCheckStatus);result.setText(R.string.ssh_check_invalid);result.setTextColor(ThemeCatalog.color(this,com.bitpoint.homeservercontrol.ui.R.attr.hscWarning));
                Toast.makeText(this,R.string.ssh_check_invalid,Toast.LENGTH_SHORT).show();
            }
            return;
        }

        checking = true;
        StatusBadge.apply(statusText,StatusBadge.Tone.WARNING);
        setCheckButtonsEnabled(false);
        TextView checkingStatus=findViewById(R.id.connectionCheckStatus);checkingStatus.setText(R.string.ssh_check_busy);checkingStatus.setTextColor(ThemeCatalog.color(this,com.bitpoint.homeservercontrol.ui.R.attr.hscWarning));
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
                setCheckButtonsEnabled(true);
                if (!primaryHost.equals(finalHost)) {
                    appendLog("Основной маршрут недоступен. Использован резервный: " + finalHost + ".");
                }
                if (finalStats.reachable) ConnectionSelector.rememberWorkingHost(this, finalHost);
                renderStats(finalStats, finalHost, port, user);
            });
        });
    }

    private void renderStats(ServerStats stats, String host, int port, String user) {
        ((DashboardLayout)findViewById(R.id.dashboardScroll)).metrics(stats);
        ipValue.setText("—".equals(stats.ip) ? host : stats.ip);
        portValue.setText(getString(com.bitpoint.homeservercontrol.ui.R.string.status_ff7a04dd6c26, port));
        userValue.setText(getString(com.bitpoint.homeservercontrol.ui.R.string.status_617bb5a0a088, user));
        latencyValue.setText(stats.reachable ? getString(com.bitpoint.homeservercontrol.ui.R.string.status_latency_format, stats.latencyMs) : getString(com.bitpoint.homeservercontrol.ui.R.string.status_36c28e09c94a));
        diskValue.setText(SurfaceStyle.quantity(stats.freeSpace));
        diskValue.setContentDescription("Свободное место сервера: "+stats.freeSpace);
        uptimeValue.setText(getString(com.bitpoint.homeservercontrol.ui.R.string.status_3597244eb434, stats.uptime));
        batteryValue.setText(stats.battery);
        batteryValue.setContentDescription("Заряд батареи сервера: "+stats.battery);
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
        if(stats.hostKeyNeedsTrust)showDashboardSheet(R.id.connectionDetailsSheet);

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
            if(stats.telemetryAvailable)statusDetail.setText("Доступ по ключу. Показатели сервера обновлены.");
            else if (stats.sftpOnly) statusDetail.setText("SFTP работает. Командная оболочка отключена; системные метрики недоступны.");
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
        TextView connectionResult=findViewById(R.id.connectionCheckStatus);
        connectionResult.setText(statusText.getText()+"\n"+statusDetail.getText());
        connectionResult.setTextColor(statusText.getCurrentTextColor());
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
                .setMessage("Отпечаток ключа:\n" + lastSeenFingerprint + "\n\nПодтверждай только если это твой сервер.")
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
        // Network mode is configured in the existing Network screen. Show the active endpoint here.
        modeGroup.setVisibility(View.GONE);boolean vpn=vpnModeButton.isChecked();localHostInput.setVisibility(vpn?View.GONE:View.VISIBLE);vpnHostInput.setVisibility(vpn?View.VISIBLE:View.GONE);
        android.view.ViewGroup connection=findViewById(R.id.drawerConnectionPane);for(int i=0;i<connection.getChildCount();i++){View label=connection.getChildAt(i);if(label.getLabelFor()==R.id.localHostInput)label.setVisibility(vpn?View.GONE:View.VISIBLE);if(label.getLabelFor()==R.id.vpnHostInput){label.setVisibility(vpn?View.VISIBLE:View.GONE);((TextView)label).setText("Адрес сервера");}}
        String host = activeHost();
        int port = parsePort();
        String user = userInput.getText().toString().trim();
        ipValue.setText(TextUtils.isEmpty(host) ? "—" : host);
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

    private void routeSharedIntent(Intent intent,boolean fresh){
        if(!fresh||intent==null||(!Intent.ACTION_SEND.equals(intent.getAction())&&!Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction())))return;
        ArrayList<Uri> shared=FileAttachments.from(intent);if(shared.isEmpty())return;
        Intent target=new Intent(this,ShareUploadActivity.class);FileAttachments.put(target,shared);startActivity(target);attachedFiles.clear();
        // Consume this delivery only after the grant-bearing private Activity was launched.
        intent.setAction(Intent.ACTION_MAIN);intent.removeExtra(Intent.EXTRA_STREAM);intent.setClipData(null);intent.setData(null);
    }
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        for (Uri uri : FileAttachments.from(intent)) FileAttachments.add(attachedFiles, uri);
        routeSharedIntent(intent,true);
        renderAttachmentEntry();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("server_home",serverHomeSelected);
        state.putString("pending_report",pendingReport);
        state.putParcelableArrayList(FileAttachments.EXTRA, new ArrayList<>(attachedFiles));
        super.onSaveInstanceState(state);
    }

    private void showDashboardSheet(int id){
        if(serverHomeSelected || !clientEnabled())return;
        if(findViewById(R.id.advancedPanel).getVisibility()==View.VISIBLE)closeToolsDrawer();
        findViewById(R.id.connectionSheet).setVisibility(id==R.id.connectionSheet?View.VISIBLE:View.GONE);
        findViewById(R.id.connectionDetailsSheet).setVisibility(id==R.id.connectionDetailsSheet?View.VISIBLE:View.GONE);
        findViewById(R.id.homeShell).setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        if(Build.VERSION.SDK_INT>=33&&drawerBackCallback==null){drawerBackCallback=(android.window.OnBackInvokedCallback)this::closeDashboardSheet;getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,(android.window.OnBackInvokedCallback)drawerBackCallback);}
    }
    private void closeDashboardSheet(){
        findViewById(R.id.connectionSheet).setVisibility(View.GONE);findViewById(R.id.connectionDetailsSheet).setVisibility(View.GONE);
        findViewById(R.id.homeShell).setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        if(Build.VERSION.SDK_INT>=33&&drawerBackCallback!=null){getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback((android.window.OnBackInvokedCallback)drawerBackCallback);drawerBackCallback=null;}
        android.view.inputmethod.InputMethodManager keyboard=(android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);if(keyboard!=null)keyboard.hideSoftInputFromWindow(findViewById(R.id.dashboardRoot).getWindowToken(),0);
    }
    private void openToolsDrawer() {
        if(serverHomeSelected || !clientEnabled())return;
        closeDashboardSheet();
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
        findViewById(R.id.homeShell).setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        panel.animate().translationX(0).setDuration(220).start();
        if (Build.VERSION.SDK_INT >= 33) {
            drawerBackCallback = (android.window.OnBackInvokedCallback) this::closeToolsDrawer;
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    (android.window.OnBackInvokedCallback) drawerBackCallback);
        }
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
        findViewById(R.id.homeShell).setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        android.view.inputmethod.InputMethodManager keyboard = (android.view.inputmethod.InputMethodManager)
                getSystemService(INPUT_METHOD_SERVICE);
        if (keyboard != null) keyboard.hideSoftInputFromWindow(panel.getWindowToken(), 0);
    }

    @Override
    @android.annotation.SuppressLint("GestureBackNavigation") // API 24–32; API 33+ uses the registered callback above.
    public void onBackPressed() {
        if(findViewById(R.id.connectionSheet).getVisibility()==View.VISIBLE||findViewById(R.id.connectionDetailsSheet).getVisibility()==View.VISIBLE)closeDashboardSheet();
        else if (findViewById(R.id.advancedPanel).getVisibility() == View.VISIBLE) closeToolsDrawer();
        else super.onBackPressed();
    }

    private void chooseTheme(){AppearanceSettings.chooseTheme(this,this::refreshAppearance);}
    private void refreshAppearance(){getIntent().putExtra("localls.openAppearance",true);recreate();}

    private void exportDiagnostics() {
        StringBuilder report = new StringBuilder();
        String version;
        try{version=getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(Exception e){version="—";}
        report.append("LocalLS ").append(version).append(" / ").append(getString(R.string.ui_build_revision)).append('\n');
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
        if(report.isEmpty()){Toast.makeText(this,"Отчёт не подготовлен. Повтори экспорт диагностики.",Toast.LENGTH_LONG).show();return;}
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
        summary.setText(recent.isEmpty() ? "—"
                : String.format(Locale.getDefault(), "%.0f мс", recent.get(recent.size() - 1)));
        while (recent.size() < 20) recent.add(0, Float.NaN);
        chart.setLabels("мс", "20 проверок");
        chart.setValues(recent, 0, Math.max(10f, peak * 1.2f));
    }

    private void cancelNetworkWork() {
        ++uiGeneration;
        if (checkTask != null) checkTask.cancel(true);
        checking = false;
        setCheckButtonsEnabled(true);
        TextView result=findViewById(R.id.connectionCheckStatus);
        if(result!=null && getString(R.string.ssh_check_busy).contentEquals(result.getText())){result.setText(R.string.ssh_check_ready);result.setTextColor(ThemeCatalog.color(this,com.bitpoint.homeservercontrol.ui.R.attr.hscTextSecondary));}
        if (statusText != null && "● ПРОВЕРКА…".contentEquals(statusText.getText())) {
            statusText.setText(com.bitpoint.homeservercontrol.ui.R.string.status_f7ed201ba529);
            statusDetail.setText(com.bitpoint.homeservercontrol.ui.R.string.status_7fad5c3e34a2);
        }
    }

    @Override
    protected void onPause() {
        ((ServerHomeLayout)findViewById(R.id.serverHome)).suspend();
        if (liveSpeedPanel != null) liveSpeedPanel.stop();
        if (wifiSetup != null) wifiSetup.stop();
        cancelNetworkWork();
        handler.removeCallbacks(autoRefreshRunnable);
        handler.removeCallbacks(localServerRefresh);
        handler.removeCallbacks(pairedMetricsRefresh);
        if (savedProfileCheck != null) handler.removeCallbacks(savedProfileCheck);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        ((ServerHomeLayout)findViewById(R.id.serverHome)).close();
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
