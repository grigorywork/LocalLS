package com.bitpoint.homeservercontrol;

import org.junit.Test;
import static org.junit.Assert.*;

public class DirectoryRequestStateTest {
    @Test public void olderReplyCannotEnableWritesDuringNewRequest() {
        DirectoryRequestState state = new DirectoryRequestState();
        long old = state.begin();
        long latest = state.begin();
        assertFalse(state.complete(old));
        assertFalse(state.isReady());
        assertTrue(state.complete(latest));
        assertTrue(state.isReady());
        assertFalse(state.isCurrent(old));
    }

    @Test public void destructionAndFailedNavigationLeaveWritesDisabled() {
        DirectoryRequestState state = new DirectoryRequestState();
        long first = state.begin();
        assertTrue(state.complete(first));
        long next = state.begin();
        assertFalse(state.isReady());
        state.invalidate();
        assertFalse(state.complete(next));
        assertFalse(state.isReady());
    }
}
