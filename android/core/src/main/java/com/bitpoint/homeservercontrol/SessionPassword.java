package com.bitpoint.homeservercontrol;

/** One-shot, process-local handoff. Secrets never enter Intent extras or saved state. */
final class SessionPassword {
    private static String pending;

    private SessionPassword() {}

    static synchronized void put(String password) {
        pending = password;
    }

    static synchronized String take() {
        String value = pending;
        pending = null;
        return value;
    }
}
