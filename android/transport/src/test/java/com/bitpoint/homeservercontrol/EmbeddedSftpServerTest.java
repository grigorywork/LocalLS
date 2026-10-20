package com.bitpoint.homeservercontrol;

import com.jcraft.jsch.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.nio.file.*;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Arrays;
import static org.junit.Assert.*;

public class EmbeddedSftpServerTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private static KeyPair host;
    private static byte[] clientPrivate;
    private static String clientPublic;
    @BeforeClass public static void keys() throws Exception {
        KeyPairGenerator generator=KeyPairGenerator.getInstance("RSA");generator.initialize(3072);host=generator.generateKeyPair();
        com.jcraft.jsch.KeyPair client=com.jcraft.jsch.KeyPair.genKeyPair(new JSch(),com.jcraft.jsch.KeyPair.RSA,3072);
        ByteArrayOutputStream priv=new ByteArrayOutputStream(),pub=new ByteArrayOutputStream();
        client.writePrivateKey(priv);client.writePublicKey(pub,"test");clientPrivate=priv.toByteArray();clientPublic=pub.toString("UTF-8");client.dispose();
    }
    private Session session(int port,String user,boolean key) throws Exception {
        JSch j=new JSch();if(key)j.addIdentity("test",clientPrivate,null,null);
        Session s=j.getSession(user,"127.0.0.1",port);s.setConfig("StrictHostKeyChecking","no");s.setConfig("PreferredAuthentications",key?"publickey":"password");s.setPassword("unused");s.setTimeout(4000);return s;
    }
    private EmbeddedSftpServer server(Path root) throws Exception {
        EmbeddedSftpServer server=new EmbeddedSftpServer();server.start("127.0.0.1",0,"localls",EmbeddedSftpServer.authorizedKey(clientPublic),host,root);return server;
    }
    @Test public void transferRenameAndDeleteWithinShare() throws Exception {
        Path root=temp.newFolder().toPath();
        try(EmbeddedSftpServer server=new EmbeddedSftpServer()) {
            int port=server.start("127.0.0.1",0,"localls",EmbeddedSftpServer.authorizedKey(clientPublic),host,root);
            Session s=session(port,"localls",true);s.connect(5000);
            try { ChannelSftp ftp=(ChannelSftp)s.openChannel("sftp");ftp.connect(4000);
                byte[] payload=new byte[1024*1024+17];new java.util.Random(42).nextBytes(payload);
                ftp.mkdir("/folder");ftp.put(new ByteArrayInputStream(payload),"/folder/input.bin");
                ByteArrayOutputStream out=new ByteArrayOutputStream();ftp.get("/folder/input.bin",out);assertArrayEquals(payload,out.toByteArray());
                ftp.rename("/folder/input.bin","/folder/renamed.bin");assertEquals(payload.length,ftp.stat("/folder/renamed.bin").getSize());
                ftp.rm("/folder/renamed.bin");ftp.rmdir("/folder");ftp.disconnect();
            } finally {s.disconnect();}
        }
    }
    @Test public void passwordAndWrongUsernameAndUnknownKeyCannotAuthenticate() throws Exception {
        try(EmbeddedSftpServer server=new EmbeddedSftpServer()) {
            int port=server.start("127.0.0.1",0,"localls",EmbeddedSftpServer.authorizedKey(clientPublic),host,temp.newFolder().toPath());
            for(Session s:new Session[]{session(port,"localls",false),session(port,"other",true)}) {
                try{s.connect(4000);fail("Unauthorized authentication");}catch(JSchException expected){}finally{s.disconnect();}
            }
            JSch other=new JSch();com.jcraft.jsch.KeyPair pair=com.jcraft.jsch.KeyPair.genKeyPair(other,com.jcraft.jsch.KeyPair.RSA,3072);
            ByteArrayOutputStream out=new ByteArrayOutputStream();pair.writePrivateKey(out);other.addIdentity("wrong",out.toByteArray(),null,null);pair.dispose();
            Session wrong=other.getSession("localls","127.0.0.1",port);wrong.setConfig("StrictHostKeyChecking","no");
            try{wrong.connect(4000);fail("Unknown key accepted");}catch(JSchException expected){}finally{wrong.disconnect();}
        }
    }
    @Test public void traversalLinksCommandsAndForwardingAreDenied() throws Exception {
        Path parent=temp.newFolder().toPath(),root=Files.createDirectory(parent.resolve("share"));Files.write(parent.resolve("secret"),"private".getBytes());
        Files.createSymbolicLink(root.resolve("escape"),parent);
        try(EmbeddedSftpServer server=new EmbeddedSftpServer()) {
            int port=server.start("127.0.0.1",0,"localls",EmbeddedSftpServer.authorizedKey(clientPublic),host,root);
            Session s=session(port,"localls",true);s.connect(5000);
            try {ChannelSftp ftp=(ChannelSftp)s.openChannel("sftp");ftp.connect(4000);
                for(String path:new String[]{"/../secret","/../../secret","/escape/secret"}) {
                    try {ftp.get(path,new ByteArrayOutputStream());fail("Read outside share: "+path);}catch(SftpException expected){}
                }
                try {ftp.symlink("/escape","/new-link");fail("Created symlink");}catch(SftpException expected){}
                for(String type:new String[]{"shell","exec"}) {
                    Channel c=s.openChannel(type);if(c instanceof ChannelExec)((ChannelExec)c).setCommand("id");
                    try{c.connect(2000);fail("Command channel accepted");}catch(JSchException expected){}finally{c.disconnect();}
                }
                try {s.setPortForwardingR(0,"127.0.0.1",22);fail("Remote forward accepted");}catch(JSchException expected){}
                ftp.disconnect();
            }finally{s.disconnect();}
        }
    }
    @Test public void hostFingerprintPersistsAcrossRestartsAndStopClosesPort() throws Exception {
        String fingerprint=EmbeddedSftpServer.fingerprint(host);assertTrue(fingerprint.startsWith("SHA256:"));
        EmbeddedSftpServer server=new EmbeddedSftpServer();int port=server.start("127.0.0.1",0,"localls",EmbeddedSftpServer.authorizedKey(clientPublic),host,temp.newFolder().toPath());
        Session s=session(port,"localls",true);s.connect(5000);String first=s.getHostKey().getKey();s.disconnect();server.close();server.close();
        try {new java.net.Socket("127.0.0.1",port).close();fail("Port still open");}catch(IOException expected){}
        try(EmbeddedSftpServer next=new EmbeddedSftpServer()) {int nextPort=next.start("127.0.0.1",0,"localls",EmbeddedSftpServer.authorizedKey(clientPublic),host,temp.newFolder().toPath());
            s=session(nextPort,"localls",true);s.connect(5000);assertEquals(first,s.getHostKey().getKey());s.disconnect();}
    }
    @Test public void localLsProbeRecognizesSftpWithoutShell() throws Exception {
        try(EmbeddedSftpServer server=new EmbeddedSftpServer()) {
            int port=server.start("127.0.0.1",0,"localls",EmbeddedSftpServer.authorizedKey(clientPublic),host,temp.newFolder().toPath());
            String pin=SshClient.probeFingerprint("127.0.0.1",port,"localls");
            assertEquals(EmbeddedSftpServer.fingerprint(host),pin);
            ServerStats stats=SshClient.probe("127.0.0.1",port,"localls",SshCredentials.key(new String(clientPrivate,"UTF-8"),""),pin);
            assertTrue(stats.authenticated);assertTrue(stats.hostKeyTrusted);assertTrue(stats.sftpOnly);assertEquals("",stats.error);
        }
    }
    @Test public void privateAndMalformedAndWeakPublicKeysAreRejected() throws Exception {
        for(String value:new String[]{new String(clientPrivate,"UTF-8"),"ssh-rsa invalid","ssh-ed25519 AAAA","",clientPublic+clientPublic}) {
            try{EmbeddedSftpServer.authorizedKey(value);fail("Invalid public key accepted");}catch(Exception expected){}
        }
        com.jcraft.jsch.KeyPair weak=com.jcraft.jsch.KeyPair.genKeyPair(new JSch(),com.jcraft.jsch.KeyPair.RSA,2048);ByteArrayOutputStream out=new ByteArrayOutputStream();weak.writePublicKey(out,"weak");weak.dispose();
        try{EmbeddedSftpServer.authorizedKey(out.toString("UTF-8"));fail("Weak RSA accepted");}catch(Exception expected){}
    }
}
