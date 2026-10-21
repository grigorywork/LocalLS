package com.bitpoint.homeservercontrol;

import android.content.Context;
import android.os.Bundle;
import java.util.Objects;

/** Immutable destination shown by a file pane and carried into its queued transfers. */
final class FileConnection {
    final String host,user,pin;final int port;
    private FileConnection(String host,int port,String user,String pin){this.host=host;this.port=port;this.user=user;this.pin=pin;}
    static FileConnection current(Context c){
        var p=c.getSharedPreferences(ServerConfig.PREFS,0);String host=ConnectionSelector.hostForFiles(c);int port=p.getInt(ServerConfig.KEY_PORT,8022);
        return new FileConnection(host,port,p.getString(ServerConfig.KEY_USER,"localls"),HostTrustStore.get(c,host,port));
    }
    Bundle bundle(){Bundle b=new Bundle();b.putString("host",host);b.putInt("port",port);b.putString("user",user);b.putString("pin",pin);return b;}
    boolean matches(Bundle b){return b!=null && Objects.equals(host,b.getString("host")) && port==b.getInt("port") && Objects.equals(user,b.getString("user")) && Objects.equals(pin,b.getString("pin"));}
}
