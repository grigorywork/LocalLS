package com.bitpoint.homeservercontrol;

import android.content.Context;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Public pairing progress only. Access is always enforced by pinned SSH and private-key proof. */
final class QrPairingState {
    private static final String PENDING="qr_pending_invitation",BOUND="qr_confirmed_";
    private QrPairingState(){}
    static boolean matchesCurrent(Context c,QrPairing code){
        FileConnection current=FileConnection.current(c);
        return current.host.equals(code.host)&&current.port==code.port&&current.user.equals(code.user)&&current.pin.equals(code.pin);
    }
    static QrPairing pending(Context c){
        String text=c.getSharedPreferences(ServerConfig.PREFS,0).getString(PENDING,"");
        if(text.isEmpty())return null;
        try{
            QrPairing code=QrPairing.decode(text,System.currentTimeMillis());
            return "server".equals(code.type)&&matchesCurrent(c,code)&&SshKeys.hasStoredKey(c,code.host,code.port,code.user)?code:null;
        }catch(Exception expired){c.getSharedPreferences(ServerConfig.PREFS,0).edit().remove(PENDING).apply();return null;}
    }
    static void remember(Context c,QrPairing code,String publicKey)throws Exception{
        if(!matchesCurrent(c,code))throw new IOException("Подключение изменилось");
        if(!c.getSharedPreferences(ServerConfig.PREFS,0).edit().putString(PENDING,code.encode()).remove(key(code,publicKey)).commit())throw new IOException("Не удалось сохранить обмен QR");
    }
    static boolean confirmed(Context c,QrPairing code,String publicKey)throws Exception{
        return matchesCurrent(c,code)&&c.getSharedPreferences(ServerConfig.PREFS,0).getBoolean(key(code,publicKey),false);
    }
    static boolean confirmed(Context c,FileConnection route,String publicKey)throws Exception{
        return route.matches(FileConnection.current(c).bundle())&&c.getSharedPreferences(ServerConfig.PREFS,0)
                .getBoolean(key(route.host,route.port,route.user,route.pin,publicKey),false);
    }
    static void complete(Context c,QrPairing code,String publicKey)throws Exception{
        if(!matchesCurrent(c,code))throw new IOException("Подключение изменилось");
        if(!c.getSharedPreferences(ServerConfig.PREFS,0).edit().putBoolean(key(code,publicKey),true).remove(PENDING).commit())throw new IOException("Не удалось сохранить привязку");
    }
    private static String key(QrPairing code,String publicKey)throws Exception{
        return key(code.host,code.port,code.user,code.pin,publicKey);
    }
    private static String key(String host,int port,String user,String pin,String publicKey)throws Exception{
        String identity=host+"\n"+port+"\n"+user+"\n"+pin+"\n"+publicKey;
        return BOUND+android.util.Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8)),android.util.Base64.NO_WRAP);
    }
}
