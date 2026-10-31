package com.bitpoint.homeservercontrol;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** Bounded foreground loopback identification check. Does not authenticate or write settings. */
final class ServerHealthProbe {
    static float read(int port) {
        if(port<1||port>65535)return Float.NaN;
        long start=System.nanoTime(),deadline=start+TimeUnit.MILLISECONDS.toNanos(700);
        try(Socket socket=new Socket()){
            socket.connect(new InetSocketAddress(InetAddress.getByAddress(new byte[]{127,0,0,1}),port),700);
            byte[] line=new byte[255];int used=0;
            while(used<line.length){
                long remaining=deadline-System.nanoTime();if(remaining<=0)return Float.NaN;
                socket.setSoTimeout((int)Math.max(1,TimeUnit.NANOSECONDS.toMillis(remaining)));
                int c=socket.getInputStream().read();if(c<0)return Float.NaN;
                if(c=='\n'){
                    String banner=new String(line,0,used,StandardCharsets.US_ASCII);
                    return banner.startsWith("SSH-2.0-")||banner.startsWith("SSH-1.99-")?Math.max(.1f,(System.nanoTime()-start)/1000000f):Float.NaN;
                }
                line[used++]=(byte)c;
            }
        }catch(IOException ignored){}
        return Float.NaN;
    }
}
