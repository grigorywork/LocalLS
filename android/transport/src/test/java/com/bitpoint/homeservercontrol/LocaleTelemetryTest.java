package com.bitpoint.homeservercontrol;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.sshd.server.*;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.ExitCallback;
import org.junit.*;
import static org.junit.Assert.*;
public class LocaleTelemetryTest {
 @Test public void languageDoesNotChangeSshCommandsOrLoseNumericStorageMetrics()throws Exception{
  SevenDevicesTest.setup();var allowed=EmbeddedSftpServer.authorizedKey(SevenDevicesTest.keys.get(0));List<String> commands=new CopyOnWriteArrayList<>();
  try(var server=org.apache.sshd.server.SshServer.setUpDefaultServer()){
   server.setHost("127.0.0.1");server.setPort(0);server.setKeyPairProvider(ignored->Collections.singleton(SevenDevicesTest.host));server.setPublickeyAuthenticator((user,key,session)->org.apache.sshd.common.config.keys.KeyUtils.compareKeys(key,allowed));
   server.setCommandFactory((channel,command)->{commands.add(command);String result=command.equals("uptime")?"up 3 hours":command.startsWith("df -h")?"/dev/test 256G 100G 156G 40% /storage":command.startsWith("ip addr")?"inet 192.0.2.7/24":command.startsWith("pgrep")?"123 sshd":command.startsWith("command -v termux")?"__NO_TERMUX_API__":command.startsWith("command -v iperf3")?"iperf 3.19":"";return new Command(){OutputStream out;ExitCallback exit;public void setInputStream(InputStream in){}public void setOutputStream(OutputStream value){out=value;}public void setErrorStream(OutputStream err){}public void setExitCallback(ExitCallback value){exit=value;}public void start(ChannelSession session,Environment env)throws IOException{out.write(result.getBytes(StandardCharsets.UTF_8));out.flush();exit.onExit(0);}public void destroy(ChannelSession session){}};});server.start();
   List<String> russian=null;
   for(String language:new String[]{"ru","en"}){AppText.language(language);commands.clear();ServerStats stats=SshClient.probe("127.0.0.1",server.getPort(),"localls",SevenDevicesTest.credentials.get(0),EmbeddedSftpServer.fingerprint(SevenDevicesTest.host));assertTrue(stats.error,stats.authenticated);assertEquals("",stats.error);assertEquals("156G",stats.freeSpace);assertEquals("192.0.2.7/24",stats.ip);assertEquals("iperf 3.19",stats.iperf);if(russian==null)russian=new ArrayList<>(commands);else assertEquals("Language must not modify remote commands",russian,commands);}
  }finally{AppText.language("ru");}
 }
}
