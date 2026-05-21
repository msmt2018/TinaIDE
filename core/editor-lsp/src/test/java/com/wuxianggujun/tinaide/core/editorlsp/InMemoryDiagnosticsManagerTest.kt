package com.wuxianggujun.tinaide.core.editorlsp

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class InMemoryDiagnosticsManagerTest {

    @Test
    fun publishDiagnostics_shouldReplacePreviousDiagnosticsForSameFile() {
        val manager = InMemoryDiagnosticsManager()
        val fileUri = "file:///project/main.cpp"
        val first = diagnostic(fileUri, "old")
        val second = diagnostic(fileUri, "new")

        manager.publishDiagnostics(fileUri, listOf(first))
        manager.publishDiagnostics(fileUri, listOf(second))

        assertThat(manager.getDiagnostics(fileUri)).containsExactly(second)
    }

    @Test
    fun listeners_shouldReceiveUpdatesOnceAndStopAfterRemoval() {
        val manager = InMemoryDiagnosticsManager()
        val fileUri = "file:///project/main.cpp"
        val received = mutableListOf<List<Diagnostic>>()
        val listener = DiagnosticsListener { _, diagnostics ->
            received += diagnostics
        }

        manager.addListener(listener)
        manager.addListener(listener)
        manager.publishDiagnostics(fileUri, listOf(diagnostic(fileUri, "first")))
        manager.removeListener(listener)
        manager.publishDiagnostics(fileUri, listOf(diagnostic(fileUri, "second")))

        assertThat(received).hasSize(1)
        assertThat(received.single().single().message).isEqualTo("first")
    }

    private fun diagnostic(fileUri: String, message: String): Diagnostic {
        return Diagnostic(
            fileUri = fileUri,
            fileName = "main.cpp",
            line = 2,
            column = 4,
            message = message,
            severity = DiagnosticSeverity.ERROR,
            source = "clangd",
            code = "syntax"
        )
    }
}
