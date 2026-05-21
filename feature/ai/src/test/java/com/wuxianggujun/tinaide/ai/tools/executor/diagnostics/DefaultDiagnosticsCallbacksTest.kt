package com.wuxianggujun.tinaide.ai.tools.executor.diagnostics

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DefaultDiagnosticsCallbacksTest {

    @Test
    fun `diagnostics cache filters by file severity and include flags`() {
        val callbacks = DefaultDiagnosticsCallbacks()
        callbacks.updateDiagnostics(
            "src/main.cpp",
            listOf(
                diagnostic(DiagnosticSeverity.ERROR, "error"),
                diagnostic(DiagnosticSeverity.WARNING, "warning"),
                diagnostic(DiagnosticSeverity.INFO, "info"),
                diagnostic(DiagnosticSeverity.HINT, "hint")
            )
        )

        val errorsOnly = callbacks.getDiagnostics(
            DiagnosticsRequest(filePath = "src/main.cpp", severity = DiagnosticSeverity.ERROR)
        )
        val withoutWarningsOrInfo = callbacks.getDiagnostics(
            DiagnosticsRequest(filePath = "src/main.cpp", includeWarnings = false, includeInfo = false)
        )

        assertThat(errorsOnly.diagnostics.map { it.message }).containsExactly("error")
        assertThat(withoutWarningsOrInfo.diagnostics.map { it.message }).containsExactly("error")
    }

    @Test
    fun `clear diagnostics removes file entries and empty updates remove cache`() {
        val callbacks = DefaultDiagnosticsCallbacks()
        callbacks.updateDiagnostics("a.cpp", listOf(diagnostic("a.cpp", DiagnosticSeverity.ERROR, "error")))

        assertThat(callbacks.clearDiagnostics("a.cpp")).isTrue()
        assertThat(callbacks.clearDiagnostics("a.cpp")).isFalse()

        callbacks.updateDiagnostics("a.cpp", listOf(diagnostic("a.cpp", DiagnosticSeverity.WARNING, "warning")))
        callbacks.updateDiagnostics("b.cpp", listOf(diagnostic("b.cpp", DiagnosticSeverity.INFO, "info")))
        callbacks.updateDiagnostics("a.cpp", emptyList())

        val all = callbacks.getAllDiagnostics()
        assertThat(all.diagnostics.map { it.filePath }).containsExactly("b.cpp")
        callbacks.clearAll()
        assertThat(callbacks.getAllDiagnostics().diagnostics).isEmpty()
    }

    @Test
    fun `diagnostics cache handles all files severity override and missing files`() {
        val callbacks = DefaultDiagnosticsCallbacks()
        callbacks.updateDiagnostics(
            "a.cpp",
            listOf(
                diagnostic("a.cpp", DiagnosticSeverity.ERROR, "error"),
                diagnostic("a.cpp", DiagnosticSeverity.WARNING, "warning")
            )
        )
        callbacks.updateDiagnostics(
            "b.cpp",
            listOf(
                diagnostic("b.cpp", DiagnosticSeverity.INFO, "info"),
                diagnostic("b.cpp", DiagnosticSeverity.HINT, "hint")
            )
        )

        val allWithInfo = callbacks.getDiagnostics(DiagnosticsRequest(includeInfo = true))
        val warningOnly = callbacks.getDiagnostics(
            DiagnosticsRequest(severity = DiagnosticSeverity.WARNING, includeWarnings = false)
        )
        val missingFile = callbacks.getDiagnostics(DiagnosticsRequest(filePath = "missing.cpp", includeInfo = true))

        assertThat(allWithInfo.diagnostics.map { it.message }).containsExactly("error", "warning", "info", "hint")
        assertThat(allWithInfo.errorCount).isEqualTo(1)
        assertThat(allWithInfo.warningCount).isEqualTo(1)
        assertThat(allWithInfo.infoCount).isEqualTo(1)
        assertThat(allWithInfo.hintCount).isEqualTo(1)
        assertThat(warningOnly.diagnostics.map { it.message }).containsExactly("warning")
        assertThat(missingFile.diagnostics).isEmpty()
        assertThat(missingFile.errorCount).isEqualTo(0)
    }

    private fun diagnostic(
        severity: DiagnosticSeverity,
        message: String
    ): Diagnostic = diagnostic("src/main.cpp", severity, message)

    private fun diagnostic(
        filePath: String,
        severity: DiagnosticSeverity,
        message: String
    ): Diagnostic = Diagnostic(
        filePath = filePath,
        line = 1,
        column = 1,
        endLine = 1,
        endColumn = 2,
        severity = severity,
        message = message
    )
}
