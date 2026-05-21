package com.wuxianggujun.tinaide.ai.tools.diagnostics

import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.ai.tools.ToolExecutionContext
import com.wuxianggujun.tinaide.ai.tools.ToolExecutionResult
import com.wuxianggujun.tinaide.ai.tools.executor.diagnostics.Diagnostic
import com.wuxianggujun.tinaide.ai.tools.executor.diagnostics.DiagnosticSeverity
import com.wuxianggujun.tinaide.ai.tools.executor.diagnostics.DiagnosticsCallbacks
import com.wuxianggujun.tinaide.ai.tools.executor.diagnostics.DiagnosticsRequest
import com.wuxianggujun.tinaide.ai.tools.executor.diagnostics.DiagnosticsResult
import com.wuxianggujun.tinaide.ai.tools.installToolTestAppStrings
import com.wuxianggujun.tinaide.ai.tools.resetToolTestAppStrings
import com.wuxianggujun.tinaide.ai.tools.success
import com.wuxianggujun.tinaide.ai.tools.toolCall
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test

class DiagnosticsToolsTest {

    @Before
    fun setUpStrings() {
        installToolTestAppStrings()
    }

    @After
    fun tearDownStrings() {
        resetToolTestAppStrings()
    }

    @Test
    fun `get diagnostics forwards filters and returns summary`(): Unit = runBlocking {
        val callbacks = RecordingDiagnosticsCallbacks(
            diagnosticsResult = RecordingDiagnosticsCallbacks.sampleResult()
        )

        val result = GetDiagnosticsTool.execute(
            toolCall(
                GetDiagnosticsTool.name,
                """{"file_path":"src/main.cpp","severity":"warning","include_warnings":false,"include_info":true}"""
            ),
            diagnosticsContext(callbacks)
        )

        val success = result.success()
        assertThat(callbacks.lastRequest).isEqualTo(
            DiagnosticsRequest(
                filePath = "src/main.cpp",
                severity = DiagnosticSeverity.WARNING,
                includeWarnings = false,
                includeInfo = true
            )
        )
        assertThat(success.content).contains("expected ';'")
        assertThat(success.content).contains("unused variable")
        assertThat(success.metadata).containsExactly(
            "errorCount", 1,
            "warningCount", 1,
            "infoCount", 0,
            "hintCount", 0,
            "totalCount", 2
        )
    }

    @Test
    fun `get all diagnostics returns clean result metadata`(): Unit = runBlocking {
        val callbacks = RecordingDiagnosticsCallbacks()

        val result = GetAllDiagnosticsTool.execute(
            toolCall(GetAllDiagnosticsTool.name),
            diagnosticsContext(callbacks)
        )

        val success = result.success()
        assertThat(success.content).contains("No diagnostics found")
        assertThat(success.metadata).containsEntry("totalCount", 0)
        assertThat(success.metadata).containsEntry("fileCount", 0)
    }

    @Test
    fun `get diagnostics ignores invalid severity and returns clean result`(): Unit = runBlocking {
        val callbacks = RecordingDiagnosticsCallbacks()

        val result = GetDiagnosticsTool.execute(
            toolCall(GetDiagnosticsTool.name, """{"severity":"fatal","include_warnings":false}"""),
            diagnosticsContext(callbacks)
        )

        val success = result.success()
        assertThat(callbacks.lastRequest).isEqualTo(
            DiagnosticsRequest(
                filePath = null,
                severity = null,
                includeWarnings = false,
                includeInfo = false
            )
        )
        assertThat(success.content).isEqualTo("No diagnostics found")
        assertThat(success.metadata).containsEntry("totalCount", 0)
    }

    @Test
    fun `get all diagnostics returns grouped summary with overflow count`(): Unit = runBlocking {
        val diagnostics = (0 until 12).map { index ->
            Diagnostic(
                filePath = "src/File$index.kt",
                line = index + 1,
                column = 1,
                endLine = index + 1,
                endColumn = 2,
                severity = if (index % 2 == 0) DiagnosticSeverity.ERROR else DiagnosticSeverity.WARNING,
                message = "issue $index"
            )
        }
        val callbacks = RecordingDiagnosticsCallbacks(
            allDiagnosticsResult = DiagnosticsResult(
                diagnostics = diagnostics,
                errorCount = 6,
                warningCount = 6,
                infoCount = 0,
                hintCount = 0
            )
        )

        val result = GetAllDiagnosticsTool.execute(
            toolCall(GetAllDiagnosticsTool.name),
            diagnosticsContext(callbacks)
        )

        val success = result.success()
        assertThat(success.content).contains("Project Diagnostics Summary:")
        assertThat(success.content).contains("src/File0.kt: 1 error(s), 0 warning(s)")
        assertThat(success.content).contains("... and 2 more file(s)")
        assertThat(success.metadata).containsExactly(
            "errorCount", 6,
            "warningCount", 6,
            "infoCount", 0,
            "hintCount", 0,
            "totalCount", 12,
            "fileCount", 12
        )
    }

