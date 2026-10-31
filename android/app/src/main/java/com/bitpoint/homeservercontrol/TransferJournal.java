package com.bitpoint.homeservercontrol;

import android.content.*;
import android.os.Bundle;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Private atomic per-request journal. Credentials are looked up at execution, never serialized. */
final class TransferJournal {
    static final String KEEP="keep",SKIP="skip",REPLACE="replace";
    private static final Object LOCK=new Object();
    private final Context context;
    final File root;
    TransferJournal(Context c){context=c.getApplicationContext();root=new File(context.getFilesDir(),"transfer-queue-v1");}
    private File file(String id)throws IOException{if(id==null||!id.matches("[a-fA-F0-9-]{36}"))throw new IOException("Неверный номер задания");return new File(root,id+".json");}
    private JSONObject read(String id)throws Exception{
        AtomicFile f=new AtomicFile(file(id));if(!f.getBaseFile().exists()&&!new File(f.getBaseFile()+".bak").exists())return null;
        if(f.getBaseFile().length()>16*1024*1024)throw new IOException("Журнал слишком большой");
        return new JSONObject(new String(f.readFully(),StandardCharsets.UTF_8));
    }
    private void write(String id,JSONObject value)throws Exception{
        if(!root.isDirectory()&&!root.mkdirs())throw new IOException("Не удалось сохранить очередь");
        byte[] raw=value.toString().getBytes(StandardCharsets.UTF_8);if(raw.length>16*1024*1024)throw new IOException("Журнал слишком большой");
        AtomicFile f=new AtomicFile(file(id));FileOutputStream out=f.startWrite();try{out.write(raw);f.finishWrite(out);}catch(Exception e){f.failWrite(out);throw e;}
    }
    static JSONObject node(Bundle b)throws Exception{
        FileNodes.Node n=FileNodes.Node.from(b);return new JSONObject().put("kind",n.kind).put("path",n.path).put("name",n.name).put("directory",n.directory).put("size",n.size);
    }
    static Bundle bundle(JSONObject j)throws Exception{Bundle b=new Bundle();for(String k:new String[]{"kind","path","name"})b.putString(k,j.getString(k));b.putBoolean("directory",j.getBoolean("directory"));b.putLong("size",j.getLong("size"));return FileNodes.Node.from(b).bundle();}
    boolean ensure(Intent request)throws Exception{synchronized(LOCK){
        String id=request.getStringExtra("request_id");JSONObject old=read(id);if(old!=null)return !"done".equals(old.optString("status"));
        String action=request.getAction();if(!Arrays.asList("upload","download","import","save").contains(action))throw new IOException("Неизвестная операция");
        ArrayList<Bundle> sources=request.getParcelableArrayListExtra("sources");if(sources==null||sources.isEmpty()||sources.size()>64)throw new IOException("Выбери от 1 до 64 элементов");
        String policy=request.getStringExtra("conflict");if(policy==null)policy=KEEP;if(!Arrays.asList(KEEP,SKIP,REPLACE).contains(policy))throw new IOException("Неверное правило имён");
        JSONObject j=new JSONObject().put("id",id).put("action",action).put("status","queued").put("created",System.currentTimeMillis())
                .put("conflict",policy).put("verify",request.getBooleanExtra("verify",false)).put("items",new JSONObject());
        JSONArray nodes=new JSONArray();for(Bundle b:sources)nodes.put(node(b));j.put("sources",nodes);
        if(request.hasExtra("destination"))j.put("destination",node(request.getBundleExtra("destination")));
        for(String key:new String[]{"remote","host","user","pin"})if(request.hasExtra(key))j.put(key,request.getStringExtra(key));
        j.put("port",request.getIntExtra("port",8022));write(id,j);prune();return true;
    }}
    Intent request(String id)throws Exception{synchronized(LOCK){JSONObject j=read(id);if(j==null)throw new IOException("Задание отсутствует");
        Intent i=new Intent(context,TransferForegroundService.class).setAction(j.getString("action")).putExtra("request_id",id).putExtra("conflict",j.getString("conflict")).putExtra("verify",j.getBoolean("verify"));
        ArrayList<Bundle> nodes=new ArrayList<>();JSONArray array=j.getJSONArray("sources");for(int n=0;n<array.length();n++)nodes.add(bundle(array.getJSONObject(n)));i.putParcelableArrayListExtra("sources",nodes);
        if(j.has("destination"))i.putExtra("destination",bundle(j.getJSONObject("destination")));
        for(String key:new String[]{"remote","host","user","pin"})if(j.has(key))i.putExtra(key,j.getString(key));return i.putExtra("port",j.getInt("port"));
    }}
    void status(String id,String status,String message)throws Exception{synchronized(LOCK){JSONObject j=read(id);if(j==null)throw new IOException("Журнал отсутствует");j.put("status",status).put("message",message).put("updated",System.currentTimeMillis());write(id,j);}}
    void pause(String id)throws Exception{synchronized(LOCK){JSONObject j=read(id);if(j!=null&&!"done".equals(j.optString("status"))&&!"cancelled".equals(j.optString("status")))status(id,"paused","Передача остановлена. Можно продолжить из очереди");}}
    private File itemFile(String id,String key)throws Exception{file(id);if(!key.matches("[a-f0-9]{64}"))throw new IOException("Неверная запись очереди");return new File(new File(root,id+"-items"),key+".json");}
    JSONObject item(String id,String key)throws Exception{synchronized(LOCK){AtomicFile f=new AtomicFile(itemFile(id,key));if(!f.getBaseFile().exists()&&!new File(f.getBaseFile()+".bak").exists())return new JSONObject();if(f.getBaseFile().length()>16*1024*1024)throw new IOException("Слишком большой снимок папки");return new JSONObject(new String(f.readFully(),StandardCharsets.UTF_8));}}
    void item(String id,String key,JSONObject value)throws Exception{synchronized(LOCK){
        File file=itemFile(id,key);if(!file.getParentFile().isDirectory()&&!file.getParentFile().mkdirs())throw new IOException("Не удалось сохранить состояние файла");
        byte[] bytes=value.toString().getBytes(StandardCharsets.UTF_8);if(bytes.length>16*1024*1024)throw new IOException("Слишком большой снимок папки");
        AtomicFile f=new AtomicFile(file);FileOutputStream out=f.startWrite();try{out.write(bytes);f.finishWrite(out);}catch(Exception e){f.failWrite(out);throw e;}
    }}
    List<JSONObject> list()throws Exception{synchronized(LOCK){
        File[] files=root.listFiles((d,n)->n.endsWith(".json")||n.endsWith(".json.bak"));if(files==null)return new ArrayList<>();
        Set<String> ids=new HashSet<>();for(File f:files)ids.add(f.getName().replace(".json.bak","").replace(".json",""));
        List<JSONObject> out=new ArrayList<>();for(String id:ids){JSONObject j=read(id);if(j!=null)out.add(j);}
        out.sort((a,b)->Long.compare(b.optLong("created"),a.optLong("created")));return out;
    }}
    void cancelAll()throws Exception{synchronized(LOCK){
        for(JSONObject j:list())if(!"done".equals(j.optString("status"))&&!"cancelled".equals(j.optString("status")))
            status(j.getString("id"),"cancelled","Отменено пользователем");
    }}
    /** Called only after the service writer queue has drained. User destinations are untouched. */
    void clearAll()throws Exception{synchronized(LOCK){
        for(JSONObject j:list()){
            String id=j.getString("id");
            ShareOutbox.completed(context,request(id));cleanParts(id);if(new File(root,id+"-parts").exists())throw new IOException("Не удалось очистить локальные временные копии");
            File items=new File(root,id+"-items");File[] files=items.listFiles();
            if(files!=null)for(File f:files)if(f.isFile()&&!f.delete())throw new IOException("Не удалось очистить журнал файла");
            if(items.isDirectory()&&!items.delete())throw new IOException("Не удалось очистить журнал папки");
            AtomicFile record=new AtomicFile(file(id));record.delete();
            if(record.getBaseFile().exists()||new File(record.getBaseFile()+".bak").exists())throw new IOException("Не удалось очистить список");
        }
    }}
    private void prune()throws Exception{
        int kept=0;for(JSONObject j:list())if("done".equals(j.optString("status"))||"cancelled".equals(j.optString("status"))){if(++kept>256){String id=j.getString("id");ShareOutbox.completed(context,request(id));cleanParts(id);File items=new File(root,id+"-items");File[] files=items.listFiles();if(files!=null)for(File f:files)if(f.isFile())f.delete();items.delete();new AtomicFile(file(id)).delete();}}
    }
    File partial(String id,String key)throws Exception{
        itemFile(id,key);File directory=new File(root,id+"-parts");if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("Нет места для дозагрузки");
        return new File(directory,key+".part");
    }
    void cleanParts(String id)throws Exception{file(id);File d=new File(root,id+"-parts");File[] files=d.listFiles();if(files!=null)for(File f:files)if(f.isFile())f.delete();d.delete();}
    static String key(FileNodes.Node source)throws Exception{return VerifiedCopy.hex(java.security.MessageDigest.getInstance("SHA-256").digest((source.kind+"\n"+source.path).getBytes(StandardCharsets.UTF_8)));}
}
