package com.wuxianggujun.tinaide.ai.tools.search

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

class SearchToolsSchemaTest {

    @Test
    fun `web search tools expose stable names and object schemas`() {
        val tools = listOf(GitHubSearchTool, WebSearchTool, ReadGitHubFileTool)

        assertThat(tools.map { it.name }).containsExactly(
            "github_search",
            "web_search",
            "read_github_file"
        ).inOrder()

        tools.forEach { tool ->
            val schema = tool.getParameters().jsonObject
            assertThat(schema["type"]?.jsonPrimitive?.content).isEqualTo("object")
            assertThat(schema).containsKey("properties")
            assertThat(schema).containsKey("required")
            assertThat(tool.isDangerous).isFalse()
        }
    }

    @Test
    fun `github file schema requires repo and path without network calls`() {
        val schema = ReadGitHubFileTool.getParameters().jsonObject

        assertThat(schema["required"].toString()).contains("repo")
        assertThat(schema["required"].toString()).contains("path")
        assertThat(schema["properties"].toString()).contains("max_lines")
    }
}
