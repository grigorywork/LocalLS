package com.bitpoint.homeservercontrol;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class SftpPayloadTelemetryTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private static KeyPair host;private static PublicKey allowed;private static SshCredentials credentials;
    @BeforeClass public static void keys()throws Exception{
        var rsa=KeyPairGenerator.getInstance("RSA");rsa.initialize(3072);host=rsa.generateKeyPair();
        var key=com.jcraft.jsch.KeyPair.genKeyPair(new com.jcraft.jsch.JSch(),com.jcraft.jsch.KeyPair.RSA,3072);
        ByteArrayOutputStream secret=new ByteArrayOutputStream(),pub=new ByteArrayOutputStream();key.writePrivateKey(secret);key.writePublicKey(pub,"disposable-test");key.dispose();
        credentials=SshCredentials.key(secret.toString("UTF-8"),"");allowed=EmbeddedSftpServer.authorizedKey(pub.toString("UTF-8"));
    }
    @Test public void equalSizedFilesCountEveryPayloadByteExactlyOnceAndIgnoreMetadata()throws Exception{
        byte[] bytes=new byte[131089];new Random(51).nextBytes(bytes);Path root=temp.newFolder().toPath();AtomicLong payload=new AtomicLong();
        try(var server=new EmbeddedSftpServer()){
            int port=server.startAuthorized("127.0.0.1",0,"localls",List.of(allowed),host,root);
            try(var files=new SftpClient.TreeSession("127.0.0.1",port,"localls",credentials,EmbeddedSftpServer.fingerprint(host))){
                files.onPayload(n->{assertTrue(n>0);payload.addAndGet(n);});
                String one=files.write("/","one.bin",new ByteArrayInputStream(bytes),bytes.length,(n,total)->true);
                String two=files.write("/","two.bin",new ByteArrayInputStream(bytes),bytes.length,(n,total)->true);
                assertEquals(2L*bytes.length,payload.get());
                files.list("/");files.entry(one);assertEquals("Metadata is not file throughput",2L*bytes.length,payload.get());
                for(String path:List.of(one,two)){var out=new ByteArrayOutputStream();files.read(path,out,(n,total)->true);assertArrayEquals(bytes,out.toByteArray());}
                assertEquals("Stream wrappers must not double-count write(byte[])",4L*bytes.length,payload.get());
                assertArrayEquals(bytes,Files.readAllBytes(root.resolve("one.bin")));assertArrayEquals(bytes,Files.readAllBytes(root.resolve("two.bin")));
            }
        }
    }
    @Test public void resumeValidationAndChecksumCountActualNetworkDataWithoutInventingLocalWrites()throws Exception{
        byte[] bytes=new byte[131089];new Random(52).nextBytes(bytes);int prefix=bytes.length/2;Path root=temp.newFolder().toPath();AtomicLong payload=new AtomicLong();
        Files.write(root.resolve(".localls-upload-meter.part"),Arrays.copyOf(bytes,prefix));
        try(var server=new EmbeddedSftpServer()){
            int port=server.startAuthorized("127.0.0.1",0,"localls",List.of(allowed),host,root);
            try(var files=new SftpClient.TreeSession("127.0.0.1",port,"localls",credentials,EmbeddedSftpServer.fingerprint(host))){
                files.onPayload(n->payload.addAndGet(n));
                files.writeResumable("/","resumed.bin",new ByteArrayInputStream(bytes),bytes.length,(n,total)->true,".localls-upload-meter.part","/resumed.bin","keep",true,null);
                assertArrayEquals(bytes,Files.readAllBytes(root.resolve("resumed.bin")));
                // Download retained prefix to validate + upload remainder + read back full hash.
                assertEquals(2L*bytes.length,payload.get());
                File partial=temp.newFile();Files.write(partial.toPath(),Arrays.copyOf(bytes,prefix));payload.set(0);
                files.readResumable("/resumed.bin",partial,(n,total)->true);
                assertArrayEquals(bytes,Files.readAllBytes(partial.toPath()));assertEquals(bytes.length,payload.get());
                Files.copy(partial.toPath(),temp.getRoot().toPath().resolve("local-copy"));assertEquals("Publishing a local file cannot add SFTP bytes",bytes.length,payload.get());
            }
        }
    }
}
