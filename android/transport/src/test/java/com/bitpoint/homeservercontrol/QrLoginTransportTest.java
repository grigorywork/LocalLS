package com.bitpoint.homeservercontrol;

import com.jcraft.jsch.JSch;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.Assert.*;

/** Real SSH: a QR offer can collect a public key, but cannot grant access before local approval. */
public class QrLoginTransportTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private static KeyPair host;
    private static String publicKey,otherPublic;
    private static SshCredentials credential,other;
    @BeforeClass public static void identities()throws Exception{
        KeyPairGenerator generator=KeyPairGenerator.getInstance("RSA");generator.initialize(3072);host=generator.generateKeyPair();
        String[][] keys=new String[2][2];
        for(int i=0;i<2;i++){
            com.jcraft.jsch.KeyPair pair=com.jcraft.jsch.KeyPair.genKeyPair(new JSch(),com.jcraft.jsch.KeyPair.RSA,3072);
            ByteArrayOutputStream priv=new ByteArrayOutputStream(),pub=new ByteArrayOutputStream();pair.writePrivateKey(priv);pair.writePublicKey(pub,"test");pair.dispose();
            keys[i][0]=priv.toString("UTF-8");keys[i][1]=EmbeddedSftpServer.normalizeAuthorizedKey(pub.toString("UTF-8"));
        }
        credential=SshCredentials.key(keys[0][0],"");publicKey=keys[0][1];other=SshCredentials.key(keys[1][0],"");otherPublic=keys[1][1];
    }
    private String token(){return UUID.randomUUID().toString();}
    private String pin(){return EmbeddedSftpServer.fingerprint(host);}
    private boolean pair(int port,String nonce,SshCredentials key)throws Exception{return SftpClient.pairingAttempt("127.0.0.1",port,"localls",nonce,key,pin());}
    private boolean login(int port,SshCredentials key)throws Exception{return SftpClient.loginAttempt("127.0.0.1",port,"localls",key,pin());}
    @Test public void copiedPublicKeyWithoutItsPrivateKeyCannotLoginEvenAfterOwnerApproval()throws Exception{
        JSch signer=new JSch(),attacker=new JSch();signer.addIdentity("attacker",other.privateKey.getBytes(java.nio.charset.StandardCharsets.UTF_8),null,null);
        com.jcraft.jsch.Identity wrongSigner=signer.getIdentityRepository().getIdentities().get(0);
        java.util.concurrent.atomic.AtomicInteger signatures=new java.util.concurrent.atomic.AtomicInteger();
        byte[] copied=java.util.Base64.getDecoder().decode(publicKey.split(" ")[1]);
        attacker.addIdentity(new com.jcraft.jsch.Identity(){
            public boolean setPassphrase(byte[] phrase){return true;}
            public byte[] getPublicKeyBlob(){return copied;}
            public byte[] getSignature(byte[] data){signatures.incrementAndGet();return wrongSigner.getSignature(data);}
            public byte[] getSignature(byte[] data,String algorithm){signatures.incrementAndGet();return wrongSigner.getSignature(data,algorithm);}
            public String getAlgName(){return "ssh-rsa";}
            public String getName(){return "copied-public-key";}
            public boolean isEncrypted(){return false;}
            public void clear(){}
        },null);
        try(EmbeddedSftpServer server=new EmbeddedSftpServer()){
            int port=server.startWaiting("127.0.0.1",0,"localls",host,temp.newFolder().toPath());String nonce=token();server.beginPairing(nonce,System.currentTimeMillis()+600000);
            assertFalse(pair(port,nonce,credential));server.approvePairing(nonce,publicKey);
            for(String username:new String[]{"localls","localls#"+nonce}){
                int before=signatures.get();com.jcraft.jsch.Session attempt=attacker.getSession(username,"127.0.0.1",port);
                attempt.setConfig("StrictHostKeyChecking","no");attempt.setConfig("PreferredAuthentications","publickey");
                try{attempt.connect(4000);fail("Possession of a public key was enough to authenticate");}
                catch(com.jcraft.jsch.JSchException expected){}finally{attempt.disconnect();}
                assertTrue("The forged signature was never attempted",signatures.get()>before);
            }
            assertTrue("Both attack sessions must reach signature verification",signatures.get()>=2);
            assertTrue("Forged signatures disrupted the approved identity",login(port,credential));
        }finally{signer.removeAllIdentity();attacker.removeAllIdentity();}
    }
    @Test public void repeatedUnauthorizedRequestsCannotChangeSharedDataOrSavedIdentity()throws Exception{
        Path root=temp.newFolder().toPath();byte[] original={4,9,16};Files.write(root.resolve("protected.bin"),original);
        try(EmbeddedSftpServer server=new EmbeddedSftpServer()){
            int port=server.start("127.0.0.1",0,"localls",EmbeddedSftpServer.authorizedKey(publicKey),host,root);
            for(int i=0;i<12;i++)assertFalse("Unapproved key authenticated on attempt "+i,login(port,other));
            assertArrayEquals(original,Files.readAllBytes(root.resolve("protected.bin")));try(java.util.stream.Stream<Path> children=Files.list(root)){assertEquals(1,children.count());}
            assertTrue("Authorized access did not recover after rejected requests",login(port,credential));
        }
    }
    @Test public void authenticatedClientCannotWriteDeleteOrRenameThroughAnOutsideSymlink()throws Exception{
        Path parent=temp.newFolder().toPath(),root=Files.createDirectory(parent.resolve("share"));byte[] secret={2,7,1,8};Files.write(parent.resolve("secret.bin"),secret);Files.write(root.resolve("inside.bin"),new byte[]{6});Files.createSymbolicLink(root.resolve("exit"),parent);
        try(EmbeddedSftpServer server=new EmbeddedSftpServer()){
            int port=server.start("127.0.0.1",0,"localls",EmbeddedSftpServer.authorizedKey(publicKey),host,root);
            JSch client=new JSch();client.addIdentity("approved",credential.privateKey.getBytes(java.nio.charset.StandardCharsets.UTF_8),null,null);
            com.jcraft.jsch.Session session=client.getSession("localls","127.0.0.1",port);session.setConfig("StrictHostKeyChecking","no");session.setConfig("PreferredAuthentications","publickey");session.connect(4000);
            try{
                com.jcraft.jsch.ChannelSftp files=(com.jcraft.jsch.ChannelSftp)session.openChannel("sftp");files.connect(3000);
                try{
                    try{files.put(new ByteArrayInputStream(new byte[]{0}),"/exit/secret.bin");fail("Wrote outside share");}catch(com.jcraft.jsch.SftpException expected){}
                    try{files.rm("/exit/secret.bin");fail("Deleted outside share");}catch(com.jcraft.jsch.SftpException expected){}
                    try{files.rename("/inside.bin","/exit/escaped.bin");fail("Renamed outside share");}catch(com.jcraft.jsch.SftpException expected){}
                    try{files.mkdir("/exit/new-folder");fail("Created folder outside share");}catch(com.jcraft.jsch.SftpException expected){}
                    assertArrayEquals(secret,Files.readAllBytes(parent.resolve("secret.bin")));assertFalse(Files.exists(parent.resolve("escaped.bin")));assertFalse(Files.exists(parent.resolve("new-folder")));assertTrue(Files.exists(root.resolve("inside.bin")));
                }finally{files.disconnect();}
            }finally{session.disconnect();client.removeAllIdentity();}
        }
    }
    @Test public void approvalPersistsAsOrdinaryKeyAccessAfterOfferClosesAndServerRestarts()throws Exception{
        Path root=temp.newFolder().toPath();String nonce=token();
        try(EmbeddedSftpServer server=new EmbeddedSftpServer()){
            int port=server.startWaiting("127.0.0.1",0,"localls",host,root);server.beginPairing(nonce,System.currentTimeMillis()+600000);
            assertFalse(pair(port,nonce,credential));assertEquals(publicKey,server.pendingPairingKey(nonce));assertFalse(login(port,credential));
            server.approvePairing(nonce,publicKey);assertTrue(pair(port,nonce,credential));assertTrue(login(port,credential));server.endPairing(nonce);assertTrue(login(port,credential));
            server.close();port=server.start("127.0.0.1",0,"localls",EmbeddedSftpServer.authorizedKey(publicKey),host,root);
            assertTrue("An approved device needed another QR after restart",login(port,credential));assertEquals("",server.pendingPairingKey(nonce));assertFalse(pair(port,nonce,credential));
            try(SftpClient.TreeSession files=new SftpClient.TreeSession("127.0.0.1",port,"localls",credential,pin())){
                byte[] bytes={1,3,7};String path=files.write("/","permanent.bin",new ByteArrayInputStream(bytes),bytes.length,(n,total)->true);ByteArrayOutputStream result=new ByteArrayOutputStream();files.read(path,result,(n,total)->true);assertArrayEquals(bytes,result.toByteArray());
            }
        }
    }
    @Test public void wrongServerPinOrNonceCannotSubmitAKeyOrGrantAccess()throws Exception{
        try(EmbeddedSftpServer server=new EmbeddedSftpServer()){
            int port=server.startWaiting("127.0.0.1",0,"localls",host,temp.newFolder().toPath());String nonce=token();server.beginPairing(nonce,System.currentTimeMillis()+600000);
            try{SftpClient.pairingAttempt("127.0.0.1",port,"localls",nonce,credential,"SHA256:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");fail("Changed server accepted");}catch(SecurityException expected){}
            assertEquals("",server.pendingPairingKey(nonce));assertFalse(pair(port,token(),credential));assertEquals("",server.pendingPairingKey(nonce));assertFalse(login(port,credential));
            try{SftpClient.pairingAttempt("127.0.0.1",port,"localls",nonce,SshCredentials.password("not-a-key"),pin());fail("Password pairing accepted");}catch(IllegalArgumentException expected){}
            assertEquals("",server.pendingPairingKey(nonce));
        }
    }
    @Test public void refreshedInvitationAndDifferentClientCannotReplaceTheApprovedIdentity()throws Exception{
        try(EmbeddedSftpServer server=new EmbeddedSftpServer()){
            int port=server.startWaiting("127.0.0.1",0,"localls",host,temp.newFolder().toPath());String first=token(),next=token();server.beginPairing(first,System.currentTimeMillis()+600000);assertFalse(pair(port,first,credential));
            server.beginPairing(next,System.currentTimeMillis()+600000);server.endPairing(first);
            try{server.approvePairing(first,publicKey);fail("Old invitation approved");}catch(IllegalStateException expected){}
            assertFalse(login(port,credential));assertFalse(pair(port,first,credential));assertFalse(pair(port,next,credential));assertEquals(publicKey,server.pendingPairingKey(next));
            assertFalse(pair(port,next,other));assertEquals(publicKey,server.pendingPairingKey(next));
            try{server.approvePairing(next,otherPublic);fail("Different key approved");}catch(IllegalStateException expected){}
            server.approvePairing(next,publicKey);assertTrue(login(port,credential));assertFalse(login(port,other));
            String third=token();server.beginPairing(third,System.currentTimeMillis()+600000);assertFalse(pair(port,third,other));assertEquals(otherPublic,server.pendingPairingKey(third));assertFalse("A second device bypassed local approval",login(port,other));assertTrue(login(port,credential));
            server.approvePairing(third,otherPublic);assertTrue(login(port,other));assertTrue("Adding a device replaced the first key",login(port,credential));
        }
    }
    @Test public void expiredOfferCannotAuthorizeButDoesNotExpireAnAlreadyApprovedKey()throws Exception{
        try(EmbeddedSftpServer server=new EmbeddedSftpServer()){
            int port=server.start("127.0.0.1",0,"localls",EmbeddedSftpServer.authorizedKey(publicKey),host,temp.newFolder().toPath());String nonce=token();long expires=System.currentTimeMillis()+300;
            server.beginPairing(nonce,expires);long remaining=expires-System.currentTimeMillis();if(remaining>=0)Thread.sleep(remaining+10);
            assertFalse(pair(port,nonce,credential));assertEquals("",server.pendingPairingKey(nonce));
            try{server.approvePairing(nonce,publicKey);fail("Expired invitation approved");}catch(IllegalStateException expected){}
            assertTrue("QR expiry revoked permanent access",login(port,credential));
        }
    }
    @Test public void stoppingWhileAuthenticationIsActiveCannotDeadlockOnThePairingLock()throws Exception{
        EmbeddedSftpServer server=new EmbeddedSftpServer();ExecutorService threads=Executors.newFixedThreadPool(2);
        try{
            int port=server.startWaiting("127.0.0.1",0,"localls",host,temp.newFolder().toPath());String nonce=token();server.beginPairing(nonce,System.currentTimeMillis()+600000);
            Future<?> attempt=threads.submit(()->{try{pair(port,nonce,credential);}catch(Exception ignored){}});
            Future<?> closing=threads.submit(()->{try{server.close();}catch(IOException e){throw new UncheckedIOException(e);}});
            closing.get(5,TimeUnit.SECONDS);attempt.get(6,TimeUnit.SECONDS);assertEquals("",server.pendingPairingKey(nonce));
        }finally{server.close();threads.shutdownNow();assertTrue(threads.awaitTermination(6,TimeUnit.SECONDS));}
    }
}
