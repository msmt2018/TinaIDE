package com.wuxianggujun.tinaide.ai.api

import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.core.serialization.JsonSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test

class ChatModelsSerializationTest {

    private val json = JsonSerializer.default

    @Test
    fun `tool call execution fields are transient and restored to defaults`() {
        val toolCall = ToolCall(
            id = "call-1",
            type = "function",
            function = ToolFunction(name = "read_file", arguments = "{}"),
            executionStatus = ToolExecutionStatus.SUCCESS,
            executionResult = "done",
            executionError = "ignored",
        )

        val encoded = json.encodeToString(toolCall)
        val decoded = json.decodeFromString<ToolCall>(encoded)

        assertThat(encoded).doesNotContain("executionStatus")
        assertThat(encoded).doesNotContain("executionResult")
        assertThat(encoded).doesNotContain("executionError")
        assertThat(decoded.executionStatus).isEqualTo(ToolExecutionStatus.PENDING)
        assertThat(decoded.executionResult).isNull()
        assertThat(decoded.executionError).isNull()
    }

    @Test
    fun `stream chunk preserves delta content usage and finish reason`() {
        val chunk = ChatStreamChunk(
            id = "chunk-1",
            choices = listOf(
                ChatStreamChoice(
                    index = 0,
                    delta = ChatDelta(
                        role = "assistant",
                        content = "hello",
                        reasoningContent = "thinking",
                        toolCalls = listOf(
                            ToolCallDelta(
                                index = 0,
                                id = "call-1",
                                type = "function",
                                function = ToolFunctionDelta(
                                    name = "read_file",
                                    arguments = "{}",
                                ),
                            ),
                        ),
                    ),
                    finishReason = "tool_calls",
                ),
            ),
            usage = ChatUsage(promptTokens = 1, completionTokens = 2, totalTokens = 3),
        )

        val encoded = json.encodeToString(chunk)
        val decoded = json.decodeFromString<ChatStreamChunk>(encoded)

        assertThat(encoded).contains("reasoning_content")
        assertThat(encoded).contains("finish_reason")
        assertThat(decoded).isEqualTo(chunk)
    }

    @Test
    fun `message contexts keep their typed payload fields`() {
        val currentFile = MessageContext.CurrentFile(
            fileName = "Main.kt",
            language = "kotlin",
            content = "fun main() = Unit",
        )
        val selectedCode = MessageContext.SelectedCode(
            fileName = "Main.kt",
            language = "kotlin",
            content = "println(1)",
            startLine = 10,
            endLine = 12,
        )
        val error = MessageContext.Error(errorMessage = "boom")

        assertThat(currentFile.fileName).isEqualTo("Main.kt")
        assertThat(selectedCode.startLine).isEqualTo(10)
        assertThat(selectedCode.endLine).isEqualTo(12)
        assertThat(error.errorMessage).isEqualTo("boom")
    }

    @Test
    fun `chat request preserves snake case api fields`() {
        val request = ChatRequest(
            model = "model-a",
            messages = listOf(
                ChatRequestMessage(role = "user", content = JsonPrimitive("hello")),
                ChatRequestMessage(role = "tool", content = JsonPrimitive("ok"), toolCallId = "call-1"),
            ),
            maxTokens = 128,
            temperature = 0.7f,
            stream = true,
            streamOptions = StreamOptions(includeUsage = true),
            tools = listOf(
                ChatRequestTool(
                    function = ChatRequestToolFunction(name = "read_file", description = "Read file"),
                ),
            ),
            toolChoice = "auto",
            thinking = ChatRequestThinking(budgetTokens = 2048),
        )

        val encoded = json.encodeToString(request)

        assertThat(encoded).contains("max_tokens")
        assertThat(encoded).contains("stream_options")
        assertThat(encoded).contains("include_usage")
        assertThat(encoded).contains("tool_choice")
        assertThat(encoded).contains("tool_call_id")
        assertThat(encoded).contains("budget_tokens")
    }

    @Test
    fun `models list response accepts gateway minimal model items`() {
        val decoded = json.decodeFromString<ModelsListResponse>(
            """
                {
                  "data":[
                    {"id":"gateway-model"}
                  ]
                }
            """.trimIndent()
        )

        assertThat(decoded.`object`).isNull()
        assertThat(decoded.data.single().id).isEqualTo("gateway-model")
        assertThat(decoded.data.single().`object`).isNull()
        assertThat(decoded.data.single().created).isNull()
        assertThat(decoded.data.single().ownedBy).isNull()
    }

    @Test
    fun `models list response preserves optional model metadata`() {
        val decoded = json.decodeFromString<ModelsListResponse>(
            """
                {
                  "object":"list",
                  "data":[
                    {
                      "id":"full-model",
                      "object":"model",
                      "created":123456,
                      "owned_by":"team-a"
                    }
                  ]
                }
            """.trimIndent()
        )
        val defaultResponse = ModelsListResponse()

        assertThat(decoded.`object`).isEqualTo("list")
        assertThat(decoded.data.single()).isEqualTo(
            ModelInfo(
                id = "full-model",
                `object` = "model",
                created = 123456,
                ownedBy = "team-a",
            )
        )
        assertThat(defaultResponse.data).isEmpty()
    }
}
