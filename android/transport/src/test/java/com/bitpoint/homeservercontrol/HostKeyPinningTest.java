package com.bitpoint.homeservercontrol;
import com.jcraft.jsch.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class HostKeyPinningTest {
    @Test public void rejectsChangedAndMalformedKeysAndAcceptsExactPin() throws Exception {
        JSch.setConfig("FingerprintHash", "sha256");
        JSch jsch = new JSch();
        KeyPair first = KeyPair.genKeyPair(jsch, KeyPair.RSA, 2048);
        KeyPair changed = KeyPair.genKeyPair(jsch, KeyPair.RSA, 2048);
        try {
            String fingerprint = new HostKey("test.invalid", first.getPublicKeyBlob()).getFingerPrint(jsch);
            PinnedHostKeyRepository pins = new PinnedHostKeyRepository(jsch, fingerprint);
            assertEquals(HostKeyRepository.OK, pins.check("test.invalid", first.getPublicKeyBlob()));
            assertEquals(HostKeyRepository.CHANGED, pins.check("test.invalid", changed.getPublicKeyBlob()));
            assertEquals(HostKeyRepository.CHANGED, pins.check("test.invalid", new byte[]{1,2,3}));
        } finally { first.dispose(); changed.dispose(); }
    }
}
