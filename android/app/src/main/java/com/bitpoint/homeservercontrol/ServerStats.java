package com.bitpoint.homeservercontrol;

final class ServerStats {
    boolean reachable;
    boolean authenticated;
    boolean hostKeyTrusted;
    boolean hostKeyNeedsTrust;
    boolean hostKeyMismatch;
    long latencyMs;

    String uptime = "—";
    String disk = "—";
    String ip = "—";
    String sshd = "—";
    String battery = "—";
    String temperature = "—";
    String charging = "—";
    String iperf = "—";
    String hostFingerprint = "—";
    String error = "";
}
