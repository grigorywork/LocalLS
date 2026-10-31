package com.bitpoint.homeservercontrol;
import android.content.Context;
/** Shared public invitation for the inline card and the full approval screen. Never contains a private key. */
final class ServerInvitation {
    private static QrPairing current;
    static synchronized QrPairing get(Context c,QrPairing preferred)throws Exception{
        if(!EmbeddedServerService.running)throw new IllegalStateException("Сначала включи сервер");
        var addresses=LocalServerAddresses.current(c);if(addresses.isEmpty())throw new IllegalStateException("Подключи телефон к Wi-Fi");
        String user=c.getSharedPreferences(EmbeddedServerService.PREFS,0).getString("user","localls");
        if(!valid(current,addresses,user))current=valid(preferred,addresses,user)?preferred:QrPairing.invite(addresses.get(0),EmbeddedServerService.boundPort,user,EmbeddedServerService.fingerprint,System.currentTimeMillis());
        EmbeddedServerService.beginQrPairing(current);return current;
    }
    private static boolean valid(QrPairing q,java.util.List<String> addresses,String user){return q!=null&&q.expires>System.currentTimeMillis()&&q.pin.equals(EmbeddedServerService.fingerprint)&&q.port==EmbeddedServerService.boundPort&&q.user.equals(user)&&addresses.contains(q.host);}
    static synchronized QrPairing active(Context c){
        if(!EmbeddedServerService.running)return null;
        return valid(current,LocalServerAddresses.current(c),c.getSharedPreferences(EmbeddedServerService.PREFS,0).getString("user","localls"))?current:null;
    }
    /** A new visible offer can enroll another device; approved keys keep ordinary SSH access. */
    static synchronized boolean renewCompleted(Context c){
        QrPairing q=active(c);if(q==null)return false;String key=EmbeddedServerService.pendingQrKey(q.nonce);
        if(!key.isEmpty()&&ServerDevices.contains(c,key)){EmbeddedServerService.endQrPairing(q.nonce);current=null;return true;}return false;
    }
    static synchronized void reset(){if(current!=null)EmbeddedServerService.endQrPairing(current.nonce);current=null;}
}
