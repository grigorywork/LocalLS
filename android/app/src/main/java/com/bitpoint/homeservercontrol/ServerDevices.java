package com.bitpoint.homeservercontrol;

import android.content.Context;
import java.io.IOException;
import java.util.*;
import org.json.*;

/** Public allow-list. The original allowed_key remains its first entry for legacy settings. */
final class ServerDevices {
    static final String KEY="allowed_devices_v1";
    static final class Device {
        final String key,name;final long created;
        Device(String key,String name,long created){this.key=key;this.name=name;this.created=created;}
    }
    static synchronized List<Device> list(Context c)throws Exception{
        var p=c.getSharedPreferences(EmbeddedServerService.PREFS,0);ArrayList<Device> result=new ArrayList<>();Set<String> keys=new HashSet<>();
        if(p.contains(KEY)){
            JSONArray array=new JSONArray(p.getString(KEY,""));if(array.length()>EmbeddedSftpServer.MAX_DEVICES)throw new IOException("Список устройств повреждён");
            for(int i=0;i<array.length();i++){
                JSONObject item=array.getJSONObject(i);String key=EmbeddedSftpServer.normalizeAuthorizedKey(item.getString("key"));
                if(!keys.add(key))throw new IOException("Повтор ключа устройства");
                result.add(new Device(key,label(item.getString("name")),item.getLong("created")));
            }
        }
        String legacy=p.getString("allowed_key","").trim();
        if(!legacy.isEmpty()){
            legacy=EmbeddedSftpServer.normalizeAuthorizedKey(legacy);
            if(keys.add(legacy))result.add(0,new Device(legacy,"Прежнее устройство",0));
        }
        if(result.size()>EmbeddedSftpServer.MAX_DEVICES)throw new IOException("Превышен лимит устройств");return result;
    }
    static synchronized void add(Context c,String text)throws Exception{
        String key=EmbeddedSftpServer.normalizeAuthorizedKey(text);List<Device> devices=list(c);
        for(Device device:devices)if(device.key.equals(key))return;
        if(devices.size()>=EmbeddedSftpServer.MAX_DEVICES)throw new IOException("Уже добавлено 7 устройств. Сначала отзови доступ одного из них");
        devices.add(new Device(key,"Устройство "+(devices.size()+1),System.currentTimeMillis()));save(c,devices);
    }
    static synchronized void remove(Context c,String text)throws Exception{
        String key=EmbeddedSftpServer.normalizeAuthorizedKey(text);List<Device> devices=list(c);devices.removeIf(d->d.key.equals(key));save(c,devices);
    }
    static synchronized void rename(Context c,String text,String name)throws Exception{
        String key=EmbeddedSftpServer.normalizeAuthorizedKey(text);List<Device> devices=list(c);
        for(int i=0;i<devices.size();i++)if(devices.get(i).key.equals(key)){Device d=devices.get(i);devices.set(i,new Device(key,label(name),d.created));save(c,devices);return;}
        throw new IOException("Устройство уже удалено");
    }
    static boolean contains(Context c,String text){try{String key=EmbeddedSftpServer.normalizeAuthorizedKey(text);for(Device d:list(c))if(d.key.equals(key))return true;}catch(Exception ignored){}return false;}
    static int count(Context c){try{return list(c).size();}catch(Exception error){return 0;}}
    static void validateLegacyEdit(Context c,String text)throws Exception{
        var p=c.getSharedPreferences(EmbeddedServerService.PREFS,0);
        if(p.contains(KEY)&&!p.getString("allowed_key","").equals(text))throw new IOException("Меняй доступ через список устройств");
    }
    private static String label(String text)throws IOException{String value=text.trim();if(value.isEmpty()||value.length()>80||value.chars().anyMatch(ch->Character.isISOControl(ch)))throw new IOException("Имя устройства: от 1 до 80 символов");return value;}
    private static void save(Context c,List<Device> devices)throws Exception{
        JSONArray array=new JSONArray();for(Device d:devices)array.put(new JSONObject().put("key",d.key).put("name",d.name).put("created",d.created));
        if(!c.getSharedPreferences(EmbeddedServerService.PREFS,0).edit().putString(KEY,array.toString()).putString("allowed_key",devices.isEmpty()?"":devices.get(0).key).commit())throw new IOException("Не удалось сохранить устройства");
    }
}
