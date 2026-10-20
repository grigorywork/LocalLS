package com.bitpoint.homeservercontrol;
import android.content.SharedPreferences;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.TreeSet;

final class RemotePlaces {
    private final SharedPreferences prefs;
    private final String key;
    RemotePlaces(SharedPreferences prefs, String host, int port, String user) {
        this.prefs=prefs;
        try {
            byte[] digest=MessageDigest.getInstance("SHA-256").digest((host+"\n"+port+"\n"+user).getBytes(StandardCharsets.UTF_8));
            StringBuilder id=new StringBuilder(); for(byte b:digest) id.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
            key="remote_places_"+id;
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    String last() { return prefs.getString(key+"_last", "."); }
    void remember(String path) { try { validate(path); } catch (IllegalArgumentException ignored) { return; } prefs.edit().putString(key+"_last",path).apply(); }
    ArrayList<String> favorites() { return new ArrayList<>(new TreeSet<>(prefs.getStringSet(key+"_favorites", java.util.Collections.emptySet()))); }
    void add(String path) {
        validate(path); TreeSet<String> set=new TreeSet<>(favorites());
        if(set.size()>=20 && !set.contains(path)) throw new IllegalArgumentException("Можно сохранить до 20 папок");
        set.add(path); prefs.edit().putStringSet(key+"_favorites",set).apply();
    }
    void remove(String path) { TreeSet<String> set=new TreeSet<>(favorites());set.remove(path);prefs.edit().putStringSet(key+"_favorites",set).apply(); }
    private static void validate(String path) {
        if(path==null || !path.startsWith("/") || path.length()>4096 || path.matches("(?s).*[\\x00-\\x1f\\x7f].*")) throw new IllegalArgumentException("Нужен проверенный абсолютный путь");
    }
}
