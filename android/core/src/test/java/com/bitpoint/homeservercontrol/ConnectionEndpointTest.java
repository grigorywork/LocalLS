package com.bitpoint.homeservercontrol;
import org.junit.Test;
import static org.junit.Assert.*;
public class ConnectionEndpointTest {
    @Test public void importsEndpointAndKeepsDefaults() {
        ConnectionEndpoint e = ConnectionEndpoint.parse("sftp://u0_a123@192.168.1.82:8022", "fallback", 22);
        assertEquals("192.168.1.82", e.host); assertEquals("u0_a123", e.user); assertEquals(8022, e.port);
        e = ConnectionEndpoint.parse("nas.local", "termux", 8022);
        assertEquals("termux", e.user); assertEquals(8022, e.port);
    }
    @Test public void supportsIpv6WithoutNetworkLookup() {
        ConnectionEndpoint e = ConnectionEndpoint.parse("ssh://user@[2001:db8::1]:22", "fallback", 8022);
        assertEquals("2001:db8::1", e.host); assertEquals(22, e.port);
        assertTrue(ConnectionEndpoint.validHost("::1"));
    }
    @Test public void rejectsSecretsPathsAndMalformedEndpoints() {
        for (String value : new String[]{"ssh://user:password@host", "ssh://host/path", "http://host", "host?x=y", "host#frag", "host:65536", "host:0", "999.1.2.3", "ssh://a%40b@host", "-host", "host..local", "host;reboot", "[::::]", "host:", "ssh://user@host:", "ssh://[::1]:"}) {
            try { ConnectionEndpoint.parse(value, "user", 8022); fail(value); }
            catch (IllegalArgumentException expected) { }
        }
    }
}
