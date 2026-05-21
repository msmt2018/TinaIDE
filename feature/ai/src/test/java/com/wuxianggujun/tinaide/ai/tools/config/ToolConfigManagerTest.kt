package com.wuxianggujun.tinaide.ai.tools.config

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.ai.api.ToolCall
import com.wuxianggujun.tinaide.ai.tools.AiTool
import com.wuxianggujun.tinaide.ai.tools.ToolCategory
import com.wuxianggujun.tinaide.ai.tools.ToolExecutionContext
import com.wuxianggujun.tinaide.ai.tools.ToolExecutionResult
import com.wuxianggujun.tinaide.ai.tools.ToolRegistry
import io.mockk.mockk
import kotlinx.serialization.json.JsonObject
import org.junit.After
import org.junit.Before
import org.junit.Test

class ToolConfigManagerTest {

    private val context = mockk<Context>(relaxed = true)

    @Before
    fun setUp() {
        ToolRegistry.clear()
    }

    @After
    fun tearDown() {
        ToolRegistry.clear()
    }

    @Test
    fun `getAllToolConfigs mirrors registry metadata and enabled state`() {
        ToolRegistry.register(fakeTool("enabled_custom_tool", ToolCategory.EDITOR, enabledByDefault = true))
        ToolRegistry.register(fakeTool("disabled_custom_tool", ToolCategory.BUILD, enabledByDefault = false))

        val configs = ToolConfigManager.getAllToolConfigs(context)

        assertThat(configs.map { it.name })
            .containsExactly("enabled_custom_tool", "disabled_custom_tool")
        assertThat(configs.single { it.name == "enabled_custom_tool" }.enabled).isTrue()
        assertThat(configs.single { it.name == "disabled_custom_tool" }.enabled).isFalse()
        assertThat(configs.single { it.name == "disabled_custom_tool" }.enabledByDefault).isFalse()
        assertThat(configs.single { it.name == "enabled_custom_tool" }.displayName)
            .isEqualTo("Enabled Custom Tool")
    }

    @Test
    fun `getToolConfigsByCategory groups registered tools`() {
        ToolRegistry.register(fakeTool("editor_tool", ToolCategory.EDITOR))
        ToolRegistry.register(fakeTool("build_tool", ToolCategory.BUILD))

        val grouped = ToolConfigManager.getToolConfigsByCategory(context)

        assertThat(grouped[ToolCategory.EDITOR]!!.map { it.name }).containsExactly("editor_tool")
        assertThat(grouped[ToolCategory.BUILD]!!.map { it.name }).containsExactly("build_tool")
    }

    @Test
    fun `save enabled states updates only known tools`() {
        ToolRegistry.register(fakeTool("known_tool", ToolCategory.CUSTOM))

        ToolConfigManager.saveToolEnabledState("known_tool", enabled = false)
        ToolConfigManager.saveToolEnabledState("missing_tool", enabled = true)

        assertThat(ToolRegistry.isEnabled("known_tool")).isFalse()
        assertThat(ToolRegistry.isEnabled("missing_tool")).isFalse()

        ToolConfigManager.saveToolEnabledStates(mapOf("known_tool" to true, "missing_tool" to true))

        assertThat(ToolRegistry.isEnabled("known_tool")).isTrue()
        assertThat(ToolRegistry.getToolEnabledStates()).doesNotContainKey("missing_tool")
    }

    @Test
    fun `reset enable disable and category toggles keep registry consistent`() {
        ToolRegistry.register(fakeTool("editor_default_on", ToolCategory.EDITOR, enabledByDefault = true))
        ToolRegistry.register(fakeTool("editor_default_off", ToolCategory.EDITOR, enabledByDefault = false))
        ToolRegistry.register(fakeTool("build_default_on", ToolCategory.BUILD, enabledByDefault = true))

        ToolConfigManager.disableAllTools()
        assertThat(ToolRegistry.getEnabledTools()).isEmpty()

        ToolConfigManager.enableAllTools()
        assertThat(ToolRegistry.getEnabledTools().map { it.name })
            .containsExactly("editor_default_on", "editor_default_off", "build_default_on")

        ToolConfigManager.setToolsCategoryEnabled(ToolCategory.EDITOR, enabled = false)
        assertThat(ToolRegistry.isEnabled("editor_default_on")).isFalse()
        assertThat(ToolRegistry.isEnabled("editor_default_off")).isFalse()
        assertThat(ToolRegistry.isEnabled("build_default_on")).isTrue()

        ToolConfigManager.resetToDefaults()
        assertThat(ToolRegistry.isEnabled("editor_default_on")).isTrue()
        assertThat(ToolRegistry.isEnabled("editor_default_off")).isFalse()
        assertThat(ToolRegistry.isEnabled("build_default_on")).isTrue()
    }

    private fun fakeTool(
        name: String,
        category: ToolCategory,
        enabledByDefault: Boolean = true,
    ): AiTool = object : AiTool {
        override val name: String = name
        override val description: String = "description for $name"
        override val category: ToolCategory = category
        override val enabledByDefault: Boolean = enabledByDefault
        override fun getParameters() = JsonObject(emptyMap())
        override suspend fun execute(toolCall: ToolCall, context: ToolExecutionContext): ToolExecutionResult = ToolExecutionResult.Success("ok")
    }
}
