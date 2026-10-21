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
    @Test public void serverStartsLockedAndOnlyLocalApprovalEnablesFirstKey() throws Exception {
        Path root=temp.newFolder().toPath();Files.write(root.resolve("test.txt"),new byte[]{1,2,3});
        try(EmbeddedSftpServer server=new EmbeddedSftpServer()) {
            int port=server.startWaiting("127.0.0.1",0,"localls",host,root);
            try(java.net.Socket probe=new java.net.Socket("127.0.0.1",port)) {assertTrue(probe.isConnected());}
            for(boolean hasKey:new boolean[]{true,false}) {
                Session denied=session(port,"localls",hasKey);
                try{denied.connect(4000);fail("Access before local approval");}catch(JSchException expected){}finally{denied.disconnect();}
            }
            server.authorizeFirstClient(EmbeddedSftpServer.authorizedKey(clientPublic));
            Session accepted=session(port,"localls",true);accepted.connect(5000);
            try {ChannelSftp files=(ChannelSftp)accepted.openChannel("sftp");files.connect(4000);
                ByteArrayOutputStream out=new ByteArrayOutputStream();files.get("/test.txt",out);assertArrayEquals(new byte[]{1,2,3},out.toByteArray());files.disconnect();
            }finally{accepted.disconnect();}
            try{server.authorizeFirstClient(host.getPublic());fail("Live replacement accepted");}catch(IllegalStateException expected){}
            server.close();
            int nextPort=server.startWaiting("127.0.0.1",0,"localls",host,root);
            Session deniedAgain=session(nextPort,"localls",true);
            try{deniedAgain.connect(4000);fail("Old authorization survived explicit locked restart");}catch(JSchException expected){}finally{deniedAgain.disconnect();}
        }
    }
    @Test public void atomicFolderUploadsNotifyOnlyFinalNamesAndCancellationDoesNotPublish()throws Exception{
        Path root=temp.newFolder().toPath();java.util.concurrent.BlockingQueue<String> received=new java.util.concurrent.LinkedBlockingQueue<>();
        try(EmbeddedSftpServer server=new EmbeddedSftpServer()){
            server.onReceived(received::add);int port=server.start("127.0.0.1",0,"localls",EmbeddedSftpServer.authorizedKey(clientPublic),host,root);
            String pin=EmbeddedSftpServer.fingerprint(host);SshCredentials credential=SshCredentials.key(new String(clientPrivate,"UTF-8"),"");
            try(SftpClient.TreeSession tree=new SftpClient.TreeSession("127.0.0.1",port,"localls",credential,pin)){
                String folder=tree.folder("/","Папка");tree.folder(folder,"пустая");byte[] payload=new byte[2*1024*1024+17];new java.util.Random(71).nextBytes(payload);
                String first=tree.write(folder,"файл.bin",new ByteArrayInputStream(payload),payload.length,(done,total)->true);
                assertEquals("Папка/файл.bin",received.poll(5,java.util.concurrent.TimeUnit.SECONDS));
                ByteArrayOutputStream copy=new ByteArrayOutputStream();tree.read(first,copy,(done,total)->true);assertArrayEquals(payload,copy.toByteArray());assertNull(received.poll(100,java.util.concurrent.TimeUnit.MILLISECONDS));
                tree.write(folder,"файл.bin",new ByteArrayInputStream(new byte[]{9}),1,(done,total)->true);
                assertEquals("Папка/файл (1).bin",received.poll(5,java.util.concurrent.TimeUnit.SECONDS));assertArrayEquals(payload,Files.readAllBytes(root.resolve("Папка/файл.bin")));
                try{tree.write(folder,"cancel.bin",new ByteArrayInputStream(payload),0,(done,total)->false);fail("Cancelled unknown-length stream was published");}catch(java.io.InterruptedIOException expected){}
                assertFalse(Files.exists(root.resolve("Папка/cancel.bin")));assertNull(received.poll(100,java.util.concurrent.TimeUnit.MILLISECONDS));
                for(RemoteEntry e:tree.list(folder))assertFalse(e.name.startsWith(".localls-upload-"));
            }
        }
    }
    @Test public void folderSnapshotExcludesInFlightUploadsButKeepsOrdinaryHiddenFiles()throws Exception{
        Path root=temp.newFolder().toPath();
        Files.write(root.resolve(".localls-upload-pending.part"),new byte[]{1});
        Files.write(root.resolve(".notes"),new byte[]{2});
        Files.write(root.resolve("complete.bin"),new byte[]{3});
        try(EmbeddedSftpServer server=new EmbeddedSftpServer()){
            int port=server.start("127.0.0.1",0,"localls",EmbeddedSftpServer.authorizedKey(clientPublic),host,root);
            try(SftpClient.TreeSession tree=new SftpClient.TreeSession("127.0.0.1",port,"localls",SshCredentials.key(new String(clientPrivate,"UTF-8"),""),EmbeddedSftpServer.fingerprint(host))){
                java.util.List<String> names=new java.util.ArrayList<>();for(RemoteEntry entry:tree.list("/"))names.add(entry.name);
                assertTrue(names.contains(".notes"));assertTrue(names.contains("complete.bin"));
                assertFalse("A partial concurrent upload must not be copied as a complete file",names.contains(".localls-upload-pending.part"));
                assertArrayEquals(new byte[]{1},Files.readAllBytes(root.resolve(".localls-upload-pending.part")));
            }
        }
    }
    @Test public void downloadCancellationOnFinalBlockMustNotReportSuccess()throws Exception{
        Path root=temp.newFolder().toPath();Files.write(root.resolve("small.bin"),new byte[]{1,2,3});
        try(EmbeddedSftpServer server=new EmbeddedSftpServer()){
            int port=server.start("127.0.0.1",0,"localls",EmbeddedSftpServer.authorizedKey(clientPublic),host,root);
            try(SftpClient.TreeSession tree=new SftpClient.TreeSession("127.0.0.1",port,"localls",SshCredentials.key(new String(clientPrivate,"UTF-8"),""),EmbeddedSftpServer.fingerprint(host))){
                try{tree.read("/small.bin",new ByteArrayOutputStream(),(done,total)->false);fail("Cancellation on final block was reported as success");}catch(InterruptedIOException expected){}
            }
        }
    }
    @Test public void changedRemoteSourceIsNotReportedAsVerifiedCopy()throws Exception{
        Path root=temp.newFolder().toPath(),source=root.resolve("changing.bin");Files.write(source,new byte[]{1,2,3});
        long originalTime=Files.getLastModifiedTime(source).toMillis();
        try(EmbeddedSftpServer server=new EmbeddedSftpServer()){
            int port=server.start("127.0.0.1",0,"localls",EmbeddedSftpServer.authorizedKey(clientPublic),host,root);
            try(SftpClient.TreeSession tree=new SftpClient.TreeSession("127.0.0.1",port,"localls",SshCredentials.key(new String(clientPrivate,"UTF-8"),""),EmbeddedSftpServer.fingerprint(host))){
                OutputStream target=new ByteArrayOutputStream(){
                    @Override public synchronized void write(byte[] bytes,int offset,int length){super.write(bytes,offset,length);try{Files.setLastModifiedTime(source,java.nio.file.attribute.FileTime.fromMillis(originalTime+10000));}catch(IOException e){throw new java.io.UncheckedIOException(e);}}
                };
                try{tree.read("/changing.bin",target,(done,total)->true);fail("Changed source was reported as a complete stable copy");}catch(IOException expected){}
            }
        }
    }
    @Test public void uploadCancellationCannotBeReversedByMonitorEnd()throws Exception{
        Path root=temp.newFolder().toPath();
        try(EmbeddedSftpServer server=new EmbeddedSftpServer()){
            int port=server.start("127.0.0.1",0,"localls",EmbeddedSftpServer.authorizedKey(clientPublic),host,root);
            try(SftpClient.TreeSession tree=new SftpClient.TreeSession("127.0.0.1",port,"localls",SshCredentials.key(new String(clientPrivate,"UTF-8"),""),EmbeddedSftpServer.fingerprint(host))){
                java.util.concurrent.atomic.AtomicInteger calls=new java.util.concurrent.atomic.AtomicInteger();
                try{tree.write("/","cancel.bin",new ByteArrayInputStream(new byte[]{1,2,3}),3,(done,total)->calls.incrementAndGet()>1);fail("Monitor end reversed cancellation");}catch(InterruptedIOException expected){}
                assertFalse(Files.exists(root.resolve("cancel.bin")));assertEquals(0,tree.list("/").size());
            }
        }
    }
    @Test public void stoppedServerDoesNotPublishPartialUploadAndRetryUsesSameIdentity()throws Exception{
        Path root=temp.newFolder().toPath();byte[] bytes=new byte[2*1024*1024+17];new java.util.Random(93).nextBytes(bytes);
        try(EmbeddedSftpServer server=new EmbeddedSftpServer()){
            int port=server.start("127.0.0.1",0,"localls",EmbeddedSftpServer.authorizedKey(clientPublic),host,root);
            String pin=EmbeddedSftpServer.fingerprint(host);SshCredentials key=SshCredentials.key(new String(clientPrivate,"UTF-8"),"");
            try(SftpClient.TreeSession tree=new SftpClient.TreeSession("127.0.0.1",port,"localls",key,pin)){
                boolean[] stopped={false};
                try{tree.write("/","retry.bin",new ByteArrayInputStream(bytes),bytes.length,(done,total)->{if(!stopped[0]){stopped[0]=true;try{server.close();}catch(IOException e){throw new java.io.UncheckedIOException(e);}}return true;});fail("Disconnected upload reported success");}catch(com.jcraft.jsch.SftpException expected){}
                assertTrue(stopped[0]);assertFalse(Files.exists(root.resolve("retry.bin")));
            }
            int nextPort=server.start("127.0.0.1",0,"localls",EmbeddedSftpServer.authorizedKey(clientPublic),host,root);
            try(SftpClient.TreeSession retry=new SftpClient.TreeSession("127.0.0.1",nextPort,"localls",key,pin)){
                assertEquals("/retry.bin",retry.write("/","retry.bin",new ByteArrayInputStream(bytes),bytes.length,(done,total)->true));
                assertArrayEquals(bytes,Files.readAllBytes(root.resolve("retry.bin")));
                assertEquals(1,retry.list("/").size());
            }
        }
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
    @Test public void simultaneousClientsTransferDifferentFiles() throws Exception {
        try(EmbeddedSftpServer server=new EmbeddedSftpServer()) {
            int port=server.start("127.0.0.1",0,"localls",EmbeddedSftpServer.authorizedKey(clientPublic),host,temp.newFolder().toPath());
            java.util.concurrent.ExecutorService workers=java.util.concurrent.Executors.newFixedThreadPool(2);
            try {
                java.util.List<java.util.concurrent.Future<?>> tasks=new java.util.ArrayList<>();
                for(int i=0;i<2;i++) {final int index=i;tasks.add(workers.submit(() -> {
                    Session connection=null;
                    try {connection=session(port,"localls",true);connection.connect(5000);ChannelSftp files=(ChannelSftp)connection.openChannel("sftp");files.connect(4000);
                        byte[] bytes=new byte[8*1024*1024+index];new java.util.Random(index+10).nextBytes(bytes);
                        files.put(new ByteArrayInputStream(bytes),"/parallel-"+index);ByteArrayOutputStream result=new ByteArrayOutputStream();files.get("/parallel-"+index,result);
                        assertArrayEquals(bytes,result.toByteArray());files.disconnect();
                    } catch(Exception e) {throw new RuntimeException(e);} finally {if(connection!=null)connection.disconnect();}
                }));}
                for(java.util.concurrent.Future<?> task:tasks)task.get(30,java.util.concurrent.TimeUnit.SECONDS);
            } finally {workers.shutdownNow();}
        }
    }
    @Test public void resumesUploadAndSupportsEmptyAndUnicodeFiles() throws Exception {
        try(EmbeddedSftpServer server=new EmbeddedSftpServer()) {
            int port=server.start("127.0.0.1",0,"localls",EmbeddedSftpServer.authorizedKey(clientPublic),host,temp.newFolder().toPath());
            Session connection=session(port,"localls",true);connection.connect(5000);
            try {ChannelSftp files=(ChannelSftp)connection.openChannel("sftp");files.connect(4000);
                byte[] bytes=new byte[2*1024*1024+7];new java.util.Random(77).nextBytes(bytes);
                String name="/Файл с пробелом.bin";
                files.put(new ByteArrayInputStream(Arrays.copyOf(bytes,bytes.length/2)),name);
                files.put(new ByteArrayInputStream(bytes),name,ChannelSftp.RESUME);
                ByteArrayOutputStream result=new ByteArrayOutputStream();files.get(name,result);assertArrayEquals(bytes,result.toByteArray());
                files.put(new ByteArrayInputStream(new byte[0]),"/пустой.txt");assertEquals(0,files.stat("/пустой.txt").getSize());
                files.disconnect();
            } finally {connection.disconnect();}
        }
    }
    @Test public void connectionLimitRejectsFifthSessionAndStoppingDisconnectsClients() throws Exception {
        EmbeddedSftpServer server=new EmbeddedSftpServer();java.util.List<Session> connections=new java.util.ArrayList<>();
        try {int port=server.start("127.0.0.1",0,"localls",EmbeddedSftpServer.authorizedKey(clientPublic),host,temp.newFolder().toPath());
            for(int i=0;i<4;i++){Session connection=session(port,"localls",true);connections.add(connection);connection.connect(5000);}
            Session excess=session(port,"localls",true);
            try{excess.connect(4000);fail("Fifth authenticated session accepted");}catch(JSchException expected){}finally{excess.disconnect();}
            ChannelSftp files=(ChannelSftp)connections.get(0).openChannel("sftp");files.connect(4000);server.close();
            try{files.ls("/");fail("Client still works after stop");}catch(SftpException expected){}
        } finally {server.close();for(Session connection:connections)connection.disconnect();}
    }
    @Test public void busyPortDoesNotPreventRetryAndDirectForwardAndHardlinkAreDenied() throws Exception {
        try(EmbeddedSftpServer server=new EmbeddedSftpServer()) {
            Path root=temp.newFolder().toPath();
            try(java.net.ServerSocket occupied=new java.net.ServerSocket(0)) {
                try{server.start("127.0.0.1",occupied.getLocalPort(),"localls",EmbeddedSftpServer.authorizedKey(clientPublic),host,root);fail("Started on busy port");}catch(IOException expected){}
            }
            int port=server.start("127.0.0.1",0,"localls",EmbeddedSftpServer.authorizedKey(clientPublic),host,root);
            Session connection=session(port,"localls",true);connection.connect(5000);
            try{ChannelSftp files=(ChannelSftp)connection.openChannel("sftp");files.connect(4000);files.put(new ByteArrayInputStream(new byte[]{42}),"/original");
                try{files.hardlink("/original","/alias");fail("Hardlink accepted");}catch(SftpException expected){}
                ChannelDirectTCPIP direct=(ChannelDirectTCPIP)connection.openChannel("direct-tcpip");direct.setHost("127.0.0.1");direct.setPort(port);
                try{direct.connect(2000);fail("Direct forward accepted");}catch(JSchException expected){}finally{direct.disconnect();}
                files.disconnect();
            }finally{connection.disconnect();}
        }
    }
    @Test public void independentOpenSshClientCanTransferWithStrictHostChecking() throws Exception {
        Path work=temp.newFolder().toPath(),root=Files.createDirectory(work.resolve("share"));
        Path identity=work.resolve("id_rsa");Files.write(identity,clientPrivate);
        Files.setPosixFilePermissions(identity,java.util.EnumSet.of(java.nio.file.attribute.PosixFilePermission.OWNER_READ,java.nio.file.attribute.PosixFilePermission.OWNER_WRITE));
        try(EmbeddedSftpServer server=new EmbeddedSftpServer()) {
            int port=server.start("127.0.0.1",0,"localls",EmbeddedSftpServer.authorizedKey(clientPublic),host,root);
            Session captured=session(port,"localls",true);captured.connect(5000);
            String hostEntry="[127.0.0.1]:"+port+" "+captured.getHostKey().getType()+" "+captured.getHostKey().getKey()+"\n";captured.disconnect();
            Path known=work.resolve("known_hosts");Files.write(known,hostEntry.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] bytes=new byte[3*1024*1024+31];new java.util.Random(991).nextBytes(bytes);
            Path input=work.resolve("input.bin"),output=work.resolve("output.bin"),batch=work.resolve("batch.txt"),log=work.resolve("openssh.log");Files.write(input,bytes);
            String commands="put "+input+" /independent.bin\nget /independent.bin "+output+"\nrm /independent.bin\n";
            Files.write(batch,commands.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            Process process=new ProcessBuilder("sftp","-b",batch.toString(),"-P",String.valueOf(port),"-i",identity.toString(),
                    "-o","BatchMode=yes","-o","StrictHostKeyChecking=yes","-o","UserKnownHostsFile="+known,
                    "-o","IdentitiesOnly=yes","-o","IdentityAgent=none","localls@127.0.0.1").redirectErrorStream(true).redirectOutput(log.toFile()).start();
            if(!process.waitFor(20,java.util.concurrent.TimeUnit.SECONDS)){process.destroyForcibly();fail("OpenSSH client timeout");}
            assertEquals(new String(Files.readAllBytes(log),java.nio.charset.StandardCharsets.UTF_8),0,process.exitValue());
            assertArrayEquals(bytes,Files.readAllBytes(output));assertFalse(Files.exists(root.resolve("independent.bin")));
        }
    }
    @Test public void ecdsaPublicKeyParsesAndAuthenticates() throws Exception {
        JSch client=new JSch();
        com.jcraft.jsch.KeyPair pair=com.jcraft.jsch.KeyPair.genKeyPair(client,com.jcraft.jsch.KeyPair.ECDSA,256);
        ByteArrayOutputStream priv=new ByteArrayOutputStream(),pub=new ByteArrayOutputStream();
        pair.writePrivateKey(priv);pair.writePublicKey(pub,"test");pair.dispose();
        String text=pub.toString("UTF-8");
        client.addIdentity("ec",priv.toByteArray(),null,null);
        try(EmbeddedSftpServer server=new EmbeddedSftpServer()) {
            int port=server.start("127.0.0.1",0,"localls",EmbeddedSftpServer.authorizedKey(text),host,temp.newFolder().toPath());
            Session session=client.getSession("localls","127.0.0.1",port);
            session.setConfig("StrictHostKeyChecking","no");session.setConfig("PreferredAuthentications","publickey");
            try {session.connect(5000);assertTrue(session.isConnected());} finally {session.disconnect();}
        }
    }
    @Test public void wrappedPublicKeyIsNormalizedWithoutChangingIdentity() throws Exception {
        String[] fields=clientPublic.trim().split("\\s+");
        String wrapped=fields[0]+"\n"+fields[1].replaceAll("(.{64})","$1\n")+"\nLocalLS";
        assertArrayEquals(EmbeddedSftpServer.authorizedKey(clientPublic).getEncoded(),EmbeddedSftpServer.authorizedKey(wrapped).getEncoded());
        assertFalse(EmbeddedSftpServer.normalizeAuthorizedKey(wrapped).contains("\n"));
    }
    @Test public void privateAndMalformedAndWeakPublicKeysAreRejected() throws Exception {
        for(String value:new String[]{new String(clientPrivate,"UTF-8"),"ssh-rsa invalid","ssh-ed25519 AAAA","",clientPublic+clientPublic}) {
            try{EmbeddedSftpServer.authorizedKey(value);fail("Invalid public key accepted");}catch(Exception expected){}
        }
        com.jcraft.jsch.KeyPair weak=com.jcraft.jsch.KeyPair.genKeyPair(new JSch(),com.jcraft.jsch.KeyPair.RSA,2048);ByteArrayOutputStream out=new ByteArrayOutputStream();weak.writePublicKey(out,"weak");weak.dispose();
        try{EmbeddedSftpServer.authorizedKey(out.toString("UTF-8"));fail("Weak RSA accepted");}catch(Exception expected){}
    }
}
