package com.bitpoint.homeservercontrol;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.Duration;
import java.util.*;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.client.channel.ClientChannel;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.signature.BuiltinSignatures;
import org.apache.sshd.sftp.client.SftpClient;
import org.apache.sshd.sftp.client.SftpClientFactory;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/** Bounded attacks against disposable loopback servers, using generated test identities. */
public class ServerAttackSimulationTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private static KeyPair host,first,second;
    @BeforeClass public static void identities()throws Exception{
        KeyPairGenerator rsa=KeyPairGenerator.getInstance("RSA");rsa.initialize(3072);host=rsa.generateKeyPair();
        KeyPairGenerator ec=KeyPairGenerator.getInstance("EC");ec.initialize(256);first=ec.generateKeyPair();second=ec.generateKeyPair();
    }
    private SshClient client(){
        SshClient client=SshClient.setUpDefaultClient();
        // The attack harness uses Apache SSHD clients, unlike the app's JSch client.
        // Drain their late NIO2 close completions too; packet/auth assertions stay unchanged.
        client.setIoServiceFactoryFactory(new org.apache.sshd.common.io.nio2.Nio2ServiceFactoryFactory(){
            @Override public org.apache.sshd.common.io.IoServiceFactory create(org.apache.sshd.common.FactoryManager manager){
                var pool=org.apache.sshd.common.util.threads.ThreadUtils.newFixedThreadPool(manager+"-attack-client-nio2",org.apache.sshd.common.io.AbstractIoServiceFactory.getNioWorkers(manager));
                return new org.apache.sshd.common.io.nio2.Nio2ServiceFactory(manager,new NioCompletionExecutor(pool),null);
            }
        });
        client.setServerKeyVerifier((s,address,key)->KeyUtils.compareKeys(host.getPublic(),key));client.start();return client;
    }
    @Test public void clientHarnessRunsLateIoCompletionAfterShutdown()throws Exception{
        SshClient client=client();
        var factory=(org.apache.sshd.common.io.nio2.Nio2ServiceFactory)client.getIoServiceFactory();
        var completion=org.apache.sshd.common.util.threads.ThreadUtils.noClose(factory.getExecutorService());
        java.util.concurrent.atomic.AtomicInteger calls=new java.util.concurrent.atomic.AtomicInteger();
        try{client.stop();completion.execute(calls::incrementAndGet);assertEquals("Late NIO completion must run exactly once",1,calls.get());assertTrue(completion.awaitTermination(4,java.util.concurrent.TimeUnit.SECONDS));}
        finally{client.close();}
    }
    private ClientSession connect(SshClient client,int port,KeyPair key)throws Exception{
        ClientSession session=client.connect("localls","127.0.0.1",port).verify(Duration.ofSeconds(4)).getSession();
        try{session.addPublicKeyIdentity(key);session.auth().verify(Duration.ofSeconds(4));return session;}
        catch(Exception e){session.close(true);throw e;}
    }
    private int start(EmbeddedSftpServer server,Path root)throws Exception{
        return server.startAuthorized("127.0.0.1",0,"localls",Arrays.asList(first.getPublic(),second.getPublic()),host,root);
    }
    private Path root()throws Exception{
        Path root=temp.newFolder().toPath();Files.write(root.resolve("canary.bin"),new byte[]{2,7,9});return root;
    }
    private void readable(SshClient client,int port)throws Exception{
        try(ClientSession session=connect(client,port,second);SftpClient files=SftpClientFactory.instance().createSftpClient(session);
            var handle=files.open("/canary.bin")){
            byte[] bytes=new byte[3];assertEquals(3,files.read(handle,0,bytes));assertArrayEquals(new byte[]{2,7,9},bytes);
        }
    }
    @Test public void channelHandleFloodIsBoundedAndCapacityRecoversAfterCloseAndFailedOpen()throws Exception{
        List<SftpClient.CloseableHandle> held=new ArrayList<>();
        try(EmbeddedSftpServer server=new EmbeddedSftpServer();SshClient client=client()){
            int port=start(server,root());
            try(ClientSession session=connect(client,port,first);SftpClient files=SftpClientFactory.instance().createSftpClient(session)){
                try{
                    for(int i=0;i<16;i++)held.add(files.open("/canary.bin"));
                    try(var excess=files.open("/canary.bin")){fail("A channel retained more than 16 file handles");}
                    catch(org.apache.sshd.sftp.common.SftpException expected){}
                    readable(client,port);held.remove(0).close();
                    try(var reused=files.open("/canary.bin")){assertTrue(reused.isOpen());}
                }finally{for(var h:held)h.close();}
                for(int i=0;i<40;i++){
                    try(var missing=files.open("/absent.bin")){fail("Missing file opened");}
                    catch(org.apache.sshd.sftp.common.SftpException expected){}
                }
                try(var recovered=files.open("/canary.bin")){assertTrue(recovered.isOpen());}
            }
            readable(client,port);
        }
    }
    @Test public void deviceQuotaCoversFilesDirectoriesAndSessionsBeforeAnyTruncation()throws Exception{
        Path root=root();List<SftpClient.CloseableHandle> held=new ArrayList<>();
        try(EmbeddedSftpServer server=new EmbeddedSftpServer();SshClient client=client()){
            int port=start(server,root);
            try(ClientSession a=connect(client,port,first);ClientSession b=connect(client,port,first);
                SftpClient one=SftpClientFactory.instance().createSftpClient(a);
                SftpClient two=SftpClientFactory.instance().createSftpClient(b);
                SftpClient three=SftpClientFactory.instance().createSftpClient(b)){
                try{
                    for(int i=0;i<16;i++){held.add(one.open("/canary.bin"));held.add(two.openDir("/"));}
                    try(var excess=three.openDir("/")){fail("One device retained more than 32 handles across sessions");}
                    catch(org.apache.sshd.sftp.common.SftpException expected){}
                    try(var excess=three.open("/canary.bin",SftpClient.OpenMode.Write,SftpClient.OpenMode.Truncate)){fail("Quota bypassed through write");}
                    catch(org.apache.sshd.sftp.common.SftpException expected){}
                    assertArrayEquals(new byte[]{2,7,9},Files.readAllBytes(root.resolve("canary.bin")));
                    readable(client,port);held.remove(0).close();
                    try(var reused=three.openDir("/")){assertTrue(reused.isOpen());}
                }finally{for(var h:held)h.close();}
            }
            // Closing a channel with retained handles must free the device quota.
            try(ClientSession a=connect(client,port,first);ClientSession b=connect(client,port,first);
                SftpClient one=SftpClientFactory.instance().createSftpClient(a);
                SftpClient two=SftpClientFactory.instance().createSftpClient(b)){
                for(int i=0;i<16;i++){one.open("/canary.bin");two.openDir("/");}
            }
            long deadline=System.nanoTime()+Duration.ofSeconds(4).toNanos();
            while(true){
                try(ClientSession session=connect(client,port,first);SftpClient files=SftpClientFactory.instance().createSftpClient(session)){
                    List<SftpClient.CloseableHandle> again=new ArrayList<>();
                    try{for(int i=0;i<16;i++)again.add(files.openDir("/"));break;}
                    catch(org.apache.sshd.sftp.common.SftpException busy){if(System.nanoTime()>deadline)throw busy;}
                    finally{for(var h:again)h.close();}
                }
            }
            readable(client,port);
        }
    }
    @Test public void sha1HostSignaturesAreRejectedWhileTheSameRsaIdentityStillWorks()throws Exception{
        try(EmbeddedSftpServer server=new EmbeddedSftpServer();SshClient attacker=client();SshClient normal=client()){
            int port=start(server,root());attacker.setSignatureFactories(Collections.singletonList(BuiltinSignatures.rsa));
            readable(normal,port);negotiationDenied(attacker,port);
            readable(normal,port);
        }
    }
    private void negotiationDenied(SshClient attacker,int port)throws Exception{
        // No client identity/auth request: rejection must occur in the transport negotiation.
        try(ClientSession session=attacker.connect("localls","127.0.0.1",port).verify(Duration.ofSeconds(4)).getSession()){
            var events=session.waitFor(EnumSet.of(ClientSession.ClientSessionEvent.WAIT_AUTH,ClientSession.ClientSessionEvent.CLOSED),Duration.ofSeconds(4));
            assertFalse("Weak transport negotiated successfully",events.contains(ClientSession.ClientSessionEvent.WAIT_AUTH));
            assertTrue("Transport did not reject the weak proposal",events.contains(ClientSession.ClientSessionEvent.CLOSED));
        }catch(IOException expected){}
    }
    @Test public void legacyCbcAndGroup1AreRejectedBeforeAnyAuthentication()throws Exception{
        try(EmbeddedSftpServer server=new EmbeddedSftpServer();SshClient cipher=client();SshClient kex=client();SshClient normal=client()){
            int port=start(server,root());
            cipher.setCipherFactories(Collections.singletonList(org.apache.sshd.common.cipher.BuiltinCiphers.aes128cbc));
            kex.setKeyExchangeFactories(Collections.singletonList(org.apache.sshd.client.kex.DHGClient.newFactory(org.apache.sshd.common.kex.BuiltinDHFactories.dhg1)));
            readable(normal,port);negotiationDenied(cipher,port);negotiationDenied(kex,port);readable(normal,port);
        }
    }
    @Test public void pathBasedMetadataOperationsCannotLeakPersistentHandleQuota()throws Exception{
        List<SftpClient.CloseableHandle> held=new ArrayList<>();
        try(EmbeddedSftpServer server=new EmbeddedSftpServer();SshClient client=client()){
            int port=start(server,root());
            try(ClientSession session=connect(client,port,first);
                SftpClient one=SftpClientFactory.instance().createSftpClient(session);
                SftpClient two=SftpClientFactory.instance().createSftpClient(session)){
                for(int i=0;i<40;i++)one.setStat("/canary.bin",new SftpClient.Attributes().size(3));
                var hashes=one.getExtension(org.apache.sshd.sftp.client.extensions.CheckFileNameExtension.class);
                assertNotNull(hashes);assertTrue("Existing check-file-name support lost",hashes.isSupported());
                for(int i=0;i<3;i++){
                    var digest=hashes.checkFileName("/canary.bin",Collections.singletonList("sha256"),0,0,0);
                    assertEquals("sha256",digest.getKey());assertArrayEquals(MessageDigest.getInstance("SHA-256").digest(new byte[]{2,7,9}),digest.getValue().iterator().next());
                }
                try{for(int i=0;i<16;i++){held.add(one.open("/canary.bin"));held.add(two.openDir("/"));}}
                finally{for(var handle:held)handle.close();}
            }
            readable(client,port);
        }
    }
    @Test public void rawTraversalAndLinksCannotReadOrModifyOutsideCanary()throws Exception{
        Path parent=temp.newFolder().toPath(),root=Files.createDirectory(parent.resolve("share"));
        Path outside=parent.resolve("outside.bin");byte[] secret={8,5,3,1};Files.write(outside,secret);
        Files.write(root.resolve("canary.bin"),new byte[]{2,7,9});Files.createSymbolicLink(root.resolve("exit"),parent);
        String[] paths={"/../outside.bin","/../../outside.bin","/exit/outside.bin","/exit/../outside.bin",outside.toString(),"/a/../../outside.bin","/..\\outside.bin","/%2e%2e/outside.bin","/outside\u0000.bin"};
        try(EmbeddedSftpServer server=new EmbeddedSftpServer();SshClient client=client()){
            int port=start(server,root);
            try(ClientSession session=connect(client,port,first);SftpClient files=SftpClientFactory.instance().createSftpClient(session)){
                for(String path:paths){
                    try(var h=files.open(path)){fail("Outside canary readable through "+path);}catch(IOException expected){}
                    try{files.remove(path);}catch(IOException expected){}
                    assertArrayEquals("Outside canary changed through "+path,secret,Files.readAllBytes(outside));
                }
                try(var h=files.open("/exit/outside.bin",SftpClient.OpenMode.Write,SftpClient.OpenMode.Truncate)){fail("Outside write allowed");}catch(IOException expected){}
                try{files.rename("/canary.bin","/exit/moved.bin");fail("Rename escaped");}catch(IOException expected){}
                try{files.symLink("/new-link","/exit");fail("Symlink created");}catch(IOException expected){}
                try{files.link("/new-hardlink","/canary.bin",false);fail("Hardlink created");}catch(IOException expected){}
            }
            assertArrayEquals(secret,Files.readAllBytes(outside));assertFalse(Files.exists(parent.resolve("moved.bin")));readable(client,port);
        }
    }
    @Test public void unauthenticatedFileAndStatusChannelsAreRejected()throws Exception{
        try(EmbeddedSftpServer server=new EmbeddedSftpServer();SshClient client=client()){
            int port=start(server,root());
            for(String type:new String[]{"sftp","localls-status-v1","localls-status-v2"}){
                try(ClientSession session=client.connect("localls","127.0.0.1",port).verify(Duration.ofSeconds(4)).getSession();
                    ClientChannel channel=session.createSubsystemChannel(type)){
                    try{channel.open().verify(Duration.ofSeconds(2));fail("Unauthenticated subsystem opened: "+type);}catch(IOException expected){}
                }
            }
            readable(client,port);
        }
    }
    @Test public void malformedSshTrafficDoesNotCrashListenerOrModifyFiles()throws Exception{
        byte[][] attacks={new byte[0],"GET / HTTP/1.1\r\n\r\n".getBytes(StandardCharsets.US_ASCII),
                "SSH-1.5-attacker\r\n".getBytes(StandardCharsets.US_ASCII),"SSH-2.0-attacker\r\n\177\377\377\377\0".getBytes(StandardCharsets.ISO_8859_1),
                "SSH-2.0-attacker\r\n\0\0\0\0\0".getBytes(StandardCharsets.US_ASCII),new byte[20000]};
        Path root=root();
        try(EmbeddedSftpServer server=new EmbeddedSftpServer();SshClient client=client()){
            int port=start(server,root);
            for(byte[] attack:attacks){
                try(Socket socket=new Socket("127.0.0.1",port)){
                    socket.setSoTimeout(250);socket.getOutputStream().write(attack);socket.getOutputStream().flush();
                    try{socket.getInputStream().read(new byte[512]);}catch(IOException ignored){}
                }
                readable(client,port);
            }
            assertArrayEquals(new byte[]{2,7,9},Files.readAllBytes(root.resolve("canary.bin")));
        }
    }
    private static byte[] frame(int type,byte[] body)throws Exception{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);
        out.writeInt(1+body.length);out.writeByte(type);out.write(body);return bytes.toByteArray();
    }
    private static byte[] packet(InputStream in,ClientChannel channel)throws Exception{
        long deadline=System.nanoTime()+Duration.ofSeconds(4).toNanos();
        while(in.available()<4&&!channel.isClosed()&&System.nanoTime()<deadline)Thread.sleep(5);
        assertTrue("Missing SFTP response header",in.available()>=4);DataInputStream data=new DataInputStream(in);
        int size=data.readInt();assertTrue(size>0&&size<1024*1024);
        while(in.available()<size&&!channel.isClosed()&&System.nanoTime()<deadline)Thread.sleep(5);
        assertTrue("Incomplete SFTP response",in.available()>=size);return data.readNBytes(size);
    }
    private static byte[] stat(int id)throws Exception{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream body=new DataOutputStream(bytes);
        byte[] path="/canary.bin".getBytes(StandardCharsets.UTF_8);body.writeInt(id);body.writeInt(path.length);body.write(path);
        return frame(17,bytes.toByteArray());
    }
    @Test public void fragmentedHeadersAndNormal64RequestPipelineRemainCompatible()throws Exception{
        try(EmbeddedSftpServer server=new EmbeddedSftpServer();SshClient client=client()){
            int port=start(server,root());
            try(ClientSession session=connect(client,port,first);ClientChannel channel=session.createSubsystemChannel("sftp")){
                channel.open().verify(Duration.ofSeconds(3));OutputStream out=channel.getInvertedIn();InputStream in=channel.getInvertedOut();
                for(byte b:frame(1,new byte[]{0,0,0,3})){out.write(b);out.flush();}
                assertEquals(2,packet(in,channel)[0]);
                for(int id=1;id<=64;id++)out.write(stat(id));out.flush();
                for(int id=1;id<=64;id++){
                    byte[] response=packet(in,channel);assertEquals(105,response[0]&255);
                    assertEquals(id,new DataInputStream(new ByteArrayInputStream(response,1,response.length-1)).readInt());
                }
                ByteArrayOutputStream combined=new ByteArrayOutputStream();combined.write(stat(65));combined.write(stat(66));
                byte[] next=stat(67);combined.write(next,0,3);out.write(combined.toByteArray());out.flush();
                for(int id=65;id<=66;id++){
                    byte[] response=packet(in,channel);assertEquals(105,response[0]&255);
                    assertEquals(id,new DataInputStream(new ByteArrayInputStream(response,1,response.length-1)).readInt());
                }
                out.write(next,3,next.length-3);out.flush();byte[] response=packet(in,channel);
                assertEquals(105,response[0]&255);assertEquals(67,new DataInputStream(new ByteArrayInputStream(response,1,response.length-1)).readInt());
            }
            readable(client,port);
        }
    }
    @Test public void full256KiBWriteFrameAcrossSshPacketsPreservesEveryByte()throws Exception{
        Path root=root();byte[] payload=new byte[256*1024];new Random(624).nextBytes(payload);
        try(EmbeddedSftpServer server=new EmbeddedSftpServer();SshClient client=client()){
            int port=start(server,root);
            try(ClientSession session=connect(client,port,first);ClientChannel channel=session.createSubsystemChannel("sftp")){
                channel.open().verify(Duration.ofSeconds(3));OutputStream out=channel.getInvertedIn();InputStream in=channel.getInvertedOut();
                out.write(frame(1,new byte[]{0,0,0,3}));out.flush();assertEquals(2,packet(in,channel)[0]);
                ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream body=new DataOutputStream(bytes);
                byte[] name="/large.bin".getBytes(StandardCharsets.US_ASCII);body.writeInt(1);body.writeInt(name.length);body.write(name);body.writeInt(26);body.writeInt(0);
                out.write(frame(3,bytes.toByteArray()));out.flush();DataInputStream opened=new DataInputStream(new ByteArrayInputStream(packet(in,channel)));
                assertEquals(102,opened.readUnsignedByte());assertEquals(1,opened.readInt());int size=opened.readInt();assertTrue(size>0&&size<=64);byte[] handle=opened.readNBytes(size);
                bytes.reset();body.writeInt(2);body.writeInt(handle.length);body.write(handle);body.writeLong(0);body.writeInt(payload.length);body.write(payload);
                out.write(frame(6,bytes.toByteArray()));out.flush();DataInputStream written=new DataInputStream(new ByteArrayInputStream(packet(in,channel)));
                assertEquals(101,written.readUnsignedByte());assertEquals(2,written.readInt());assertEquals(0,written.readInt());
                bytes.reset();body.writeInt(3);body.writeInt(handle.length);body.write(handle);out.write(frame(4,bytes.toByteArray()));out.flush();
                DataInputStream closed=new DataInputStream(new ByteArrayInputStream(packet(in,channel)));
                assertEquals(101,closed.readUnsignedByte());assertEquals(3,closed.readInt());assertEquals(0,closed.readInt());
            }
            assertArrayEquals(payload,Files.readAllBytes(root.resolve("large.bin")));readable(client,port);
        }
    }
    @Test public void pendingRequestFloodClosesAttackingChannelAndLeavesOtherDeviceUsable()throws Exception{
        var entered=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);
        try(EmbeddedSftpServer server=new EmbeddedSftpServer();SshClient client=client()){
            int port=start(server,root());java.lang.reflect.Field field=EmbeddedSftpServer.class.getDeclaredField("server");field.setAccessible(true);
            var ssh=(org.apache.sshd.server.SshServer)field.get(server);
            var factory=(org.apache.sshd.sftp.server.SftpSubsystemFactory)ssh.getSubsystemFactories().get(0);
            factory.addSftpEventListener(new org.apache.sshd.sftp.server.SftpEventListener(){
                @Override public void received(org.apache.sshd.server.session.ServerSession session,int type,int id)throws IOException{
                    if(type==17&&id==1000){entered.countDown();try{release.await(6,java.util.concurrent.TimeUnit.SECONDS);}
                        catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException(e);}}
                }
            });
            try(ClientSession session=connect(client,port,first);ClientChannel channel=session.createSubsystemChannel("sftp")){
                channel.open().verify(Duration.ofSeconds(3));OutputStream out=channel.getInvertedIn();InputStream in=channel.getInvertedOut();
                out.write(frame(1,new byte[]{0,0,0,3}));out.flush();assertEquals(2,packet(in,channel)[0]);
                out.write(stat(1000));out.flush();assertTrue("Controlled slow operation did not start",entered.await(3,java.util.concurrent.TimeUnit.SECONDS));
                try{for(int id=1;id<=132;id++)out.write(stat(id));out.flush();}catch(IOException closedByServer){}
                long deadline=System.nanoTime()+Duration.ofSeconds(3).toNanos();
                while(!channel.isClosed()&&System.nanoTime()<deadline)Thread.sleep(5);
                assertTrue("Request flood was retained instead of closing its channel",channel.isClosed());
            }finally{release.countDown();}
            readable(client,port);
        }finally{release.countDown();}
    }
    @Test public void boundedUnauthenticatedConnectionBurstDoesNotBreakExistingFileSession()throws Exception{
        List<Socket> attackers=new ArrayList<>();
        try(EmbeddedSftpServer server=new EmbeddedSftpServer();SshClient client=client()){
            int port=start(server,root());
            try(ClientSession session=connect(client,port,first);SftpClient files=SftpClientFactory.instance().createSftpClient(session)){
                try{
                    for(int i=0;i<40;i++){Socket socket=new Socket();attackers.add(socket);socket.connect(new InetSocketAddress("127.0.0.1",port),1000);}
                    try(var handle=files.open("/canary.bin")){
                        byte[] bytes=new byte[3];assertEquals(3,files.read(handle,0,bytes));assertArrayEquals(new byte[]{2,7,9},bytes);
                    }
                }finally{for(Socket socket:attackers)socket.close();}
            }
            readable(client,port);
        }
    }
    @Test public void malformedSftpFramesCannotKeepAChannelStalledOrBreakOtherClients()throws Exception{
        byte[][] attacks={frame(255,new byte[]{0,0,0,1}),frame(3,new byte[]{0,0,0,2}),
                frame(3,new byte[]{0,0,0,3,127,-1,-1,-1}),new byte[]{0,0,0,0},new byte[]{-1,-1,-1,-1},new byte[]{127,-1,-1,-1}};
        try(EmbeddedSftpServer server=new EmbeddedSftpServer();SshClient client=client()){
            int port=start(server,root());
            for(byte[] attack:attacks){
                try(ClientSession session=connect(client,port,first);ClientChannel channel=session.createSubsystemChannel("sftp")){
                    channel.open().verify(Duration.ofSeconds(3));OutputStream out=channel.getInvertedIn();InputStream in=channel.getInvertedOut();
                    out.write(frame(1,new byte[]{0,0,0,3}));out.flush();
                    long deadline=System.nanoTime()+Duration.ofSeconds(3).toNanos();
                    while(in.available()<4&&!channel.isClosed()&&System.nanoTime()<deadline)Thread.sleep(5);
                    assertTrue("SFTP init failed",in.available()>=4);DataInputStream reply=new DataInputStream(in);
                    int length=reply.readInt();assertTrue(length>0&&length<65536);assertEquals(2,reply.readUnsignedByte());reply.readNBytes(length-1);
                    out.write(attack);out.flush();deadline=System.nanoTime()+Duration.ofSeconds(3).toNanos();
                    while(in.available()==0&&!channel.isClosed()&&channel.getExitStatus()==null&&System.nanoTime()<deadline)Thread.sleep(5);
                    assertTrue("Malformed SFTP frame retained a stalled channel: "+Arrays.toString(attack),channel.isClosed()||channel.getExitStatus()!=null||in.available()>0);
                }
                readable(client,port);
            }
        }
    }
}
