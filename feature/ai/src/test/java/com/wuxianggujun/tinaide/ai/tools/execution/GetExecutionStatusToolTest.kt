package com.wuxianggujun.tinaide.ai.tools.execution

import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.ai.api.ToolCall
import com.wuxianggujun.tinaide.ai.api.ToolFunction
import com.wuxianggujun.tinaide.ai.tools.ToolExecutionContext
import com.wuxianggujun.tinaide.ai.tools.ToolExecutionResult
import com.wuxianggujun.tinaide.ai.tools.executor.execution.ExecutionStatus
import com.wuxianggujun.tinaide.ai.tools.installToolTestAppStrings
import com.wuxianggujun.tinaide.ai.tools.resetToolTestAppStrings
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test

class GetExecutionStatusToolTest {
    @Before
    fun setUpStrings() {
        installToolTestAppStrings()
    }

    @After
    fun tearDownStrings() {
        resetToolTestAppStrings()
    }

    @Test
    fun `execute returns status content and forwards execution id`(): Unit = runBlocking {
        val callbacks = RecordingExecutionCallbacks(statusResult = ExecutionStatus.SUCCESS)

        val result = GetExecutionStatusTool.execute(
            statusToolCall("exec-42"),
            ToolExecutionContext(additionalData = mapOf("executionCallbacks" to callbacks))
        )

        assertThat(result).isInstanceOf(ToolExecutionResult.Success::class.java)
        val success = result as ToolExecutionResult.Success
        assertThat(success.content).contains("Execution Status: SUCCESS")
        assertThat(success.content).contains("Execution ID: exec-42")
        assertThat(callbacks.lastStatusExecutionId).isEqualTo("exec-42")
    }

    @Test
    fun `execute formats all non success statuses`(): Unit = runBlocking {
        val statuses = listOf(
            ExecutionStatus.PENDING,
            ExecutionStatus.RUNNING,
            ExecutionStatus.FAILED,
            ExecutionStatus.CANCELLED,
            ExecutionStatus.TIMEOUT
        )

        statuses.forEach { status ->
            val callbacks = RecordingExecutionCallbacks(statusResult = status)

            val result = GetExecutionStatusTool.execute(
                statusToolCall("exec-${status.name.lowercase()}"),
                ToolExecutionContext(additionalData = mapOf("executionCallbacks" to callbacks))
            )

            assertThat(result).isInstanceOf(ToolExecutionResult.Success::class.java)
            val success = result as ToolExecutionResult.Success
            assertThat(success.content).contains(status.name)
            assertThat(success.content).contains("exec-${status.name.lowercase()}")
        }
    }

    @Test
    fun `execute returns not found error when status is unavailable`(): Unit = runBlocking {
        val callbacks = RecordingExecutionCallbacks(statusResult = null)

        val result = GetExecutionStatusTool.execute(
            statusToolCall("exec-missing"),
            ToolExecutionContext(additionalData = mapOf("executionCallbacks" to callbacks))
        )

        assertThat(result).isEqualTo(
            ToolExecutionResult.Error(
                "Execution not found: exec-missing (may have been cleaned up or never existed)"
            )
        )
        assertThat(callbacks.lastStatusExecutionId).isEqualTo("exec-missing")
    }

    @Test
    fun `execute rejects blank execution id`(): Unit = runBlocking {
        val result = GetExecutionStatusTool.execute(
            statusToolCall(" "),
            ToolExecutionContext(additionalData = mapOf("executionCallbacks" to RecordingExecutionCallbacks()))
        )

        assertThat(result).isEqualTo(ToolExecutionResult.Error("Execution ID is required"))
    }

    @Test
    fun `execute returns error when callbacks are unavailable`(): Unit = runBlocking {
        val result = GetExecutionStatusTool.execute(
            statusToolCall("exec-42"),
            ToolExecutionContext()
        )

        assertThat(result).isInstanceOf(ToolExecutionResult.Error::class.java)
        assertThat((result as ToolExecutionResult.Error).message).contains("Execution callbacks not available")
    }

    private fun statusToolCall(executionId: String): ToolCall = ToolCall(
        id = "call-1",
        type = "function",
        function = ToolFunction(
            name = GetExecutionStatusTool.name,
            arguments = """{"execution_id":"$executionId"}"""
        )
    )
}
