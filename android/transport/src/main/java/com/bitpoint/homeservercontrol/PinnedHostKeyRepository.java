package com.bitpoint.homeservercontrol;

import com.jcraft.jsch.HostKey;
import com.jcraft.jsch.HostKeyRepository;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.UserInfo;

/**
 * Small in-memory HostKeyRepository used to pin the SSH host key before authentication.
 * When expectedFingerprint is empty it acts as a capture-only TOFU probe and never stores data.
 */
final class PinnedHostKeyRepository implements HostKeyRepository {
    private final JSch jsch;
    private final String expectedFingerprint;
    private volatile String lastFingerprint = "";
    private volatile HostKey lastHostKey;

    PinnedHostKeyRepository(JSch jsch, String expectedFingerprint) {
        this.jsch = jsch;
        this.expectedFingerprint = expectedFingerprint == null ? "" : expectedFingerprint.trim();
    }

    String getLastFingerprint() {
        return lastFingerprint;
    }

    @Override
    public int check(String host, byte[] key) {
        try {
            HostKey hostKey = new HostKey(host, key);
            lastHostKey = hostKey;
            lastFingerprint = hostKey.getFingerPrint(jsch);
            if (expectedFingerprint.isEmpty()) return OK;
            return expectedFingerprint.equals(lastFingerprint) ? OK : CHANGED;
        } catch (Exception e) {
            return CHANGED;
        }
    }

    @Override public void add(HostKey hostkey, UserInfo ui) { }
    @Override public void remove(String host, String type) { }
    @Override public void remove(String host, String type, byte[] key) { }
    @Override public String getKnownHostsRepositoryID() { return "HomeServerControl in-memory pin"; }

    @Override
    public HostKey[] getHostKey() {
        HostKey key = lastHostKey;
        return key == null ? null : new HostKey[]{key};
    }

    @Override
    public HostKey[] getHostKey(String host, String type) {
        HostKey key = lastHostKey;
        if (key == null) return null;
        if (host != null && !host.equals(key.getHost())) return null;
        if (type != null && !type.equals(key.getType())) return null;
        return new HostKey[]{key};
    }
}
