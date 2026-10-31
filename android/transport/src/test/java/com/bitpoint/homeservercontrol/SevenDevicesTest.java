package com.bitpoint.homeservercontrol;

import com.jcraft.jsch.JSch;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class SevenDevicesTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    static KeyPair host;static final List<String> keys=new ArrayList<>();static final List<SshCredentials> credentials=new ArrayList<>();
    @BeforeClass public static void setup()throws Exception{
        KeyPairGenerator generator=KeyPairGenerator.getInstance("RSA");generator.initialize(3072);host=generator.generateKeyPair();
        for(int i=0;i<8;i++){
            var pair=com.jcraft.jsch.KeyPair.genKeyPair(new JSch(),com.jcraft.jsch.KeyPair.ECDSA,256);ByteArrayOutputStream pub=new ByteArrayOutputStream(),priv=new ByteArrayOutputStream();pair.writePublicKey(pub,"device");pair.writePrivateKey(priv);pair.dispose();
            keys.add(EmbeddedSftpServer.normalizeAuthorizedKey(pub.toString("UTF-8")));credentials.add(SshCredentials.key(priv.toString("UTF-8"),""));
        }
    }
    private boolean login(int port,int device)throws Exception{return SftpClient.loginAttempt("127.0.0.1",port,"localls",credentials.get(device),EmbeddedSftpServer.fingerprint(host));}
    @Test public void sevenDistinctDevicesCanTransferSimultaneouslyButEighthCannotEnroll()throws Exception{
        try(var server=new EmbeddedSftpServer()){
            int port=server.startWaiting("127.0.0.1",0,"localls",host,temp.newFolder().toPath());List<SftpClient.TreeSession> sessions=new ArrayList<>();
            try{
                for(int i=0;i<7;i++){
                    String nonce=UUID.randomUUID().toString();server.beginPairing(nonce,System.currentTimeMillis()+600000);
                    assertFalse(SftpClient.pairingAttempt("127.0.0.1",port,"localls",nonce,credentials.get(i),EmbeddedSftpServer.fingerprint(host)));
                    assertEquals(keys.get(i),server.pendingPairingKey(nonce));assertFalse(login(port,i));server.approvePairing(nonce,keys.get(i));server.endPairing(nonce);
                    SftpClient.TreeSession session=new SftpClient.TreeSession("127.0.0.1",port,"localls",credentials.get(i),EmbeddedSftpServer.fingerprint(host));sessions.add(session);
                    byte[] content={(byte)i,3,5};session.write("/","device-"+i+".bin",new ByteArrayInputStream(content),content.length,(n,total)->true);
                }
                for(int i=0;i<7;i++){ByteArrayOutputStream result=new ByteArrayOutputStream();sessions.get(i).read("/device-"+i+".bin",result,(n,total)->true);assertArrayEquals(new byte[]{(byte)i,3,5},result.toByteArray());}
                String nonce=UUID.randomUUID().toString();server.beginPairing(nonce,System.currentTimeMillis()+600000);assertFalse(SftpClient.pairingAttempt("127.0.0.1",port,"localls",nonce,credentials.get(7),EmbeddedSftpServer.fingerprint(host)));assertEquals("",server.pendingPairingKey(nonce));
                try{server.approveOptical(nonce,keys.get(7));fail("Eighth device added");}catch(IllegalStateException expected){}assertFalse(login(port,7));
                for(int i=0;i<7;i++)assertTrue("An old device was replaced",login(port,i));
            }finally{for(var session:sessions)session.close();}
        }
    }
    @Test public void revocationClosesOnlyThatDeviceAndItsOldQrCannotReauthorizeIt()throws Exception{
        List<PublicKey> allowed=new ArrayList<>();for(int i=0;i<7;i++)allowed.add(EmbeddedSftpServer.authorizedKey(keys.get(i)));
        try(var server=new EmbeddedSftpServer()){
            int port=server.startAuthorized("127.0.0.1",0,"localls",allowed,host,temp.newFolder().toPath());String nonce=UUID.randomUUID().toString();server.beginPairing(nonce,System.currentTimeMillis()+600000);server.approveOptical(nonce,keys.get(2));
            try(var removed=new SftpClient.TreeSession("127.0.0.1",port,"localls",credentials.get(2),EmbeddedSftpServer.fingerprint(host));var survivor=new SftpClient.TreeSession("127.0.0.1",port,"localls",credentials.get(0),EmbeddedSftpServer.fingerprint(host))){
                server.revokeClient(keys.get(2));assertFalse(login(port,2));
                try{removed.list("/");fail("Revoked connection retained file access");}catch(Exception expected){}
                assertNotNull(survivor.list("/"));assertEquals("",server.pendingPairingKey(nonce));
                try{server.beginPairing(nonce,System.currentTimeMillis()+600000);fail("Revoked QR reopened");}catch(IllegalStateException expected){}
                try{server.approveOptical(nonce,keys.get(2));fail("Old consent revived a revoked device");}catch(IllegalStateException expected){}
                String next=UUID.randomUUID().toString();server.beginPairing(next,System.currentTimeMillis()+600000);server.approveOptical(next,keys.get(7));assertTrue(login(port,7));assertFalse(login(port,2));assertTrue(login(port,0));
            }
        }
    }
}
