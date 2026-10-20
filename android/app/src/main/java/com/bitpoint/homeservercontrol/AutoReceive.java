package com.bitpoint.homeservercontrol;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.io.*;
import java.util.concurrent.*;

/** Durable list of completed uploads. No UI confirmation, no private keys or URIs in the queue. */
final class AutoReceive implements AutoCloseable {
    static volatile String status="Автосохранение готово";
    static volatile boolean error,active;
    private long lastProgress;
    static volatile String diagnostic="";
    private final Context context;
    private final Queue queue;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private volatile boolean closed;
    AutoReceive(Context c){context=c.getApplicationContext();queue=new Queue(context);retry();}
    synchronized void received(String relative){
        if(closed)return;
        try {
            TransferPaths.relative(relative);if(relative.isEmpty() || TransferPaths.temporary(relative))return;
            File f=SharedFolderAccess.within(EmbeddedServerService.share(context),new File(EmbeddedServerService.share(context),relative));
            if(!f.isFile())return;
            queue.offer(relative,f.length(),f.lastModified());retry();
        }catch(Exception e){error=true;status="Не удалось поставить файл на автосохранение. Оригинал находится в LocalLS.";}
    }
    void retry(){if(!closed)try{worker.execute(this::drain);}catch(RejectedExecutionException ignored){}}
    private void drain(){
        while(!closed){
            Queue.Item item=queue.next();if(item==null)return;String relative=item.path;
            try {
                error=false;active=true;diagnostic="";status="Автосохранение → "+ReceiveFolder.label(context);
                LocalFileExporter.copy(EmbeddedServerService.share(context),relative,ReceiveFolder.destination(context,relative),(done,total)->{if(closed)throw new java.util.concurrent.CancellationException();long now=android.os.SystemClock.elapsedRealtime();if(now-lastProgress>250 || done==total){lastProgress=now;status="Автосохранение · "+LocalFileExportService.format(done)+" / "+LocalFileExportService.format(total);}});
                queue.done(item);active=false;status="Сохранено автоматически → "+ReceiveFolder.label(context);
            }catch(Exception e){
                active=false;if(closed)return;diagnostic=LocalExportFailure.report("EXPORT",e);error=true;status="Автосохранение не завершено. Оригинал в LocalLS. Проверь папку и свободное место, затем нажми «Повторить сохранение».";
                return;
            }
        }
    }
    @Override public synchronized void close(){if(closed)return;closed=true;worker.execute(queue::close);worker.shutdown();}
    static final class Queue extends SQLiteOpenHelper {
        Queue(Context c){super(c,"completed-uploads.db",null,1);}
        @Override public void onCreate(SQLiteDatabase db){db.execSQL("CREATE TABLE uploads(path TEXT PRIMARY KEY, size INTEGER NOT NULL, modified INTEGER NOT NULL, saved INTEGER NOT NULL DEFAULT 0)");}
        @Override public void onUpgrade(SQLiteDatabase db,int old,int next){throw new IllegalStateException("Unknown receive queue version");}
        synchronized void offer(String path,long size,long modified){
            try(Cursor c=getReadableDatabase().rawQuery("SELECT size,modified FROM uploads WHERE path=?",new String[]{path})){
                if(c.moveToFirst() && c.getLong(0)==size && c.getLong(1)==modified)return;
            }
            ContentValues v=new ContentValues();v.put("path",path);v.put("size",size);v.put("modified",modified);v.put("saved",0);
            getWritableDatabase().insertWithOnConflict("uploads",null,v,SQLiteDatabase.CONFLICT_REPLACE);
        }
        static final class Item{final String path;final long size,modified;Item(String p,long s,long m){path=p;size=s;modified=m;}}
        synchronized Item next(){try(Cursor c=getReadableDatabase().rawQuery("SELECT path,size,modified FROM uploads WHERE saved=0 ORDER BY rowid LIMIT 1",null)){return c.moveToFirst()?new Item(c.getString(0),c.getLong(1),c.getLong(2)):null;}}
        synchronized void done(Item item){getWritableDatabase().execSQL("UPDATE uploads SET saved=1 WHERE path=? AND size=? AND modified=?",new Object[]{item.path,item.size,item.modified});}
    }
}
