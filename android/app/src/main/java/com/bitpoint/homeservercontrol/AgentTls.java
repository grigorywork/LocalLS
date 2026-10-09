package com.bitpoint.homeservercontrol;

import android.content.Context;
import java.net.InetSocketAddress;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import javax.net.ssl.*;

/** Explicit certificate pinning; bootstrap probe never sends credentials. */
final class AgentTls {
    static final String LEGACY="legacy_agent_http";
    private static String pref(String host,int port){return "agent_tls_sha256_"+host.toLowerCase(java.util.Locale.ROOT)+":"+port;}
    static String pin(Context c,String host,int port){return SshKeys.prefs(c).getString(pref(host,port),"");}
    static void trust(Context c,String host,int port,String pin){SshKeys.prefs(c).edit().putString(pref(host,port),pin).putBoolean(LEGACY,false).apply();}
    static String fingerprint(X509Certificate cert) throws Exception {byte[] hash=MessageDigest.getInstance("SHA-256").digest(cert.getEncoded());StringBuilder b=new StringBuilder("SHA256:");for(byte n:hash)b.append(String.format(java.util.Locale.ROOT,"%02x",n&255));return b.toString();}
    static SSLSocketFactory factory(String expected,X509Certificate[] capture) throws Exception {
        if(capture==null&&!expected.matches("SHA256:[a-f0-9]{64}"))throw new Exception("Подтвердите HTTPS-сертификат агента");
        X509TrustManager trust=new X509TrustManager(){
            public X509Certificate[] getAcceptedIssuers(){return new X509Certificate[0];}
            public void checkClientTrusted(X509Certificate[] c,String a)throws java.security.cert.CertificateException{throw new java.security.cert.CertificateException("Client authentication unsupported");}
            public void checkServerTrusted(X509Certificate[] chain,String auth)throws java.security.cert.CertificateException{
                try{if(chain==null||chain.length==0||chain.length>8||chain[0].getEncoded().length>16384)throw new Exception("No certificate");
                    if(capture!=null){capture[0]=chain[0];return;}
                    chain[0].checkValidity();if(!expected.equals(fingerprint(chain[0])))throw new Exception("Certificate changed");
                }catch(Exception e){throw new java.security.cert.CertificateException("Сертификат агента не совпал или истёк");}
            }
        };
        SSLContext context=SSLContext.getInstance("TLSv1.2");context.init(null,new TrustManager[]{trust},null);return context.getSocketFactory();
    }
    static String probe(String host,int port)throws Exception{
        X509Certificate[] capture=new X509Certificate[1];
        try(SSLSocket socket=(SSLSocket)factory("",capture).createSocket()){
            socket.connect(new InetSocketAddress(host,port),5000);socket.setSoTimeout(5000);socket.startHandshake();
            if(capture[0]==null)throw new Exception("Нет сертификата");return fingerprint(capture[0]);
        }
    }
}
