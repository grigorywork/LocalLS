package com.bitpoint.homeservercontrol;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.WindowManager;
import android.widget.*;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Visual migration; server passwords/config/startup are never changed here. */
public class SecurityActivity extends BaseActivity {
    private static final int IMPORT = 71, EXPORT = 72;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private LinearLayout content;
    private TextView status, result;
    private EditText phrase, bootstrap;
    private CheckBox keyMode, legacy;
    private String host, user, exportedPublic = "";
    private int port, agentPort;
    private boolean busy;
    private SharedPreferences prefs;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        prefs = SshKeys.prefs(this); host = ConnectionSelector.selectedHost(this);
        port = prefs.getInt(ServerConfig.KEY_PORT, ServerConfig.DEFAULT_PORT);
        agentPort = prefs.getInt(ServerConfig.KEY_AGENT_PORT, ServerConfig.DEFAULT_AGENT_PORT);
        user = prefs.getString(ServerConfig.KEY_USER, ServerConfig.DEFAULT_USER);
        ScrollView scroll = new ScrollView(this); content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        int pad = (int)(14 * getResources().getDisplayMetrics().density); content.setPadding(pad, pad, pad, pad); scroll.addView(content); setContentView(scroll);
        text("Безопасность · " + user + "@" + host + ":" + port);
        text("Ключ хранится зашифрованным в Android Keystore. Приватный ключ не попадает в профиль, буфер обмена или логи.");
        status = text("Проверяем локальный ключ…");
        keyMode = new CheckBox(this); keyMode.setText("Вход только по SSH-ключу · без парольного fallback"); keyMode.setChecked(SshKeys.keyMode(this)); content.addView(keyMode);
        keyMode.setOnCheckedChangeListener((b, on) -> prefs.edit().putString(SshKeys.MODE, on ? "key" : "password").apply());
        button("Создать ключ RSA 3072", () -> new AlertDialog.Builder(this).setTitle("Создать SSH-ключ?")
                .setMessage("Текущий локальный ключ этого профиля будет заменён. Ключи сервера и его пароль не изменяются.")
                .setNegativeButton("Отмена", null).setPositiveButton("Создать", (d,w) -> task(() -> { saveForRoutes(SshKeys.generate()); return "Ключ создан и зашифрован"; })).show());
        phrase = secret("Парольная фраза импортируемого ключа · если есть");
        button("Импортировать приватный ключ", () -> { Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE); startActivityForResult(i, IMPORT); });
        button("Экспортировать публичный ключ .pub", () -> task(() -> {
            JSONObject info = SshKeys.info(this, host, port, user); exportedPublic = info.optString(SshKeys.PUBLIC);
            if (exportedPublic.isEmpty()) throw new Exception("KEY_MISSING");
            runOnUiThread(() -> startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/plain").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE, "LocalLS-id_rsa.pub"), EXPORT));
            return "Выберите место для публичного ключа";
        }));
        button("Удалить локальный ключ", () -> new AlertDialog.Builder(this).setTitle("Удалить локальный ключ?")
                .setMessage("На сервере публичный ключ останется. Для входа потребуется другой разрешённый ключ или пароль. Режим входа сам не переключится.")
                .setNegativeButton("Отмена", null).setPositiveButton("Удалить", (d,w) -> task(() -> { SshKeys.clear(this, host, port, user); String other = ConnectionSelector.alternateHost(this); if (!other.isEmpty()) SshKeys.clear(this, other, port, user); return "Локальный ключ удалён"; })).show());
        text("Переход с пароля: сначала проверьте сервер и подтвердите его SSH fingerprint на главном экране.");
        bootstrap = secret("Действующий пароль сервера · только для установки ключа");
        button("Установить ключ и проверить вход", this::install);
        text("Существующие authorized_keys сохраняются. После успешной проверки клиент переходит на ключ и удаляет сохранённый пароль. Парольный вход сервера здесь не отключается.");
        text("Управляющий агент · независимый HTTPS-канал");
        button("Проверить HTTPS-сертификат агента", () -> task(() -> {
            String fingerprint = AgentTls.probe(host, agentPort);
            runOnUiThread(() -> { if (isDestroyed()) return; new AlertDialog.Builder(this).setTitle("Доверять сертификату агента?")
                    .setMessage(host + ":" + agentPort + "\n" + fingerprint + "\nСверьте с сертификатом на сервере. Токен не отправлялся.")
                    .setNegativeButton("Отмена", null).setPositiveButton("Доверять", (d,w) -> { AgentTls.trust(this, host, agentPort, fingerprint); legacy.setChecked(false); result.setText("HTTPS-сертификат сохранён"); }).show(); });
            return "Сертификат получен без отправки токена";
        }));
        legacy = new CheckBox(this); legacy.setText("Временная совместимость со старым HTTP-агентом"); legacy.setChecked(prefs.getBoolean(AgentTls.LEGACY, false)); content.addView(legacy);
        legacy.setOnCheckedChangeListener((b,on) -> { if (on) new AlertDialog.Builder(this).setTitle("HTTP без шифрования")
                .setMessage("Токен старого агента будет отправляться без TLS. Обновите агент на HTTPS; этот режим нужен только для временной совместимости.")
                .setNegativeButton("Отмена", (d,w) -> legacy.setChecked(false)).setPositiveButton("Временно разрешить", (d,w) -> prefs.edit().putBoolean(AgentTls.LEGACY,true).apply()).show();
            else prefs.edit().putBoolean(AgentTls.LEGACY,false).apply(); });
        result = text(""); button("Назад", this::finish); refresh();
    }
    private void install() {
        final String password = bootstrap.getText().toString(); bootstrap.setText("");
        if (password.isEmpty()) { result.setText("Введите действующий пароль для установки публичного ключа"); return; }
        new AlertDialog.Builder(this).setTitle("Добавить публичный ключ?")
                .setMessage(user + "@" + host + ":" + port + "\nДобавим ключ, проверим новый вход и только после успеха переключим клиент. Конфигурация и пароль сервера сохраняются.")
                .setNegativeButton("Отмена", null).setPositiveButton("Установить", (d,w) -> task(() -> {
                    String pin = HostTrustStore.get(this, host, port); if (pin.isEmpty()) throw new Exception("HOST_UNTRUSTED");
                    SshCredentials key = SshKeys.keyCredentials(this, host, port, user); if (!key.available()) throw new Exception("KEY_MISSING");
                    JSONObject info = SshKeys.inspect(key.privateKey,key.passphrase);
                    SftpClient.installPublicKey(host,port,user,SshCredentials.password(password),pin,info.getString(SshKeys.PUBLIC));
                    SftpClient.list(host,port,user,key,pin,".");
                    prefs.edit().putString(SshKeys.MODE,"key").putBoolean(ServerConfig.KEY_SAVE_PASSWORD,false).apply(); SecurePrefs.clearPassword(this);
                    runOnUiThread(() -> keyMode.setChecked(true));
                    return "Вход по SSH-ключу проверен. Сохранённый пароль удалён; пароль сервера не отключён.";
                })).show();
    }
    private void saveForRoutes(JSONObject key) throws Exception {
        SshKeys.save(this,host,port,user,key); String other = ConnectionSelector.alternateHost(this);
        if (!other.isEmpty() && !other.equals(host)) SshKeys.save(this,other,port,user,key);
    }
    private TextView text(String value) { TextView t = new TextView(this); t.setText(value); t.setTextColor(ThemeCatalog.color(this,com.bitpoint.homeservercontrol.ui.R.attr.hscTextPrimary)); t.setTextSize(14); t.setPadding(0,10,0,10); content.addView(t); return t; }
    private EditText secret(String hint) { EditText e = new EditText(this); e.setHint(hint); e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD); e.setSaveEnabled(false); e.setSingleLine(true); e.setMinimumHeight(Math.round(48 * getResources().getDisplayMetrics().density)); if (android.os.Build.VERSION.SDK_INT >= 26) e.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO); e.setTextColor(ThemeCatalog.color(this,com.bitpoint.homeservercontrol.ui.R.attr.hscTextPrimary)); e.setHintTextColor(ThemeCatalog.color(this,com.bitpoint.homeservercontrol.ui.R.attr.hscTextSecondary)); content.addView(e); return e; }
    private void button(String label,Runnable fn) { Button b = new Button(this); b.setText(label); b.setAllCaps(false); content.addView(b,new LinearLayout.LayoutParams(-1,(int)(52*getResources().getDisplayMetrics().density))); IconButtons.apply(b,com.bitpoint.homeservercontrol.ui.R.drawable.ic_action_key); b.setOnClickListener(v -> { if (!busy) fn.run(); }); }
    interface Work { String run() throws Exception; }
    private void task(Work work) { if (busy) return; busy = true; keyMode.setEnabled(false); legacy.setEnabled(false); result.setText("Выполняется…"); worker.execute(() -> { String output;
        try { output = work.run(); } catch (Exception | LinkageError e) { output = "Операция не выполнена. Проверьте ключ, парольную фразу, SSH fingerprint, доступ к серверу или поддержку HTTPS агентом."; }
        final String value = output; runOnUiThread(() -> { if (!isDestroyed() && !isFinishing()) { busy=false; keyMode.setEnabled(true); legacy.setEnabled(true); result.setText(value); refresh(); } }); }); }
    private void refresh() { worker.execute(() -> { JSONObject info = SshKeys.info(this,host,port,user); final String value = info.optString("fingerprint","Ключ этого профиля ещё не создан / не загружен"); runOnUiThread(() -> { if (!isDestroyed()) status.setText(value); }); }); }
    @Override protected void onActivityResult(int request,int code,Intent data) { super.onActivityResult(request,code,data); if (code != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri=data.getData();
        if (request==IMPORT) { final String passphrase=phrase.getText().toString(); phrase.setText(""); task(() -> {
            ByteArrayOutputStream out=new ByteArrayOutputStream(); try(InputStream in=getContentResolver().openInputStream(uri)) { if(in==null)throw new Exception("NO_FILE"); byte[] buf=new byte[4096]; int n; while((n=in.read(buf))!=-1){ if(out.size()+n>128*1024)throw new Exception("KEY_LARGE");out.write(buf,0,n); } }
            String privateKey=out.toString(StandardCharsets.UTF_8.name()); JSONObject info=SshKeys.inspect(privateKey,passphrase); info.put(SshKeys.PRIVATE,privateKey).put(SshKeys.PHRASE,passphrase);
            runOnUiThread(() -> { if (isDestroyed()) return; new AlertDialog.Builder(this).setTitle("Импортировать этот ключ?").setMessage(info.optString("fingerprint") + "\nТекущий локальный ключ этого профиля будет заменён.").setNegativeButton("Отмена",null).setPositiveButton("Импортировать",(d,w) -> task(() -> { saveForRoutes(info); return "Ключ импортирован и зашифрован"; })).show(); }); return "Проверьте fingerprint и подтвердите импорт";
        }); } else if (request==EXPORT) task(() -> { if(exportedPublic.isEmpty())throw new Exception("KEY_MISSING"); try(OutputStream out=getContentResolver().openOutputStream(uri)){if(out==null)throw new Exception("NO_FILE");out.write((exportedPublic+"\n").getBytes(StandardCharsets.UTF_8));}exportedPublic="";return "Публичный ключ сохранён без приватной части"; });
    }
    @Override protected void onDestroy() { phrase.setText(""); bootstrap.setText(""); worker.shutdownNow(); super.onDestroy(); }
}
