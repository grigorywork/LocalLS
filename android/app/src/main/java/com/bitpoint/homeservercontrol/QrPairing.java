package com.bitpoint.homeservercontrol;

import org.json.JSONObject;
import org.json.JSONTokener;
import java.util.*;

/** Two optical messages exchange public keys only; neither carries a login secret. */
final class QrPairing {
    static final long LIFETIME=10*60*1000L;
    final String type,host,user,pin,nonce,publicKey;
    final int port;
    final long expires;
    private QrPairing(String type,String host,int port,String user,String pin,String nonce,long expires,String key){
        if(!("server".equals(type)||"client".equals(type)) || !ConnectionEndpoint.validHost(host)
                || !ConnectionEndpoint.validUser(user) || port<1 || port>65535
                || !pin.matches("SHA256:[A-Za-z0-9+/]{43}") || !nonce.matches("[a-f0-9-]{36}"))
            throw new IllegalArgumentException("Неверный QR LocalLS");
        this.type=type;this.host=host;this.port=port;this.user=user;this.pin=pin;this.nonce=nonce;this.expires=expires;publicKey=key;
    }
    static QrPairing invite(String host,int port,String user,String pin,long now){
        return new QrPairing("server",host,port,user,pin,UUID.randomUUID().toString(),now+LIFETIME,"");
    }
    QrPairing reply(String publicKey)throws Exception{
        return new QrPairing("client",host,port,user,pin,nonce,expires,EmbeddedSftpServer.normalizeAuthorizedKey(publicKey));
    }
    String syncCode(String key)throws Exception{
        String canonical=EmbeddedSftpServer.normalizeAuthorizedKey(key);
        String transcript="LocalLS-login-v1\n"+host+"\n"+port+"\n"+user+"\n"+pin+"\n"+nonce+"\n"+expires+"\n"+canonical;
        byte[] hash=java.security.MessageDigest.getInstance("SHA-256").digest(transcript.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        long value=0;for(int i=0;i<6;i++)value=(value<<8)|(hash[i]&255L);
        String digits=String.format(java.util.Locale.ROOT,"%08d",value%100000000L);return digits.substring(0,4)+" "+digits.substring(4);
    }
    String encode()throws Exception{
        JSONObject j=new JSONObject().put("format","LocalLS-pairing").put("version",1).put("type",type)
                .put("host",host).put("port",port).put("user",user).put("pin",pin).put("nonce",nonce).put("expires",expires);
        if("client".equals(type))j.put("publicKey",publicKey);return j.toString();
    }
    static QrPairing decode(String text,long now)throws Exception{
        if(text==null || text.length()>12000)throw new IllegalArgumentException("Неверный QR LocalLS");
        // The protocol has primitive fields only. Reject nesting before JSONTokener's
        // recursive parser so an untrusted camera payload cannot exhaust the stack.
        final char openObject=0x7b,closeObject=0x7d;
        boolean quoted=false,escape=false,opened=false,ended=false;
        for(int i=0;i<text.length();i++){
            char ch=text.charAt(i);
            if(quoted){if(escape)escape=false;else if(ch=='\\')escape=true;else if(ch=='"')quoted=false;continue;}
            if(ch=='"'){quoted=true;continue;}
            if(ch=='/'||ch=='#'||ch==0x27)throw new IllegalArgumentException("Нестандартный JSON в QR");
            if(ch=='['||ch==']'||(ch==openObject&&(opened||ended)))throw new IllegalArgumentException("Неверная структура QR");
            if(ch==openObject)opened=true;
            if(ch==closeObject){opened=false;ended=true;}
        }
        JSONTokener parser=new JSONTokener(text);Object value=parser.nextValue();
        if(!(value instanceof JSONObject)||parser.nextClean()!=0)throw new IllegalArgumentException("Неверный QR LocalLS");
        JSONObject j=(JSONObject)value;String type=j.getString("type");
        Set<String> allowed=new HashSet<>(Arrays.asList("format","version","type","host","port","user","pin","nonce","expires"));
        if("client".equals(type))allowed.add("publicKey");
        if(j.length()!=allowed.size())throw new IllegalArgumentException("Неизвестные поля QR");
        for(Iterator<String> it=j.keys();it.hasNext();)if(!allowed.contains(it.next()))throw new IllegalArgumentException("Неизвестные поля QR");
        if(!"LocalLS-pairing".equals(j.get("format")) || !(j.get("version") instanceof Integer) || j.getInt("version")!=1
                || !(j.get("port") instanceof Integer) || !(j.get("expires") instanceof Long || j.get("expires") instanceof Integer))
            throw new IllegalArgumentException("Неподдерживаемый QR");
        for(String key:new String[]{"type","host","user","pin","nonce"})if(!(j.get(key) instanceof String))throw new IllegalArgumentException("Неверное поле QR");
        long expires=j.getLong("expires");
        if(expires<=now || expires-now>LIFETIME)throw new IllegalArgumentException("QR истёк. Покажи новый код на сервере и проверь время телефонов");
        String key="";
        if("client".equals(type)){
            if(!(j.get("publicKey") instanceof String))throw new IllegalArgumentException("Неверный публичный ключ");
            key=EmbeddedSftpServer.normalizeAuthorizedKey(j.getString("publicKey"));
        }
        return new QrPairing(type,j.getString("host"),j.getInt("port"),j.getString("user"),j.getString("pin"),j.getString("nonce"),expires,key);
    }
    boolean matches(QrPairing reply){
        return "server".equals(type)&&"client".equals(reply.type)&&host.equals(reply.host)&&port==reply.port
                &&user.equals(reply.user)&&pin.equals(reply.pin)&&nonce.equals(reply.nonce)&&expires==reply.expires;
    }
}
