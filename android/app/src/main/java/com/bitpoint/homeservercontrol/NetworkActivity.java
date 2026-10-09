package com.bitpoint.homeservercontrol;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NetworkActivity extends BaseActivity {
    private EditText localHostInput, vpnHostInput, iperfTargetInput;
    private RadioGroup modeGroup;
    private CheckBox autoFallbackCheck;
    private RadioButton localModeButton, vpnModeButton;
    private TextView resultText;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final Handler handler = new Handler(Looper.getMainLooper());

    private volatile int requestGeneration;
    private java.util.concurrent.Future<?> requestTask;
    private AlertDialog discoveryDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ConfigMigrator.migrate(this);
        setContentView(R.layout.activity_network);
        localHostInput = findViewById(R.id.networkLocalHostInput);
        vpnHostInput = findViewById(R.id.networkVpnHostInput);
        iperfTargetInput = findViewById(R.id.networkIperfTargetInput);
        modeGroup = findViewById(R.id.networkModeGroup);
        localModeButton = findViewById(R.id.networkLocalModeButton);
        vpnModeButton = findViewById(R.id.networkVpnModeButton);
        resultText = findViewById(R.id.networkResultText);
        autoFallbackCheck = findViewById(R.id.networkAutoFallbackCheck);

        load();
        findViewById(R.id.networkVpnHeader).setOnClickListener(v -> {
            android.view.View pane = findViewById(R.id.networkVpnPane);
            boolean expanded = pane.getVisibility() != android.view.View.VISIBLE;
            pane.setVisibility(expanded ? android.view.View.VISIBLE : android.view.View.GONE);
            v.setSelected(expanded); IconButtons.decorate(v);
        });
        if (getIntent().getBooleanExtra("expand_vpn", false)) findViewById(R.id.networkVpnHeader).performClick();
        modeGroup.setOnCheckedChangeListener((group, id) -> cancelRequest());
        android.text.TextWatcher watcher = new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) { cancelRequest(); }
            public void afterTextChanged(android.text.Editable s) { }
        };
        localHostInput.addTextChangedListener(watcher);
        vpnHostInput.addTextChangedListener(watcher);
        findViewById(R.id.networkBackButton).setOnClickListener(v -> finish());
        findViewById(R.id.networkSaveButton).setOnClickListener(v -> save());
        findViewById(R.id.networkTestButton).setOnClickListener(v -> testConnection());
        findViewById(R.id.networkDiscoverButton).setOnClickListener(v -> discoverLocalServer());
        findViewById(R.id.tailscaleWizardButton).setOnClickListener(v -> showTailscaleWizard());
    }

    private void load() {
        SharedPreferences prefs = getSharedPreferences(ServerConfig.PREFS, MODE_PRIVATE);
        localHostInput.setText(prefs.getString(ServerConfig.KEY_LOCAL_HOST, ServerConfig.DEFAULT_LOCAL_HOST));
        vpnHostInput.setText(prefs.getString(ServerConfig.KEY_VPN_HOST, ServerConfig.DEFAULT_VPN_HOST));
        iperfTargetInput.setText(prefs.getString(ServerConfig.KEY_IPERF_TARGET, ""));
        String mode = prefs.getString(ServerConfig.KEY_MODE, ServerConfig.MODE_LOCAL);
        autoFallbackCheck.setChecked(prefs.getBoolean(ServerConfig.KEY_AUTO_FALLBACK, false));
        if (ServerConfig.MODE_VPN.equals(mode)) vpnModeButton.setChecked(true);
        else localModeButton.setChecked(true);
    }

    private void save() {
        String local = localHostInput.getText().toString().trim();
        String vpn = vpnHostInput.getText().toString().trim();
        if (TextUtils.isEmpty(local)) {
            Toast.makeText(this, "Локальный адрес не должен быть пустым", Toast.LENGTH_SHORT).show();
            return;
        }
        String mode = vpnModeButton.isChecked() ? ServerConfig.MODE_VPN : ServerConfig.MODE_LOCAL;
        if (ServerConfig.MODE_VPN.equals(mode) && TextUtils.isEmpty(vpn)) {
            Toast.makeText(this, "Для VPN-режима укажи Tailscale IP или имя", Toast.LENGTH_SHORT).show();
            return;
        }
        getSharedPreferences(ServerConfig.PREFS, MODE_PRIVATE).edit()
                .putString(ServerConfig.KEY_LOCAL_HOST, local)
                .putString(ServerConfig.KEY_VPN_HOST, vpn)
                .putString(ServerConfig.KEY_MODE, mode)
                .putString(ServerConfig.KEY_IPERF_TARGET, iperfTargetInput.getText().toString().trim())
                .putBoolean(ServerConfig.KEY_AUTO_FALLBACK, autoFallbackCheck.isChecked())
                .apply();
        Toast.makeText(this, "Сетевые настройки сохранены", Toast.LENGTH_SHORT).show();
    }

    private void testConnection() {
        String primary = activeHost();
        String alternate = alternateHost();
        int port = getSharedPreferences(ServerConfig.PREFS, MODE_PRIVATE)
                .getInt(ServerConfig.KEY_PORT, ServerConfig.DEFAULT_PORT);
        if (TextUtils.isEmpty(primary)) {
            Toast.makeText(this, "Укажи адрес", Toast.LENGTH_SHORT).show();
            return;
        }
        cancelRequest();
        final int generation = requestGeneration;
        resultText.setText(getString(com.bitpoint.homeservercontrol.ui.R.string.status_29607d83ada3, primary, port));
        final boolean autoFallback = autoFallbackCheck.isChecked();
        requestTask = executor.submit(() -> {
            ProbeResult first = probe(primary, port);
            StringBuilder result = new StringBuilder();
            result.append("Основной: ").append(primary).append(" — ").append(first.message);


            if (autoFallback && !TextUtils.isEmpty(alternate) && !alternate.equals(primary)) {
                ProbeResult second = probe(alternate, port);
                result.append("\nРезервный: ").append(alternate).append(" — ").append(second.message);
                if (!first.ok && second.ok) {

                    result.append("\nАвтопереход сможет использовать резервный маршрут.");
                }
            }
            final String text = result.toString();
            handler.post(() -> {
                if (!isUiActive() || generation != requestGeneration) return;
                resultText.setText(text);
            });
        });
    }

    private ProbeResult probe(String host, int port) {
        long started = System.nanoTime();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 3500);
            long ms = Math.max(1L, (System.nanoTime() - started) / 1_000_000L);
            return new ProbeResult(true, "SSH-порт доступен • " + ms + " мс");
        } catch (Exception e) {
            String name = e.getClass().getSimpleName();
            if ("ConnectException".equals(name)) {
                return new ProbeResult(false, "соединение отклонено: проверь sshd и порт 8022");
            } else if ("SocketTimeoutException".equals(name)) {
                return new ProbeResult(false, "таймаут: проверь Wi‑Fi/VPN и адрес");
            } else if ("UnknownHostException".equals(name)) {
                return new ProbeResult(false, "адрес не найден");
            }
            return new ProbeResult(false, "нет соединения: " + safe(e));
        }
    }

    private String alternateHost() {
        return vpnModeButton.isChecked()
                ? localHostInput.getText().toString().trim()
                : vpnHostInput.getText().toString().trim();
    }

    private static final class ProbeResult {
        final boolean ok;
        final String message;
        ProbeResult(boolean ok, String message) { this.ok = ok; this.message = message; }
    }

    private String activeHost() {
        return vpnModeButton.isChecked()
                ? vpnHostInput.getText().toString().trim()
                : localHostInput.getText().toString().trim();
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
                                    + ". Использовать его как локальный сервер?")
                            .setPositiveButton("Использовать", (d, which) ->
                                    applyDiscoveredHost(candidates[0], port));
                } else {
                    dialog.setMessage("Найдено несколько устройств. Выбери сервер по его адресу.")
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
        localModeButton.setChecked(true);
        resultText.setText(getString(com.bitpoint.homeservercontrol.ui.R.string.status_12b30fbc136c, host, port));
    }

    private void showTailscaleWizard() {
        new AlertDialog.Builder(this)
                .setTitle("Мастер Tailscale")
                .setMessage("1. На сервере и другом устройстве установи Tailscale.\n\n"
                        + "2. Войди в один Tailscale-аккаунт и включи VPN на обоих устройствах.\n\n"
                        + "3. На сервере найди адрес вида 100.x.x.x или MagicDNS-имя.\n\n"
                        + "4. Введи его в поле VPN / Tailscale выше.\n\n"
                        + "5. Сначала нажми «Проверить SSH-порт». Локальный адрес 192.168.x.x при этом не удаляй — он остаётся запасным домашним маршрутом.\n\n"
                        + "Важно: не пробрасывай SSH 8022 и агент 8787 напрямую в интернет.")
                .setPositiveButton("Понятно", null)
                .show();
    }

    private static String safe(Exception e) {
        String m = e.getMessage();
        return TextUtils.isEmpty(m) ? e.getClass().getSimpleName() : m;
    }

    private void cancelRequest() {
        ++requestGeneration;
        if (requestTask != null) requestTask.cancel(true);
        findViewById(R.id.networkDiscoverButton).setEnabled(true);
        if (discoveryDialog != null) discoveryDialog.dismiss();
        if (resultText.getText().toString().startsWith("Проверяю ") || resultText.getText().toString().startsWith("Ищу устройства"))
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
