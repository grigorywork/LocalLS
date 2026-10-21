package com.bitpoint.homeservercontrol;

import org.junit.Test;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import static org.junit.Assert.*;

public class LocalServerDiscoveryTest {
    private static final class Banner implements AutoCloseable {
        final ServerSocket socket;final ExecutorService worker=Executors.newSingleThreadExecutor();
        Banner(String host,int port,String banner)throws Exception{
            socket=new ServerSocket();socket.bind(new InetSocketAddress(host,port));
            worker.execute(()->{while(!socket.isClosed())try(Socket client=socket.accept()){client.getOutputStream().write(banner.getBytes(StandardCharsets.US_ASCII));}catch(IOException ignored){}});
        }
        public void close()throws Exception{socket.close();worker.shutdownNow();assertTrue(worker.awaitTermination(3,TimeUnit.SECONDS));}
    }
    @Test public void discoveryFindsOnlySshBannerAndSkipsOwnPhone()throws Exception{
        try(Banner own=new Banner("127.0.0.1",0,"SSH-2.0-own\r\n");Banner ssh=new Banner("127.0.0.2",own.socket.getLocalPort(),"SSH-2.0-fixture\r\n");Banner http=new Banner("127.0.0.3",own.socket.getLocalPort(),"HTTP/1.1 200 OK\r\n")){
            LocalServerDiscovery.Result result=LocalServerDiscovery.discover("127.0.0.1",own.socket.getLocalPort());assertEquals(java.util.Collections.singletonList("127.0.0.2"),result.hosts);assertEquals("127.0.0.1",result.localIp);
        }
    }
    @Test public void missingAddressReturnsActionableResultWithoutProbing(){LocalServerDiscovery.Result result=LocalServerDiscovery.discover(null,8022);assertTrue(result.hosts.isEmpty());assertTrue(result.message.contains("Wi-Fi"));}
    @Test public void cancellationPreservesInterruptAndStopsDiscovery()throws Exception{
        int port;try(ServerSocket pick=new ServerSocket(0)){port=pick.getLocalPort();}Thread.currentThread().interrupt();
        try{LocalServerDiscovery.Result result=LocalServerDiscovery.discover("127.0.0.1",port);assertTrue(result.hosts.isEmpty());assertTrue(result.message.contains("отменён"));assertTrue(Thread.currentThread().isInterrupted());}finally{Thread.interrupted();}
    }
}
