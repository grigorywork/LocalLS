package com.bitpoint.homeservercontrol;

/** Readiness facts for the local server, independent of an Activity or credentials. */
public final class ServerWizardState {
    public enum Step { TERMUX, FOLDER, PERMISSION, SSH, KEY, AGENT }
    public enum Status { WAITING, RUNNING, READY, FAILED }
    private final Status[] states = new Status[Step.values().length];
    private final String[] details = new String[states.length];
    public ServerWizardState() { resetFrom(Step.TERMUX); }
    public void resetFrom(Step step) {
        for (int i = step.ordinal(); i < states.length; i++) { states[i] = Status.WAITING; details[i] = ""; }
    }
    public void set(Step step, Status status, String detail) {
        if (status == Status.RUNNING || status == Status.FAILED) resetFrom(step);
        states[step.ordinal()] = status;
        details[step.ordinal()] = detail == null ? "" : detail;
    }
    public Status status(Step step) { return states[step.ordinal()]; }
    public String detail(Step step) { return details[step.ordinal()]; }
    public boolean sshReady() { return status(Step.SSH) == Status.READY; }
    public boolean complete() {
        for (Status state : states) if (state != Status.READY) return false;
        return true;
    }
}
