package com.bitpoint.homeservercontrol;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import static org.junit.Assert.*;
public class EmbeddedAndroidBootstrapTest {
    @Test public void startsWithoutDesktopHomeAndUsernameInFreshVm() throws Exception {
        LinkedHashSet<String> entries=new LinkedHashSet<>();
        for(Class<?> c:new Class<?>[]{EmbeddedAndroidBootstrapProbe.class,EmbeddedSftpServer.class,ConnectionEndpoint.class,
                org.apache.sshd.server.SshServer.class,org.apache.sshd.common.config.keys.AuthorizedKeyEntry.class,
                org.apache.sshd.sftp.server.SftpSubsystemFactory.class,org.slf4j.LoggerFactory.class,
                Class.forName("org.slf4j.nop.NOPServiceProvider")})
            entries.add(Paths.get(c.getProtectionDomain().getCodeSource().getLocation().toURI()).toString());
        Path log=Files.createTempFile("localls-android-bootstrap-", ".log");
        try {
            Process p=new ProcessBuilder(System.getProperty("java.home")+"/bin/java","-cp",String.join(java.io.File.pathSeparator,entries),EmbeddedAndroidBootstrapProbe.class.getName())
                    .redirectErrorStream(true).redirectOutput(log.toFile()).start();
            if(!p.waitFor(20,TimeUnit.SECONDS)){p.destroyForcibly();fail("Bootstrap timed out");}
            String output=new String(Files.readAllBytes(log),java.nio.charset.StandardCharsets.UTF_8);
            assertEquals(output,0,p.exitValue());assertTrue(output,output.contains("STARTED_WITHOUT_DESKTOP_PROPERTIES"));
        }finally{Files.deleteIfExists(log);}
    }
}
