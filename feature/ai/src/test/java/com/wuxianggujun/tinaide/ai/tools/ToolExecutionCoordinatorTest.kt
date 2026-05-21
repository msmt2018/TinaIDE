package com.wuxianggujun.tinaide.ai.tools

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.ai.api.ToolCall
import com.wuxianggujun.tinaide.ai.api.ToolFunction
import com.wuxianggujun.tinaide.core.i18n.AppStrings
import com.wuxianggujun.tinaide.core.i18n.R
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import org.junit.After
import org.junit.Before
import org.junit.Test

class ToolExecutionCoordinatorTest {

    @Before
    fun setUp() {
        resetAppStrings()
        val context = mockk<Context>()
        every { context.applicationContext } returns context
        every { context.getString(R.string.ai_tool_call_missing_name) } returns "missing name"
        every { context.getString(R.string.ai_tool_not_found, "missing") } returns "not found: missing"
        every { context.getString(R.string.ai_tool_execution_failed, "boom") } returns "failed: boom"
        every { context.getString(R.string.ai_tool_cancelled_previous_failed) } returns "previous failed"
        every { context.getString(R.string.ai_tool_cancelled_previous_cancelled) } returns "previous cancelled"
        AppStrings.initialize(context)
    }

    @After
    fun tearDown() {
        resetAppStrings()
    }

    private fun resetAppStrings() {
        val field = AppStrings::class.java.getDeclaredField("appContext")
        field.isAccessible = true
        field.set(null, null)
    }

    private fun call(name: String?): ToolCall = ToolCall(
        id = "c-1",
        type = "function",
        function = name?.let { ToolFunction(name = it, arguments = null) },
    )

    private fun fakeTool(
        isDangerous: Boolean = false,
        onExecute: suspend (ToolCall, ToolExecutionContext) -> ToolExecutionResult =
            { _, _ -> ToolExecutionResult.Success("ok") },
    ): AiTool = object : AiTool {
        override val name: String = "fake"
        override val description: String = "for tests"
        override val category: ToolCategory = ToolCategory.CUSTOM
        override val isDangerous: Boolean = isDangerous
        override fun getParameters() = JsonObject(emptyMap())
        override suspend fun execute(toolCall: ToolCall, context: ToolExecutionContext): ToolExecutionResult = onExecute(toolCall, context)
    }

    @Test
    fun `execute returns Error when tool name missing`(): Unit = runBlocking {
        val coordinator = ToolExecutionCoordinator { null }
        val result = coordinator.execute(call(name = null), ToolExecutionContext())
        assertThat(result).isInstanceOf(ToolExecutionResult.Error::class.java)
        assertThat((result as ToolExecutionResult.Error).message).isEqualTo("missing name")
    }

    @Test
    fun `execute returns Error when locator cant find tool`(): Unit = runBlocking {
        val coordinator = ToolExecutionCoordinator { null }
        val result = coordinator.execute(call("missing"), ToolExecutionContext())
        assertThat(result).isInstanceOf(ToolExecutionResult.Error::class.java)
        assertThat((result as ToolExecutionResult.Error).message).contains("missing")
    }

    @Test
    fun `execute catches tool exceptions`(): Unit = runBlocking {
        val coordinator = ToolExecutionCoordinator { fakeTool { _, _ -> error("boom") } }
        val result = coordinator.execute(call("fake"), ToolExecutionContext())
        assertThat(result).isInstanceOf(ToolExecutionResult.Error::class.java)
        assertThat((result as ToolExecutionResult.Error).message).isEqualTo("failed: boom")
    }

    @Test
    fun executeRethrowsCancellationException(): Unit = runBlocking {
        val coordinator = ToolExecutionCoordinator {
            fakeTool { _, _ -> throw CancellationException("cancelled") }
        }

        try {
            coordinator.execute(call("fake"), ToolExecutionContext())
            throw AssertionError("Expected CancellationException")
        } catch (e: CancellationException) {
            assertThat(e).hasMessageThat().isEqualTo("cancelled")
        }
    }

    @Test
    fun `execute forwards successful result`(): Unit = runBlocking {
        val coordinator = ToolExecutionCoordinator {
            fakeTool { _, _ -> ToolExecutionResult.Success("hi") }
        }
        val result = coordinator.execute(call("fake"), ToolExecutionContext())
        assertThat(result).isInstanceOf(ToolExecutionResult.Success::class.java)
        assertThat((result as ToolExecutionResult.Success).content).isEqualTo("hi")
    }

    @Test
    fun `isAutoExecutionAllowed blocks dangerous tool when flag off`() {
        val coordinator = ToolExecutionCoordinator { fakeTool(isDangerous = true) }
        assertThat(
            coordinator.isAutoExecutionAllowed(call("fake"), allowDangerousAuto = false)
        ).isFalse()
    }

    @Test
    fun `isAutoExecutionAllowed permits dangerous tool when flag on`() {
        val coordinator = ToolExecutionCoordinator { fakeTool(isDangerous = true) }
        assertThat(
            coordinator.isAutoExecutionAllowed(call("fake"), allowDangerousAuto = true)
        ).isTrue()
    }

    @Test
    fun `isAutoExecutionAllowed permits safe tool regardless of flag`() {
        val coordinator = ToolExecutionCoordinator { fakeTool(isDangerous = false) }
        assertThat(
            coordinator.isAutoExecutionAllowed(call("fake"), allowDangerousAuto = false)
        ).isTrue()
    }

    @Test
    fun `buildCascadeCancelReason produces distinct messages`() {
        val coordinator = ToolExecutionCoordinator { null }
        assertThat(
            coordinator.buildCascadeCancelReason(ToolExecutionCoordinator.PreviousStatus.FAILED)
        ).isEqualTo("previous failed")
        assertThat(
            coordinator.buildCascadeCancelReason(ToolExecutionCoordinator.PreviousStatus.CANCELLED)
        ).isEqualTo("previous cancelled")
    }
}
