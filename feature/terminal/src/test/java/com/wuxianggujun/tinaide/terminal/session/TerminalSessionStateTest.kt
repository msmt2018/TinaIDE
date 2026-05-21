package com.wuxianggujun.tinaide.terminal.session

import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.terminal.shell.TerminalBackend
import org.junit.Test

class TerminalSessionStateTest {

    @Test
    fun create_shouldUseStartingHostDefaults() {
        val state = TerminalSessionState.create(id = "session-1", title = "Build")

        assertThat(state.id).isEqualTo("session-1")
        assertThat(state.title).isEqualTo("Build")
        assertThat(state.backend).isEqualTo(TerminalBackend.HOST)
        assertThat(state.status).isEqualTo(SessionStatus.STARTING)
        assertThat(state.canReceiveInput).isFalse()
        assertThat(state.isTerminated).isFalse()
    }

    @Test
    fun withExited_shouldRecordExitCodeAndTerminateSession() {
        val state = TerminalSessionState.create().withStatus(SessionStatus.RUNNING)

        val exited = state.withExited(127)

        assertThat(exited.status).isEqualTo(SessionStatus.EXITED)
        assertThat(exited.exitCode).isEqualTo(127)
        assertThat(exited.isTerminated).isTrue()
        assertThat(exited.errorMessage).isNull()
    }

    @Test
    fun withError_shouldClearTerminalSessionAndMarkTerminated() {
        val errored = TerminalSessionState.create().withError("shell missing")

        assertThat(errored.status).isEqualTo(SessionStatus.ERROR)
        assertThat(errored.errorMessage).isEqualTo("shell missing")
        assertThat(errored.session).isNull()
        assertThat(errored.isTerminated).isTrue()
    }

    @Test
    fun withRunEnded_shouldKeepShellStateAndStoreProgramExitCode() {
        val running = TerminalSessionState.create().withStatus(SessionStatus.RUNNING)

        val waitingClose = running.withRunEnded(0)

        assertThat(waitingClose.status).isEqualTo(SessionStatus.RUNNING)
        assertThat(waitingClose.runExitCode).isEqualTo(0)
        assertThat(waitingClose.isTerminated).isFalse()
    }
}
