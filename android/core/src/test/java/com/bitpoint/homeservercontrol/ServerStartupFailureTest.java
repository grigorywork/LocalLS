package com.bitpoint.homeservercontrol;
import org.junit.Test;
import static org.junit.Assert.*;
public class ServerStartupFailureTest {
    @Test public void nestedBootstrapCauseIsVisibleWithoutSecretMessage() {
        Throwable cause=new ExceptionInInitializerError(new IllegalArgumentException("private-key-password-example"));
        String report=ServerStartupFailure.report("NETWORK",cause);
        assertTrue(report.contains("ExceptionInInitializerError"));assertTrue(report.contains("IllegalArgumentException"));
        assertTrue(report.contains("NETWORK"));assertFalse(report.contains("private-key-password-example"));
    }
    @Test public void portCollisionAndIdentityFailureHaveActionableHints() {
        assertTrue(ServerStartupFailure.hint("NETWORK",new java.io.IOException(new java.net.BindException("sensitive"))).contains("Порт занят"));
        assertTrue(ServerStartupFailure.hint("IDENTITY",new IllegalStateException("sensitive")).contains("Ключ не заменён"));
        assertFalse(ServerStartupFailure.report("sensitive",new RuntimeException("sensitive")).contains("sensitive"));
    }
}
