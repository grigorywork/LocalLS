package com.bitpoint.homeservercontrol;

final class SecretRedactor {
    private SecretRedactor() {}

    static String redact(String text, String... secrets) {
        String result = text == null ? "" : text;
        for (String secret : secrets) {
            if (secret != null && !secret.isEmpty()) result = result.replace(secret, "[скрыто]");
        }
        return result;
    }
}
