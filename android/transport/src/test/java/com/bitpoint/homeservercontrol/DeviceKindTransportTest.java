package com.bitpoint.homeservercontrol;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class DeviceKindTransportTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    @Test public void authenticatedFileListingReportsTheActualPeerKindAndPreservesFiles()throws Exception{
        SevenDevicesTest.setup();var kind=new AtomicReference<>(DeviceKind.PHONE);Path root=temp.newFolder().toPath();Files.writeString(root.resolve("keep.txt"),"unchanged");
        try(var server=new EmbeddedSftpServer()){
            server.telemetry(()->new ServerTelemetry(-1,-1,-1,-1,-1,kind.get()));
            int port=server.start("127.0.0.1",0,"localls",EmbeddedSftpServer.authorizedKey(SevenDevicesTest.keys.get(0)),SevenDevicesTest.host,root);
            String pin=EmbeddedSftpServer.fingerprint(SevenDevicesTest.host);
            for(DeviceKind expected:DeviceKind.values()){
                kind.set(expected);
                var listing=SftpClient.browse("127.0.0.1",port,"localls",SevenDevicesTest.credentials.get(0),pin,"/",true);
                assertEquals(expected,listing.deviceKind);assertEquals("keep.txt",listing.entries.get(0).name);
            }
            try{SftpClient.browse("127.0.0.1",port,"localls",SevenDevicesTest.credentials.get(0),"wrong-pin","/",true);fail("Untrusted identity must not supply labels");}catch(Exception expected){}
            assertEquals("unchanged",Files.readString(root.resolve("keep.txt")));
        }
    }
    @Test public void oldServerWithoutKindSubsystemStillListsFiles()throws Exception{
        SevenDevicesTest.setup();Path root=temp.newFolder().toPath();Files.writeString(root.resolve("old.txt"),"old");
        var server=org.apache.sshd.server.SshServer.setUpDefaultServer();
        server.setHost("127.0.0.1");server.setPort(0);server.setKeyPairProvider(session->java.util.List.of(SevenDevicesTest.host));
        var allowed=EmbeddedSftpServer.authorizedKey(SevenDevicesTest.keys.get(0));
        server.setPublickeyAuthenticator((user,key,session)->user.equals("localls")&&key.equals(allowed));
        server.setFileSystemFactory(new org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory(root));
        server.setSubsystemFactories(java.util.List.of(new org.apache.sshd.sftp.server.SftpSubsystemFactory.Builder().build()));
        server.start();try{
            var listing=SftpClient.browse("127.0.0.1",server.getPort(),"localls",SevenDevicesTest.credentials.get(0),EmbeddedSftpServer.fingerprint(SevenDevicesTest.host),"/",true);
            assertEquals(DeviceKind.UNKNOWN,listing.deviceKind);assertEquals("old.txt",listing.entries.get(0).name);
        }finally{server.stop(true);}
    }
}
