package com.bitpoint.homeservercontrol;
import org.junit.Test;
import static org.junit.Assert.*;
public class ServerWizardStateTest {
    @Test public void failureInvalidatesDependentReadinessAndCannotReportSuccess() {
        ServerWizardState state = new ServerWizardState();
        for (ServerWizardState.Step step : ServerWizardState.Step.values())
            state.set(step, ServerWizardState.Status.READY, "checked");
        assertTrue(state.complete());
        state.set(ServerWizardState.Step.SSH, ServerWizardState.Status.FAILED, "unreachable");
        assertFalse(state.complete()); assertFalse(state.sshReady());
        assertEquals(ServerWizardState.Status.WAITING, state.status(ServerWizardState.Step.KEY));
        assertEquals(ServerWizardState.Status.WAITING, state.status(ServerWizardState.Step.AGENT));
        assertEquals(ServerWizardState.Status.READY, state.status(ServerWizardState.Step.TERMUX));
    }
    @Test public void agentFailureKeepsVerifiedSshAndKeyForRecovery() {
        ServerWizardState state = new ServerWizardState();
        for (ServerWizardState.Step step : ServerWizardState.Step.values())
            state.set(step, ServerWizardState.Status.READY, "checked");
        state.set(ServerWizardState.Step.AGENT, ServerWizardState.Status.FAILED, "port occupied");
        assertTrue(state.sshReady()); assertFalse(state.complete());
        assertEquals(ServerWizardState.Status.READY, state.status(ServerWizardState.Step.KEY));
    }
}
