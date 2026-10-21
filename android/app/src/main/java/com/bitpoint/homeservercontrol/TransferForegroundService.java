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
    static final String UPLOAD="upload",DOWNLOAD="download",IMPORT="import",SAVE="save",CANCEL="cancel";
    static final class State {
        final boolean active,error;final String text,diagnostic;final long bytes,total;final int queued;
        State(boolean a,boolean e,String t,String d,long b,long n,int q){active=a;error=e;text=t;diagnostic=d;bytes=b;total=n;queued=q;}
    }
    private static final String REQUEST_ID="request_id";
    private static final Object REQUEST_LOCK=new Object();
    private static final LinkedHashSet<String> completedRequests=new LinkedHashSet<>();
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
    static volatile State state=new State(false,false,"Передач пока нет","",0,0,0);
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final AtomicInteger queued=new AtomicInteger();
    private volatile int generation,latestId;
    private TransferPowerGuard power;
    private long lastProgress;
    private static final int NOTIFICATION=2208;
    static void enqueue(Context c,String action,List<FileNodes.Node> sources,String remotePath,FileNodes.Node destination){
        enqueue(c,action,sources,remotePath,destination,FileConnection.current(c));
    }
    static void enqueue(Context c,String action,List<FileNodes.Node> sources,String remotePath,FileNodes.Node destination,FileConnection endpoint){
        if(sources.isEmpty() || sources.size()>64)throw new IllegalArgumentException("Выбери от 1 до 64 элементов");
        Intent i=new Intent(c,TransferForegroundService.class).setAction(action).putExtra(REQUEST_ID,UUID.randomUUID().toString());
        ArrayList<Bundle> nodes=new ArrayList<>();for(FileNodes.Node n:sources)nodes.add(n.bundle());i.putParcelableArrayListExtra("sources",nodes);
        if(destination!=null)i.putExtra("destination",destination.bundle());i.putExtra("remote",remotePath);
        i.putExtras(endpoint.bundle());
        ArrayList<Uri> grants=new ArrayList<>();for(FileNodes.Node n:sources)if("doc".equals(n.kind))grants.add(Uri.parse(n.path));
        if(!grants.isEmpty()){ClipData clip=ClipData.newRawUri("LocalLS files",grants.get(0));for(int n=1;n<grants.size();n++)clip.addItem(new ClipData.Item(grants.get(n)));i.setClipData(clip);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);}
        if(Build.VERSION.SDK_INT>=26)c.startForegroundService(i);else c.startService(i);
    }
    static void retry(Context c){FailedJob previous=lastFailed;if(previous!=null){Intent i=new Intent(previous.request);if(Build.VERSION.SDK_INT>=26)c.startForegroundService(i);else c.startService(i);}}
    static boolean canRetry(){return lastFailed!=null;}
    static void stop(Context c){c.stopService(new Intent(c,TransferForegroundService.class));}
    static void cancel(Context c){c.startService(new Intent(c,TransferForegroundService.class).setAction(CANCEL));}
    @Override public void onCreate(){super.onCreate();power=new TransferPowerGuard(this);if(Build.VERSION.SDK_INT>=26)getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel("file_transfers","Передача файлов",NotificationManager.IMPORTANCE_LOW));}
    private Notification notification(String text){
        PendingIntent open=PendingIntent.getActivity(this,60,new Intent(this,FilesActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent cancel=PendingIntent.getService(this,61,new Intent(this,TransferForegroundService.class).setAction(CANCEL),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,"file_transfers"):new Notification.Builder(this);
        return b.setSmallIcon(com.bitpoint.homeservercontrol.ui.R.drawable.ic_cloud_notification).setContentTitle("LocalLS · передача файлов").setContentText(text).setContentIntent(open).setOngoing(true).addAction(0,"Отменить очередь",cancel).build();
    }
    @Override public int onStartCommand(Intent intent,int flags,int id){
        latestId=id;
        if(intent==null){stopSelf(id);return START_NOT_STICKY;}
        if(CANCEL.equals(intent.getAction())){
            generation++;int count=queued.get();
            state=new State(count>0,false,count>0?"Останавливаем очередь…":"Очередь пуста.","",0,0,count);
            if(count==0)stopSelf(id);
            return START_NOT_STICKY;
        }
        Intent request=new Intent(intent);
        String supplied=request.getStringExtra(REQUEST_ID);
        final String requestId=supplied==null || supplied.isEmpty()?UUID.randomUUID().toString():supplied;
        request.putExtra(REQUEST_ID,requestId);
        try{startForeground(NOTIFICATION,notification("Подготовка файлов и папок…"));}
        catch(RuntimeException error){
            String diagnostic=LocalExportFailure.report("FOREGROUND",error);failed(request,diagnostic);
            state=new State(false,true,"Android не разрешил запустить фоновую передачу. Открой LocalLS и повтори.",diagnostic,0,0,0);
            stopSelf(id);return START_NOT_STICKY;
        }
        // Repeated retry taps retain the id; deliberate new copies receive new ids.
        if(!claim(requestId)){if(queued.get()==0)stopSelf(id);return START_NOT_STICKY;}
        int count=queued.incrementAndGet(),batch=generation;
        state=new State(true,false,"В очереди: "+count,"",0,0,count);
        worker.execute(()->{
            Object telemetry=new Object();boolean success=false,claimReleased=false;
            try{
                if(batch!=generation)return;
                power.acquire();state=new State(true,false,"Подготовка передачи…","",0,0,queued.get());
                TransferSpeedStore.begin(telemetry);new Job(request,batch,telemetry).run();
                if(batch==generation){
                    success=true;FailedJob remaining=succeeded(requestId);
                    state=new State(false,remaining!=null,remaining==null?"Готово. Файлы и папки скопированы.":"Задание скопировано. Есть неудачная передача для повтора.",remaining==null?"":remaining.diagnostic,0,0,queued.get()-1);
                    try{
                        if(TransferSpeedStore.snapshot().peak>0)MetricHistoryStore.addSpeed(this,TransferSpeedStore.snapshot().peak);
                        TransferLogStore.add(this,"Копирование файлов и папок","Готово");
                    }catch(RuntimeException ignored){/* Telemetry must not turn a completed copy into a retry. */}
                }
            }catch(Exception | LinkageError error){
                if(batch==generation){
                    String diagnostic=LocalExportFailure.report("TRANSFER",error);
                    synchronized(REQUEST_LOCK){
                        failed(request,diagnostic);
                        state=new State(false,true,"Передача не завершена. Исходники сохранены. Проверь сеть, папку и свободное место.",diagnostic,0,0,queued.get()-1);
                        inFlight.remove(requestId);claimReleased=true;
                    }
                    try{TransferLogStore.add(this,"Копирование файлов и папок","Ошибка. Можно повторить.");}catch(RuntimeException ignored){}
                }
            }finally{
                if(!claimReleased)release(requestId);
                TransferSpeedStore.end(telemetry,success?"Завершено":batch==generation?"Ошибка":"Отменено");
                if(queued.decrementAndGet()==0){
                    power.release();new Handler(getMainLooper()).post(()->{
                        if(queued.get()==0){
                            if(batch!=generation)state=new State(false,false,"Очередь отменена. Завершённые файлы сохранены.","",0,0,0);
                            stopSelf(latestId);
                        }
                    });
                }
            }
        });
        return START_NOT_STICKY;
    }
    private final class Job {
        final Intent request;final int batch;final Object telemetry;long previousBytes,previousTime;double smoothedSpeed;int visited,completed;SftpClient.TreeSession remote;
        Job(Intent i,int b,Object t){request=i;batch=b;telemetry=t;}
        void check()throws IOException{if(batch!=generation || Thread.currentThread().isInterrupted())throw new InterruptedIOException();power.acquire();}
        void visit(int depth)throws IOException{check();if(depth>48 || ++visited>10000)throw new IOException("Слишком большая папка: максимум 10000 элементов и 48 уровней");}
        void progress(long bytes,long total){
            if(batch!=generation)return;
            long now=SystemClock.elapsedRealtime();
            if(bytes<previousBytes){previousBytes=0;previousTime=now;}
            if(previousTime>0 && now>previousTime){double sample=Math.max(0,bytes-previousBytes)*1000d/(now-previousTime);smoothedSpeed=smoothedSpeed==0?sample:smoothedSpeed*.7+sample*.3;TransferSpeedStore.record(telemetry,smoothedSpeed);}
            previousBytes=bytes;previousTime=now;if(now-lastProgress<200 && bytes!=total)return;lastProgress=now;
            String text="Скопировано файлов: "+completed+" · "+LocalFileExportService.format(bytes)+(total>0?" / "+LocalFileExportService.format(total):"");
            state=new State(true,false,text,"",bytes,total,queued.get());getSystemService(NotificationManager.class).notify(NOTIFICATION,notification(text));
        }
        boolean remoteProgress(long n,long total){try{check();progress(n,total);return true;}catch(IOException e){return false;}}
        void run()throws Exception{
            String action=request.getAction();ArrayList<Bundle> raw=request.getParcelableArrayListExtra("sources");if(raw==null)throw new IOException();
            ArrayList<FileNodes.Node> sources=new ArrayList<>();for(Bundle b:raw)sources.add(FileNodes.Node.from(b));
            if(UPLOAD.equals(action) || DOWNLOAD.equals(action)){
                String host=request.getStringExtra("host"),user=request.getStringExtra("user");int port=request.getIntExtra("port",8022);
                try(SftpClient.TreeSession session=new SftpClient.TreeSession(host,port,user,SshKeys.keyCredentials(TransferForegroundService.this,host,port,user),request.getStringExtra("pin"))){
                    remote=session;
                    for(FileNodes.Node node:sources){check();if(UPLOAD.equals(action))upload(node,request.getStringExtra("remote"),0);else download(node,destination(),0);}
                }
            }else for(FileNodes.Node node:sources){check();if(IMPORT.equals(action))importShare(node,shareDestination(),0);else if(SAVE.equals(action))save(node,destination(),0);else throw new IOException("Неизвестная операция");}
        }
        File shareDestination()throws IOException{
            Bundle b=request.getBundleExtra("destination");File root=EmbeddedServerService.share(TransferForegroundService.this);root.mkdirs();
            return b==null?root:SharedFolderAccess.within(root,new File(root,TransferPaths.relative(FileNodes.Node.from(b).path)));
        }
        FileNodes.Node destination(){Bundle b=request.getBundleExtra("destination");return b!=null?FileNodes.Node.from(b):FileNodes.phoneRoot(TransferForegroundService.this);}
        FileNodes.Node subfolder(FileNodes.Node parent,String name)throws Exception{
            TransferPaths.name(name);
            if("doc".equals(parent.kind)){Uri at=Uri.parse(parent.path);String unique=DocumentFiles.unique(TransferForegroundService.this,at,name);Uri made=DocumentFiles.create(TransferForegroundService.this,at,unique,DocumentFiles.DIRECTORY);return new FileNodes.Node("doc",made.toString(),unique,true,0);}
            List<FileNodes.Node> found=FileNodes.children(TransferForegroundService.this,parent);Set<String> names=new HashSet<>();for(FileNodes.Node n:found)names.add(n.name);
            String unique=name;for(int n=1;names.contains(unique);n++)unique=TransferPaths.duplicate(name,n);
            return new FileNodes.Node("downloads",parent.path+unique+"/",unique,true,0);
        }
        LocalFileExporter.Destination output(FileNodes.Node parent,String name){
            TransferPaths.name(name);
            if("doc".equals(parent.kind))return new DocumentFiles.Destination(TransferForegroundService.this,Uri.parse(parent.path),name,true);
            if(!"downloads".equals(parent.kind))throw new IllegalArgumentException("Выбери папку телефона");
            return new LocalExportDestination(TransferForegroundService.this,name,"application/octet-stream",null,parent.path.endsWith("/")?parent.path.substring(0,parent.path.length()-1):parent.path);
        }
        void upload(FileNodes.Node source,String parent,int depth)throws Exception{
            visit(depth);if(source.directory){String folder=remote.folder(parent,source.name);for(FileNodes.Node child:FileNodes.children(TransferForegroundService.this,source))upload(child,folder,depth+1);}
            else{try(InputStream in=FileNodes.open(TransferForegroundService.this,source)){remote.write(parent,source.name,in,source.size,this::remoteProgress);}completed++;}
        }
        void download(FileNodes.Node source,FileNodes.Node parent,int depth)throws Exception{
            visit(depth);if(source.directory){FileNodes.Node folder=subfolder(parent,source.name);for(RemoteEntry e:remote.list(source.path))download(new FileNodes.Node("remote",e.path,e.name,e.directory,e.size),folder,depth+1);}
            else{LocalFileExporter.Destination target=output(parent,source.name);boolean done=false;
                try{try(OutputStream out=target.open()){if(out==null)throw new IOException();remote.read(source.path,out,this::remoteProgress);check();}check();target.publish();done=true;completed++;}finally{if(!done)target.abort();}}
        }
        void importShare(FileNodes.Node source,File parent,int depth)throws Exception{
            visit(depth);File target=FileNodes.uniqueShare(TransferForegroundService.this,parent,source.name);
            if(source.directory){if(!target.mkdir())throw new IOException();for(FileNodes.Node n:FileNodes.children(TransferForegroundService.this,source))importShare(n,target,depth+1);}
            else{File temp=File.createTempFile(".import-",".part",parent);boolean done=false;
                try{try(InputStream in=FileNodes.open(TransferForegroundService.this,source);OutputStream out=new FileOutputStream(temp)){copy(in,out,source.size);}check();if(Build.VERSION.SDK_INT<26)throw new IOException("Android 8 required");java.nio.file.Files.move(temp.toPath(),target.toPath());done=true;completed++;}finally{if(!done)temp.delete();}}
        }
        void save(FileNodes.Node source,FileNodes.Node parent,int depth)throws Exception{
            visit(depth);if(source.directory){FileNodes.Node folder=subfolder(parent,source.name);for(FileNodes.Node n:FileNodes.children(TransferForegroundService.this,source))save(n,folder,depth+1);}
            else{LocalFileExporter.Destination target=output(parent,source.name);boolean done=false;
                try{try(InputStream in=FileNodes.open(TransferForegroundService.this,source);OutputStream out=target.open()){if(out==null)throw new IOException();copy(in,out,source.size);}check();target.publish();done=true;completed++;}finally{if(!done)target.abort();}}
        }
        void copy(InputStream in,OutputStream out,long size)throws Exception{check();byte[] buffer=new byte[65536];long count=0;int n;while((n=in.read(buffer))!=-1){check();out.write(buffer,0,n);count+=n;progress(count,size);}check();if(size>0 && size!=count)throw new IOException("Исходный файл изменился");out.flush();}
    }
    @Override public IBinder onBind(Intent i){return null;}
    @Override public void onTimeout(int id,int type){generation++;stopSelf();}
    @Override public void onDestroy(){generation++;worker.shutdownNow();if(state.active)state=new State(false,true,"Передача остановлена Android. Завершённые файлы сохранены; повтори незавершённую передачу.","SERVICE_STOPPED",0,0,0);power.release();stopForeground(true);super.onDestroy();}
}
