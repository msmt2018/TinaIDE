package com.wuxianggujun.tinaide.ai.tools.diagnostics

import com.wuxianggujun.tinaide.ai.tools.executor.diagnostics.Diagnostic
import com.wuxianggujun.tinaide.ai.tools.executor.diagnostics.DiagnosticSeverity
import com.wuxianggujun.tinaide.ai.tools.executor.diagnostics.DiagnosticsCallbacks
import com.wuxianggujun.tinaide.ai.tools.executor.diagnostics.DiagnosticsRequest
import com.wuxianggujun.tinaide.ai.tools.executor.diagnostics.DiagnosticsResult

internal class RecordingDiagnosticsCallbacks(
    private val diagnosticsResult: DiagnosticsResult = emptyResult(),
    private val allDiagnosticsResult: DiagnosticsResult = diagnosticsResult,
    private val clearResult: Boolean = true
) : DiagnosticsCallbacks {
    var lastRequest: DiagnosticsRequest? = null
        private set
    var lastClearedFilePath: String? = null
        private set

    override fun getDiagnostics(request: DiagnosticsRequest): DiagnosticsResult {
        lastRequest = request
        return diagnosticsResult
    }

    override fun getAllDiagnostics(): DiagnosticsResult = allDiagnosticsResult

    override fun clearDiagnostics(filePath: String): Boolean {
        lastClearedFilePath = filePath
        return clearResult
    }

    companion object {
        fun emptyResult(): DiagnosticsResult = DiagnosticsResult(
            diagnostics = emptyList(),
            errorCount = 0,
            warningCount = 0,
            infoCount = 0,
            hintCount = 0
        )

        fun sampleResult(): DiagnosticsResult = DiagnosticsResult(
            diagnostics = listOf(
                Diagnostic(
                    filePath = "src/main.cpp",
                    line = 4,
                    column = 8,
                    endLine = 4,
                    endColumn = 12,
                    severity = DiagnosticSeverity.ERROR,
                    message = "expected ';'",
                    code = "E100"
                ),
                Diagnostic(
                    filePath = "src/util.cpp",
                    line = 8,
                    column = 2,
                    endLine = 8,
                    endColumn = 9,
                    severity = DiagnosticSeverity.WARNING,
                    message = "unused variable"
                )
            ),
            errorCount = 1,
            warningCount = 1,
            infoCount = 0,
            hintCount = 0
        )
    }
}
