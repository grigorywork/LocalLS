package com.bitpoint.homeservercontrol;
import java.util.concurrent.atomic.*;
import java.nio.file.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
public class ServerTelemetryTransportTest {
 @Rule public TemporaryFolder temp=new TemporaryFolder();
 @Test public void freshMetricsUsePinnedKeyAuthenticationWithoutShellOrShareFiles()throws Exception{
  SevenDevicesTest.setup();var value=new AtomicReference<>(new ServerTelemetry(73,2147483648L,4294967296L,3660));var reads=new AtomicInteger();
  Path root=temp.newFolder().toPath();Files.writeString(root.resolve("keep.txt"),"unchanged");
  try(var server=new EmbeddedSftpServer()){
   server.telemetry(()->{reads.incrementAndGet();return value.get();});int port=server.start("127.0.0.1",0,"localls",EmbeddedSftpServer.authorizedKey(SevenDevicesTest.keys.get(0)),SevenDevicesTest.host,root);
   String pin=EmbeddedSftpServer.fingerprint(SevenDevicesTest.host);
   ServerStats stats=SshClient.probe("127.0.0.1",port,"localls",SevenDevicesTest.credentials.get(0),pin);
   assertTrue(stats.error,stats.authenticated);assertTrue(stats.sftpOnly);assertEquals("",stats.error);assertEquals("73%",stats.battery);assertEquals("2.0 ГБ",stats.freeSpace);assertEquals(1,reads.get());
   value.set(new ServerTelemetry(72,1073741824L,4294967296L,3720));stats=SshClient.probe("127.0.0.1",port,"localls",SevenDevicesTest.credentials.get(0),pin);assertEquals("72%",stats.battery);assertEquals("1.0 ГБ",stats.freeSpace);assertEquals(2,reads.get());
   assertFalse(SshClient.probe("127.0.0.1",port,"localls",SevenDevicesTest.credentials.get(1),pin).authenticated);assertEquals(2,reads.get());
   assertTrue(SshClient.probe("127.0.0.1",port,"localls",SevenDevicesTest.credentials.get(0),"wrong-pin").hostKeyMismatch);assertEquals(2,reads.get());
   try(var files=Files.list(root)){assertEquals(1,files.count());}assertEquals("unchanged",Files.readString(root.resolve("keep.txt")));
   assertFalse(SshClient.runCommand("127.0.0.1",port,"localls",SevenDevicesTest.credentials.get(0),pin,"id",2000).success);
   server.revokeClient(SevenDevicesTest.keys.get(0));assertFalse(SshClient.probe("127.0.0.1",port,"localls",SevenDevicesTest.credentials.get(0),pin).authenticated);assertEquals(2,reads.get());
  }
 }
}
