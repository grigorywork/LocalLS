package com.bitpoint.homeservercontrol;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import java.io.*;
import java.util.*;

/** A file reference, never a byte array. Local roots are scoped, granted documents or app Downloads. */
final class FileNodes {
    static final class Node {
        final String kind,path,name;final boolean directory;final long size;
        Node(String k,String p,String n,boolean d,long s){kind=k;path=p;name=n;directory=d;size=s;}
        Bundle bundle(){Bundle b=new Bundle();b.putString("kind",kind);b.putString("path",path);b.putString("name",name);b.putBoolean("directory",directory);b.putLong("size",size);return b;}
        static Node from(Bundle b){return new Node(b.getString("kind"),b.getString("path"),TransferPaths.name(b.getString("name")),b.getBoolean("directory"),b.getLong("size"));}
    }
    static Node shareRoot(){return new Node("share","","Мой сервер",true,0);}
    static Node downloadsRoot(){return new Node("downloads","","Загрузки / LocalLS",true,0);}
    static Node document(Context c,Uri uri)throws IOException{DocumentFiles.Entry e=DocumentFiles.info(c,uri);return new Node("doc",uri.toString(),e.name,e.directory,e.size);}
    static List<Node> children(Context c,Node folder)throws Exception {
        ArrayList<Node> out=new ArrayList<>();
        if("share".equals(folder.kind)){
            File root=EmbeddedServerService.share(c),dir=SharedFolderAccess.within(root,new File(root,TransferPaths.relative(folder.path)));
            File[] found=dir.listFiles();if(found==null && dir.exists())throw new IOException("Папка недоступна");
            if(found!=null)for(File f:found){try{SharedFolderAccess.within(root,f);String relative=folder.path.isEmpty()?f.getName():folder.path+"/"+f.getName();
                if(!TransferPaths.temporary(relative))out.add(new Node("share",relative,TransferPaths.name(f.getName()),f.isDirectory(),f.length()));}catch(IOException ignored){}}
        }else if("doc".equals(folder.kind))for(DocumentFiles.Entry e:DocumentFiles.children(c,Uri.parse(folder.path)))out.add(new Node("doc",e.uri.toString(),e.name,e.directory,e.size));
        else if("downloads".equals(folder.kind)){
            if(Build.VERSION.SDK_INT<29)return out;
            String base=Environment.DIRECTORY_DOWNLOADS+"/LocalLS/"+folder.path;
            Set<String> directories=new HashSet<>();
            try(Cursor x=c.getContentResolver().query(MediaStore.Downloads.EXTERNAL_CONTENT_URI,new String[]{MediaStore.MediaColumns._ID,MediaStore.MediaColumns.DISPLAY_NAME,MediaStore.MediaColumns.RELATIVE_PATH,MediaStore.MediaColumns.SIZE},MediaStore.MediaColumns.IS_PENDING+"=0",null,null)){
                if(x!=null)while(x.moveToNext()){
                    String parent=x.getString(2);if(parent==null || !parent.startsWith(base))continue;
                    String tail=parent.substring(base.length());
                    if(!tail.isEmpty()){String name=TransferPaths.name(tail.split("/")[0]);if(directories.add(name))out.add(new Node("downloads",folder.path+name+"/",name,true,0));}
                    else out.add(new Node("doc",ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI,x.getLong(0)).toString(),TransferPaths.name(x.getString(1)),false,x.getLong(3)));
                }
            }
        }else throw new IOException("Неизвестная папка");
        if(out.size()>10000)throw new IOException("Слишком много файлов");
        out.sort((a,b)->a.directory!=b.directory?(a.directory?-1:1):a.name.compareToIgnoreCase(b.name));return out;
    }
    static InputStream open(Context c,Node n)throws Exception {
        if(n.directory)throw new IOException("Нужен файл");
        InputStream in;
        if("share".equals(n.kind)){File root=EmbeddedServerService.share(c);in=new FileInputStream(SharedFolderAccess.within(root,new File(root,TransferPaths.relative(n.path))));}
        else if("doc".equals(n.kind))in=c.getContentResolver().openInputStream(Uri.parse(n.path));
        else throw new IOException("Неизвестный файл");
        if(in==null)throw new IOException("Нет доступа к файлу");return in;
    }
    static File uniqueShare(Context c,File parent,String name)throws IOException{
        File root=EmbeddedServerService.share(c);root.mkdirs();parent=SharedFolderAccess.within(root,parent);TransferPaths.name(name);
        File file=new File(parent,name);for(int i=1;file.exists();i++){if(i>9999)throw new IOException("Слишком много одинаковых имён");file=new File(parent,TransferPaths.duplicate(name,i));}return file;
    }
}
