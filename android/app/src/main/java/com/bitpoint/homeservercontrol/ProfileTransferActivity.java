package com.bitpoint.homeservercontrol;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProfileTransferActivity extends BaseActivity {
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private TextView summary;
    private String pendingExport="";
    private boolean closed;
    private SharedPreferences prefs() { return getSharedPreferences(ServerConfig.PREFS,MODE_PRIVATE); }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if(state!=null) pendingExport=state.getString("profile_export", "");
        LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(24,24,24,24);
        TextView title=new TextView(this);title.setText("Перенос подключения");title.setTextSize(22);content.addView(title);
        summary=new TextView(this);summary.setTextSize(16);content.addView(summary);render();
        TextView help=new TextView(this);help.setText("Файл содержит адреса, пользователя и порты. SSH-ключи, пароль, токен агента и доверенные отпечатки не переносятся. После импорта проверь сервер и настрой доступ по ключу. Сначала сохрани изменения на главном экране.");content.addView(help);
        Button export=new Button(this);export.setText("Сохранить параметры в файл");content.addView(export);export.setOnClickListener(v -> {
            try { pendingExport=ConnectionProfile.fromPrefs(prefs()).encode();
                Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json");
                intent.putExtra(Intent.EXTRA_TITLE,"LocalLS-connection.json");startActivityForResult(intent,901);
            } catch(Exception e) { message("Проверь сохранённые параметры подключения"); }
        });
        Button load=new Button(this);load.setText("Загрузить и проверить файл");content.addView(load);load.setOnClickListener(v -> {
            try { startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"),902); }
            catch(Exception e) { message("Не удалось открыть выбор файлов"); }
        });
        Button back=new Button(this);back.setText("Назад");content.addView(back);back.setOnClickListener(v -> finish());
        ScrollView scroll=new ScrollView(this);scroll.addView(content);setContentView(scroll);
    }
    private void render() { try { summary.setText(ConnectionProfile.fromPrefs(prefs()).summary()); } catch(Exception e) { summary.setText("Сначала настрой подключение на главном экране"); } }
    private void message(String text) { if(!closed && !isFinishing()) Toast.makeText(this,text,Toast.LENGTH_LONG).show(); }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(result!=RESULT_OK || data==null || data.getData()==null || (request!=901 && request!=902)) return;
        Uri uri=data.getData(); final String payload=pendingExport;
        io.execute(() -> {
            try {
                if(request==901) {
                    if(payload.isEmpty()) throw new IllegalStateException();
                    try(OutputStream out=getContentResolver().openOutputStream(uri,"w")) {
                        if(out==null) throw new IllegalStateException();out.write(payload.getBytes(StandardCharsets.UTF_8));
                    }
                    runOnUiThread(() -> message("Параметры сохранены без секретов"));
                } else {
                    ByteArrayOutputStream bytes=new ByteArrayOutputStream();
                    try(InputStream in=getContentResolver().openInputStream(uri)) {
                        if(in==null) throw new IllegalStateException();byte[] buffer=new byte[1024];int n;
                        while((n=in.read(buffer))!=-1) { if(bytes.size()+n>8192) throw new IllegalArgumentException();bytes.write(buffer,0,n); }
                    }
                    ConnectionProfile profile=ConnectionProfile.decode(new String(bytes.toByteArray(),StandardCharsets.UTF_8));
                    runOnUiThread(() -> { if(closed || isFinishing()) return;
                        new AlertDialog.Builder(this).setTitle("Параметры проверены")
                            .setMessage(profile.summary()+"\n\nПрименить эти параметры? Сохранённый токен агента будет очищен, фоновое наблюдение выключено. SSH-ключи остаются на устройстве.")
                            .setNegativeButton("Отмена",null).setPositiveButton("Применить",(d,w) -> {
                                profile.apply(prefs()); MonitoringScheduler.setEnabled(this,false); render();message("Параметры перенесены. Проверь SSH-подключение.");
                            }).show();
                    });
                }
            } catch(Exception e) { runOnUiThread(() -> message(request==901?"Не удалось сохранить файл":"Файл не подходит: нужен профиль LocalLS до 8 КБ с корректными адресами и портами")); }
        });
    }
    @Override protected void onSaveInstanceState(Bundle state) { state.putString("profile_export",pendingExport);super.onSaveInstanceState(state); }
    @Override protected void onDestroy() { closed=true;io.shutdownNow();super.onDestroy(); }
}
