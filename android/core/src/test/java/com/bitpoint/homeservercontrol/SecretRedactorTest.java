package com.bitpoint.homeservercontrol;
import org.junit.Test;
import static org.junit.Assert.*;
public class SecretRedactorTest {
    @Test public void hidesOverlappingPasswordAndTokenInEitherOrder() {
        String text = "password=short; token=short-long-token";
        String expected = "password=[скрыто]; token=[скрыто]";
        assertEquals(expected, SecretRedactor.redact(text, "short", "short-long-token"));
        assertEquals(expected, SecretRedactor.redact(text, "short-long-token", "short"));
    }
    @Test public void preservesOtherTextAndHandlesMissingValues() {
        assertEquals("status ok", SecretRedactor.redact("status ok", null, ""));
        assertEquals("", SecretRedactor.redact(null, "secret"));
    }
}
