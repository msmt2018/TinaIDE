package com.wuxianggujun.tinaide.ai.tools.execution

import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.ai.api.ToolCall
import com.wuxianggujun.tinaide.ai.api.ToolFunction
import com.wuxianggujun.tinaide.ai.tools.ToolExecutionContext
import com.wuxianggujun.tinaide.ai.tools.ToolExecutionResult
import com.wuxianggujun.tinaide.ai.tools.executor.execution.ExecutionOutputResult
import com.wuxianggujun.tinaide.ai.tools.executor.execution.ExecutionStatus
import com.wuxianggujun.tinaide.ai.tools.installToolTestAppStrings
import com.wuxianggujun.tinaide.ai.tools.resetToolTestAppStrings
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test

class GetExecutionOutputToolTest {
    @Before
    fun setUpStrings() {
        installToolTestAppStrings()
    }

    @After
    fun tearDownStrings() {
        resetToolTestAppStrings()
    }

    @Test
    fun `execute returns output content metadata and forwards execution id`(): Unit = runBlocking {
        val callbacks = RecordingExecutionCallbacks(
            outputResult = ExecutionOutputResult(
                executionId = "exec-ignored",
                output = "hello stdout",
                errorOutput = "warning stderr",
                status = ExecutionStatus.FAILED,
                exitCode = 2
            )
        )

        val result = GetExecutionOutputTool.execute(
            outputToolCall("exec-42"),
            ToolExecutionContext(additionalData = mapOf("executionCallbacks" to callbacks))
        )

        assertThat(result).isInstanceOf(ToolExecutionResult.Success::class.java)
        val success = result as ToolExecutionResult.Success
        assertThat(success.content).contains("Execution ID: exec-42")
        assertThat(success.content).contains("Status: FAILED")
        assertThat(success.content).contains("Exit Code: 2")
        assertThat(success.content).contains("hello stdout")
        assertThat(success.content).contains("warning stderr")
        assertThat(success.metadata).containsExactly(
            "executionId",
            "exec-42",
            "status",
            "FAILED",
            "exitCode",
            2
        )
        assertThat(callbacks.lastOutputExecutionId).isEqualTo("exec-42")
    }

    @Test
    fun `execute truncates long stdout and stderr`() = runBlocking {
        val callbacks = RecordingExecutionCallbacks(
            outputResult = ExecutionOutputResult(
                executionId = "exec-long",
                output = "o".repeat(10001),
                errorOutput = "e".repeat(5001),
                status = ExecutionStatus.RUNNING,
                exitCode = 0
            )
        )

        val result = GetExecutionOutputTool.execute(
            outputToolCall("exec-long"),
            ToolExecutionContext(additionalData = mapOf("executionCallbacks" to callbacks))
        )

        assertThat(result).isInstanceOf(ToolExecutionResult.Success::class.java)
        val success = result as ToolExecutionResult.Success
        assertThat(success.content).contains("... (output truncated, showing first 10000 characters)")
        assertThat(success.content).contains("... (error output truncated, showing first 5000 characters)")
        assertThat(success.metadata["status"]).isEqualTo("RUNNING")
    }

    @Test
    fun `execute returns not found error when output is unavailable`(): Unit = runBlocking {
        val callbacks = RecordingExecutionCallbacks(outputResult = null)

        val result = GetExecutionOutputTool.execute(
            outputToolCall("exec-missing"),
            ToolExecutionContext(additionalData = mapOf("executionCallbacks" to callbacks))
        )

        assertThat(result).isEqualTo(
            ToolExecutionResult.Error(
                "Execution output not found: exec-missing (execution may not exist or output not available yet)"
            )
        )
        assertThat(callbacks.lastOutputExecutionId).isEqualTo("exec-missing")
    }

    @Test
    fun `execute rejects blank execution id`(): Unit = runBlocking {
        val result = GetExecutionOutputTool.execute(
            outputToolCall(" "),
            ToolExecutionContext(additionalData = mapOf("executionCallbacks" to RecordingExecutionCallbacks()))
        )

        assertThat(result).isEqualTo(ToolExecutionResult.Error("Execution ID is required"))
    }

    @Test
    fun `execute returns error when callbacks are unavailable`(): Unit = runBlocking {
        val result = GetExecutionOutputTool.execute(
            outputToolCall("exec-42"),
            ToolExecutionContext()
        )

        assertThat(result).isInstanceOf(ToolExecutionResult.Error::class.java)
        assertThat((result as ToolExecutionResult.Error).message).contains("Execution callbacks not available")
    }

    private fun outputToolCall(executionId: String): ToolCall = ToolCall(
        id = "call-1",
        type = "function",
        function = ToolFunction(
            name = GetExecutionOutputTool.name,
            arguments = """{"execution_id":"$executionId"}"""
        )
    )
}
