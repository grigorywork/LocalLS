package com.bitpoint.homeservercontrol;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Durable list of completed uploads. One failed item must not block independent files. */
final class AutoReceive implements AutoCloseable {
    static volatile String status="Автосохранение готово";
    static volatile boolean error,active;
    static volatile String diagnostic="";
    private long lastProgress;
    private final Context context;
    private final Queue queue;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final AtomicBoolean requested=new AtomicBoolean(),scheduled=new AtomicBoolean();
    private volatile boolean closed;

    AutoReceive(Context c){context=c.getApplicationContext();queue=new Queue(context);retry();}

    synchronized void received(String relative){
        if(closed)return;
        try {
            TransferPaths.relative(relative);
            if(relative.isEmpty() || TransferPaths.temporary(relative))return;
            File f=SharedFolderAccess.within(EmbeddedServerService.share(context),new File(EmbeddedServerService.share(context),relative));
            if(!f.isFile())return;
            queue.offer(relative,f.length(),f.lastModified());
            retry();
        }catch(Exception e){failure("Не удалось поставить файл на автосохранение. Оригинал находится в LocalLS.",e);}
    }

    synchronized void retry(){
        if(closed)return;
        requested.set(true);
        if(!scheduled.compareAndSet(false,true))return;
        try{worker.execute(this::runPending);}
        catch(RejectedExecutionException e){scheduled.set(false);if(!closed)failure("Очередь сохранения остановлена. Перезапусти сервер.",e);}
    }

    private void runPending(){
        try{
            while(!closed && requested.getAndSet(false))drain();
        }catch(Exception e){if(!closed)failure("Не удалось прочитать очередь сохранения. Перезапусти сервер. Оригиналы находятся в LocalLS.",e);}
        finally{
            active=false;scheduled.set(false);
            if(!closed && requested.get())retry();
        }
    }

    private void failure(String text,Exception e){
        error=true;diagnostic=LocalExportFailure.report("EXPORT",e);status=text;active=false;
    }

    private void drain(){
        long after=0;int saved=0,missing=0,failed=0;
        String firstFailure="";
        error=false;diagnostic="";
        while(!closed){
            Queue.Item item=queue.nextAfter(after);
            if(item==null)break;
            after=item.id;
            try{
                File root=EmbeddedServerService.share(context);
                File source=SharedFolderAccess.within(root,new File(root,TransferPaths.relative(item.path)));
                if(!source.exists()){
                    queue.discard(item);missing++;continue;
                }
                active=true;status="Автосохранение → "+ReceiveFolder.label(context);
                LocalFileExporter.copy(root,item.path,ReceiveFolder.destination(context,item.path),(done,total)->{
                    if(closed)throw new CancellationException();
                    long now=android.os.SystemClock.elapsedRealtime();
                    if(now-lastProgress>250 || done==total){
                        lastProgress=now;status="Автосохранение · "+LocalFileExportService.format(done)+" / "+LocalFileExportService.format(total);
                    }
                });
                queue.done(item);saved++;
            }catch(Exception e){
                if(closed)return;
                failed++;
                if(firstFailure.isEmpty())firstFailure=LocalExportFailure.report("EXPORT",e);
                // Keep the failed row pending, but continue after its id in this pass.
            }
        }
        if(closed){active=false;return;}
        if(failed>0){
            error=true;diagnostic=firstFailure;
            status="Сохранено: "+saved+". Ожидают повтора: "+failed+". Проверь папку и свободное место, затем нажми «Повторить сохранение».";
        }else{
            error=false;diagnostic="";
            status=saved>0?"Сохранено автоматически → "+ReceiveFolder.label(context):"Автосохранение готово";
        }
        if(missing>0)status+=" Пропущены удалённые исходники: "+missing+".";
        active=false;
    }

    @Override public synchronized void close(){
        if(closed)return;
        closed=true;worker.execute(queue::close);worker.shutdown();
    }

    static final class Queue extends SQLiteOpenHelper {
        Queue(Context c){super(c,"completed-uploads.db",null,1);}
        @Override public void onCreate(SQLiteDatabase db){db.execSQL("CREATE TABLE uploads(path TEXT PRIMARY KEY, size INTEGER NOT NULL, modified INTEGER NOT NULL, saved INTEGER NOT NULL DEFAULT 0)");}
        @Override public void onUpgrade(SQLiteDatabase db,int old,int next){throw new IllegalStateException("Unknown receive queue version");}
        synchronized void offer(String path,long size,long modified){
            try(Cursor c=getReadableDatabase().rawQuery("SELECT size,modified,saved FROM uploads WHERE path=?",new String[]{path})){
                if(c.moveToFirst() && c.getLong(0)==size && c.getLong(1)==modified && c.getInt(2)!=2)return;
            }
            ContentValues v=new ContentValues();v.put("path",path);v.put("size",size);v.put("modified",modified);v.put("saved",0);
            if(getWritableDatabase().insertWithOnConflict("uploads",null,v,SQLiteDatabase.CONFLICT_REPLACE)==-1)
                throw new SQLiteException("Receive queue insert failed");
        }
        static final class Item{
            final long id,size,modified;final String path;
            Item(long id,String path,long size,long modified){this.id=id;this.path=path;this.size=size;this.modified=modified;}
        }
        synchronized Item next(){return nextAfter(0);}
        synchronized Item nextAfter(long id){
            try(Cursor c=getReadableDatabase().rawQuery("SELECT rowid,path,size,modified FROM uploads WHERE saved=0 AND rowid>? ORDER BY rowid LIMIT 1",new String[]{Long.toString(id)})){
                return c.moveToFirst()?new Item(c.getLong(0),c.getString(1),c.getLong(2),c.getLong(3)):null;
            }
        }
        synchronized void done(Item item){finish(item,1);}
        synchronized void discard(Item item){finish(item,2);}
        private void finish(Item item,int state){
            getWritableDatabase().execSQL("UPDATE uploads SET saved=? WHERE rowid=? AND path=? AND size=? AND modified=?",new Object[]{state,item.id,item.path,item.size,item.modified});
        }
    }
}
