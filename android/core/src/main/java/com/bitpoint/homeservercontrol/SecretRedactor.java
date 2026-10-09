package com.bitpoint.homeservercontrol;

final class SecretRedactor {
    private SecretRedactor() {}

    static String redact(String text, String... secrets) {
        String result = text == null ? "" : text;
        // A password may be a prefix of the agent token. Hide the longer value first.
        String[] ordered = secrets.clone();
        java.util.Arrays.sort(ordered, java.util.Comparator.comparingInt(
                (String value) -> value == null ? 0 : value.length()).reversed());
        for (String secret : ordered) {
            if (secret != null && !secret.isEmpty()) result = result.replace(secret, "[скрыто]");
        }
        return result;
    }
}
