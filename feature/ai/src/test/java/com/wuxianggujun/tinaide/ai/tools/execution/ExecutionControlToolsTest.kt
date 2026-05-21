package com.wuxianggujun.tinaide.ai.tools.execution

import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.ai.tools.ToolExecutionContext
import com.wuxianggujun.tinaide.ai.tools.ToolExecutionResult
import com.wuxianggujun.tinaide.ai.tools.executor.execution.BuildError
import com.wuxianggujun.tinaide.ai.tools.executor.execution.BuildErrorsResult
import com.wuxianggujun.tinaide.ai.tools.executor.execution.ErrorSeverity
import com.wuxianggujun.tinaide.ai.tools.installToolTestAppStrings
import com.wuxianggujun.tinaide.ai.tools.resetToolTestAppStrings
import com.wuxianggujun.tinaide.ai.tools.success
import com.wuxianggujun.tinaide.ai.tools.toolCall
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test

class ExecutionControlToolsTest {
    @Before
    fun setUpStrings() {
        installToolTestAppStrings()
    }

    @After
    fun tearDownStrings() {
        resetToolTestAppStrings()
    }

    @Test
    fun `stop execution forwards id and reports success`(): Unit = runBlocking {
        val callbacks = RecordingExecutionCallbacks()

        val result = StopExecutionTool.execute(
            toolCall(StopExecutionTool.name, """{"execution_id":"exec-42"}"""),
            executionContext(callbacks)
        )

        assertThat(result).isEqualTo(ToolExecutionResult.Success("Execution stopped successfully: exec-42"))
        assertThat(callbacks.lastStoppedExecutionId).isEqualTo("exec-42")
    }

    @Test
    fun `stop execution reports callback failure`(): Unit = runBlocking {
        val callbacks = RecordingExecutionCallbacks(stopResult = false)

        val result = StopExecutionTool.execute(
            toolCall(StopExecutionTool.name, """{"execution_id":"exec-42"}"""),
            executionContext(callbacks)
        )

        assertThat(result).isEqualTo(
            ToolExecutionResult.Error("Failed to stop execution: exec-42 (execution may not exist or already completed)")
        )
    }

    @Test
    fun `get build errors returns summary metadata and forwards optional id`(): Unit = runBlocking {
        val callbacks = RecordingExecutionCallbacks(
            buildErrorsResult = RecordingExecutionCallbacks.failedBuildErrorsResult()
        )

        val result = GetBuildErrorsTool.execute(
            toolCall(GetBuildErrorsTool.name, """{"execution_id":"build-1"}"""),
            executionContext(callbacks)
        )

        val success = result.success()
        assertThat(success.content).contains("Build Errors Found")
        assertThat(success.content).contains("missing semicolon")
        assertThat(success.content).contains("unused variable")
        assertThat(success.metadata).containsExactly(
            "hasErrors",
            true,
            "errorCount",
            1,
            "warningCount",
            1,
            "executionId",
            "build-1"
        )
        assertThat(callbacks.lastBuildErrorsExecutionId).isEqualTo("build-1")
    }

    @Test
    fun `get build errors formats warnings clean state info severity and overflow`() = runBlocking {
        val warningOnly = RecordingExecutionCallbacks(
            buildErrorsResult = BuildErrorsResult(
                hasErrors = false,
                errorCount = 0,
                warningCount = 1,
                errors = listOf(
                    BuildError(
                        file = null,
                        line = null,
                        column = null,
                        message = "global warning",
                        severity = ErrorSeverity.WARNING
                    )
                )
            )
        )
        val clean = RecordingExecutionCallbacks(buildErrorsResult = RecordingExecutionCallbacks.emptyBuildErrorsResult())
        val manyErrors = RecordingExecutionCallbacks(
            buildErrorsResult = BuildErrorsResult(
                hasErrors = true,
                errorCount = 51,
                warningCount = 0,
                errors = (1..51).map { index ->
                    BuildError(
                        file = "src/File$index.kt",
                        line = index,
                        column = index + 1,
                        message = "issue $index",
                        severity = if (index == 51) ErrorSeverity.INFO else ErrorSeverity.ERROR
                    )
                }
            )
        )

        val warnings = GetBuildErrorsTool.execute(
            toolCall(GetBuildErrorsTool.name),
            executionContext(warningOnly)
        ).success()
        val cleanResult = GetBuildErrorsTool.execute(
            toolCall(GetBuildErrorsTool.name),
            executionContext(clean)
        ).success()
        val overflow = GetBuildErrorsTool.execute(
            toolCall(GetBuildErrorsTool.name, """{"execution_id":"build-many"}"""),
            executionContext(manyErrors)
        ).success()

        assertThat(warnings.content).contains("Build Warnings Found")
        assertThat(warnings.content).contains("global warning")
        assertThat(warnings.metadata["executionId"]).isEqualTo("current")
        assertThat(cleanResult.content).contains("No Build Errors or Warnings")
        assertThat(overflow.content).contains("... and 1 more errors/warnings")
        assertThat(overflow.content).doesNotContain("issue 51")
        assertThat(overflow.metadata["errorCount"]).isEqualTo(51)
    }

    @Test
    fun `navigation tools invoke their callbacks`(): Unit = runBlocking {
        val callbacks = RecordingExecutionCallbacks()

        val runResult = NavigateToRunOutputTool.execute(
            toolCall(NavigateToRunOutputTool.name),
            executionContext(callbacks)
        )
        val buildResult = NavigateToBuildLogTool.execute(
            toolCall(NavigateToBuildLogTool.name),
            executionContext(callbacks)
        )

        assertThat(runResult).isInstanceOf(ToolExecutionResult.Success::class.java)
        assertThat(buildResult).isInstanceOf(ToolExecutionResult.Success::class.java)
        assertThat(callbacks.didNavigateToRunOutput).isTrue()
        assertThat(callbacks.didNavigateToBuildLog).isTrue()
    }

    @Test
    fun `control tools return errors when callbacks are unavailable or id is blank`(): Unit = runBlocking {
        val missingStopCallbacks = StopExecutionTool.execute(
            toolCall(StopExecutionTool.name, """{"execution_id":"exec-42"}"""),
            ToolExecutionContext()
        )
        val blankStop = StopExecutionTool.execute(
            toolCall(StopExecutionTool.name, """{"execution_id":" "}"""),
            executionContext(RecordingExecutionCallbacks())
        )
        val missingBuildErrorCallbacks = GetBuildErrorsTool.execute(
            toolCall(GetBuildErrorsTool.name),
            ToolExecutionContext()
        )
        val missingRunNavigationCallbacks = NavigateToRunOutputTool.execute(
            toolCall(NavigateToRunOutputTool.name),
            ToolExecutionContext()
        )
        val missingBuildNavigationCallbacks = NavigateToBuildLogTool.execute(
            toolCall(NavigateToBuildLogTool.name),
            ToolExecutionContext()
        )

        assertThat(missingStopCallbacks).isInstanceOf(ToolExecutionResult.Error::class.java)
        assertThat(blankStop).isEqualTo(ToolExecutionResult.Error("Execution ID is required"))
        assertThat(missingBuildErrorCallbacks).isInstanceOf(ToolExecutionResult.Error::class.java)
        assertThat(missingRunNavigationCallbacks).isInstanceOf(ToolExecutionResult.Error::class.java)
        assertThat(missingBuildNavigationCallbacks).isInstanceOf(ToolExecutionResult.Error::class.java)
    }

    private fun executionContext(callbacks: RecordingExecutionCallbacks): ToolExecutionContext = ToolExecutionContext(additionalData = mapOf("executionCallbacks" to callbacks))
}
