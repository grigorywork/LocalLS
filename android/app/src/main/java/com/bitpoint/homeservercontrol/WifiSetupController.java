package com.bitpoint.homeservercontrol;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSuggestion;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import java.net.Inet4Address;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;

/** Changes Wi-Fi only on this device, using Android's user-approved network APIs. */
final class WifiSetupController {
    static final int PERMISSION = 7310, ADD_NETWORK = 7311;
    private final MainActivity activity;
    private final EditText ssid, password;
    private final CheckBox open;
    private final TextView status;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String notice = "Настройка Wi-Fi этого телефона. Подключение подтверждает Android.";
    private boolean running;
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            if (!running) return;
            updateStatus();
            handler.postDelayed(this, 3000);
        }
    };
    WifiSetupController(MainActivity activity) {
        this.activity = activity;
        ssid = activity.findViewById(R.id.wifiSsidInput);
        password = activity.findViewById(R.id.wifiPasswordInput);
        open = activity.findViewById(R.id.wifiOpenNetworkCheck);
        status = activity.findViewById(R.id.wifiStatusText);
        password.setSaveEnabled(false);
        open.setOnCheckedChangeListener((button, checked) -> {
            password.setEnabled(!checked);
            if (checked) password.setText("");
        });
        activity.findViewById(R.id.wifiConnectButton).setOnClickListener(v -> connect());
        activity.findViewById(R.id.wifiSettingsButton).setOnClickListener(v -> openSettings());
        activity.findViewById(R.id.wifiRefreshButton).setOnClickListener(v -> updateStatus());
    }
    static String validationError(String name, String secret, boolean isOpen) {
        if (name.isEmpty() || name.getBytes(StandardCharsets.UTF_8).length > 32 || name.indexOf('\0') >= 0)
            return "Имя Wi-Fi: от 1 до 32 байт.";
        if (!isOpen && (secret.length() < 8 || secret.length() > 63 || !secret.matches("[ -~]+")))
            return "Пароль WPA2: 8–63 печатных латинских символа. Для открытой сети отметь «Сеть без пароля».";
        return "";
    }
    private void connect() {
        String name = ssid.getText().toString();
        String secret = password.getText().toString();
        String error = validationError(name, secret, open.isChecked());
        if (!error.isEmpty()) { notice = error; updateStatus(); return; }
        if (Build.VERSION.SDK_INT < 29) {
            notice = "На этой версии Android добавь сеть в системных настройках Wi-Fi.";
            password.setText(""); openSettings(); return;
        }
        if (Build.VERSION.SDK_INT == 29 && activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            notice = "Android 10 требует разрешение местоположения для сетевой рекомендации.";
            updateStatus();
            activity.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION);
            return;
        }
        try {
            WifiNetworkSuggestion.Builder builder = new WifiNetworkSuggestion.Builder().setSsid(name);
            if (!open.isChecked()) builder.setWpa2Passphrase(secret);
            WifiNetworkSuggestion suggestion = builder.build();
            if (Build.VERSION.SDK_INT >= 30) {
                // Resolve a system component BEFORE including credentials. No implicit password intent.
                Intent intent = new Intent(Settings.ACTION_WIFI_ADD_NETWORKS);
                ResolveInfo target = null;
                for (ResolveInfo candidate : activity.getPackageManager().queryIntentActivities(intent,
                        PackageManager.MATCH_DEFAULT_ONLY | PackageManager.MATCH_SYSTEM_ONLY)) {
                    if (candidate.activityInfo != null && candidate.activityInfo.exported) { target = candidate; break; }
                }
                if (target == null) {
                    notice = "Системное добавление сети недоступно. Открой настройки Wi-Fi.";
                    password.setText(""); updateStatus(); return;
                }
                intent.setComponent(new ComponentName(target.activityInfo.packageName, target.activityInfo.name));
                ArrayList<WifiNetworkSuggestion> networks = new ArrayList<>();
                networks.add(suggestion);
                intent.putParcelableArrayListExtra(Settings.EXTRA_WIFI_NETWORK_LIST, networks);
                activity.startActivityForResult(intent, ADD_NETWORK);
                notice = "Подтверди сохранение сети в Android. Текущий IP будет показан после подключения.";
            } else {
                WifiManager wifi = (WifiManager) activity.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                int result = wifi.addNetworkSuggestions(Collections.singletonList(suggestion));
                if (result == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS
                        || result == WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_DUPLICATE) {
                    notice = "Сеть рекомендована Android. Разреши рекомендацию LocalLS в уведомлении Android; "
                            + "подключение выбирает система. Если сеть не меняется, выбери её в настройках Wi-Fi.";
                } else if (result == WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_APP_DISALLOWED) {
                    notice = "Android запретил сетевые рекомендации LocalLS. Разреши их в системных настройках.";
                } else notice = "Android не принял сетевую рекомендацию. Используй системные настройки Wi-Fi.";
            }
        } catch (RuntimeException e) {
            // Never display framework exception messages: some vendors include SSID/passphrase.
            notice = "Не удалось передать сеть Android. Проверь разрешения и системные настройки Wi-Fi.";
        } finally { password.setText(""); }
        updateStatus();
    }
    void onPermissionResult(int[] grants) {
        if (grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED) connect();
        else { password.setText(""); notice = "Разрешение не выдано. Подключись через настройки Wi-Fi."; updateStatus(); }
    }
    void onAddResult(int result) {
        notice = result == Activity.RESULT_OK ? "Android обработал сеть. Фактическое подключение и IP — ниже."
                : "Добавление сети отменено или не завершено.";
        updateStatus();
    }
    private void openSettings() {
        try { activity.startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)); }
        catch (RuntimeException e) { notice = "Открой Wi-Fi в системных настройках телефона."; updateStatus(); }
    }
    void start() { if (!running) { running = true; handler.post(refresh); } }
    void stop() { running = false; handler.removeCallbacks(refresh); }
    void destroy() { stop(); password.setText(""); }
    private void updateStatus() {
        String current = "Wi-Fi не подключён";
        try {
            ConnectivityManager manager = (ConnectivityManager) activity.getSystemService(Context.CONNECTIVITY_SERVICE);
            for (Network network : manager.getAllNetworks()) {
                NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
                if (capabilities == null || !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                        || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue;
                LinkProperties properties = manager.getLinkProperties(network);
                current = "Wi-Fi подключён";
                if (properties != null) for (LinkAddress address : properties.getLinkAddresses())
                    if (address.getAddress() instanceof Inet4Address) {
                        current += " • IP: " + address.getAddress().getHostAddress(); break;
                    }
                break;
            }
        } catch (RuntimeException e) { current = "Статус Wi-Fi недоступен"; }
        String text = notice + "\n\n" + current + "\nSSH использует текущую сеть Android; после смены сети адрес сервера может измениться.";
        if (!text.contentEquals(status.getText())) status.setText(text);
    }
}
