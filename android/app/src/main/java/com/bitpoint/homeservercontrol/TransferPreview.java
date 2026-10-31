package com.bitpoint.homeservercontrol;

import android.content.Context;
import java.io.*;
import java.util.*;

/** Read-only preflight; it creates no destination files and performs no publication. */
final class TransferPreview {
    long bytes,available=-1;boolean unknown;int files,visited;
    private final Context context;
    private SftpClient.TreeSession remote;
    private TransferPreview(Context c){context=c;}
    static TransferPreview inspect(Context c,String action,List<FileNodes.Node> sources,String path,FileNodes.Node destination,FileConnection endpoint)throws Exception{
        TransferPreview p=new TransferPreview(c);
        if("upload".equals(action)||"download".equals(action)){
            if(!HostTrustStore.get(c,endpoint.host,endpoint.port).equals(endpoint.pin))throw new IOException("Подключение изменилось");
            try(SftpClient.TreeSession tree=new SftpClient.TreeSession(endpoint.host,endpoint.port,endpoint.user,SshKeys.keyCredentials(c,endpoint.host,endpoint.port,endpoint.user),endpoint.pin)){
                p.remote=tree;for(FileNodes.Node n:sources)p.visit(n,0);
                p.available="upload".equals(action)?tree.available(path):TransferCapacity.local(c,destination);
            }
        }else{for(FileNodes.Node n:sources)p.visit(n,0);p.available=TransferCapacity.local(c,destination);}
        return p;
    }
    private void visit(FileNodes.Node source,int depth)throws Exception{
        VerifiedCopy.check();if(depth>48||++visited>10000)throw new IOException("Максимум 10000 элементов и 48 уровней");
        if(source.directory){
            if("remote".equals(source.kind))for(RemoteEntry e:remote.list(source.path))visit(new FileNodes.Node("remote",e.path,e.name,e.directory,e.size),depth+1);
            else for(FileNodes.Node n:FileNodes.children(context,source))visit(n,depth+1);
        }else{
            long size=source.size;files++;
            if("share".equals(source.kind)){File root=EmbeddedServerService.share(context);File f=SharedFolderAccess.within(root,new File(root,TransferPaths.relative(source.path)));if(!f.isFile())throw new IOException("Исходный файл недоступен");size=f.length();}
            else if("remote".equals(source.kind)){RemoteEntry e=remote.entry(source.path);if(e==null||e.directory)throw new IOException("Исходный файл недоступен");size=e.size;}
            else{size=DocumentFiles.info(context,android.net.Uri.parse(source.path)).size;if(size==0)unknown=true;}
            bytes=Math.addExact(bytes,Math.max(0,size));
        }
    }
    String summary(){return "Файлов: "+files+"\n"+TransferCapacity.describe(bytes,unknown,available)+(available>=0&&bytes>available?"\nОбъём больше свободного места. Для новых копий нужно освободить память":"");}
}
