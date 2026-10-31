package com.bitpoint.homeservercontrol;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import java.io.*;
import java.util.*;

/** Access only to documents explicitly granted by Android's picker. */
final class DocumentFiles {
    static final String DIRECTORY=DocumentsContract.Document.MIME_TYPE_DIR;
    static final class Entry {
        final Uri uri;final String name,mime;final long size;final boolean directory;
        Entry(Uri u,String n,String m,long s){uri=u;name=TransferPaths.name(n);mime=m;size=Math.max(0,s);directory=DIRECTORY.equals(m);}
    }
    static Uri root(Uri tree){return DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree));}
    private static Cursor query(Context c,Uri uri,String[] columns){
        if(android.os.Build.VERSION.SDK_INT>=26)return c.getContentResolver().query(uri,columns,(android.os.Bundle)null,null);
        return c.getContentResolver().query(uri,columns,null,null,null);
    }
    static Entry info(Context c,Uri uri)throws IOException {
        try(Cursor x=query(c,uri,new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE})) {
            if(x==null || !x.moveToFirst())throw new IOException("Документ недоступен");
            String mime=c.getContentResolver().getType(uri);
            return new Entry(uri,x.getString(0),mime==null?"application/octet-stream":mime,x.isNull(1)?0:x.getLong(1));
        }
    }
    static List<Entry> children(Context c,Uri folder)throws IOException {
        Uri q=DocumentsContract.buildChildDocumentsUriUsingTree(folder,DocumentsContract.getDocumentId(folder));
        ArrayList<Entry> out=new ArrayList<>();
        try(Cursor x=query(c,q,new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,OpenableColumns.DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE,OpenableColumns.SIZE})) {
            if(x==null)throw new IOException("Папка недоступна");
            android.os.Bundle extras=x.getExtras();
            if(extras!=null && extras.getBoolean(DocumentsContract.EXTRA_LOADING,false))throw new IOException("Папка ещё загружается. Обнови список позже");
            if(extras!=null && extras.get(DocumentsContract.EXTRA_ERROR)!=null)throw new IOException("Не удалось получить полный список папки");
            while(x.moveToNext()) {if(out.size()>=10000)throw new IOException("В папке больше 10000 элементов");
                out.add(new Entry(DocumentsContract.buildDocumentUriUsingTree(folder,x.getString(0)),x.getString(1),x.getString(2),x.isNull(3)?0:x.getLong(3)));}
        }
        out.sort((a,b)->a.directory!=b.directory?(a.directory?-1:1):a.name.compareToIgnoreCase(b.name));return out;
    }
    static Uri directory(Context c,Uri parent,String name)throws IOException {
        TransferPaths.name(name);
        for(Entry e:children(c,parent))if(e.name.equals(name)){if(!e.directory)throw new IOException("На месте папки уже есть файл");return e.uri;}
        return createOwned(c,parent,name,DIRECTORY);
    }
    static Uri create(Context c,Uri parent,String name,String mime)throws IOException {
        Uri made=DocumentsContract.createDocument(c.getContentResolver(),parent,mime,TransferPaths.name(name));
        if(made==null)throw new IOException("Не удалось создать документ");return made;
    }
    private static boolean sameDocument(Uri a,Uri b){
        if(a.equals(b))return true;
        try{return Objects.equals(a.getAuthority(),b.getAuthority())&&DocumentsContract.getDocumentId(a).equals(DocumentsContract.getDocumentId(b));}
        catch(IllegalArgumentException e){return false;}
    }
    /** Ownership is established before a caller may write or delete a create result. */
    private static Uri createOwned(Context c,Uri parent,String name,String mime)throws IOException {
        List<Entry> before=children(c,parent);
        Uri candidate=create(c,parent,name,mime);
        if(sameDocument(parent,candidate))throw new IOException("Провайдер вернул родительскую папку вместо нового документа");
        for(Entry entry:before)if(sameDocument(entry.uri,candidate))throw new IOException("Провайдер вернул существующий документ вместо нового");
        Entry made=info(c,candidate);
        if(made.directory!=DIRECTORY.equals(mime)||!name.equals(made.name))throw new IOException("Провайдер не подтвердил имя и тип нового документа");
        for(Entry entry:children(c,parent))if(sameDocument(entry.uri,candidate))return candidate;
        throw new IOException("Провайдер не подтвердил создание документа в выбранной папке");
    }
    static String unique(Context c,Uri parent,String name)throws IOException {
        TransferPaths.name(name);Set<String> found=new HashSet<>();for(Entry e:children(c,parent))found.add(e.name);
        if(!found.contains(name))return name;
        for(int i=1;i<10000;i++){String next=TransferPaths.duplicate(name,i);if(!found.contains(next))return next;}
        throw new IOException("Слишком много одинаковых имён");
    }
    static void grant(Context c,Intent data)throws IOException {
        if(data==null || data.getData()==null || !"content".equals(data.getData().getScheme()))throw new IOException("Папка не выбрана");
        int flags=data.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        if((flags&Intent.FLAG_GRANT_READ_URI_PERMISSION)==0)throw new IOException("Нет доступа к папке");
        if((flags&Intent.FLAG_GRANT_WRITE_URI_PERMISSION)!=0)c.getContentResolver().takePersistableUriPermission(data.getData(),Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        else c.getContentResolver().takePersistableUriPermission(data.getData(),Intent.FLAG_GRANT_READ_URI_PERMISSION);
    }
    static final class Destination implements LocalFileExporter.Destination {
        private final Context context;private final Uri tree;private final String relative;
        private Uri created;private final boolean documentParent;
        Destination(Context c,Uri tree,String path){this(c,tree,path,false);}
        Destination(Context c,Uri tree,String path,boolean documentParent){context=c.getApplicationContext();this.tree=tree;relative=TransferPaths.relative(path);this.documentParent=documentParent;}
        @Override public OutputStream open()throws Exception {
            String[] parts=relative.split("/");Uri dir=documentParent?tree:root(tree);
            for(int i=0;i<parts.length-1;i++)dir=directory(context,dir,parts[i]);
            String name=unique(context,dir,parts[parts.length-1]);
            created=createOwned(context,dir,name,"application/octet-stream");
            OutputStream out=context.getContentResolver().openOutputStream(created,"w");
            if(out==null)throw new IOException("Нет доступа к записи");return out;
        }
        @Override public void publish(){}
        @Override public InputStream readBack()throws Exception{InputStream in=context.getContentResolver().openInputStream(created);if(in==null)throw new IOException("Нет доступа к проверке копии");return in;}
        @Override public void abort(){if(created!=null)try{DocumentsContract.deleteDocument(context.getContentResolver(),created);}catch(Exception ignored){}}
    }
}