    @Test
    fun `clear diagnostics forwards file path and handles failures`(): Unit = runBlocking {
        val successCallbacks = RecordingDiagnosticsCallbacks(clearResult = true)
        val failureCallbacks = RecordingDiagnosticsCallbacks(clearResult = false)

        val success = ClearDiagnosticsTool.execute(
            toolCall(ClearDiagnosticsTool.name, """{"file_path":"src/main.cpp"}"""),
            diagnosticsContext(successCallbacks)
        )
        val failure = ClearDiagnosticsTool.execute(
            toolCall(ClearDiagnosticsTool.name, """{"file_path":"src/main.cpp"}"""),
            diagnosticsContext(failureCallbacks)
        )

        assertThat(success).isEqualTo(ToolExecutionResult.Success("Diagnostics cleared for: src/main.cpp"))
        assertThat(failure).isEqualTo(ToolExecutionResult.Error("Failed to clear diagnostics for: src/main.cpp"))
        assertThat(successCallbacks.lastClearedFilePath).isEqualTo("src/main.cpp")
    }

    @Test
    fun `diagnostics tools require callbacks and file path where needed`(): Unit = runBlocking {
        assertThat(GetDiagnosticsTool.execute(toolCall(GetDiagnosticsTool.name), ToolExecutionContext()))
            .isEqualTo(ToolExecutionResult.Error("Diagnostics callbacks not available"))
        assertThat(ClearDiagnosticsTool.execute(toolCall(ClearDiagnosticsTool.name, """{"file_path":" "}"""), diagnosticsContext(RecordingDiagnosticsCallbacks())))
            .isEqualTo(ToolExecutionResult.Error("File path is required"))
    }

    @Test
    fun `diagnostics tools rethrow cancellation exception`(): Unit = runBlocking {
        val callbacks = cancellingDiagnosticsCallbacks()

        assertCancellationRethrown {
            GetDiagnosticsTool.execute(toolCall(GetDiagnosticsTool.name), diagnosticsContext(callbacks))
        }
        assertCancellationRethrown {
            GetAllDiagnosticsTool.execute(toolCall(GetAllDiagnosticsTool.name), diagnosticsContext(callbacks))
        }
        assertCancellationRethrown {
            ClearDiagnosticsTool.execute(
                toolCall(ClearDiagnosticsTool.name, """{"file_path":"src/main.cpp"}"""),
                diagnosticsContext(callbacks)
            )
        }
    }

    @Test
    fun `diagnostics tools map callback failures to tool errors`(): Unit = runBlocking {
        val callbacks = failingDiagnosticsCallbacks()

        assertThat(GetDiagnosticsTool.execute(toolCall(GetDiagnosticsTool.name), diagnosticsContext(callbacks)))
            .isEqualTo(ToolExecutionResult.Error("Failed to get diagnostics: boom"))
        assertThat(GetAllDiagnosticsTool.execute(toolCall(GetAllDiagnosticsTool.name), diagnosticsContext(callbacks)))
            .isEqualTo(ToolExecutionResult.Error("Failed to get all diagnostics: boom"))
        assertThat(
            ClearDiagnosticsTool.execute(
                toolCall(ClearDiagnosticsTool.name, """{"file_path":"src/main.cpp"}"""),
                diagnosticsContext(callbacks)
            )
        ).isEqualTo(ToolExecutionResult.Error("Failed to clear diagnostics: boom"))
    }

    private fun diagnosticsContext(callbacks: RecordingDiagnosticsCallbacks): ToolExecutionContext = ToolExecutionContext(additionalData = mapOf("diagnosticsCallbacks" to callbacks))

    private fun diagnosticsContext(callbacks: DiagnosticsCallbacks): ToolExecutionContext = ToolExecutionContext(additionalData = mapOf("diagnosticsCallbacks" to callbacks))

    private fun cancellingDiagnosticsCallbacks(): DiagnosticsCallbacks = object : DiagnosticsCallbacks {
        override fun getDiagnostics(request: DiagnosticsRequest): DiagnosticsResult = throw CancellationException("cancelled")

        override fun getAllDiagnostics(): DiagnosticsResult = throw CancellationException("cancelled")

        override fun clearDiagnostics(filePath: String): Boolean = throw CancellationException("cancelled")
    }

    private fun failingDiagnosticsCallbacks(): DiagnosticsCallbacks = object : DiagnosticsCallbacks {
        override fun getDiagnostics(request: DiagnosticsRequest): DiagnosticsResult = throw IllegalStateException("boom")

        override fun getAllDiagnostics(): DiagnosticsResult = throw IllegalStateException("boom")

        override fun clearDiagnostics(filePath: String): Boolean = throw IllegalStateException("boom")
    }

    private suspend fun assertCancellationRethrown(block: suspend () -> Unit) {
        try {
            block()
            throw AssertionError("Expected CancellationException")
        } catch (e: CancellationException) {
            assertThat(e).hasMessageThat().isEqualTo("cancelled")
        }
    }
}
