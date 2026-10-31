package com.bitpoint.homeservercontrol;
import java.nio.file.*;import java.security.*;import java.time.Duration;import java.util.*;import java.util.concurrent.*;
import org.apache.sshd.client.SshClient;import org.apache.sshd.client.session.ClientSession;import org.apache.sshd.common.config.keys.KeyUtils;import org.apache.sshd.sftp.client.*;
import org.junit.*;import static org.junit.Assert.*;
public class ServerPresenceTest {
    private void await(java.util.function.BooleanSupplier ready)throws Exception{long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(4);while(!ready.getAsBoolean()&&System.nanoTime()<until)Thread.sleep(10);assertTrue(ready.getAsBoolean());}
    private ClientSession login(SshClient c,int port,KeyPair key)throws Exception{ClientSession s=c.connect("localls","127.0.0.1",port).verify(Duration.ofSeconds(4)).getSession();s.addPublicKeyIdentity(key);s.auth().verify(Duration.ofSeconds(4));return s;}
    @Test public void presenceCountsOnlyAuthenticatedAllowedDevicesAcrossSessionsAndRevocation()throws Exception{
        var rsa=KeyPairGenerator.getInstance("RSA");rsa.initialize(3072);var host=rsa.generateKeyPair();var ec=KeyPairGenerator.getInstance("EC");ec.initialize(256);var first=ec.generateKeyPair();var second=ec.generateKeyPair();var unknown=ec.generateKeyPair();Path root=Files.createTempDirectory("localls-presence-");
        try(EmbeddedSftpServer server=new EmbeddedSftpServer();SshClient client=SshClient.setUpDefaultClient()){
            int port=server.startAuthorized("127.0.0.1",0,"localls",Arrays.asList(first.getPublic(),second.getPublic()),host,root);client.setServerKeyVerifier((s,a,k)->KeyUtils.compareKeys(k,host.getPublic()));client.start();
            try(ClientSession untrusted=client.connect("localls","127.0.0.1",port).verify(Duration.ofSeconds(4)).getSession()){assertTrue(server.onlineClientFingerprints().isEmpty());untrusted.addPublicKeyIdentity(unknown);try{untrusted.auth().verify(Duration.ofSeconds(4));fail("Unknown key authenticated");}catch(java.io.IOException expected){}assertTrue(server.onlineClientFingerprints().isEmpty());}
            try(ClientSession a=login(client,port,first);ClientSession b=login(client,port,first);ClientSession c=login(client,port,second)){
                await(()->server.onlineClientFingerprints().size()==2);a.close(true).await(4000);assertEquals(2,server.onlineClientFingerprints().size());server.revokeClient(org.apache.sshd.common.config.keys.PublicKeyEntry.toString(first.getPublic()));await(()->server.onlineClientFingerprints().equals(Collections.singleton(KeyUtils.getFingerPrint(second.getPublic()))));assertTrue(c.isOpen());
            }
            await(()->server.onlineClientFingerprints().isEmpty());
        }finally{Files.deleteIfExists(root);}
    }
    @Test public void receiptCarriesVerifiedSenderSkipsReadsAndTemporaryFilesUntilCommit()throws Exception{
        var rsa=KeyPairGenerator.getInstance("RSA");rsa.initialize(3072);var host=rsa.generateKeyPair();var ec=KeyPairGenerator.getInstance("EC");ec.initialize(256);var key=ec.generateKeyPair();Path root=Files.createTempDirectory("localls-receipt-");Files.write(root.resolve("read.txt"),new byte[]{7});List<EmbeddedSftpServer.Receipt> rows=new CopyOnWriteArrayList<>();
        try(EmbeddedSftpServer server=new EmbeddedSftpServer();SshClient client=SshClient.setUpDefaultClient()){
            server.onReceipt(rows::add);int port=server.startAuthorized("127.0.0.1",0,"localls",Collections.singletonList(key.getPublic()),host,root);client.setServerKeyVerifier((s,a,k)->KeyUtils.compareKeys(k,host.getPublic()));client.start();
            try(ClientSession session=login(client,port,key);org.apache.sshd.sftp.client.SftpClient files=SftpClientFactory.instance().createSftpClient(session)){
                try(var handle=files.open("/read.txt")){assertEquals(1,files.read(handle,0,new byte[1]));}assertTrue(rows.isEmpty());
                try(var output=files.write("/.localls-upload-hidden")){output.write(new byte[]{1,2,3});}assertTrue(rows.isEmpty());files.rename("/.localls-upload-hidden","/committed.txt");await(()->rows.size()==1);assertEquals("committed.txt",rows.get(0).path);assertEquals(3,rows.get(0).bytes);assertEquals(KeyUtils.getFingerPrint(key.getPublic()),rows.get(0).key);
                server.onReceipt(receipt->{throw new IllegalStateException("Test callback");});try(var output=files.write("/survives.txt")){output.write(9);}assertArrayEquals(new byte[]{9},Files.readAllBytes(root.resolve("survives.txt")));
            }
        }finally{try(var files=Files.list(root)){for(Path p:files.toList())Files.delete(p);}Files.delete(root);}
    }
}
