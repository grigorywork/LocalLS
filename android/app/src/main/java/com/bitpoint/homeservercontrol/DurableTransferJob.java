package com.bitpoint.homeservercontrol;

import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import org.json.*;
import java.io.*;
import java.nio.file.Files;
import java.util.*;

/** Stable tree snapshots and per-file publication checkpoints survive service/process recreation. */
final class DurableTransferJob {
    interface Check {void run()throws IOException;}
    private final Context context;
    private final Intent request;
    private final String id,action,policy;
    private final boolean verify;
    private final TransferJournal journal;
    private final Check check;
    private final LocalFileExporter.Progress progress;
    private final java.util.function.LongConsumer payload;
    private SftpClient.TreeSession remote;
    private int visited;
    private long largest;
    String sourceKey="";
    DurableTransferJob(Context c,Intent request,Check check,LocalFileExporter.Progress progress){
        this(c,request,check,progress,null);
    }
    DurableTransferJob(Context c,Intent request,Check check,LocalFileExporter.Progress progress,java.util.function.LongConsumer payload){
        context=c;this.request=request;this.check=check;this.progress=progress;journal=new TransferJournal(c);
        this.payload=payload;
        id=request.getStringExtra("request_id");action=request.getAction();String value=request.getStringExtra("conflict");policy=value==null?TransferJournal.KEEP:value;verify=request.getBooleanExtra("verify",false);
    }
    void run()throws Exception{
        if("upload".equals(action)||"download".equals(action)){
            String host=request.getStringExtra("host"),user=request.getStringExtra("user");int port=request.getIntExtra("port",8022);
            String pin=request.getStringExtra("pin");String current=HostTrustStore.get(context,host,port);
            if(!current.equals(pin))throw new IOException("Доверенный ключ сервера изменился. Проверь подключение");
            try(SftpClient.TreeSession session=new SftpClient.TreeSession(host,port,user,SshKeys.keyCredentials(context,host,port,user),pin)){remote=session;if(payload!=null)remote.onPayload(payload);copyTree();}
        }else copyTree();
    }
    private void visit(int depth)throws IOException{check.run();if(depth>48||++visited>10000)throw new IOException("Максимум 10000 элементов и 48 уровней");}
    private List<FileNodes.Node> sources()throws Exception{ArrayList<Bundle> raw=request.getParcelableArrayListExtra("sources");if(raw==null)throw new IOException("Нет исходников");List<FileNodes.Node> out=new ArrayList<>();for(Bundle b:raw)out.add(FileNodes.Node.from(b));return out;}
    private FileNodes.Node destination(){Bundle b=request.getBundleExtra("destination");return b!=null?FileNodes.Node.from(b):"import".equals(action)?FileNodes.shareRoot():FileNodes.phoneRoot(context);}
    private void copyTree()throws Exception{
        FileNodes.Node parent=destination();
        List<FileNodes.Node> nodes=sources();long bytes=0;for(FileNodes.Node n:nodes){
            JSONObject checkpoint=journal.item(id,TransferJournal.key(n));if(checkpoint.optBoolean("done"))continue;
            if(TransferJournal.SKIP.equals(policy)&&!claimedDestination(checkpoint)){
                boolean exists="upload".equals(action)?remote.entry(SftpClient.join(request.getStringExtra("remote"),n.name))!=null:TransferOutput.find(context,parent,n.name)!=null;
                if(exists){String key=TransferJournal.key(n);done(key,journal.item(id,key),"Пропущено");continue;}
            }
            bytes=Math.addExact(bytes,prepare(n,0));
        }
        long free="upload".equals(action)?remote.available(request.getStringExtra("remote")):TransferCapacity.local(context,parent);
        TransferCapacity.require(bytes,free);
        if(!"upload".equals(action)){
            // A verified staging copy and destination can coexist on the same volume.
            TransferCapacity.require(largest,journal.root.getUsableSpace());
        }
        visited=0;for(FileNodes.Node source:nodes)copy(source,parent,request.getStringExtra("remote"),0);
    }
    private static boolean claimedDestination(JSONObject item){return item.has("targetName")||item.has("targetPath")||item.has("folder")||item.has("folderPath")||item.has("output");}
    private List<FileNodes.Node> children(FileNodes.Node source)throws Exception{
        String key=TransferJournal.key(source);JSONObject item=journal.item(id,key);JSONArray snapshot=item.optJSONArray("children");
        if(snapshot==null){
            snapshot=new JSONArray();List<FileNodes.Node> children;
            if("remote".equals(source.kind)){
                children=new ArrayList<>();for(RemoteEntry e:remote.list(source.path))children.add(new FileNodes.Node("remote",e.path,e.name,e.directory,e.size));
            }else children=FileNodes.children(context,source);
            for(FileNodes.Node n:children)snapshot.put(TransferJournal.node(n.bundle()));item.put("children",snapshot);journal.item(id,key,item);
        }
        List<FileNodes.Node> out=new ArrayList<>();for(int n=0;n<snapshot.length();n++)out.add(FileNodes.Node.from(TransferJournal.bundle(snapshot.getJSONObject(n))));return out;
    }
    private long prepare(FileNodes.Node source,int depth)throws Exception{
        visit(depth);if(TransferPaths.temporary(source.name))throw new IOException("Файл ещё не опубликован");JSONObject item=journal.item(id,TransferJournal.key(source));if(item.optBoolean("done"))return 0;
        if(source.directory){long bytes=0;for(FileNodes.Node child:children(source))bytes=Math.addExact(bytes,prepare(child,depth+1));return bytes;}
        long size=source.size;String stamp;
        if("share".equals(source.kind)){
            File f=SharedFolderAccess.within(EmbeddedServerService.share(context),new File(EmbeddedServerService.share(context),TransferPaths.relative(source.path)));
            if(!f.isFile())throw new FileNotFoundException("Исходный файл недоступен");size=f.length();stamp=size+":"+f.lastModified();
        }else if("outbox".equals(source.kind)){File f=ShareOutbox.file(context,source.path);if(!f.isFile())throw new FileNotFoundException();size=f.length();stamp=size+":"+f.lastModified();}
        else if("remote".equals(source.kind)){RemoteEntry e=remote.entry(source.path);if(e==null||e.directory)throw new IOException("Исходный файл недоступен");size=e.size;stamp=remote.identity(source.path);}
        else{DocumentFiles.Entry e=DocumentFiles.info(context,Uri.parse(source.path));size=e.size>0?e.size:-1;stamp=String.valueOf(size);}
        if((source.size>0&&size>=0&&source.size!=size)||(item.has("stamp")&&!item.getString("stamp").equals(stamp)))throw new IOException("Исходный файл изменился. Создай новое задание");
        item.put("stamp",stamp).put("size",size);String key=TransferJournal.key(source);journal.item(id,key,item);
        if(item.optBoolean("publishing")&&item.has("sha256")){
            if("upload".equals(action)&&item.has("targetPath")){
                RemoteEntry ready=remote.entry(item.getString("targetPath"));
                if(ready!=null&&!ready.directory&&item.getString("sha256").equals(remote.checksum(ready.path))){remote.recoverPublished(ready.path,".localls-upload-"+id+"-"+key+".part",item.getString("sha256"));done(key,item,"Скопировано");return 0;}
            }else if(item.has("output")){
                JSONObject saved=item.getJSONObject("output");FileNodes.Node at=FileNodes.Node.from(TransferJournal.bundle(saved.getJSONObject("parent")));
                FileNodes.Node ready=TransferOutput.find(context,at,item.getString("targetName"));
                if(ready!=null&&!ready.directory)try(InputStream in=FileNodes.open(context,ready)){
                    if(item.getString("sha256").equals(VerifiedCopy.sha256(in))){new TransferOutput(context,at,item.getString("targetName"),null,saved,s->journal.item(id,key,item.put("output",s))).recovered();done(key,item,"Скопировано");journal.partial(id,key).delete();return 0;}
                }
            }
        }
        long retained=0;
        if("upload".equals(action)&&item.has("targetPath")){RemoteEntry part=remote.entry(SftpClient.join(SftpClient.parent(item.getString("targetPath")),".localls-upload-"+id+"-"+key+".part"));if(part!=null&&!part.directory)retained=part.size;}
        else retained=journal.partial(id,key).length();
        largest=Math.max(largest,Math.max(0,size-retained));return "upload".equals(action)?Math.max(0,size-retained):Math.max(0,size);
    }
    private void done(String key,JSONObject item,String result)throws Exception{check.run();item.put("done",true).put("result",result);journal.item(id,key,item);}
    private void copy(FileNodes.Node source,FileNodes.Node parent,String remoteParent,int depth)throws Exception{
        visit(depth);String key=TransferJournal.key(source);if(!source.directory){TransferForegroundService.currentName=source.name;sourceKey=key;}JSONObject item=journal.item(id,key);if(item.optBoolean("done"))return;
        if(source.directory){
            if("upload".equals(action)){
                String folder=item.optString("folderPath");
                if(folder.isEmpty()){
                    RemoteEntry existing=remote.entry(SftpClient.join(remoteParent,source.name));
                    if(existing!=null&&TransferJournal.SKIP.equals(policy)){done(key,item,"Пропущено");return;}
                    folder=TransferJournal.KEEP.equals(policy)?remote.unique(remoteParent,source.name):SftpClient.join(remoteParent,source.name);
                    item.put("folderPath",folder);journal.item(id,key,item);
                }
                remote.ensureFolder(folder);for(FileNodes.Node child:children(source))copy(child,null,folder,depth+1);
            }else{
                FileNodes.Node folder;
                if(item.has("folder"))folder=FileNodes.Node.from(TransferJournal.bundle(item.getJSONObject("folder")));
                else{
                    FileNodes.Node existing=TransferOutput.find(context,parent,source.name);
                    if(existing!=null&&TransferJournal.SKIP.equals(policy)){done(key,item,"Пропущено");return;}
                    folder=makeFolder(parent,source.name,existing);item.put("folder",TransferJournal.node(folder.bundle()));journal.item(id,key,item);
                }
                for(FileNodes.Node child:children(source))copy(child,folder,null,depth+1);
            }
            done(key,journal.item(id,key),"Папка скопирована");return;
        }
        if("upload".equals(action)){upload(source,remoteParent,key,item);return;}
        String name=item.optString("targetName");FileNodes.Node prior;
        if(name.isEmpty()){
            prior=TransferOutput.find(context,parent,source.name);
            if(prior!=null&&TransferJournal.SKIP.equals(policy)){done(key,item,"Пропущено");return;}
            name=source.name;if(TransferJournal.KEEP.equals(policy))name=unique(parent,name);
            item.put("targetName",name);journal.item(id,key,item);
        }
        prior=TransferOutput.find(context,parent,name);
        TransferOutput output=new TransferOutput(context,parent,name,prior,item.optJSONObject("output"),s->journal.item(id,key,item.put("output",s)));
        if(item.optBoolean("publishing")&&prior!=null&&!prior.directory&&item.has("sha256")){
            try(InputStream in=FileNodes.open(context,prior)){if(item.getString("sha256").equals(VerifiedCopy.sha256(in))){output.recovered();done(key,item,"Скопировано");journal.partial(id,key).delete();return;}}
        }
        if(prior!=null&&!output.owns(prior)&&!TransferJournal.REPLACE.equals(policy))throw new IOException("Имя назначения уже занято. Создай новое задание");
        if(prior!=null&&source.kind.equals(prior.kind)&&source.path.equals(prior.path)){done(key,item,"Исходник уже в назначении");return;}
        File partial=journal.partial(id,key);long size=item.getLong("size");
        if("download".equals(action))remote.readResumable(source.path,partial,this::remoteProgress);
        else try(InputStream in=FileNodes.open(context,source)){
            long offset=partial.length();if(size>=0&&offset>size)throw new IOException("Частичная копия изменилась");
            if(offset>0)try(InputStream saved=new FileInputStream(partial)){VerifiedCopy.prefix(in,saved,offset,this::report);}
            try(FileOutputStream out=new FileOutputStream(partial,true)){VerifiedCopy.copy(in,out,offset,size,this::report);out.getFD().sync();}
        }
        check.run();String hash;try(InputStream in=new FileInputStream(partial)){hash=VerifiedCopy.sha256(in);}
        if(verify){String original;if("download".equals(action))original=remote.checksum(source.path);else try(InputStream in=FileNodes.open(context,source)){original=VerifiedCopy.sha256(in);}
            if(!hash.equals(original))throw new IOException("SHA-256 исходника и копии не совпадает");}
        item.put("sha256",hash).put("publishing",true);journal.item(id,key,item);
        boolean success=false;
        try{
            try(InputStream in=new FileInputStream(partial);OutputStream out=output.open()){if(out==null)throw new IOException("Нет доступа к записи");VerifiedCopy.copy(in,out,0,partial.length(),this::report);}
            check.run();if(verify||output.requiresReadBack())try(InputStream in=output.readBack()){if(!hash.equals(VerifiedCopy.sha256(in)))throw new IOException("SHA-256 записанной копии не совпадает");}
            check.run();unchanged(source,item);output.publish();success=true;done(key,item,verify?"SHA-256 проверен":"Скопировано");partial.delete();
        }finally{if(!success)output.abort();}
    }
    private void upload(FileNodes.Node source,String parent,String key,JSONObject item)throws Exception{
        String target=item.optString("targetPath");
        if(target.isEmpty()){
            RemoteEntry existing=remote.entry(SftpClient.join(parent,source.name));
            if(existing!=null&&TransferJournal.SKIP.equals(policy)){done(key,item,"Пропущено");return;}
            target=TransferJournal.KEEP.equals(policy)?remote.unique(parent,source.name):SftpClient.join(parent,source.name);
            item.put("targetPath",target);journal.item(id,key,item);
        }
        RemoteEntry existing=remote.entry(target);
        if(existing!=null&&item.optBoolean("publishing")&&item.has("sha256")&&item.getString("sha256").equals(remote.checksum(target))){done(key,item,"Скопировано");return;}
        if(existing!=null&&!TransferJournal.REPLACE.equals(policy))throw new IOException("Имя назначения занято другой передачей");
        try(InputStream in=FileNodes.open(context,source)){
            remote.writeResumable(parent,source.name,in,item.getLong("size"),this::remoteProgress,
                    ".localls-upload-"+id+"-"+key+".part",target,policy,verify,(path,hash)->{
                        check.run();unchanged(source,item);item.put("sha256",hash).put("publishing",true);journal.item(id,key,item);
                    });
        }
        done(key,item,verify?"SHA-256 проверен":"Скопировано");
    }
    private void report(long n,long total){try{check.run();progress.update(n,total);}catch(IOException e){throw new Cancelled(e);}}
    private void unchanged(FileNodes.Node source,JSONObject item)throws Exception{
        if("share".equals(source.kind)){File root=EmbeddedServerService.share(context);File f=SharedFolderAccess.within(root,new File(root,TransferPaths.relative(source.path)));if(!f.isFile()||!(f.length()+":"+f.lastModified()).equals(item.getString("stamp")))throw new IOException("Исходный файл изменился во время передачи");}
        else if("remote".equals(source.kind)&&!remote.identity(source.path).equals(item.getString("stamp")))throw new IOException("Исходный файл на сервере изменился");
    }
    private boolean remoteProgress(long n,long total){try{check.run();progress.update(n,total);return true;}catch(IOException e){return false;}}
    private static final class Cancelled extends RuntimeException{Cancelled(IOException cause){super(cause);}}
    private String unique(FileNodes.Node parent,String name)throws Exception{Set<String> names=new HashSet<>();for(FileNodes.Node n:FileNodes.children(context,parent))names.add(n.name);if(!names.contains(name))return name;for(int n=1;n<10000;n++){String next=TransferPaths.duplicate(name,n);if(!names.contains(next))return next;}throw new IOException("Слишком много одинаковых имён");}
    private FileNodes.Node makeFolder(FileNodes.Node parent,String name,FileNodes.Node existing)throws Exception{
        if(existing!=null&&TransferJournal.REPLACE.equals(policy)){if(!existing.directory)throw new IOException("На месте папки уже есть файл");return existing;}
        String next=TransferJournal.KEEP.equals(policy)?unique(parent,name):name;
        if("doc".equals(parent.kind)){
            Uri folder=Uri.parse(parent.path);List<DocumentFiles.Entry> before=DocumentFiles.children(context,folder);Uri made=DocumentFiles.create(context,folder,next,DocumentFiles.DIRECTORY);
            if(TransferOutput.sameDocument(folder,made))throw new IOException("Провайдер вернул родительскую папку вместо новой");
            for(DocumentFiles.Entry e:before)if(TransferOutput.sameDocument(e.uri,made))throw new IOException("Провайдер вернул существующий документ вместо новой папки");
            DocumentFiles.Entry created=DocumentFiles.info(context,made);boolean child=false;for(DocumentFiles.Entry e:DocumentFiles.children(context,folder))if(TransferOutput.sameDocument(e.uri,made))child=true;
            if(!created.directory||!created.name.equals(next)||!child)throw new IOException("Провайдер не подтвердил новую папку в выбранном месте");
            return new FileNodes.Node("doc",made.toString(),next,true,0);
        }
        if("share".equals(parent.kind)){
            File root=EmbeddedServerService.share(context);File at=SharedFolderAccess.within(root,new File(new File(root,TransferPaths.relative(parent.path)),next));if(!at.mkdir())throw new IOException("Не удалось создать папку");
            return new FileNodes.Node("share",parent.path.isEmpty()?next:parent.path+"/"+next,next,true,0);
        }
        return new FileNodes.Node("downloads",parent.path+next+"/",next,true,0);
    }
}
