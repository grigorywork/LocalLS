package com.bitpoint.homeservercontrol;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.webkit.MimeTypeMap;
import java.io.*;
import java.util.LinkedHashSet;
import java.util.UUID;
import java.util.concurrent.*;

/** Owns local export work independently of any activity. No task is resumed after process death. */
public class LocalFileExportService extends Service {
    static final class State {
        final boolean active,success;final long copied,total;final String text,diagnostic;
        State(boolean active,boolean success,long copied,long total,String text,String diagnostic) {
            this.active=active;this.success=success;this.copied=copied;this.total=total;this.text=text;this.diagnostic=diagnostic;
        }
    }
    static volatile State state=new State(false,false,0,0,"Нажми на принятый файл, чтобы сохранить его на телефон.","");
    private static final int NOTICE=2212;
    private static final String REQUEST_ID="export_request_id";
    private static final Object REQUEST_LOCK=new Object();
    private static final LinkedHashSet<String> completedRequests=new LinkedHashSet<>();
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Object lifecycle=new Object();
    private boolean destroyed,busy,finished=true;
    private int latestId;
    private long operation;
    private PowerManager.WakeLock wake;
    static synchronized void start(Context context,String relative,Uri destination,int grants) {
        if(state.active)throw new IllegalStateException("Сохранение уже идёт");
        state=new State(true,false,0,0,"Подготовка сохранения…","");
        Intent intent=new Intent(context,LocalFileExportService.class).putExtra("relative",relative).putExtra(REQUEST_ID,UUID.randomUUID().toString());
        if(destination!=null)intent.setData(destination).addFlags(grants & (Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION));
        try {if(Build.VERSION.SDK_INT>=26)context.startForegroundService(intent);else context.startService(intent);}
        catch(RuntimeException error) {fail("FOREGROUND",error);throw error;}
    }
    private static void fail(String stage,Throwable error) {
        State old=state;state=new State(false,false,old.copied,old.total,LocalExportFailure.hint(stage,error),LocalExportFailure.report(stage,error));
    }
    @Override public int onStartCommand(Intent intent,int flags,int id) {
        synchronized(lifecycle){return accept(intent,id);}
    }
    private int accept(Intent intent,int id) {
        if(destroyed)return START_NOT_STICKY;
        latestId=id;
        // This is a single-export service. Extra deliveries cannot replace its accepted task.
        if(busy)return START_NOT_STICKY;
        if(intent==null || intent.getStringExtra("relative")==null) {finished=true;state=new State(false,false,0,0,"Сохранение не запущено.","");stopSelf(id);return START_NOT_STICKY;}
        String supplied=intent.getStringExtra(REQUEST_ID);
        final String requestId=supplied==null || supplied.isEmpty()?UUID.randomUUID().toString():supplied;
        synchronized(REQUEST_LOCK){if(completedRequests.contains(requestId)){stopSelf(id);return START_NOT_STICKY;}}
        try {
            NotificationManager manager=getSystemService(NotificationManager.class);
            if(Build.VERSION.SDK_INT>=26)manager.createNotificationChannel(new NotificationChannel("local_export","Сохранение принятых файлов",NotificationManager.IMPORTANCE_LOW));
            startForeground(NOTICE,notification("Подготовка сохранения…"));
            if(wake==null){PowerManager power=getSystemService(PowerManager.class);wake=power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"LocalLS:LocalExport");wake.setReferenceCounted(false);}
            wake.acquire(30*60*1000L);
        } catch(RuntimeException error) {fail("FOREGROUND",error);finished=true;releasePower();stopSelf(id);return START_NOT_STICKY;}
        finished=false;busy=true;final long accepted=++operation;
        state=new State(true,false,0,0,"Подготовка сохранения…","");
        final String relative=intent.getStringExtra("relative");final Uri destination=intent.getData();
        worker.execute(() -> {
            State completed;
            try {
                File root=EmbeddedServerService.share(this);
                File source=SharedFolderAccess.within(root,new File(root,relative));
                String name=source.getName(),extension=MimeTypeMap.getFileExtensionFromUrl(name.replace(" ","_")).toLowerCase(java.util.Locale.ROOT);
                String mime=MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);if(mime==null)mime="application/octet-stream";
                LocalExportDestination target=new LocalExportDestination(this,name,mime,destination);
                final long[] last={0};
                long count=LocalFileExporter.copy(root,relative,target,(copied,total) -> {
                    synchronized(lifecycle){
                    if(destroyed || accepted!=operation)throw new java.util.concurrent.CancellationException();
                    long now=SystemClock.elapsedRealtime();
                    if(copied==0 || copied==total || now-last[0]>=250) {
                        last[0]=now;String progress="Сохранение: "+format(copied)+" / "+format(total);
                        state=new State(true,false,copied,total,progress,"");
                        if(copied==0 || copied==total || now%1000<250)main.post(() -> {synchronized(lifecycle){if(!destroyed && busy && accepted==operation)getSystemService(NotificationManager.class).notify(NOTICE,notification(progress));}});
                    }
                    }
                });
                completed=new State(false,true,count,count,"Файл сохранён на телефон\n"+target.location(),"");
            } catch(LocalFileExporter.Failure error) {
                State old=state;completed=new State(false,false,old.copied,old.total,LocalExportFailure.hint(error.stage,error),LocalExportFailure.report(error.stage,error));
            } catch(Exception error) {
                State old=state;completed=new State(false,false,old.copied,old.total,LocalExportFailure.hint("SOURCE",error),LocalExportFailure.report("SOURCE",error));
            }
            final State result=completed;
            main.post(() -> {synchronized(lifecycle){if(!destroyed && accepted==operation){
                finished=true;busy=false;state=result;releasePower();
                if(result.success)synchronized(REQUEST_LOCK){completedRequests.add(requestId);while(completedRequests.size()>256)completedRequests.remove(completedRequests.iterator().next());}
                // Include ignored duplicate/empty deliveries so Android can actually stop this instance.
                stopSelf(latestId);
            }}});
        });
        return START_NOT_STICKY;
    }
    static String format(long bytes) {return String.format(java.util.Locale.getDefault(),"%.1f МБ",bytes/(1024d*1024d));}
    private Notification notification(String text) {
        Intent open=new Intent(this,EmbeddedFilesActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pending=PendingIntent.getActivity(this,84,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,"local_export"):new Notification.Builder(this);
        return builder.setSmallIcon(com.bitpoint.homeservercontrol.ui.R.drawable.ic_cloud_notification)
                .setContentTitle("LocalLS · сохранение файла").setContentText(text).setContentIntent(pending).setOngoing(true).setCategory(Notification.CATEGORY_PROGRESS).build();
    }
    private void releasePower(){if(wake!=null && wake.isHeld())wake.release();}
    @Override public void onTimeout(int id,int type) {synchronized(lifecycle){destroyed=true;operation++;if(!finished)fail("STOPPED",new InterruptedIOException());finished=true;busy=false;worker.shutdownNow();releasePower();stopSelf();}}
    @Override public void onDestroy() {
        synchronized(lifecycle){
        destroyed=true;if(!finished)fail("STOPPED",new InterruptedIOException());
        operation++;busy=false;worker.shutdownNow();main.removeCallbacksAndMessages(null);releasePower();
        }
        stopForeground(true);super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) {return null;}
}
