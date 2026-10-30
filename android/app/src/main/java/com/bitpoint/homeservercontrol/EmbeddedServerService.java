package com.bitpoint.homeservercontrol;

import android.app.*;
import android.content.*;
import android.os.*;
import java.io.File;
import java.security.KeyPair;
import java.util.concurrent.*;

/** Explicit, foreground-only lifetime. No auto-start after reboot or process death. */
public class EmbeddedServerService extends Service {
    static final String PREFS="embedded_sftp_settings", STOP="localls.embedded.STOP", AUTHORIZE="localls.embedded.AUTHORIZE_FIRST", AUTHORIZE_QR="localls.embedded.AUTHORIZE_QR";
    static volatile boolean running,starting,waitingForClient;
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
    private static final Semaphore LISTENER_SLOT=new Semaphore(1,true);
    private final Object lifecycle=new Object();
    private boolean ownsListener;
    private final Handler startupWatchdog=new Handler(Looper.getMainLooper());
    private final Runnable renewPower=new Runnable(){public void run(){synchronized(lifecycle){if(!destroyed && wake!=null){wake.acquire(15*60*1000L);startupWatchdog.postDelayed(this,10*60*1000L);}}}};
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private EmbeddedSftpServer server;
    private final ThreadPoolExecutor receiptWorker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(64),new ThreadPoolExecutor.DiscardOldestPolicy());
    static java.util.Set<String> onlineDevices(){EmbeddedSftpServer endpoint=pairingEndpoint;return running&&endpoint!=null?endpoint.onlineClientFingerprints():java.util.Collections.emptySet();}
    private static volatile EmbeddedSftpServer pairingEndpoint;
    static void beginQrPairing(QrPairing invite){
        EmbeddedSftpServer endpoint=pairingEndpoint;
        if(!running||endpoint==null||!fingerprint.equals(invite.pin)||boundPort!=invite.port)throw new IllegalStateException("Сервер изменился. Обнови QR");
        endpoint.beginPairing(invite.nonce,invite.expires);
    }
    static String pendingQrKey(String nonce){EmbeddedSftpServer endpoint=pairingEndpoint;return running&&endpoint!=null?endpoint.pendingPairingKey(nonce):"";}
    static void endQrPairing(String nonce){EmbeddedSftpServer endpoint=pairingEndpoint;if(endpoint!=null)endpoint.endPairing(nonce);}
    static void approveQrPairing(Context c,String nonce,String publicKey){c.startService(new Intent(c,EmbeddedServerService.class).setAction(AUTHORIZE_QR).putExtra("nonce",nonce).putExtra("public_key",publicKey));}
    static void approveOpticalPairing(Context c,String nonce,String publicKey){c.startService(new Intent(c,EmbeddedServerService.class).setAction(AUTHORIZE_QR).putExtra("nonce",nonce).putExtra("public_key",publicKey).putExtra("optical",true));}
    static void revokeDevice(Context c,String publicKey)throws Exception{
        synchronized(ServerDevices.class){
            if(starting)throw new IllegalStateException("Дождись завершения запуска сервера");
            ServerDevices.remove(c,publicKey);EmbeddedSftpServer endpoint=pairingEndpoint;if(endpoint!=null)endpoint.revokeClient(publicKey);
            if(running){waitingForClient=ServerDevices.count(c)==0;status=runningStatus();}
        }
    }
    private volatile AutoReceive autoReceive;
    static final String RETRY_SAVE="localls.embedded.RETRY_SAVE";
    private volatile boolean destroyed;
    private PowerManager.WakeLock wake;
    private android.net.wifi.WifiManager.WifiLock wifi;
    static File share(Context c) { return new File(c.getFilesDir(),"sftp-share"); }
    static boolean launchPending(){
        long requested=launchRequested;
        if(requested!=0&&!running&&!starting&&SystemClock.elapsedRealtime()-requested>10000&&launchRequested==requested){
            launchRequested=0;failure("FOREGROUND",new java.util.concurrent.TimeoutException());
        }
        return starting||launchRequested!=0;
    }
    static void start(Context c) {
        Intent i=new Intent(c,EmbeddedServerService.class);
        launchRequested=SystemClock.elapsedRealtime();lastStage="DISPATCH";status="Запрашиваем запуск службы…";
        try {if(Build.VERSION.SDK_INT>=26)c.startForegroundService(i);else c.startService(i);}
        catch(RuntimeException e) {launchRequested=0;failure("FOREGROUND",e);throw e;}
    }
    static void authorizeFirstClient(Context context,String publicKey) {
        if(!running || !waitingForClient)throw new IllegalStateException("Сначала запусти сервер в ожидании телефона");
        context.startService(new Intent(context,EmbeddedServerService.class).setAction(AUTHORIZE).putExtra("public_key",publicKey));
    }
    private static String runningStatus() {
        return waitingForClient?"Сервер готов, порт "+boundPort+". Ожидает первое устройство. Вход пока закрыт.":
                "Сервер работает, порт "+boundPort+". Доступ устройств по сохранённым ключам.";
    }
    private void refreshAccessState(){synchronized(ServerDevices.class){synchronized(lifecycle){if(destroyed)return;waitingForClient=ServerDevices.count(this)==0;lastStage="RUNNING";status=runningStatus();}}}
    private void approve(Intent intent,int id) {
        if(destroyed || !running || !waitingForClient) {if(!running && !starting)stopSelf(id);return;}
        final String text=intent.getStringExtra("public_key");
        worker.execute(() -> {
            if(destroyed || !running || !waitingForClient)return;
            try {
                String normalized=EmbeddedSftpServer.normalizeAuthorizedKey(text);
                synchronized(ServerDevices.class){server.authorizeFirstClient(EmbeddedSftpServer.authorizedKey(normalized));ServerDevices.add(this,normalized);}
                refreshAccessState();
            } catch(Exception | LinkageError error) {
                synchronized(lifecycle){if(!destroyed){failure("KEY",error);stopSelf();}}
            }
        });
    }
    private void approveQr(Intent intent){
        final String nonce=intent.getStringExtra("nonce"),text=intent.getStringExtra("public_key");
        worker.execute(()->{
            if(destroyed||!running)return;
            boolean applied=false;
            try{
                String normalized=EmbeddedSftpServer.normalizeAuthorizedKey(text);
                synchronized(ServerDevices.class){if(intent.getBooleanExtra("optical",false))server.approveOptical(nonce,normalized);else server.approvePairing(nonce,normalized);applied=true;ServerDevices.add(this,normalized);}
                refreshAccessState();
            }catch(Exception error){synchronized(lifecycle){if(!destroyed){if(applied){failure("KEY",error);stopSelf();}else status=runningStatus()+" QR не подтверждён. Обнови код.";}}}
        });
    }
    @Override public int onStartCommand(Intent intent,int flags,int id) {
        if(intent!=null && AUTHORIZE.equals(intent.getAction())) {approve(intent,id);return START_NOT_STICKY;}
        if(intent!=null && AUTHORIZE_QR.equals(intent.getAction())) {approveQr(intent);return START_NOT_STICKY;}
        if(intent!=null && RETRY_SAVE.equals(intent.getAction())){if(autoReceive!=null)autoReceive.retry();else stopSelf(id);return START_NOT_STICKY;}
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
        p.edit().putBoolean("server_role",true).apply();
        final String user=p.getString("user","localls");
        final int port=p.getInt("port",8022);
        startupWatchdog.postDelayed(() -> {
            if(!destroyed && starting && !running) {failure("TIMEOUT",new TimeoutException());stopSelf();}
        },45000);
        worker.execute(() -> {
            String stage="KEY";boolean keep=false;
            try {
                java.util.List<java.security.PublicKey> allowed=new java.util.ArrayList<>();
                for(ServerDevices.Device device:ServerDevices.list(this))allowed.add(EmbeddedSftpServer.authorizedKey(device.key));
                stage="IDENTITY";
                KeyPair identity=EmbeddedServerIdentity.loadOrCreate(this);
                if(destroyed)return;
                stage="NETWORK";
                // A preceding service may still be closing its listener after onDestroy returned.
                if(!LISTENER_SLOT.tryAcquire(30,TimeUnit.SECONDS))throw new TimeoutException("Previous server is still stopping");
                ownsListener=true;if(destroyed)return;
                EmbeddedSftpServer next=new EmbeddedSftpServer();server=next;autoReceive=new AutoReceive(this);
                if(destroyed)return;
                next.onReceived(autoReceive::received);
                next.onReceipt(receipt->{if(!destroyed)receiptWorker.execute(()->{try(ServerReceiptStore journal=new ServerReceiptStore(this)){journal.record(receipt);}catch(RuntimeException ignored){}});});
                next.telemetry(()->DeviceTelemetry.read(this));
                int openedPort=next.startAuthorized("0.0.0.0",port,user,allowed,identity,share(this).toPath());
                synchronized(lifecycle){
                    if(destroyed)return;
                    stage="POWER";
                    // CPU wake alone does not keep the Wi-Fi radio available with the screen off.
                    // Own both locks only for this explicitly started foreground server.
                    android.net.wifi.WifiManager wlan=getApplicationContext().getSystemService(android.net.wifi.WifiManager.class);
                    if(wlan!=null){wifi=wlan.createWifiLock(android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF,"LocalLS:EmbeddedSftp");wifi.setReferenceCounted(false);wifi.acquire();}
                    PowerManager pm=getSystemService(PowerManager.class);wake=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"LocalLS:EmbeddedSftp");wake.setReferenceCounted(false);wake.acquire(15*60*1000L);startupWatchdog.post(renewPower);
                    boundPort=openedPort;waitingForClient=allowed.isEmpty();fingerprint=EmbeddedSftpServer.fingerprint(identity);
                    lastStage=waitingForClient?"WAITING_CLIENT":"RUNNING";pairingEndpoint=next;running=true;status=runningStatus();keep=true;
                }
            } catch(Exception | LinkageError e) {
                synchronized(lifecycle){if(!destroyed){running=false;failure(stage,e);stopSelf();}}
            } finally {
                synchronized(lifecycle){if(!destroyed)starting=false;}
                if(!keep || destroyed)closeResources();
            }
        });
        return START_NOT_STICKY;
    }
    @Override public void onTimeout(int id,int type) { status="Сервер остановлен: Android ограничил время фоновой службы. Запусти его снова.";stopSelf(); }
    private void closeResources(){
        if(pairingEndpoint==server)pairingEndpoint=null;
        try{if(server!=null)server.close();}catch(Exception ignored){}finally{server=null;}
        try{if(autoReceive!=null)autoReceive.close();}catch(RuntimeException ignored){}finally{autoReceive=null;}
        // The service owns receipt IO too. Existing lifecycle callers awaiting worker termination
        // must not outlive a queued database write (or its Android context/test runtime).
        if(destroyed){receiptWorker.shutdown();try{if(!receiptWorker.awaitTermination(5,TimeUnit.SECONDS))receiptWorker.shutdownNow();}catch(InterruptedException error){receiptWorker.shutdownNow();Thread.currentThread().interrupt();}}
        synchronized(lifecycle){
            startupWatchdog.removeCallbacks(renewPower);
            try{if(wifi!=null && wifi.isHeld())wifi.release();}catch(RuntimeException ignored){}finally{wifi=null;}
            try{if(wake!=null && wake.isHeld())wake.release();}catch(RuntimeException ignored){}finally{wake=null;}
        }
        if(ownsListener){ownsListener=false;LISTENER_SLOT.release();}
    }
    @Override public void onDestroy() {
        synchronized(lifecycle){
            destroyed=true;if(autoReceive!=null)autoReceive.close();startupWatchdog.removeCallbacksAndMessages(null);running=false;starting=false;waitingForClient=false;
            if(status.startsWith("Сервер работает") || status.startsWith("Сервер готов") || status.startsWith("Запуск"))status="Сервер выключен";
        }
        // The worker also sees resources whose constructors finished after destruction began.
        worker.execute(this::closeResources);worker.shutdown();receiptWorker.shutdown();stopForeground(true);super.onDestroy();
    }
    @Override public IBinder onBind(Intent i) { return null; }
}
