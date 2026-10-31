package com.bitpoint.homeservercontrol;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.*;
import android.text.TextUtils;
import android.widget.*;
import java.util.concurrent.*;

/** Finds a server and edits its address without changing a saved route or trusted identity. */
public class NetworkActivity extends BaseActivity {
    private EditText localHostInput;
    private TextView resultText;
    private String loadedMode,loadedHost,loadedUser;
    private int loadedPort;
    private final ExecutorService executor=Executors.newFixedThreadPool(2);
    private final Handler handler=new Handler(Looper.getMainLooper());
    private volatile int requestGeneration;
    private Future<?> requestTask;
    private AlertDialog discoveryDialog;

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);ConfigMigrator.migrate(this);setContentView(R.layout.activity_network);
        localHostInput=findViewById(R.id.networkLocalHostInput);resultText=findViewById(R.id.networkResultText);
        SettingsUi.surface(findViewById(R.id.networkAddressCard));
        SharedPreferences prefs=getSharedPreferences(ServerConfig.PREFS,MODE_PRIVATE);
        boolean restored=state!=null&&state.getBoolean("address_editor",false);
        loadedMode=restored?state.getString("editor_mode",ServerConfig.MODE_LOCAL):prefs.getString(ServerConfig.KEY_MODE,ServerConfig.MODE_LOCAL);
        loadedHost=restored?state.getString("editor_host",""):ConnectionSelector.selectedHost(this);
        loadedUser=restored?state.getString("editor_user",ServerConfig.DEFAULT_USER):prefs.getString(ServerConfig.KEY_USER,ServerConfig.DEFAULT_USER);
        loadedPort=restored?state.getInt("editor_port",ServerConfig.DEFAULT_PORT):prefs.getInt(ServerConfig.KEY_PORT,ServerConfig.DEFAULT_PORT);
        // A draft and its original endpoint must survive rotation together.
        // Do not restore a legacy local-only EditText state into the unified address field.
        localHostInput.setSaveEnabled(false);
        localHostInput.setText(restored?state.getString("editor_draft",loadedHost):loadedHost);
        localHostInput.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){cancelRequest();}
            public void afterTextChanged(android.text.Editable s){}
        });
        findViewById(R.id.networkBackButton).setOnClickListener(v->finish());
        findViewById(R.id.networkSaveButton).setOnClickListener(v->save());
        findViewById(R.id.networkDiscoverButton).setOnClickListener(v->discoverLocalServer());
    }
    @Override protected void onSaveInstanceState(Bundle state){
        state.putBoolean("address_editor",true);state.putString("editor_mode",loadedMode);
        state.putString("editor_host",loadedHost);state.putString("editor_user",loadedUser);state.putInt("editor_port",loadedPort);
        state.putString("editor_draft",localHostInput.getText().toString());super.onSaveInstanceState(state);
    }
    private void save(){
        String host=localHostInput.getText().toString().trim();localHostInput.setError(null);
        if(!ConnectionEndpoint.validHost(host)){localHostInput.setError("Укажи IP или имя сервера без порта и пути");return;}
        SharedPreferences prefs=getSharedPreferences(ServerConfig.PREFS,MODE_PRIVATE);
        if(!loadedMode.equals(prefs.getString(ServerConfig.KEY_MODE,ServerConfig.MODE_LOCAL))||!loadedHost.equals(ConnectionSelector.selectedHost(this))||loadedPort!=prefs.getInt(ServerConfig.KEY_PORT,ServerConfig.DEFAULT_PORT)||!loadedUser.equals(prefs.getString(ServerConfig.KEY_USER,ServerConfig.DEFAULT_USER))){
            Toast.makeText(this,"Подключение изменилось. Открой поиск заново.",Toast.LENGTH_LONG).show();return;
        }
        if(loadedPort<1||loadedPort>65535||!ConnectionEndpoint.validUser(loadedUser)){Toast.makeText(this,"Проверь порт и пользователя в подключении",Toast.LENGTH_LONG).show();return;}
        try{SshKeys.moveRoute(this,loadedHost,host,loadedPort,loadedUser);}
        catch(Exception error){Toast.makeText(this,"Не удалось сохранить доступ. Прежний адрес сохранён.",Toast.LENGTH_LONG).show();return;}
        // Old alternate profiles remain intact; there is no VPN control or automatic migration.
        prefs.edit().putString(ServerConfig.MODE_VPN.equals(loadedMode)?ServerConfig.KEY_VPN_HOST:ServerConfig.KEY_LOCAL_HOST,host).apply();
        loadedHost=host;Toast.makeText(this,"Адрес сохранён",Toast.LENGTH_SHORT).show();
    }

    private void discoverLocalServer() {
        int port = getSharedPreferences(ServerConfig.PREFS, MODE_PRIVATE)
                .getInt(ServerConfig.KEY_PORT, ServerConfig.DEFAULT_PORT);
        cancelRequest();
        final int generation = requestGeneration;
        resultText.setText(getString(com.bitpoint.homeservercontrol.ui.R.string.status_d2229423fc38, port));
        findViewById(R.id.networkDiscoverButton).setEnabled(false);
        requestTask = executor.submit(() -> {
            LocalServerDiscovery.Result result = LocalServerDiscovery.discover(port);
            handler.post(() -> {
                if (!isUiActive() || generation != requestGeneration) return;
                findViewById(R.id.networkDiscoverButton).setEnabled(true);
                String own = TextUtils.isEmpty(result.localIp) ? "—" : result.localIp;
                if (result.hosts.isEmpty()) {
                    resultText.setText(getString(com.bitpoint.homeservercontrol.ui.R.string.status_e5faf35a2fce, own, result.message));
                    return;
                }
                String[] candidates = result.hosts.toArray(new String[0]);
                AlertDialog.Builder dialog = new AlertDialog.Builder(this)
                        .setTitle("Найден SSH-сервер")
                        .setNegativeButton("Отмена", null);
                if (candidates.length == 1) {
                    dialog.setMessage("Найден адрес " + candidates[0]
                                    + ". Подставить его в поле адреса?")
                            .setPositiveButton("Использовать", (d, which) ->
                                    applyDiscoveredHost(candidates[0], port));
                } else {
                    // AlertDialog displays either a message or its item list, not both.
                    dialog.setTitle("Выбери SSH-сервер")
                            .setItems(candidates, (d, which) ->
                                    applyDiscoveredHost(candidates[which], port));
                }
                discoveryDialog = dialog.show();
                resultText.setText(getString(com.bitpoint.homeservercontrol.ui.R.string.status_e1d5c4bee882, own, result.message, TextUtils.join(", ", result.hosts)));
            });
        });
    }

    private void applyDiscoveredHost(String host, int port) {
        localHostInput.setText(host);
        resultText.setText(getString(com.bitpoint.homeservercontrol.ui.R.string.status_12b30fbc136c, host, port));
    }

    private void cancelRequest() {
        ++requestGeneration;
        if (requestTask != null) requestTask.cancel(true);
        findViewById(R.id.networkDiscoverButton).setEnabled(true);
        if (discoveryDialog != null) discoveryDialog.dismiss();
        if (resultText.getText().toString().startsWith("Ищу устройства"))
            resultText.setText(com.bitpoint.homeservercontrol.ui.R.string.status_74ceac806dfb);
    }
    @Override protected void onPause() { cancelRequest(); super.onPause(); }
    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
