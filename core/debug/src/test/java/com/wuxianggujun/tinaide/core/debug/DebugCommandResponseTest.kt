package com.wuxianggujun.tinaide.core.debug

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DebugCommandResponseTest {

    @Test
    fun setBreakpoint_shouldDefaultToUnconditionalBreakpoint() {
        val command = DebugCommand.SetBreakpoint(
            file = "/project/main.cpp",
            line = 42
        )

        assertThat(command.file).isEqualTo("/project/main.cpp")
        assertThat(command.line).isEqualTo(42)
        assertThat(command.condition).isNull()
    }

    @Test
    fun stoppedResponse_shouldDefaultToMainThreadWhenThreadIsMissing() {
        val location = SourceLocation(
            file = "/project/main.cpp",
            line = 12,
            function = "main",
            address = 0x1000
        )

        val response = DebugResponse.Stopped(
            reason = PauseReason.BREAKPOINT,
            location = location
        )

        assertThat(response.threadId).isEqualTo(0)
        assertThat(response.location).isEqualTo(location)
        assertThat(response.reason).isEqualTo(PauseReason.BREAKPOINT)
    }

    @Test
    fun debugState_shouldCarryTerminationDetails() {
        val state = DebugState.Terminated(
            sessionId = "dbg-1",
            reason = TerminateReason.CRASH,
            exitCode = 139,
            message = "SIGSEGV"
        )

        assertThat(state.sessionId).isEqualTo("dbg-1")
        assertThat(state.reason).isEqualTo(TerminateReason.CRASH)
        assertThat(state.exitCode).isEqualTo(139)
        assertThat(state.message).isEqualTo("SIGSEGV")
    }
}
