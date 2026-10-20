package com.bitpoint.homeservercontrol;

import android.app.AlertDialog;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class EmbeddedServerActivity extends BaseActivity {
    private EditText port,user,key;
    private TextView state;
    private Button start;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private boolean closed;
    private final Runnable refresh=new Runnable() { public void run() { render();if(!closed)ui.postDelayed(this,1000); } };
    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);layout.setPadding(24,24,24,24);
        text(layout,"Этот телефон — SFTP-сервер",22);
        text(layout,"Работает внутри LocalLS, без Termux. Доступ только к отдельной папке LocalLS, не ко всему телефону. Выбранные файлы можно скопировать в неё ниже. При удалении приложения эта папка и host key удаляются.",16);
        state=text(layout,"",16);
        SharedPreferences p=getSharedPreferences(EmbeddedServerService.PREFS,MODE_PRIVATE);
        user=field(layout,"Пользователь",p.getString("user","localls"),true);user.setId(R.id.embeddedServerUser);
        port=field(layout,"Порт (1024–65535)",String.valueOf(p.getInt("port",8022)),true);port.setInputType(InputType.TYPE_CLASS_NUMBER);port.setId(R.id.embeddedServerPort);
        key=field(layout,"Публичный ключ клиента (.pub), не приватный",p.getString("allowed_key",""),false);key.setId(R.id.embeddedServerKey);
        text(layout,"На Windows можно создать ключ командой: ssh-keygen -t rsa -b 3072. Скопируй сюда содержимое файла id_rsa.pub. Поддерживается RSA 3072+ или ECDSA P-256. Пароль для входа отключён.",14);
        button(layout,"Загрузить публичный ключ из файла",() -> pick(910,"*/*",false));
        start=button(layout,"Включить сервер",() -> startServer());
        button(layout,"Остановить сервер",() -> stopService(new Intent(this,EmbeddedServerService.class)));
        button(layout,"Добавить файлы в папку сервера",() -> pick(911,"*/*",true));
        button(layout,"Файлы сервера / сохранить на телефон",() -> startActivity(new Intent(this,EmbeddedFilesActivity.class)));
        button(layout,"Скопировать данные подключения",() -> {
            if(!EmbeddedServerService.running) { message("Сначала включи сервер");return; }
            ClipboardManager c=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            c.setPrimaryClip(ClipData.newPlainText("LocalLS SFTP",connectionText()));message("Адреса и fingerprint скопированы");
        });
        button(layout,"Старый режим через Termux",() -> startActivity(new Intent(this,ServerSetupActivity.class)));
        text(layout,"Служба показывает постоянное уведомление. Android может ограничить её работу в фоне (включая лимит dataSync до 6 часов). Автозапуск после перезагрузки выключен. Подключайся с ПК по Wi-Fi/VPN; сервер слушает сетевые интерфейсы телефона. Сверь fingerprint перед первым входом.",14);
        button(layout,"Назад",() -> finish());
        ScrollView scroll=new ScrollView(this);scroll.addView(layout);setContentView(scroll);
        if(Build.VERSION.SDK_INT<26) {start.setEnabled(false);state.setText("Нужен Android 8 или новее");}
    }
    private TextView text(LinearLayout parent,String value,int size) { TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setPadding(0,10,0,10);parent.addView(t);return t; }
    private EditText field(LinearLayout parent,String label,String value,boolean single) {
        text(parent,label,16);EditText input=new EditText(this);input.setText(value);input.setSingleLine(single);input.setMinHeight(48);parent.addView(input);return input;
    }
    private Button button(LinearLayout parent,String label,Runnable action) { Button b=new Button(this);b.setText(label);parent.addView(b);b.setOnClickListener(v -> action.run());return b; }
    private void pick(int request,String type,boolean multiple) {
        try {Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(type);i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,multiple);startActivityForResult(i,request);}
        catch(Exception e) {message("Не удалось открыть выбор файлов Android");}
    }
    private void startServer() {
        if(EmbeddedServerService.running || EmbeddedServerService.starting)return;
        final int parsed;final String name=user.getText().toString().trim(),pub=key.getText().toString().trim();
        try { parsed=Integer.parseInt(port.getText().toString());if(parsed<1024 || parsed>65535)throw new Exception(); }
        catch(Exception e) {port.setError("Укажи порт от 1024 до 65535");return;}
        if(!ConnectionEndpoint.validUser(name)) {user.setError("Неверное имя SSH-пользователя");return;}
        start.setEnabled(false);
        worker.execute(() -> {
            try {EmbeddedSftpServer.authorizedKey(pub);
                getSharedPreferences(EmbeddedServerService.PREFS,MODE_PRIVATE).edit().putString("user",name).putInt("port",parsed).putString("allowed_key",pub).apply();
                ui.post(() -> {if(closed)return;try {
                    if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)
                        requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},912);
                    EmbeddedServerService.start(this);
                } catch(Exception e) {message("Android запретил запуск службы. Открой приложение и повтори.");} });
            } catch(Exception | LinkageError e) {ui.post(() -> {if(!closed){key.setError("Нужен публичный RSA 3072+ или ECDSA P-256 ключ; приватный ключ вставлять нельзя");start.setEnabled(true);}});}
        });
    }
    private String connectionText() {
        String name=getSharedPreferences(EmbeddedServerService.PREFS,MODE_PRIVATE).getString("user","localls");
        StringBuilder s=new StringBuilder("SFTP, пользователь: "+name+", порт: "+EmbeddedServerService.boundPort+", папка: /\n");
        try {Enumeration<NetworkInterface> interfaces=NetworkInterface.getNetworkInterfaces();
            while(interfaces.hasMoreElements()) {NetworkInterface n=interfaces.nextElement();if(!n.isUp() || n.isLoopback())continue;
                Enumeration<InetAddress> addresses=n.getInetAddresses();while(addresses.hasMoreElements()) {InetAddress a=addresses.nextElement();
                    if(a instanceof Inet4Address)s.append("sftp://").append(name).append('@').append(a.getHostAddress()).append(':').append(EmbeddedServerService.boundPort).append("\n");
                }
            }
        } catch(Exception ignored) { }
        return s.append("Fingerprint: ").append(EmbeddedServerService.fingerprint).toString();
    }
    private void render() {
        if(Build.VERSION.SDK_INT<26)return;
        state.setText(EmbeddedServerService.status+(EmbeddedServerService.running?"\n"+connectionText():""));
        start.setEnabled(!EmbeddedServerService.running && !EmbeddedServerService.starting);
        boolean editable=!EmbeddedServerService.running && !EmbeddedServerService.starting;
        key.setEnabled(editable);port.setEnabled(editable);user.setEnabled(editable);
    }
    private void message(String value) {if(!closed)Toast.makeText(this,value,Toast.LENGTH_LONG).show();}
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);if(result!=RESULT_OK || data==null)return;
        ArrayList<Uri> uris=new ArrayList<>();if(data.getData()!=null)uris.add(data.getData());
        if(data.getClipData()!=null)for(int i=0;i<Math.min(20,data.getClipData().getItemCount());i++)uris.add(data.getClipData().getItemAt(i).getUri());
        worker.execute(() -> {
            try {
                if(request==910 && !uris.isEmpty()) {
                    ByteArrayOutputStream out=new ByteArrayOutputStream();
                    try(InputStream in=getContentResolver().openInputStream(uris.get(0))) {if(in==null)throw new IOException();byte[] b=new byte[1024];int n;
                        while((n=in.read(b))!=-1) {if(out.size()+n>16384)throw new IOException();out.write(b,0,n);}}
                    String publicKey=out.toString("UTF-8");EmbeddedSftpServer.authorizedKey(publicKey);
                    ui.post(() -> {if(!closed && !EmbeddedServerService.running && !EmbeddedServerService.starting)key.setText(publicKey.trim());});
                } else if(request==911) {
                    File share=EmbeddedServerService.share(this);if(!share.isDirectory() && !share.mkdirs())throw new IOException();
                    for(Uri uri:uris) {
                        String name="file";try(Cursor cursor=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)) {if(cursor!=null && cursor.moveToFirst())name=cursor.getString(0);}
                        if(name==null)name="file";name=name.replaceAll("[\\p{Cntrl}/\\\\]","_");if(name.length()>120)name=name.substring(0,120);if(name.equals(".") || name.equals("..") || name.isEmpty())name="file";
                        File temp=File.createTempFile(".import-",".part",share);
                        try {
                            try(InputStream in=getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(temp)) {if(in==null)throw new IOException();byte[] buffer=new byte[65536];int n;
                                while((n=in.read(buffer))!=-1) {if(Thread.currentThread().isInterrupted())throw new IOException();out.write(buffer,0,n);}}
                            File target=new File(share,name);if(target.exists())target=new File(share,System.currentTimeMillis()+"-"+java.util.UUID.randomUUID().toString().substring(0,8)+"-"+name);
                            if(Build.VERSION.SDK_INT>=26) java.nio.file.Files.move(temp.toPath(),target.toPath());
                            else throw new IOException("Android 8 required");
                        } finally {temp.delete();}
                    }
                    ui.post(() -> message("Файлы добавлены в папку сервера"));
                }
            } catch(Exception | LinkageError e) {ui.post(() -> message("Не удалось обработать файл. Для ключа нужен .pub, для импорта — доступный файл и свободное место."));}
        });
    }
    @Override protected void onResume() {super.onResume();closed=false;ui.post(refresh);}
    @Override protected void onPause() {ui.removeCallbacks(refresh);super.onPause();}
    @Override protected void onDestroy() {closed=true;ui.removeCallbacksAndMessages(null);worker.shutdownNow();super.onDestroy();}
}
