package com.bitpoint.homeservercontrol;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.*;
import java.time.Duration;
import java.util.*;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.sftp.client.SftpClientFactory;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class SessionAdmissionTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private static KeyPair host,first,second;
    @BeforeClass public static void keys()throws Exception{var rsa=KeyPairGenerator.getInstance("RSA");rsa.initialize(3072);host=rsa.generateKeyPair();var ec=KeyPairGenerator.getInstance("EC");ec.initialize(256);first=ec.generateKeyPair();second=ec.generateKeyPair();}
    private ClientSession connect(SshClient client,int port,KeyPair key)throws Exception{var session=client.connect("localls","127.0.0.1",port).verify(Duration.ofSeconds(4)).getSession();try{session.addPublicKeyIdentity(key);session.auth().verify(Duration.ofSeconds(4));return session;}catch(Exception e){session.close(true);throw e;}}
    @SuppressWarnings("unchecked") private Collection<org.apache.sshd.common.session.Session> sessions(EmbeddedSftpServer server)throws Exception{var field=EmbeddedSftpServer.class.getDeclaredField("sessions");field.setAccessible(true);return (Collection<org.apache.sshd.common.session.Session>)field.get(server);}
    private long live(Collection<org.apache.sshd.common.session.Session> sessions){return sessions.stream().filter(s->s.isOpen()).count();}
    private void canary(org.apache.sshd.sftp.client.SftpClient files)throws Exception{try(var handle=files.open("/canary.bin")){byte[] bytes=new byte[3];assertEquals(3,files.read(handle,0,bytes));assertArrayEquals(new byte[]{2,7,9},bytes);}}
    @Test public void saturatedUnauthenticatedBudgetAdmitsApprovedDeviceWithoutEvictingAuthenticatedSessions()throws Exception{
        Path root=temp.newFolder().toPath();Files.write(root.resolve("canary.bin"),new byte[]{2,7,9});List<Socket> attackers=new ArrayList<>();
        try(EmbeddedSftpServer server=new EmbeddedSftpServer();SshClient client=SshClient.setUpDefaultClient()){
            int port=server.startAuthorized("127.0.0.1",0,"localls",List.of(first.getPublic(),second.getPublic()),host,root);client.setServerKeyVerifier((s,a,k)->KeyUtils.compareKeys(host.getPublic(),k));client.start();var tracked=sessions(server);int limit=EmbeddedSftpServer.MAX_DEVICES*EmbeddedSftpServer.SESSIONS_PER_DEVICE+8;
            try(ClientSession one=connect(client,port,first);var files=SftpClientFactory.instance().createSftpClient(one)){
                try{
                    for(int i=1;i<limit;i++){Socket socket=new Socket();attackers.add(socket);socket.connect(new InetSocketAddress("127.0.0.1",port),1000);}
                    long deadline=System.nanoTime()+Duration.ofSeconds(3).toNanos();while(live(tracked)<limit&&System.nanoTime()<deadline)Thread.sleep(5);assertEquals("Attack did not fill the bounded pre-authentication slots",limit,live(tracked));canary(files);
                    try(ClientSession two=connect(client,port,second);var other=SftpClientFactory.instance().createSftpClient(two)){
                        assertTrue(one.isAuthenticated());assertTrue(one.isOpen());canary(files);canary(other);assertTrue("Live session budget was increased",live(tracked)<=limit);
                        for(int i=0;i<8;i++){Socket socket=new Socket();attackers.add(socket);socket.connect(new InetSocketAddress("127.0.0.1",port),1000);}
                        canary(files);canary(other);assertTrue(one.isOpen());assertTrue(two.isOpen());assertTrue("Flood exceeded the live session budget",live(tracked)<=limit);
                    }
                }finally{for(Socket socket:attackers)socket.close();}
            }
        }
    }
}
