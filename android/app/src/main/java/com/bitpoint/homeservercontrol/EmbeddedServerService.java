package com.bitpoint.homeservercontrol;

import android.app.*;
import android.content.*;
import android.os.*;
import java.io.File;
import java.security.KeyPair;
import java.util.concurrent.*;

/** Explicit, foreground-only lifetime. No auto-start after reboot or process death. */
public class EmbeddedServerService extends Service {
    static final String PREFS="embedded_sftp_settings", STOP="localls.embedded.STOP";
    static volatile boolean running,starting;
    static volatile String status="Сервер выключен",fingerprint="";
    static volatile int boundPort;
    static volatile long launchRequested;
    static volatile String lastStage="IDLE";
    static volatile String diagnostic="";
    static void failure(String stage,Throwable error) {
        lastStage=stage;
        diagnostic="LocalLS Android / SDK "+Build.VERSION.SDK_INT+"\n"+ServerStartupFailure.report(stage,error);
        status=ServerStartupFailure.hint(stage,error)+"\nКод: "+stage+" / "+error.getClass().getSimpleName();
    }
    private final Handler startupWatchdog=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private EmbeddedSftpServer server;
    private volatile boolean destroyed;
    private PowerManager.WakeLock wake;
    static File share(Context c) { return new File(c.getFilesDir(),"sftp-share"); }
    static void start(Context c) {
        Intent i=new Intent(c,EmbeddedServerService.class);
        launchRequested=SystemClock.elapsedRealtime();lastStage="DISPATCH";status="Запрашиваем запуск службы…";
        try {if(Build.VERSION.SDK_INT>=26)c.startForegroundService(i);else c.startService(i);}
        catch(RuntimeException e) {launchRequested=0;failure("FOREGROUND",e);throw e;}
    }
    @Override public int onStartCommand(Intent intent,int flags,int id) {
        launchRequested=0;lastStage="SERVICE_RECEIVED";
        if(intent!=null && STOP.equals(intent.getAction())) { stopSelf();return START_NOT_STICKY; }
        if(Build.VERSION.SDK_INT<26) { status="Встроенный сервер требует Android 8 или новее";stopSelf();return START_NOT_STICKY; }
        if(running || starting) return START_NOT_STICKY;
        try {
        NotificationManager manager=getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("embedded_sftp","Файловый сервер LocalLS",NotificationManager.IMPORTANCE_LOW));
        PendingIntent open=PendingIntent.getActivity(this,81,new Intent(this,EmbeddedServerActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop=PendingIntent.getService(this,82,new Intent(this,EmbeddedServerService.class).setAction(STOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        startForeground(2210,new Notification.Builder(this,"embedded_sftp")
                .setSmallIcon(com.bitpoint.homeservercontrol.ui.R.drawable.ic_cloud_notification).setContentTitle("SFTP-сервер LocalLS")
                .setContentText("Доступ по ключу к папке LocalLS. Нажми, чтобы открыть управление.")
                .setContentIntent(open).setOngoing(true).addAction(0,"Остановить",stop).build());
        } catch(RuntimeException e) {failure("FOREGROUND",e);stopSelf();return START_NOT_STICKY;}
        starting=true;diagnostic="";lastStage="SERVICE_STARTING";status="Запуск сервера…";
        SharedPreferences p=getSharedPreferences(PREFS,MODE_PRIVATE);
        final String publicKey=p.getString("allowed_key", ""), user=p.getString("user","localls");
        final int port=p.getInt("port",8022);
        startupWatchdog.postDelayed(() -> {
            if(!destroyed && starting && !running) {failure("TIMEOUT",new TimeoutException());stopSelf();}
        },45000);
        worker.execute(() -> {
            String stage="KEY";
            try {
                java.security.PublicKey allowed=EmbeddedSftpServer.authorizedKey(publicKey);
                stage="IDENTITY";
                KeyPair identity=EmbeddedServerIdentity.loadOrCreate(this);
                if(destroyed)return;
                stage="NETWORK";
                EmbeddedSftpServer next=new EmbeddedSftpServer();
                boundPort=next.start("0.0.0.0",port,user,allowed,identity,share(this).toPath());server=next;
                if(destroyed) { next.close();return; }
                fingerprint=EmbeddedSftpServer.fingerprint(identity);
                stage="POWER";
                PowerManager pm=getSystemService(PowerManager.class);wake=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"LocalLS:EmbeddedSftp");wake.acquire(6*60*60*1000L);
                lastStage="RUNNING";running=true;
                if(destroyed) { running=false;next.close();return; }
                status="Сервер работает, порт "+boundPort+". Вход только по ключу; папка / — файлы LocalLS.";
            } catch(Exception | LinkageError e) {
                if(!destroyed) {running=false;failure(stage,e);stopSelf();}
            } finally { if(!destroyed)starting=false; }
        });
        return START_NOT_STICKY;
    }
    @Override public void onTimeout(int id,int type) { status="Сервер остановлен: Android ограничил время фоновой службы. Запусти его снова.";stopSelf(); }
    @Override public void onDestroy() {
        destroyed=true;startupWatchdog.removeCallbacksAndMessages(null);running=false;starting=false;
        if(status.startsWith("Сервер работает") || status.startsWith("Запуск"))status="Сервер выключен";
        worker.execute(() -> { try { if(server!=null)server.close(); } catch(Exception ignored) { }
            if(wake!=null && wake.isHeld())wake.release(); });
        worker.shutdown();stopForeground(true);super.onDestroy();
    }
    @Override public IBinder onBind(Intent i) { return null; }
}
