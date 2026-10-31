package com.bitpoint.homeservercontrol;

import android.content.Context;
import android.content.SharedPreferences;

final class HostTrustStore {
    private HostTrustStore() {}

    static String get(Context context, String host, int port) {
        return prefs(context).getString(key(host, port), "");
    }

    static synchronized void trust(Context context, String host, int port, String fingerprint) {
        prefs(context).edit().putString(key(host, port), fingerprint).apply();
    }

    static synchronized void clear(Context context, String host, int port) {
        prefs(context).edit().remove(key(host, port)).apply();
    }
    /** Compare and commit public QR fields together; a delayed task cannot replace newer trust. */
    static synchronized void pair(Context c,String host,int port,String user,String fingerprint)throws java.io.IOException{
        if(!ConnectionEndpoint.validHost(host)||!ConnectionEndpoint.validUser(user)||port<1||port>65535
                ||fingerprint==null||!fingerprint.matches("SHA256:[A-Za-z0-9+/]{43}"))throw new java.io.IOException("Неверное подключение QR");
        String previous=get(c,host,port);if(!previous.isEmpty()&&!previous.equals(fingerprint))throw new java.io.IOException("Ключ сервера изменился. Подключение заблокировано");
        if(!prefs(c).edit().putString(key(host,port),fingerprint).putString(ServerConfig.KEY_LOCAL_HOST,host)
                .putInt(ServerConfig.KEY_PORT,port).putString(ServerConfig.KEY_USER,user).putString(ServerConfig.KEY_MODE,ServerConfig.MODE_LOCAL)
                .remove(ServerConfig.KEY_LAST_WORKING_HOST).commit())throw new java.io.IOException("Не удалось сохранить подключение");
    }

    /** Local trusted addresses only; never probes or discovers network devices. */
    static java.util.List<String> hostsForFingerprint(Context context,int port,String fingerprint) {
        java.util.Set<String> hosts=new java.util.TreeSet<>();
        if(fingerprint==null || fingerprint.isEmpty())return new java.util.ArrayList<>(hosts);
        String prefix=ServerConfig.KEY_TRUSTED_FINGERPRINT_PREFIX,suffix=":"+port;
        for(java.util.Map.Entry<String,?> entry:prefs(context).getAll().entrySet()) {
            String name=entry.getKey();
            if(!name.startsWith(prefix) || !name.endsWith(suffix) || !fingerprint.equals(entry.getValue()))continue;
            String host=name.substring(prefix.length(),name.length()-suffix.length());
            if(ConnectionEndpoint.validHost(host))hosts.add(host);
        }
        return new java.util.ArrayList<>(hosts);
    }

    private static String key(String host, int port) {
        return ServerConfig.KEY_TRUSTED_FINGERPRINT_PREFIX + host + ":" + port;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(ServerConfig.PREFS, Context.MODE_PRIVATE);
    }
}
