package com.bitpoint.homeservercontrol;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.util.*;

/** Device-local bounded receipt log; a receipt means SFTP closed/committed the shared file, not auto-export. */
final class ServerReceiptStore extends SQLiteOpenHelper {
    ServerReceiptStore(Context context){super(context,"server-receipts.db",null,1);}
    @Override public void onCreate(SQLiteDatabase db){db.execSQL("CREATE TABLE receipts(id INTEGER PRIMARY KEY, path TEXT NOT NULL, sender TEXT NOT NULL, bytes INTEGER NOT NULL, modified INTEGER NOT NULL, received INTEGER NOT NULL, UNIQUE(path,bytes,modified))");}
    @Override public void onUpgrade(SQLiteDatabase db,int old,int next){throw new IllegalStateException("Unknown receipt schema");}
    void record(EmbeddedSftpServer.Receipt event){
        TransferPaths.relative(event.path);if(event.path.isEmpty()||TransferPaths.temporary(event.path)||event.bytes<0)return;
        ContentValues row=new ContentValues();row.put("path",event.path);row.put("sender",event.key);row.put("bytes",event.bytes);row.put("modified",event.modified);row.put("received",System.currentTimeMillis());
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{db.insertWithOnConflict("receipts",null,row,SQLiteDatabase.CONFLICT_IGNORE);db.execSQL("DELETE FROM receipts WHERE id NOT IN (SELECT id FROM receipts ORDER BY id DESC LIMIT 100)");db.setTransactionSuccessful();}finally{db.endTransaction();}
    }
    static final class Entry {
        final String path,sender;final long bytes,received;
        Entry(String path,String sender,long bytes,long received){this.path=path;this.sender=sender;this.bytes=bytes;this.received=received;}
        String name(){return new java.io.File(path).getName().replaceAll("\\p{Cntrl}"," ");}
    }
    List<Entry> list(){ArrayList<Entry> out=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT path,sender,bytes,received FROM receipts ORDER BY id DESC LIMIT 100",null)){while(c.moveToNext())out.add(new Entry(c.getString(0),c.getString(1),c.getLong(2),c.getLong(3)));}return out;}
    static String sender(Context context,String fingerprint){try{for(var device:ServerDevices.list(context))if(EmbeddedSftpServer.publicFingerprint(device.key).equals(fingerprint))return device.name;}catch(Exception ignored){}return "Устройство";}
}
