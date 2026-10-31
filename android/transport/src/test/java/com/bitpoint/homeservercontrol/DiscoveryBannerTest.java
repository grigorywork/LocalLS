package com.bitpoint.homeservercontrol;

import org.junit.Test;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import static org.junit.Assert.*;

/** SSH identification is a byte stream; a TCP read need not contain the entire prefix. */
public class DiscoveryBannerTest {
    private static final class SplitBanner implements AutoCloseable {
        final ServerSocket server=new ServerSocket();
        final ExecutorService worker=Executors.newSingleThreadExecutor();
        SplitBanner(boolean slow)throws Exception{
            server.bind(new InetSocketAddress("127.0.0.2",0));
            worker.submit(()->{
                try(Socket client=server.accept()){
                    OutputStream out=client.getOutputStream();out.write('S');out.flush();
                    Thread.sleep(slow?220:120);
                    out.write((slow?"S":"SH-2.0-split-fixture\r\n").getBytes(StandardCharsets.US_ASCII));out.flush();
                    if(slow){Thread.sleep(220);out.write("H-2.0-too-late\r\n".getBytes(StandardCharsets.US_ASCII));out.flush();}
                }catch(IOException ignored){}catch(InterruptedException e){Thread.currentThread().interrupt();}
            });
        }
        @Override public void close()throws Exception{server.close();worker.shutdownNow();assertTrue(worker.awaitTermination(2,TimeUnit.SECONDS));}
    }
    @Test public void fragmentedSshPrefixIsDiscoveredWithoutAuthentication()throws Exception{
        try(SplitBanner endpoint=new SplitBanner(false)){
            LocalServerDiscovery.Result result=LocalServerDiscovery.discover("127.0.0.1",endpoint.server.getLocalPort());
            assertEquals("A split TCP prefix is still an SSH endpoint",java.util.List.of("127.0.0.2"),result.hosts);
        }
    }
    @Test public void slowTrickleCannotExtendTheOverallBannerReadDeadline()throws Exception{
        try(SplitBanner endpoint=new SplitBanner(true)){
            long start=System.nanoTime();LocalServerDiscovery.Result result=LocalServerDiscovery.discover("127.0.0.1",endpoint.server.getLocalPort());
            assertTrue(result.hosts.isEmpty());assertTrue("Discovery remains bounded",TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start)<2500);
        }
    }
}
