package com.bitpoint.homeservercontrol;
import java.nio.file.*;
import java.security.*;
public class EmbeddedAndroidBootstrapProbe {
    public static void main(String[] args) throws Exception {
        System.clearProperty("user.home");System.clearProperty("user.name");
        org.apache.sshd.common.util.OsUtils.setAndroid(true);
        KeyPairGenerator generator=KeyPairGenerator.getInstance("RSA");generator.initialize(3072);KeyPair pair=generator.generateKeyPair();
        Path root=Files.createTempDirectory("localls-android-bootstrap-");
        try(EmbeddedSftpServer server=new EmbeddedSftpServer()) {
            int port=server.start("127.0.0.1",0,"localls",pair.getPublic(),pair,root);
            try(java.net.Socket socket=new java.net.Socket("127.0.0.1",port)) {System.out.println("STARTED_WITHOUT_DESKTOP_PROPERTIES");}
        } finally {Files.deleteIfExists(root);}
    }
}
