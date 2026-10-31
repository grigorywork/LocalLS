package com.bitpoint.homeservercontrol;

import android.content.*;
import android.net.Uri;
import android.provider.*;
import org.json.JSONObject;
import java.io.*;
import java.nio.file.Files;
import java.util.*;

/** Persist output ownership and backup names before writing/renaming; recover without claiming user files. */
final class TransferOutput implements LocalFileExporter.Destination {
    interface Checkpoint {void save(JSONObject state)throws Exception;}
    private final Context context;
    private final FileNodes.Node parent,prior;
    private final String name;
    private final JSONObject state;
    private final Checkpoint checkpoint;
    private Uri made,oldUri;
    private File temporary,target,backup;
    private LocalExportDestination media;
    private boolean published,fallbackWrite;
    TransferOutput(Context c,FileNodes.Node parent,String name,FileNodes.Node prior)throws Exception{this(c,parent,name,prior,new JSONObject(),s->{});}
    TransferOutput(Context c,FileNodes.Node parent,String name,FileNodes.Node prior,JSONObject saved,Checkpoint checkpoint)throws Exception{
        context=c;this.parent=parent;this.name=TransferPaths.name(name);state=saved==null?new JSONObject():new JSONObject(saved.toString());this.checkpoint=checkpoint;
        if(state.has("parent")){FileNodes.Node savedParent=FileNodes.Node.from(TransferJournal.bundle(state.getJSONObject("parent")));if(!savedParent.kind.equals(parent.kind)||!savedParent.path.equals(parent.path))throw new IOException("Папка назначения не совпала с журналом");}
        if(state.has("name")&&!name.equals(state.getString("name")))throw new IOException("Имя назначения не совпало с журналом");
        FileNodes.Node remembered=state.has("prior")?FileNodes.Node.from(TransferJournal.bundle(state.getJSONObject("prior"))):prior;
        if(!state.has("prior")&&remembered!=null&&(state.optString("madeUri").equals(remembered.path)||state.optString("mediaUri").equals(remembered.path)))remembered=null;
        this.prior=remembered;
        state.put("parent",TransferJournal.node(parent.bundle())).put("name",name);
        if(this.prior!=null)state.put("prior",TransferJournal.node(this.prior.bundle()));
        if(state.has("madeUri")){Uri owned=Uri.parse(state.getString("madeUri"));if(sameDocument(owned,Uri.parse(parent.path)))throw new IOException("Журнал не может считать папку временным файлом");made=owned;}
        if(state.has("oldUri"))oldUri=Uri.parse(state.getString("oldUri"));
        if("share".equals(parent.kind)){
            File root=EmbeddedServerService.share(c);root.mkdirs();File folder=SharedFolderAccess.within(root,new File(root,TransferPaths.relative(parent.path)));
            target=SharedFolderAccess.within(root,new File(folder,name));
            if(state.has("temporaryName"))temporary=ownedFile(folder,state.getString("temporaryName"),".part");
            if(state.has("backupName"))backup=ownedFile(folder,state.getString("backupName"),".previous");
        }
    }
    private File ownedFile(File parent,String name,String suffix)throws Exception{
        TransferPaths.name(name);if(!name.startsWith(".import-")||!name.endsWith(suffix))throw new IOException("Неверное временное имя в журнале");
        return SharedFolderAccess.within(EmbeddedServerService.share(context),new File(parent,name));
    }
    private void save()throws Exception{checkpoint.save(new JSONObject(state.toString()));}
    private String backupName()throws Exception{
        if(!state.has("backupName")){state.put("backupName",".import-"+UUID.randomUUID()+".previous");save();}
        String value=TransferPaths.name(state.getString("backupName"));if(!value.startsWith(".import-")||!value.endsWith(".previous"))throw new IOException("Неверная резервная копия в журнале");return value;
    }
    static FileNodes.Node find(Context c,FileNodes.Node parent,String name)throws Exception{for(FileNodes.Node n:FileNodes.children(c,parent))if(n.name.equals(name))return n;return null;}
    boolean owns(FileNodes.Node node){return node!=null&&((made!=null&&made.toString().equals(node.path))||state.optString("mediaUri").equals(node.path));}
    private void rejectDirectoryTarget()throws Exception{
        if(target!=null){if(target.exists()&&!target.isFile())throw new IOException("Нельзя заменить папку файлом");}
        else{FileNodes.Node current=find(context,parent,name);if(current!=null&&current.directory)throw new IOException("Нельзя заменить папку файлом");}
    }
    private boolean childDocument(Uri folder,Uri candidate)throws Exception{
        for(DocumentFiles.Entry entry:DocumentFiles.children(context,folder))if(sameDocument(entry.uri,candidate))return true;
        return false;
    }
    @Override public OutputStream open()throws Exception{
        if(prior!=null&&prior.directory)throw new IOException("Нельзя заменить папку файлом");rejectDirectoryTarget();save();
        if("share".equals(parent.kind)){
            if(temporary==null){String next=".import-"+UUID.randomUUID()+".part";state.put("temporaryName",next);save();temporary=ownedFile(target.getParentFile(),next,".part");}
            return new FileOutputStream(temporary,false);
        }
        if("doc".equals(parent.kind)){
            if(made==null){
                Uri folder=Uri.parse(parent.path);String next=prior==null?name:".import-"+UUID.randomUUID()+".part";
                List<DocumentFiles.Entry> before=DocumentFiles.children(context,folder);
                Uri candidate=DocumentFiles.create(context,folder,next,"application/octet-stream");
                if(sameDocument(folder,candidate))throw new IOException("Провайдер вернул папку вместо нового файла");
                for(DocumentFiles.Entry e:before)if(sameDocument(e.uri,candidate))throw new IOException("Провайдер вернул существующий документ вместо новой копии");
                DocumentFiles.Entry created=DocumentFiles.info(context,candidate);
                if(created.directory||!created.name.equals(next)||!childDocument(folder,candidate))throw new IOException("Провайдер не подтвердил новый файл в выбранной папке");
                made=candidate;state.put("madeUri",made.toString()).put("madeName",next);save();
            }
            DocumentFiles.Entry info=DocumentFiles.info(context,made);if(info.directory||!info.name.equals(state.optString("madeName",name))&&!info.name.startsWith(".import-")){made=null;throw new IOException("Принадлежность временного документа не подтверждена");}
            OutputStream out;try{out=context.getContentResolver().openOutputStream(made,"wt");}catch(IllegalArgumentException|UnsupportedOperationException e){fallbackWrite=true;out=context.getContentResolver().openOutputStream(made,"w");}
            if(out==null)throw new IOException("Нет доступа к записи");return out;
        }
        if(!"downloads".equals(parent.kind))throw new IOException("Неизвестная папка назначения");
        String path=parent.path.endsWith("/")?parent.path.substring(0,parent.path.length()-1):parent.path;
        Uri existing=state.has("mediaUri")?Uri.parse(state.getString("mediaUri")):null;
        media=new LocalExportDestination(context,name,"application/octet-stream",existing,path,true);
        try{return media.open();}finally{if(media.uri()!=null){state.put("mediaUri",media.uri().toString());save();}}
    }
    static boolean sameDocument(Uri a,Uri b){
        if(a.equals(b))return true;try{return Objects.equals(a.getAuthority(),b.getAuthority())&&DocumentsContract.getDocumentId(a).equals(DocumentsContract.getDocumentId(b));}catch(Exception e){return false;}
    }
    @Override public InputStream readBack()throws Exception{
        if(temporary!=null)return new FileInputStream(temporary);if(media!=null)return media.readBack();
        InputStream in=context.getContentResolver().openInputStream(made);if(in==null)throw new IOException("Нет доступа к проверке копии");return in;
    }
    boolean requiresReadBack(){return fallbackWrite||media!=null&&media.requiresReadBack();}
    private static synchronized void moveFile(File from,File to)throws IOException{
        if(!from.isFile()||to.exists())throw new IOException("Не удалось безопасно переместить файл");
        if(android.os.Build.VERSION.SDK_INT>=26)Files.move(from.toPath(),to.toPath());
        else if(!from.renameTo(to))throw new IOException("Не удалось переместить файл");
    }
    private Uri documentBackup()throws Exception{
        if(!state.has("backupName"))return null;String wanted=backupName();
        for(DocumentFiles.Entry e:DocumentFiles.children(context,Uri.parse(parent.path)))if(e.name.equals(wanted)){if(e.directory)throw new IOException("Резервное имя занято папкой");return e.uri;}return null;
    }
    @Override public void publish()throws Exception{
        rejectDirectoryTarget();
        if(temporary!=null){
            if(backup==null&&prior!=null)backup=ownedFile(target.getParentFile(),backupName(),".previous");
            if(target.exists()){
                if(prior==null||backup.exists())throw new IOException("Имя назначения занято другой передачей");
                moveFile(target,backup);
            }
            try{moveFile(temporary,target);published=true;state.put("published",true);save();}
            catch(Exception e){if(!published)restoreBackup();throw e;}
            cleanupBackup();return;
        }
        if(media!=null){
            if(prior!=null){
                oldUri=Uri.parse(prior.path);String backup=backupName();state.put("oldUri",oldUri.toString());save();
                String oldName=DocumentFiles.info(context,oldUri).name;
                if(!oldName.equals(backup)){ContentValues values=new ContentValues();values.put(MediaStore.MediaColumns.DISPLAY_NAME,backup);if(context.getContentResolver().update(oldUri,values,null,null)!=1)throw new IOException("Не удалось сохранить прежнюю копию");}
            }
            try{if(prior!=null)media.rename(name);media.publish();published=true;state.put("published",true);save();}catch(Exception e){if(!published)restoreBackup();throw e;}
            cleanupBackup();return;
        }
        if(prior!=null){
            oldUri=documentBackup();FileNodes.Node occupied=find(context,parent,name);
            if(oldUri!=null&&occupied!=null&&!owns(occupied))throw new IOException("Имя назначения занято после сохранения прежней копии");
            if(oldUri==null&&occupied!=null&&!owns(occupied)){
                String backup=backupName();oldUri=DocumentsContract.renameDocument(context.getContentResolver(),Uri.parse(occupied.path),backup);
                if(oldUri==null)throw new IOException("Хранилище не поддерживает безопасную замену");state.put("oldUri",oldUri.toString());save();
            }
            try{Uri renamed=DocumentsContract.renameDocument(context.getContentResolver(),made,name);if(renamed==null)throw new IOException("Не удалось опубликовать копию");made=renamed;published=true;state.put("madeUri",made.toString()).put("published",true);save();}
            catch(Exception e){if(!published)restoreBackup();throw e;}
            cleanupBackup();
        }else{published=true;state.put("published",true);save();}
    }
    void recovered()throws Exception{published=true;state.put("published",true);save();if(temporary!=null&&temporary.isFile())temporary.delete();cleanupBackup();}
    private void cleanupBackup(){
        try{
            if(backup!=null){if(backup.isFile())backup.delete();return;}
            if("doc".equals(parent.kind)){Uri uri=documentBackup();if(uri!=null)DocumentsContract.deleteDocument(context.getContentResolver(),uri);}
            else if("downloads".equals(parent.kind)&&state.has("oldUri")&&state.has("backupName")){
                Uri uri=Uri.parse(state.getString("oldUri"));if(DocumentFiles.info(context,uri).name.equals(state.getString("backupName")))context.getContentResolver().delete(uri,null,null);
            }
        }catch(Exception ignored){/* Keep the tracked backup if its provider is unavailable. */}
    }
    private void restoreBackup(){
        try{
            if(backup!=null){if(backup.exists()&&!target.exists())moveFile(backup,target);return;}
            if(find(context,parent,name)!=null)return;
            if("doc".equals(parent.kind)){Uri uri=documentBackup();if(uri!=null)DocumentsContract.renameDocument(context.getContentResolver(),uri,name);}
            else if("downloads".equals(parent.kind)&&oldUri!=null&&state.has("backupName")&&DocumentFiles.info(context,oldUri).name.equals(state.getString("backupName"))){ContentValues v=new ContentValues();v.put(MediaStore.MediaColumns.DISPLAY_NAME,name);context.getContentResolver().update(oldUri,v,null,null);}
        }catch(Exception ignored){/* Never overwrite another file to restore a tracked backup. */}
    }
    @Override public void abort(){
        if(published)return;if(temporary!=null&&temporary.isFile())temporary.delete();if(media!=null)media.abort();
        if(made!=null)try{if(!DocumentFiles.info(context,made).directory)DocumentsContract.deleteDocument(context.getContentResolver(),made);}catch(Exception ignored){}
        restoreBackup();
        // The owned output was removed; keep its backup plan but do not reuse a stale URI on retry.
        state.remove("madeUri");state.remove("madeName");state.remove("mediaUri");state.remove("temporaryName");try{save();}catch(Exception ignored){}
    }
}
