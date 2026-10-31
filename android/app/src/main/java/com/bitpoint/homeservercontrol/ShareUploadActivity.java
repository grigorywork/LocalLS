package com.bitpoint.homeservercontrol;
import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

/** Visible share ingress; queues against the saved pinned endpoint without editing it. */
public class ShareUploadActivity extends BaseActivity {
 private final ExecutorService worker=Executors.newSingleThreadExecutor();private final Handler ui=new Handler(Looper.getMainLooper());private TextView status;private boolean started;private volatile boolean closed;private FileConnection route;private ArrayList<Uri> files;
 @Override protected void onCreate(Bundle state){super.onCreate(state);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(24,24,24,24);status=new TextView(this);status.setTextSize(20);root.addView(status);Button send=new Button(this);send.setText("Отправить на сервер");root.addView(send);Button back=new Button(this);back.setText("Закрыть");root.addView(back);back.setOnClickListener(v->finish());setContentView(root);
  files=FileAttachments.from(getIntent());route=FileConnection.current(this);status.setText("На сервер "+route.host+":"+route.port+"\nФайлов: "+files.size()+"\nПапка приёма сервера: «Загрузки / LocalLS» или выбранная владельцем папка.");
  boolean ready=!files.isEmpty()&&!route.pin.isEmpty()&&SshKeys.hasStoredKey(this,route.host,route.port,route.user)&&!DeviceRole.serverOnly(this);send.setEnabled(ready);send.setOnClickListener(v->{send.setEnabled(false);submit();});
  if(!ready)status.setText("Отправка не начата. Привяжи сервер по ключу и выбери файлы через «Поделиться → LocalLS».");
  else if(getSharedPreferences(ServerConfig.PREFS,0).getBoolean(ShareOutbox.AUTO,false)){send.setEnabled(false);submit();}
 }
 private void submit(){if(started)return;started=true;status.setText("Сохраняем файлы в очередь…");worker.execute(()->{
  List<FileNodes.Node> nodes=null;boolean queued=false;try{
   nodes=ShareOutbox.stage(this,files,text->ui.post(()->{if(!closed)status.setText(text);}));
   if(closed||Thread.currentThread().isInterrupted())throw new java.io.InterruptedIOException();
   if(!route.matches(FileConnection.current(this).bundle()))throw new java.io.IOException("Подключение изменилось");
   TransferForegroundService.enqueue(this,TransferForegroundService.UPLOAD,nodes,".",null,route);queued=true;
   ui.post(()->{if(!closed)status.setText("Файлы добавлены в очередь сервера. Передача продолжится в фоне. При недоступном сервере задание сохранится для повтора.");});
  }catch(Exception e){
   if(nodes!=null)try{for(var job:new TransferJournal(this).list()){var sources=job.getJSONArray("sources");for(int n=0;n<sources.length();n++)for(var node:nodes)if(node.path.equals(sources.getJSONObject(n).optString("path")))queued=true;}}catch(Exception ignored){}
   ui.post(()->{if(!closed)status.setText("Не удалось подготовить отправку. Исходные файлы сохранены. Проверь доступ к ним, подключение и свободное место.");});}
  finally{if(!queued&&nodes!=null)ShareOutbox.clean(this,nodes);}
 });}
 @Override protected void onDestroy(){closed=true;worker.shutdownNow();ui.removeCallbacksAndMessages(null);super.onDestroy();}
}
