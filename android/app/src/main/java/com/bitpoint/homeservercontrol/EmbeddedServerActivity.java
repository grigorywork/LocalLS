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
    private TextView state,address,receiveState;
    private WifiAddressView wifiAddress;
    private boolean checkingKey;
    private Button start,importKey;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private boolean closed;
    private final Runnable refresh=new Runnable() { public void run() { render();if(!closed)ui.postDelayed(this,1000); } };
    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);layout.setPadding(24,24,24,24);
        text(layout,"Мой сервер · телефон 1",22);
        text(layout,"Сначала настрой этот телефон. Адрес ниже приложение определяет само. Затем подключим телефон 2.",16);
        button(layout,"Инструкция: два телефона",() -> startActivity(new Intent(this,PhoneSetupGuideActivity.class)));
        text(layout,"1. Настрой и включи сервер",20);
        wifiAddress=new WifiAddressView(this);wifiAddress.setId(R.id.embeddedWifiAddress);layout.addView(wifiAddress);
        SharedPreferences p=getSharedPreferences(EmbeddedServerService.PREFS,MODE_PRIVATE);
        user=field(layout,"Пользователь",p.getString("user","localls"),true);user.setId(R.id.embeddedServerUser);
        port=field(layout,"Порт",String.valueOf(p.getInt("port",8022)),true);port.setInputType(InputType.TYPE_CLASS_NUMBER);port.setId(R.id.embeddedServerPort);
        address=text(layout,"",16);address.setId(R.id.embeddedServerAddress);address.setTextIsSelectable(true);
        state=text(layout,"",16);
        start=button(layout,"Включить сервер",() -> startServer());
        button(layout,"Остановить сервер",() -> stopService(new Intent(this,EmbeddedServerService.class)));
        button(layout,"Скопировать данные подключения",() -> {
            if(LocalServerAddresses.current(this).isEmpty()) {message("Подключи телефон к Wi-Fi. Адрес появится здесь автоматически.");return;}
            ClipboardManager c=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            c.setPrimaryClip(ClipData.newPlainText("LocalLS SFTP",connectionText(true)));message("Данные подключения скопированы");
        });
        text(layout,"2. Разреши телефон 2",20);
        text(layout,"Без добавленного телефона сервер запускается в ожидании: вход закрыт для всех. На телефоне 2 создай ключ в «Подключение → Адрес и ключ → Ключ доступа» и передай сюда файл .pub. Нажми кнопку ниже и подтверди разрешение.",14);
        key=field(layout,"Разрешённый публичный ключ (.pub)",p.getString("allowed_key",""),false);key.setId(R.id.embeddedServerKey);key.setMinLines(2);key.setMaxLines(3);key.setVerticalScrollBarEnabled(true);
        importKey=button(layout,"Добавить телефон 2 (.pub)",() -> pick(910,"*/*",false));
        text(layout,"Для замены ранее добавленного ключа останови сервер. Закрытый ключ остаётся только на телефоне 2.",14);
        text(layout,"3. Принимай файлы",20);
        text(layout,"Другому устройству доступна только папка LocalLS. Завершённые передачи сохраняются на телефон автоматически, без подтверждения каждого файла. Для другой папки один раз разреши доступ через Android.",14);
        receiveState=text(layout,"",16);
        button(layout,"Папка сохранения",() -> {try{startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION),914);}catch(Exception e){message("Выбор папки недоступен");}});
        button(layout,"Сохранять в «Загрузки / LocalLS»",() -> {if(Build.VERSION.SDK_INT<29){message("На Android 8–9 выбери папку кнопкой выше");return;}getSharedPreferences(EmbeddedServerService.PREFS,0).edit().remove(ReceiveFolder.TREE).apply();render();});
        button(layout,"Повторить сохранение",() -> {if(EmbeddedServerService.running)startService(new Intent(this,EmbeddedServerService.class).setAction(EmbeddedServerService.RETRY_SAVE));else message("Включи сервер для обработки очереди сохранения");});
        button(layout,"Файлы · телефон и мой сервер",() -> startActivity(new Intent(this,FilesActivity.class).putExtra("own_server",true)));
        button(layout,"Скопировать диагностику запуска",() -> {
            ClipboardManager clipboard=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            String report=EmbeddedServerService.diagnostic;
            if(report.isEmpty()) report="LocalLS / SDK "+Build.VERSION.SDK_INT+"\nЭтап: "+EmbeddedServerService.lastStage+
                    "\nrunning="+EmbeddedServerService.running+"; starting="+EmbeddedServerService.starting+"; checkingKey="+checkingKey+
                    "; waitingForClient="+EmbeddedServerService.waitingForClient+"; serviceRequested="+(EmbeddedServerService.launchRequested!=0)+"\n"+EmbeddedServerService.status;
            if(!AutoReceive.diagnostic.isEmpty())report+="\n"+AutoReceive.diagnostic;
            clipboard.setPrimaryClip(ClipData.newPlainText("LocalLS startup",report));message("Диагностика скопирована без ключей и паролей");
        });
        text(layout,"Сервер и передачи работают в фоне с уведомлением, в том числе при выключенном экране. В системных настройках батареи разреши LocalLS работу без ограничений. Принудительная остановка Android завершит службу; после перезагрузки включи сервер вручную. Подключайся с другого устройства по Wi-Fi/VPN; сервер слушает сетевые интерфейсы телефона. Сверь fingerprint перед первым входом.",14);
        button(layout,"Настройки фоновой работы",() -> {try{startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));}catch(Exception e){message("Открой настройки Android → Приложения → LocalLS → Батарея");}});
        button(layout,"Назад",() -> finish());
        ScrollView scroll=new ScrollView(this);scroll.addView(layout);setContentView(scroll);
        render();
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
        if(checkingKey || EmbeddedServerService.running || EmbeddedServerService.starting || EmbeddedServerService.launchRequested!=0)return;
        final int parsed;final String name=user.getText().toString().trim(),pub=key.getText().toString().trim();
        try { parsed=Integer.parseInt(port.getText().toString());if(parsed<1024 || parsed>65535)throw new Exception(); }
        catch(Exception e) {port.setError("Укажи порт от 1024 до 65535");EmbeddedServerService.failure("INPUT",e);render();message(EmbeddedServerService.status);return;}
        if(!ConnectionEndpoint.validUser(name)) {user.setError("Неверное имя SSH-пользователя");EmbeddedServerService.failure("INPUT",new IllegalArgumentException());render();message(EmbeddedServerService.status);return;}
        checkingKey=true;EmbeddedServerService.lastStage="UI_PUBLIC_KEY";EmbeddedServerService.status="Проверка публичного ключа…";render();
        worker.execute(() -> {
            try {String normalized=pub.isEmpty()?"":EmbeddedSftpServer.normalizeAuthorizedKey(pub);
                getSharedPreferences(EmbeddedServerService.PREFS,MODE_PRIVATE).edit().putString("user",name).putInt("port",parsed).putString("allowed_key",normalized).apply();
                ui.post(() -> {if(closed)return;checkingKey=false;key.setText(normalized);try {
                    EmbeddedServerService.start(this);
                    if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)
                        requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},912);
                } catch(Exception e) {EmbeddedServerService.failure("FOREGROUND",e);render();message(EmbeddedServerService.status);} });
            } catch(Exception | LinkageError e) {ui.post(() -> {if(!closed){checkingKey=false;EmbeddedServerService.failure("KEY",e);key.setError("Нужен публичный RSA 3072+ или ECDSA P-256 ключ");render();message(EmbeddedServerService.status);}});}
        });
    }
    private String connectionText(boolean includeAddress) {
        String name=user.getText().toString().trim();
        int selected=8022;try {selected=Integer.parseInt(port.getText().toString().trim());}catch(Exception ignored) { }
        int number=EmbeddedServerService.running?EmbeddedServerService.boundPort:selected;
        StringBuilder result=new StringBuilder();
        if(includeAddress){
            java.util.List<String> values=LocalServerAddresses.current(this);
            if(values.isEmpty())result.append("Подключи телефон к Wi-Fi — адрес появится автоматически.\n");
            else for(String ip:values)result.append("IP: ").append(ip).append("\nsftp://").append(name).append('@').append(ip).append(':').append(number).append("\n");
        }
        result.append("Порт: ").append(number).append(" · Пользователь: ").append(name);
        if(EmbeddedServerService.running)result.append("\nFingerprint: ").append(EmbeddedServerService.fingerprint);
        return result.toString();
    }
    private void importedKey(String normalized) {
        if(closed)return;
        if(EmbeddedServerService.running) {
            if(!EmbeddedServerService.waitingForClient) {message("Для замены ключа останови сервер");return;}
            new AlertDialog.Builder(this).setTitle("Разрешить телефон 2?")
                .setMessage("Выбранный файл должен быть публичным ключом телефона 2. Вход получит только устройство с соответствующим закрытым ключом.")
                .setNegativeButton("Отмена",null).setPositiveButton("Разрешить",(dialog,which) -> {
                    try {EmbeddedServerService.authorizeFirstClient(this,normalized);key.setText(normalized);message("Сохраняем разрешение…");}
                    catch(Exception error){message("Не удалось добавить телефон. Проверь состояние сервера.");}
                }).show();
        } else if(!checkingKey && !EmbeddedServerService.starting && EmbeddedServerService.launchRequested==0) {
            key.setText(normalized);message("Ключ выбран. Нажми «Включить сервер».");
        }
    }
    private void render() {
        if(Build.VERSION.SDK_INT<26)return;
        if(EmbeddedServerService.launchRequested!=0 && android.os.SystemClock.elapsedRealtime()-EmbeddedServerService.launchRequested>10000 && !EmbeddedServerService.starting && !EmbeddedServerService.running) {
            EmbeddedServerService.launchRequested=0;EmbeddedServerService.failure("FOREGROUND",new java.util.concurrent.TimeoutException());
        }
        state.setText(EmbeddedServerService.status);
        if(receiveState!=null){receiveState.setText("Сохранение → "+ReceiveFolder.label(this)+"\n"+AutoReceive.status);StatusBadge.apply(receiveState,AutoReceive.error?StatusBadge.Tone.ERROR:AutoReceive.active?StatusBadge.Tone.WARNING:StatusBadge.Tone.SUCCESS);}
        StatusBadge.apply(state,EmbeddedServerService.running?(EmbeddedServerService.waitingForClient?StatusBadge.Tone.WARNING:StatusBadge.Tone.SUCCESS):
            checkingKey || EmbeddedServerService.starting || EmbeddedServerService.launchRequested!=0?StatusBadge.Tone.WARNING:
            EmbeddedServerService.diagnostic.isEmpty()?StatusBadge.Tone.NEUTRAL:StatusBadge.Tone.ERROR);
        address.setText(connectionText(false));
        wifiAddress.refresh();
        start.setEnabled(!checkingKey && EmbeddedServerService.launchRequested==0 && !EmbeddedServerService.running && !EmbeddedServerService.starting);
        boolean editable=!checkingKey && EmbeddedServerService.launchRequested==0 && !EmbeddedServerService.running && !EmbeddedServerService.starting;
        key.setEnabled(editable);port.setEnabled(editable);user.setEnabled(editable);
        importKey.setEnabled(editable || (EmbeddedServerService.running && EmbeddedServerService.waitingForClient));
    }
    private void message(String value) {if(!closed)Toast.makeText(this,value,Toast.LENGTH_LONG).show();}
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);if(result!=RESULT_OK || data==null)return;
        if(request==914){try{DocumentFiles.grant(this,data);if((data.getFlags()&Intent.FLAG_GRANT_WRITE_URI_PERMISSION)==0)throw new IOException();getSharedPreferences(EmbeddedServerService.PREFS,0).edit().putString(ReceiveFolder.TREE,data.getData().toString()).apply();render();message("Папка сохранена. Подтверждать каждый файл не нужно.");}catch(Exception e){message("Не удалось сохранить доступ к папке");}return;}
        ArrayList<Uri> uris=new ArrayList<>();if(data.getData()!=null)uris.add(data.getData());
        if(data.getClipData()!=null)for(int i=0;i<Math.min(20,data.getClipData().getItemCount());i++)uris.add(data.getClipData().getItemAt(i).getUri());
        worker.execute(() -> {
            try {
                if(request==910 && !uris.isEmpty()) {
                    ByteArrayOutputStream out=new ByteArrayOutputStream();
                    try(InputStream in=getContentResolver().openInputStream(uris.get(0))) {if(in==null)throw new IOException();byte[] b=new byte[1024];int n;
                        while((n=in.read(b))!=-1) {if(out.size()+n>16384)throw new IOException();out.write(b,0,n);}}
                    String publicKey=EmbeddedSftpServer.normalizeAuthorizedKey(out.toString("UTF-8"));
                    ui.post(() -> importedKey(publicKey));
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
