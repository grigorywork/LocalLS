package com.bitpoint.homeservercontrol;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class InterruptedShutdownTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private static KeyPair host;private static PublicKey allowed;private static SshCredentials credentials;
    @BeforeClass public static void keys()throws Exception{
        var rsa=KeyPairGenerator.getInstance("RSA");rsa.initialize(3072);host=rsa.generateKeyPair();var key=com.jcraft.jsch.KeyPair.genKeyPair(new com.jcraft.jsch.JSch(),com.jcraft.jsch.KeyPair.RSA,3072);ByteArrayOutputStream privateKey=new ByteArrayOutputStream(),publicKey=new ByteArrayOutputStream();key.writePrivateKey(privateKey);key.writePublicKey(publicKey,"test");key.dispose();credentials=SshCredentials.key(privateKey.toString("UTF-8"),"");allowed=EmbeddedSftpServer.authorizedKey(publicKey.toString("UTF-8"));
    }
    @Test public void interruptedShutdownPreservesCallerFlagAndRestartsOnSamePortWithSameKeys()throws Exception{
        Path root=temp.newFolder().toPath();byte[] bytes={2,5,9};Files.write(root.resolve("canary.bin"),bytes);String pin=EmbeddedSftpServer.fingerprint(host);int port=0;
        for(int round=0;round<10;round++){
            EmbeddedSftpServer server=new EmbeddedSftpServer();SftpClient.TreeSession client=null;List<Socket> raw=new ArrayList<>();Thread peer=null;AtomicReference<Throwable> peerError=new AtomicReference<>();
            try{
                port=server.startAuthorized("127.0.0.1",port,"localls",List.of(allowed),host,root);client=new SftpClient.TreeSession("127.0.0.1",port,"localls",credentials,pin);ByteArrayOutputStream got=new ByteArrayOutputStream();client.read("/canary.bin",got,(n,total)->true);assertArrayEquals(bytes,got.toByteArray());
                for(int i=0;i<4;i++)raw.add(new Socket("127.0.0.1",port));final var closing=client;peer=new Thread(()->{try{closing.close();for(Socket socket:raw)socket.close();}catch(Throwable e){peerError.set(e);}},"disposable-shutdown-peer");peer.start();boolean preserved;
                Thread.currentThread().interrupt();try{server.close();preserved=Thread.currentThread().isInterrupted();}finally{Thread.interrupted();}
                peer.join(5000);assertFalse("Peer cleanup stalled",peer.isAlive());assertNull(peerError.get());assertTrue("Shutdown lost the caller's interruption flag",preserved);assertArrayEquals(bytes,Files.readAllBytes(root.resolve("canary.bin")));
            }finally{Thread.interrupted();if(client!=null)client.close();for(Socket socket:raw)socket.close();server.close();if(peer!=null)peer.join(5000);}
        }
        try(EmbeddedSftpServer restarted=new EmbeddedSftpServer()){
            assertEquals(port,restarted.startAuthorized("127.0.0.1",port,"localls",List.of(allowed),host,root));try(var client=new SftpClient.TreeSession("127.0.0.1",port,"localls",credentials,pin)){ByteArrayOutputStream got=new ByteArrayOutputStream();client.read("/canary.bin",got,(n,total)->true);assertArrayEquals(bytes,got.toByteArray());}
        }
    }
}
