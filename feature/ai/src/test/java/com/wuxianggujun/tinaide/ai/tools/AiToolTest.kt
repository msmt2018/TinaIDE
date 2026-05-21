package com.wuxianggujun.tinaide.ai.tools

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.core.i18n.R
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.JsonObject
import org.junit.After
import org.junit.Before
import org.junit.Test

class AiToolTest {
    @Before
    fun setUpStrings() {
        installToolTestAppStrings()
    }

    @After
    fun tearDownStrings() {
        resetToolTestAppStrings()
    }

    @Test
    fun `default tool metadata converts to request tool schema`() {
        val tool = fakeTool()

        val requestTool = tool.toRequestTool()

        assertThat(requestTool.type).isEqualTo("function")
        assertThat(requestTool.function.name).isEqualTo("sample_tool")
        assertThat(requestTool.function.description).isEqualTo("Sample description")
        assertThat(requestTool.function.parameters).isEqualTo(JsonObject(emptyMap()))
        assertThat(tool.getDetailedDescription()).isEqualTo("Sample description")
        assertThat(tool.getDangerousConfirmation(toolCall("sample_tool"))).isNull()
        assertThat(tool.enabledByDefault).isTrue()
        assertThat(tool.isDangerous).isFalse()
    }

    @Test
    fun `default display helpers delegate to tool i18n`() {
        val context = mockk<Context>()
        every { context.getString(any<Int>()) } answers { "res-${firstArg<Int>()}" }
        val tool = fakeTool(name = "read_file")

        assertThat(tool.getFriendlyName(context)).isEqualTo("res-${R.string.ai_tool_read_file}")
        assertThat(ToolCategory.CUSTOM.getDisplayName(context))
            .isEqualTo("res-${R.string.ai_tool_category_custom}")
    }

    @Test
    fun `dangerous confirmation uses localized default buttons and warning severity`() {
        val confirmation = DangerousToolConfirmation(
            title = "Danger",
            message = "Confirm before continuing",
        )

        assertThat(confirmation.details).isNull()
        assertThat(confirmation.confirmButtonText).startsWith("string-")
        assertThat(confirmation.cancelButtonText).startsWith("string-")
        assertThat(confirmation.severity).isEqualTo(ConfirmationSeverity.WARNING)
    }

    @Test
    fun `dangerous confirmation keeps explicit button text and severity`() {
        val confirmation = DangerousToolConfirmation(
            title = "Delete file",
            message = "This cannot be undone",
            details = "build.gradle.kts",
            confirmButtonText = "Delete",
            cancelButtonText = "Keep",
            severity = ConfirmationSeverity.DANGER,
        )

        assertThat(confirmation.title).isEqualTo("Delete file")
        assertThat(confirmation.details).isEqualTo("build.gradle.kts")
        assertThat(confirmation.confirmButtonText).isEqualTo("Delete")
        assertThat(confirmation.cancelButtonText).isEqualTo("Keep")
        assertThat(confirmation.severity).isEqualTo(ConfirmationSeverity.DANGER)
    }

    private fun fakeTool(
        name: String = "sample_tool",
        description: String = "Sample description",
    ): AiTool = object : AiTool {
        override val name: String = name
        override val description: String = description
        override val category: ToolCategory = ToolCategory.CUSTOM
        override fun getParameters() = JsonObject(emptyMap())
        override suspend fun execute(
            toolCall: com.wuxianggujun.tinaide.ai.api.ToolCall,
            context: ToolExecutionContext,
        ): ToolExecutionResult = ToolExecutionResult.Success("ok")
    }
}
