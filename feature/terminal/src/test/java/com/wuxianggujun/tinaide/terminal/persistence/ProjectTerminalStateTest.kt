package com.wuxianggujun.tinaide.terminal.persistence

import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.terminal.persistence.db.TerminalSessionEntity
import com.wuxianggujun.tinaide.terminal.persistence.db.TerminalStateEntity
import org.junit.Test

class ProjectTerminalStateTest {

    @Test
    fun normalized_shouldDropBlankSessionIdsAndFallbackToFirstValidSession() {
        val first = TerminalSessionSnapshot(id = "session-1", title = "Build")
        val second = TerminalSessionSnapshot(id = "session-2", title = "Run")
        val state = ProjectTerminalState(
            activeSessionId = "missing",
            sessions = listOf(
                TerminalSessionSnapshot(id = "", title = "Invalid"),
                first,
                second
            ),
            updatedAt = 0L
        )

        val normalized = state.normalized(currentTime = 1234L)

        assertThat(normalized.sessions).containsExactly(first, second).inOrder()
        assertThat(normalized.activeSessionId).isEqualTo("session-1")
        assertThat(normalized.updatedAt).isEqualTo(1234L)
    }

    @Test
    fun normalized_shouldKeepValidActiveSessionAndExistingTimestamp() {
        val state = ProjectTerminalState(
            activeSessionId = "session-2",
            sessions = listOf(
                TerminalSessionSnapshot(id = "session-1"),
                TerminalSessionSnapshot(id = "session-2")
            ),
            updatedAt = 99L
        )

        val normalized = state.normalized(currentTime = 1234L)

        assertThat(normalized.activeSessionId).isEqualTo("session-2")
        assertThat(normalized.updatedAt).isEqualTo(99L)
    }

    @Test
    fun activeSessionSnapshot_shouldReturnMatchingSessionOnly() {
        val state = ProjectTerminalState(
            activeSessionId = "session-2",
            sessions = listOf(
                TerminalSessionSnapshot(id = "session-1"),
                TerminalSessionSnapshot(id = "session-2", title = "Run")
            )
        )

        assertThat(state.activeSessionSnapshot()?.title).isEqualTo("Run")
        assertThat(state.copy(activeSessionId = "missing").activeSessionSnapshot()).isNull()
    }

    @Test
    fun terminalEntities_shouldRoundTripDomainModels() {
        val snapshot = TerminalSessionSnapshot(
            id = "session-1",
            title = "Shell",
            backend = "proot",
            workingDirectory = "/workspace",
            cursorRow = 3,
            cursorColumn = 7,
            rows = 40,
            columns = 120,
            transcript = "hello",
            transcriptLines = 1,
            createdAt = 42L
        )
        val state = ProjectTerminalState(
            activeSessionId = "session-1",
            sessions = listOf(snapshot),
            updatedAt = 88L
        )

        val stateEntity = TerminalStateEntity.fromSnapshot("/project", state)
        val sessionEntity = TerminalSessionEntity.fromDomainModel("/project", snapshot)

        assertThat(stateEntity.projectPath).isEqualTo("/project")
        assertThat(stateEntity.activeSessionId).isEqualTo("session-1")
        assertThat(stateEntity.updatedAt).isEqualTo(88L)
        assertThat(sessionEntity.toDomainModel()).isEqualTo(snapshot.copy(savedAt = 0L))
    }
}
