package com.wuxianggujun.tinaide.ai.tools

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import org.junit.Test

class ToolRegistryTest {

    @Test
    fun `register enable disable and unregister update tool state`() {
        ToolRegistry.clear()
        try {
            val tool = fakeRegistryTool(name = "fake_registry_tool")

            ToolRegistry.register(tool)

            assertThat(ToolRegistry.getTool("fake_registry_tool")).isSameInstanceAs(tool)
            assertThat(ToolRegistry.isEnabled("fake_registry_tool")).isTrue()
            assertThat(ToolRegistry.getEnabledRequestTools().single().function.name)
                .isEqualTo("fake_registry_tool")

            ToolRegistry.disableTool("fake_registry_tool")
            assertThat(ToolRegistry.isEnabled("fake_registry_tool")).isFalse()
            assertThat(ToolRegistry.getEnabledTools()).isEmpty()

            ToolRegistry.enableTool("fake_registry_tool")
            assertThat(ToolRegistry.isEnabled("fake_registry_tool")).isTrue()

            ToolRegistry.unregister("fake_registry_tool")
            assertThat(ToolRegistry.getTool("fake_registry_tool")).isNull()
            assertThat(ToolRegistry.isEnabled("fake_registry_tool")).isFalse()
        } finally {
            ToolRegistry.clear()
        }
    }

    @Test
    fun `listeners receive registry events`() {
        ToolRegistry.clear()
        val events = mutableListOf<String>()
        val listener = object : ToolRegistryListener {
            override fun onToolRegistered(tool: AiTool) {
                events += "registered:${tool.name}"
            }

            override fun onToolUnregistered(tool: AiTool) {
                events += "unregistered:${tool.name}"
            }

            override fun onToolEnabled(toolName: String) {
                events += "enabled:$toolName"
            }

            override fun onToolDisabled(toolName: String) {
                events += "disabled:$toolName"
            }

            override fun onRegistryCleared() {
                events += "cleared"
            }
        }

        try {
            ToolRegistry.addListener(listener)
            ToolRegistry.register(fakeRegistryTool(name = "observed_tool"))
            ToolRegistry.disableTool("observed_tool")
            ToolRegistry.enableTool("observed_tool")
            ToolRegistry.unregister("observed_tool")
            ToolRegistry.clear()

            assertThat(events).containsExactly(
                "registered:observed_tool",
                "disabled:observed_tool",
                "enabled:observed_tool",
                "unregistered:observed_tool",
                "cleared"
            ).inOrder()
        } finally {
            ToolRegistry.removeListener(listener)
            ToolRegistry.clear()
        }
    }

    @Test
    fun `default listener callbacks are safe no ops`() {
        val listener = object : ToolRegistryListener {}
        val tool = fakeRegistryTool(name = "noop_tool")

        listener.onToolRegistered(tool)
        listener.onToolUnregistered(tool)
        listener.onToolEnabled(tool.name)
        listener.onToolDisabled(tool.name)
        listener.onRegistryCleared()

        assertThat(ToolRegistry.getTool(tool.name)).isNull()
    }

    @Test
    fun `set enabled states ignores unknown tools`() {
        ToolRegistry.clear()
        try {
            ToolRegistry.register(fakeRegistryTool(name = "known_tool"))

            ToolRegistry.setToolEnabledStates(mapOf("known_tool" to false, "unknown_tool" to true))

            assertThat(ToolRegistry.isEnabled("known_tool")).isFalse()
            assertThat(ToolRegistry.getToolEnabledStates()).doesNotContainKey("unknown_tool")
        } finally {
            ToolRegistry.clear()
        }
    }

    @Test
    fun `execution result json includes status content and metadata`(): Unit = runBlocking {
        val successJson = ToolExecutionResult.Success(
            content = "done",
            metadata = mapOf("id" to "1")
        ).toJsonString()
        val errorJson = ToolExecutionResult.Error("bad").toJsonString()
        val cancelledJson = ToolExecutionResult.Cancelled("stop").toJsonString()

        assertThat(successJson).contains("\"status\": \"success\"")
        assertThat(successJson).contains("\"result\": \"done\"")
        assertThat(successJson).contains("\"id\": \"1\"")
        assertThat(errorJson).contains("\"status\": \"error\"")
        assertThat(cancelledJson).contains("\"status\": \"cancel\"")
    }

    private fun fakeRegistryTool(name: String): AiTool = object : AiTool {
        override val name: String = name
        override val description: String = "test tool"
        override val category: ToolCategory = ToolCategory.CUSTOM
        override fun getParameters() = JsonObject(emptyMap())
        override suspend fun execute(toolCall: com.wuxianggujun.tinaide.ai.api.ToolCall, context: ToolExecutionContext): ToolExecutionResult = ToolExecutionResult.Success("ok")
    }
}
