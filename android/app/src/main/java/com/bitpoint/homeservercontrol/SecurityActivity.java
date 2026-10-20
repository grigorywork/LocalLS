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
    private EditText phrase;
    private String host, user, exportedPublic = "";
    private int port;
    private boolean busy;
    private SharedPreferences prefs;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        prefs = SshKeys.prefs(this); host = ConnectionSelector.selectedHost(this);
        port = prefs.getInt(ServerConfig.KEY_PORT, ServerConfig.DEFAULT_PORT);
        user = prefs.getString(ServerConfig.KEY_USER, ServerConfig.DEFAULT_USER);
        ScrollView scroll = new ScrollView(this); content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        int pad = (int)(14 * getResources().getDisplayMetrics().density); content.setPadding(pad, pad, pad, pad); scroll.addView(content); setContentView(scroll);
        text("Ключ доступа");
        text("Подключение к " + user + "@" + host + ":" + port);
        text("Создай ключ на этом телефоне и передай публичный файл .pub на телефон-сервер. Там открой «Мой сервер» и добавь этот файл в разрешённые ключи.");
        text("Ключ хранится зашифрованным в Android Keystore. Приватный ключ не попадает в профиль, буфер обмена или логи.");
        text("SSH/SFTP: только ключ. Парольный вход клиента отключён.");
        status = text("Проверяем локальный ключ…");
        button("Создать ключ RSA 3072", () -> new AlertDialog.Builder(this).setTitle("Создать SSH-ключ?")
                .setMessage("Текущий локальный ключ этого профиля будет заменён. После замены нужно заново передать публичный файл на сервер.")
                .setNegativeButton("Отмена", null).setPositiveButton("Создать", (d,w) -> task(() -> { saveForRoutes(SshKeys.generate()); return "Ключ создан и зашифрован"; })).show());
        button("Экспортировать публичный ключ .pub", () -> task(() -> {
            JSONObject info = SshKeys.info(this, host, port, user); exportedPublic = info.optString(SshKeys.PUBLIC);
            if (exportedPublic.isEmpty()) throw new Exception("KEY_MISSING");
            runOnUiThread(() -> startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/plain").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE, "LocalLS-id_rsa.pub"), EXPORT));
            return "Выберите место для публичного ключа";
        }));
        LinearLayout basic=content;
        Button more=new Button(this);more.setText("Дополнительно");more.setAllCaps(false);content.addView(more);
        LinearLayout advanced=new LinearLayout(this);advanced.setOrientation(LinearLayout.VERTICAL);advanced.setVisibility(android.view.View.GONE);content.addView(advanced);
        content=advanced;
        phrase = secret("Парольная фраза импортируемого ключа · если есть");
        button("Импортировать приватный ключ", () -> { Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE); startActivityForResult(i, IMPORT); });
        button("Удалить локальный ключ", () -> new AlertDialog.Builder(this).setTitle("Удалить локальный ключ?")
                .setMessage("На сервере публичный ключ останется. Для входа потребуется другой разрешённый ключ. Парольный вход клиента недоступен.")
                .setNegativeButton("Отмена", null).setPositiveButton("Удалить", (d,w) -> task(() -> { SshKeys.clear(this, host, port, user); String other = ConnectionSelector.alternateHost(this); if (!other.isEmpty()) SshKeys.clear(this, other, port, user); return "Локальный ключ удалён"; })).show());
        content=basic;
        more.setOnClickListener(v -> advanced.setVisibility(advanced.getVisibility()==android.view.View.VISIBLE?android.view.View.GONE:android.view.View.VISIBLE));
        result = text(""); button("Назад", this::finish); refresh();
    }
    private void saveForRoutes(JSONObject key) throws Exception {
        SshKeys.save(this,host,port,user,key); String other = ConnectionSelector.alternateHost(this);
        if (!other.isEmpty() && !other.equals(host)) SshKeys.save(this,other,port,user,key);
    }
    private TextView text(String value) { TextView t = new TextView(this); t.setText(value); t.setTextColor(ThemeCatalog.color(this,com.bitpoint.homeservercontrol.ui.R.attr.hscTextPrimary)); t.setTextSize(14); t.setPadding(0,10,0,10); content.addView(t); return t; }
    private EditText secret(String hint) { EditText e = new EditText(this); e.setHint(hint); e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD); e.setSaveEnabled(false); e.setSingleLine(true); e.setMinimumHeight(Math.round(48 * getResources().getDisplayMetrics().density)); if (android.os.Build.VERSION.SDK_INT >= 26) e.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO); e.setTextColor(ThemeCatalog.color(this,com.bitpoint.homeservercontrol.ui.R.attr.hscTextPrimary)); e.setHintTextColor(ThemeCatalog.color(this,com.bitpoint.homeservercontrol.ui.R.attr.hscTextSecondary)); content.addView(e); return e; }
    private void button(String label,Runnable fn) { Button b = new Button(this); b.setText(label); b.setAllCaps(false); content.addView(b,new LinearLayout.LayoutParams(-1,(int)(52*getResources().getDisplayMetrics().density))); IconButtons.apply(b,com.bitpoint.homeservercontrol.ui.R.drawable.ic_action_key); b.setOnClickListener(v -> { if (!busy) fn.run(); }); }
    interface Work { String run() throws Exception; }
    private void task(Work work) { if (busy) return; busy = true; result.setText("Выполняется…"); worker.execute(() -> { String output;
        try { output = work.run(); } catch (Exception | LinkageError e) { output = "Операция не выполнена. Проверьте наличие ключа, доступ к файлу или парольную фразу импорта."; }
        final String value = output; runOnUiThread(() -> { if (!isDestroyed() && !isFinishing()) { busy=false; result.setText(value); refresh(); } }); }); }
    private void refresh() { worker.execute(() -> { JSONObject info = SshKeys.info(this,host,port,user); final String value = info.optString("fingerprint","Ключ этого профиля ещё не создан / не загружен"); runOnUiThread(() -> { if (!isDestroyed()) status.setText(value); }); }); }
    @Override protected void onActivityResult(int request,int code,Intent data) { super.onActivityResult(request,code,data); if (code != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri=data.getData();
        if (request==IMPORT) { final String passphrase=phrase.getText().toString(); phrase.setText(""); task(() -> {
            ByteArrayOutputStream out=new ByteArrayOutputStream(); try(InputStream in=getContentResolver().openInputStream(uri)) { if(in==null)throw new Exception("NO_FILE"); byte[] buf=new byte[4096]; int n; while((n=in.read(buf))!=-1){ if(out.size()+n>128*1024)throw new Exception("KEY_LARGE");out.write(buf,0,n); } }
            String privateKey=out.toString(StandardCharsets.UTF_8.name()); JSONObject info=SshKeys.inspect(privateKey,passphrase); info.put(SshKeys.PRIVATE,privateKey).put(SshKeys.PHRASE,passphrase);
            runOnUiThread(() -> { if (isDestroyed()) return; new AlertDialog.Builder(this).setTitle("Импортировать этот ключ?").setMessage(info.optString("fingerprint") + "\nТекущий локальный ключ этого профиля будет заменён.").setNegativeButton("Отмена",null).setPositiveButton("Импортировать",(d,w) -> task(() -> { saveForRoutes(info); return "Ключ импортирован и зашифрован"; })).show(); }); return "Проверьте fingerprint и подтвердите импорт";
        }); } else if (request==EXPORT) task(() -> { if(exportedPublic.isEmpty())throw new Exception("KEY_MISSING"); try(OutputStream out=getContentResolver().openOutputStream(uri)){if(out==null)throw new Exception("NO_FILE");out.write((exportedPublic+"\n").getBytes(StandardCharsets.UTF_8));}exportedPublic="";return "Публичный ключ сохранён без приватной части"; });
    }
    @Override protected void onDestroy() { phrase.setText(""); worker.shutdownNow(); super.onDestroy(); }
}
