package com.bitpoint.homeservercontrol;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** The service owns the queue, streams and pinned sessions; closing an Activity never cancels work. */
public class TransferForegroundService extends Service {
    static final String UPLOAD="upload",DOWNLOAD="download",IMPORT="import",SAVE="save",CANCEL="cancel",CLEAR="clear";
    static volatile boolean clearing;
    private static TransferForegroundService clearOwner;
    static final class State {
        final boolean active,error;final String text,diagnostic,requestId,sourceKey;final long bytes,total;final int queued;
        State(boolean a,boolean e,String t,String d,long b,long n,int q){this(a,e,t,d,b,n,q,"","");}
        State(boolean a,boolean e,String t,String d,long b,long n,int q,String id,String key){active=a;error=e;text=t;diagnostic=d;bytes=b;total=n;queued=q;requestId=id;sourceKey=key;}
    }
    private static final String REQUEST_ID="request_id";
    private static final Object REQUEST_LOCK=new Object();
    private static final LinkedHashSet<String> completedRequests=new LinkedHashSet<>();
    private static final Map<String,TransferForegroundService> writers=new HashMap<>();
    private void acquireWriter(String id)throws InterruptedException{synchronized(REQUEST_LOCK){
        while(writers.containsKey(id))REQUEST_LOCK.wait();
        if(Thread.currentThread().isInterrupted())throw new InterruptedException();writers.put(id,this);
    }}
    private void releaseWriter(String id){synchronized(REQUEST_LOCK){if(writers.get(id)==this){writers.remove(id);REQUEST_LOCK.notifyAll();}}}
    private static final class FailedJob {
        final Intent request;final String diagnostic;
        FailedJob(Intent request,String diagnostic){this.request=new Intent(request);this.diagnostic=diagnostic;}
    }
    private static volatile FailedJob lastFailed;
    private final Set<String> inFlight=new HashSet<>();
    private boolean claim(String id){synchronized(REQUEST_LOCK){return !completedRequests.contains(id) && inFlight.add(id);}}
    private void release(String id){synchronized(REQUEST_LOCK){inFlight.remove(id);}}
    private static void failed(Intent request,String diagnostic){synchronized(REQUEST_LOCK){lastFailed=new FailedJob(request,diagnostic);}}
    private static FailedJob succeeded(String id){synchronized(REQUEST_LOCK){
        completedRequests.add(id);while(completedRequests.size()>256)completedRequests.remove(completedRequests.iterator().next());
        if(lastFailed!=null && id.equals(lastFailed.request.getStringExtra(REQUEST_ID)))lastFailed=null;
        return lastFailed;
    }}
    static volatile String currentName="Подготовка передачи";
    static volatile State state=new State(false,false,"Передач пока нет","",0,0,0);
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final AtomicInteger queued=new AtomicInteger();
    private final Object lifecycle=new Object();
    private boolean destroyed;
    private long commandRevision;
    private volatile int generation,latestId;
    private TransferPowerGuard power;
    private TransferJournal journal;
    private long lastProgress;
    private static final int NOTIFICATION=2208;
    static void enqueue(Context c,String action,List<FileNodes.Node> sources,String remotePath,FileNodes.Node destination){
        enqueue(c,action,sources,remotePath,destination,FileConnection.current(c));
    }
    static void enqueue(Context c,String action,List<FileNodes.Node> sources,String remotePath,FileNodes.Node destination,FileConnection endpoint){
        enqueue(c,action,sources,remotePath,destination,endpoint,TransferJournal.KEEP,false);
    }
    static void enqueue(Context c,String action,List<FileNodes.Node> sources,String remotePath,FileNodes.Node destination,FileConnection endpoint,String conflict,boolean verify){
        if(sources.isEmpty() || sources.size()>64)throw new IllegalArgumentException("Выбери от 1 до 64 элементов");
        Intent i=new Intent(c,TransferForegroundService.class).setAction(action).putExtra(REQUEST_ID,UUID.randomUUID().toString());
        ArrayList<Bundle> nodes=new ArrayList<>();for(FileNodes.Node n:sources)nodes.add(n.bundle());i.putParcelableArrayListExtra("sources",nodes);
        if(destination!=null)i.putExtra("destination",destination.bundle());i.putExtra("remote",remotePath);
        i.putExtras(endpoint.bundle());
        i.putExtra("conflict",conflict).putExtra("verify",verify);
        if((SAVE.equals(action)||DOWNLOAD.equals(action))&&!i.hasExtra("destination"))i.putExtra("destination",FileNodes.phoneRoot(c).bundle());
        ArrayList<Uri> grants=new ArrayList<>();for(FileNodes.Node n:sources)if("doc".equals(n.kind))grants.add(Uri.parse(n.path));
        if(!grants.isEmpty()){ClipData clip=ClipData.newRawUri("LocalLS files",grants.get(0));for(int n=1;n<grants.size();n++)clip.addItem(new ClipData.Item(grants.get(n)));i.setClipData(clip);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);}
        synchronized(REQUEST_LOCK){
            if(clearing)throw new IllegalStateException("Подожди окончания очистки очереди");
            try{new TransferJournal(c).ensure(i);}catch(Exception error){throw new IllegalStateException("Не удалось сохранить очередь",error);}
            launch(c,i);
        }
    }
    private static void launch(Context c,Intent request){if(Build.VERSION.SDK_INT>=26)c.startForegroundService(request);else c.startService(request);}
    static void resume(Context c,String id)throws Exception{synchronized(REQUEST_LOCK){if(clearing)throw new IllegalStateException("Очищаем список");launch(c,new TransferJournal(c).request(id));}}
    static void restore(Context c){
        if(state.active)return;
        try{int pending=0;TransferJournal journal=new TransferJournal(c);for(org.json.JSONObject j:journal.list()){
            String status=j.optString("status");if("done".equals(status))ShareOutbox.completed(c,journal.request(j.getString("id")));if(!"done".equals(status)&&!"cancelled".equals(status)){
                pending++;if(lastFailed==null)lastFailed=new FailedJob(journal.request(j.getString("id")),j.optString("message"));
            }
        }if(pending>0)state=new State(false,true,"Сохранено незавершённых заданий: "+pending+". Открой очередь для продолжения.","",0,0,pending);
        }catch(Exception e){state=new State(false,true,"Не удалось прочитать сохранённую очередь. Данные очереди сохранены.","QUEUE_READ",0,0,0);}
    }
    static void retry(Context c){FailedJob previous=lastFailed;if(previous!=null){Intent i=new Intent(previous.request);if(Build.VERSION.SDK_INT>=26)c.startForegroundService(i);else c.startService(i);}}
    static boolean canRetry(){return lastFailed!=null;}
    static void stop(Context c){c.stopService(new Intent(c,TransferForegroundService.class));}
    static void cancel(Context c){c.startService(new Intent(c,TransferForegroundService.class).setAction(CANCEL));}
    static void clear(Context c){c.startService(new Intent(c,TransferForegroundService.class).setAction(CLEAR));}
    @Override public void onCreate(){super.onCreate();journal=new TransferJournal(this);power=new TransferPowerGuard(this);if(Build.VERSION.SDK_INT>=26)getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel("file_transfers","Передача файлов",NotificationManager.IMPORTANCE_LOW));}
    private Notification notification(String text){
        PendingIntent open=PendingIntent.getActivity(this,60,new Intent(this,FilesActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent cancel=PendingIntent.getService(this,61,new Intent(this,TransferForegroundService.class).setAction(CANCEL),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,"file_transfers"):new Notification.Builder(this);
        return b.setSmallIcon(com.bitpoint.homeservercontrol.ui.R.drawable.ic_cloud_notification).setContentTitle("LocalLS · передача файлов").setContentText(text).setContentIntent(open).setOngoing(true).addAction(0,"Отменить очередь",cancel).build();
    }
    @Override public int onStartCommand(Intent intent,int flags,int id){
        synchronized(lifecycle){return startCommand(intent,id);}
    }
    private int startCommand(Intent intent,int id){
        if(destroyed)return START_NOT_STICKY;
        commandRevision++;
        latestId=id;
        if(intent==null){stopSelf(id);return START_NOT_STICKY;}
        if(CANCEL.equals(intent.getAction())||CLEAR.equals(intent.getAction())){
            boolean clear=CLEAR.equals(intent.getAction());if(clearing)return START_NOT_STICKY;
            if(clear)synchronized(REQUEST_LOCK){clearing=true;clearOwner=this;}
            try{journal.cancelAll();synchronized(REQUEST_LOCK){lastFailed=null;}}
            catch(Exception e){if(clear)synchronized(REQUEST_LOCK){clearing=false;clearOwner=null;}state=new State(queued.get()>0,true,"Не удалось сохранить отмену. Журнал сохранён.","QUEUE_CANCEL",0,0,queued.get());if(queued.get()==0)stopSelf(id);return START_NOT_STICKY;}
            generation++;int count=queued.get();
            state=new State(count>0,false,clear?"Очищаем список…":count>0?"Останавливаем очередь…":"Очередь пуста.","",0,0,count);
            if(clear){
                worker.execute(()->{
                    try{
                        // A previous service instance may still be closing a provider stream.
                        synchronized(REQUEST_LOCK){while(!writers.isEmpty())REQUEST_LOCK.wait();}
                        synchronized(lifecycle){if(destroyed)return;journal.clearAll();state=new State(false,false,"Очередь пуста.","",0,0,0);}
                    }
                    catch(Exception e){state=new State(false,true,"Не удалось полностью очистить список. Повтори очистку.","QUEUE_CLEAR",0,0,0);}
                    finally{synchronized(REQUEST_LOCK){if(clearOwner==this){clearing=false;clearOwner=null;}}new Handler(getMainLooper()).post(()->{synchronized(lifecycle){if(!destroyed&&queued.get()==0)stopSelf(latestId);}});}
                });
            }else if(count==0)stopSelf(id);
            return START_NOT_STICKY;
        }
        if(clearing)return START_NOT_STICKY;
        Intent candidate=new Intent(intent);
        String supplied=candidate.getStringExtra(REQUEST_ID);
        final String requestId=supplied==null || supplied.isEmpty()?UUID.randomUUID().toString():supplied;
        candidate.putExtra(REQUEST_ID,requestId);
        // The destination belongs to this request, including retries, not to future UI choices.
        if((SAVE.equals(candidate.getAction()) || DOWNLOAD.equals(candidate.getAction())) && !candidate.hasExtra("destination"))
            candidate.putExtra("destination",FileNodes.phoneRoot(this).bundle());
        final Intent request;
        try{
            if(!journal.ensure(candidate)){if(queued.get()==0)stopSelf(id);return START_NOT_STICKY;}
            request=journal.request(requestId);
        }catch(Exception error){state=new State(false,true,"Не удалось сохранить задание. Передача не начата.",LocalExportFailure.report("DESTINATION",error),0,0,queued.get());stopSelf(id);return START_NOT_STICKY;}
        try{startForeground(NOTIFICATION,notification("Подготовка файлов и папок…"));}
        catch(RuntimeException error){
            String diagnostic=LocalExportFailure.report("FOREGROUND",error);failed(request,diagnostic);
            try{journal.status(requestId,"failed",diagnostic);}catch(Exception ignored){}
            state=new State(false,true,"Android не разрешил запустить фоновую передачу. Открой LocalLS и повтори.",diagnostic,0,0,0);
            stopSelf(id);return START_NOT_STICKY;
        }
        // Repeated retry taps retain the id; deliberate new copies receive new ids.
        if(!claim(requestId)){if(queued.get()==0)stopSelf(id);return START_NOT_STICKY;}
        int count=queued.incrementAndGet(),batch=generation;
        state=new State(true,false,"В очереди: "+count,"",0,0,count);
        worker.execute(()->{
            Object telemetry=new Object();boolean success=false,claimReleased=false,ownsWriter=false;
            try{
                // A provider may ignore interruption after onDestroy. Its cleanup must finish
                // before a replacement Service reuses the same journal/output identifiers.
                acquireWriter(requestId);ownsWriter=true;
                synchronized(lifecycle){
                    if(destroyed || batch!=generation)return;
                    journal.status(requestId,"running","Подготовка передачи");
                    currentName="Подготовка передачи";power.acquire();state=new State(true,false,"Подготовка передачи…","",0,0,queued.get());
                }
                TransferSpeedStore.begin(telemetry);new Job(request,batch,telemetry).run();
                synchronized(lifecycle){if(!destroyed && batch==generation){
                    journal.status(requestId,"done","Готово");journal.cleanParts(requestId);ShareOutbox.completed(this,request);
                    success=true;FailedJob remaining=succeeded(requestId);int pending=queued.get()-1;
                    state=new State(pending>0,remaining!=null,pending>0?"Задание скопировано. Осталось в очереди: "+pending:remaining==null?"Готово. Файлы и папки скопированы.":"Задание скопировано. Есть неудачная передача для повтора.",remaining==null?"":remaining.diagnostic,0,0,pending);
                }}
                if(success){
                    try{
                        TransferSpeedStore.Snapshot completedSpeed=TransferSpeedStore.snapshot();
                        if(completedSpeed.jobPeak>0)MetricHistoryStore.addSpeed(this,completedSpeed.jobPeak);
                        TransferLogStore.add(this,"Копирование файлов и папок","Готово");
                    }catch(RuntimeException ignored){/* Telemetry must not turn a completed copy into a retry. */}
                }
            }catch(Exception | LinkageError error){
                boolean reported=false;
                synchronized(lifecycle){if(!destroyed && batch==generation){
                    String diagnostic=LocalExportFailure.report("TRANSFER",error);
                    try{journal.status(requestId,"failed",error instanceof IOException?error.getMessage():"Передача не завершена");}catch(Exception persist){error.addSuppressed(persist);}
                    synchronized(REQUEST_LOCK){
                        failed(request,diagnostic);
                        int pending=queued.get()-1;
                        state=new State(pending>0,true,"Передача не завершена. Исходники сохранены. Проверь сеть, папку и свободное место."+(pending>0?" Осталось в очереди: "+pending:""),diagnostic,0,0,pending);
                        inFlight.remove(requestId);claimReleased=true;
                    }
                    reported=true;
                }}
                if(reported)try{TransferLogStore.add(this,"Копирование файлов и папок","Ошибка. Можно повторить.");}catch(RuntimeException ignored){}
            }finally{
                if(ownsWriter)releaseWriter(requestId);
                if(!claimReleased)release(requestId);
                TransferSpeedStore.end(telemetry,success?"Завершено":batch==generation?"Ошибка":"Отменено");
                synchronized(lifecycle){if(queued.decrementAndGet()==0){
                    power.release();
                    if(!destroyed){
                        if(batch!=generation)state=new State(false,false,"Очередь отменена. Завершённые файлы сохранены.","",0,0,0);
                        final long finishedRevision=commandRevision;final int finishedId=latestId;
                        new Handler(getMainLooper()).post(()->{synchronized(lifecycle){
                            if(!destroyed && !clearing && queued.get()==0 && finishedRevision==commandRevision)stopSelf(finishedId);
                        }});
                    }
                }}
            }
        });
        return START_NOT_STICKY;
    }
    private final class Job {
        final Intent request;final int batch;final Object telemetry;DurableTransferJob durable;long payloadBytes;int visited,completed;SftpClient.TreeSession remote;
        Job(Intent i,int b,Object t){request=i;batch=b;telemetry=t;}
        void check()throws IOException{synchronized(lifecycle){if(destroyed || batch!=generation || Thread.currentThread().isInterrupted())throw new InterruptedIOException();power.acquire();}}
        void visit(int depth)throws IOException{check();if(depth>48 || ++visited>10000)throw new IOException("Слишком большая папка: максимум 10000 элементов и 48 уровней");}
        void progress(long bytes,long total){
            synchronized(lifecycle){
            if(destroyed || batch!=generation)return;
            long now=SystemClock.elapsedRealtime();
            if(now-lastProgress<200 && bytes!=total)return;lastProgress=now;
            String text="Передача: "+LocalFileExportService.format(bytes)+(total>0?" / "+LocalFileExportService.format(total):"");
            state=new State(true,false,text,"",bytes,total,queued.get(),request.getStringExtra(REQUEST_ID),durable==null?"":durable.sourceKey);getSystemService(NotificationManager.class).notify(NOTIFICATION,notification(text));
            }
        }
        void transferred(long bytes){synchronized(lifecycle){
            if(destroyed || batch!=generation || bytes<=0)return;
            payloadBytes=Math.addExact(payloadBytes,bytes);
            TransferSpeedStore.recordBytes(telemetry,payloadBytes);
        }}
        boolean remoteProgress(long n,long total){try{check();progress(n,total);return true;}catch(IOException e){return false;}}
        void run()throws Exception{durable=new DurableTransferJob(TransferForegroundService.this,request,this::check,this::progress,this::transferred);durable.run();}
    }

    @Override public IBinder onBind(Intent i){return null;}
    @Override public void onTimeout(int id,int type){synchronized(lifecycle){generation++;stopSelf();}}
    @Override public void onDestroy(){synchronized(lifecycle){destroyed=true;generation++;worker.shutdownNow();synchronized(REQUEST_LOCK){for(String key:inFlight)try{journal.pause(key);}catch(Exception ignored){}if(clearOwner==this){clearing=false;clearOwner=null;}}if(state.active)state=new State(false,true,"Передача остановлена Android. Очередь и завершённые файлы сохранены.","SERVICE_STOPPED",0,0,0);power.release();}stopForeground(true);super.onDestroy();}
}
