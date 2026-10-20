package com.bitpoint.homeservercontrol;

final class SshCommandResult {
    boolean success;
    int exitCode = -1;
    String output = "";
    String error = "";
    String fingerprint = "";
}
