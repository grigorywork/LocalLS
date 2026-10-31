package com.bitpoint.homeservercontrol;
import android.content.*;
import android.net.Uri;
import java.io.*;
import java.util.*;

/** Own durable copies outlive temporary grants from Telegram and other share sources. */
final class ShareOutbox {
 static final String AUTO="share_send_immediately";
 static File file(Context c,String id)throws IOException{
  if(id==null||!id.matches("[a-f0-9-]{36}\\.bin"))throw new IOException("Неверный файл отправки");
  File root=new File(c.getFilesDir(),"share-outbox");if(!root.exists()&&!root.mkdirs())throw new IOException("Нет папки отправки");
  return SharedFolderAccess.within(root,new File(root,id));
 }
 static List<FileNodes.Node> stage(Context c,List<Uri> uris,java.util.function.Consumer<String> status)throws Exception{
  if(uris.isEmpty()||uris.size()>64)throw new IOException("Выбери от 1 до 64 файлов");List<FileNodes.Node> nodes=new ArrayList<>();List<File> created=new ArrayList<>();
  try{for(Uri uri:uris){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();
   FileNodes.Node source=FileNodes.document(c,uri);if(source.directory)throw new IOException("Выбери файлы, не папку");status.accept("Подготовка: "+source.name);
   String id=UUID.randomUUID()+".bin";File dest=file(c,id);created.add(dest);long size=0;
   try(InputStream in=FileNodes.open(c,source);OutputStream out=new FileOutputStream(dest)){
    byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();if(dest.getParentFile().getUsableSpace()<n+1048576L)throw new IOException("Не хватает места для сохранения отправки");out.write(buffer,0,n);size=Math.addExact(size,n);}
   }
   if(source.size>0&&source.size!=size)throw new IOException("Исходный файл изменился");nodes.add(new FileNodes.Node("outbox",id,source.name,false,size));
  }return nodes;}catch(Exception e){for(File f:created)f.delete();throw e;}
 }
 static void clean(Context c,List<FileNodes.Node> nodes){for(FileNodes.Node n:nodes)if("outbox".equals(n.kind))try{file(c,n.path).delete();}catch(IOException ignored){}}
 static void completed(Context c,Intent request){try{ArrayList<android.os.Bundle> raw=request.getParcelableArrayListExtra("sources");if(raw!=null){List<FileNodes.Node> nodes=new ArrayList<>();for(var b:raw)nodes.add(FileNodes.Node.from(b));clean(c,nodes);}}catch(Exception ignored){}}
}
