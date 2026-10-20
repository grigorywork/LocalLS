package com.bitpoint.homeservercontrol;

/** UI-thread state for directory reads. Only the latest request may enable writes. */
final class DirectoryRequestState {
    private long generation;
    private boolean ready;

    long begin() {
        ready = false;
        return ++generation;
    }

    boolean isCurrent(long request) { return request == generation; }

    boolean complete(long request) {
        if (!isCurrent(request)) return false;
        ready = true;
        return true;
    }

    boolean isReady() { return ready; }

    void invalidate() { begin(); }
}
