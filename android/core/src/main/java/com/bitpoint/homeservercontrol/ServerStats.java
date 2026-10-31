package com.bitpoint.homeservercontrol;

final class ServerStats {
    boolean reachable;
    boolean authenticated;
    boolean sftpOnly;
    boolean telemetryAvailable;
    boolean hostKeyTrusted;
    boolean hostKeyNeedsTrust;
    boolean hostKeyMismatch;
    long latencyMs;
    long freeBytes=-1,totalBytes=-1,uptimeSeconds=-1;

    String uptime = "—";
    String disk = "—";
    String freeSpace = "—";
    String ip = "—";
    String sshd = "—";
    String battery = "—";
    String temperature = "—";
    String charging = "—";
    String iperf = "—";
    String hostFingerprint = "—";
    String error = "";
}
