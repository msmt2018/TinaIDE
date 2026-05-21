package com.wuxianggujun.tinaide.ai.repository

import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.ai.api.ChatConversation
import com.wuxianggujun.tinaide.ai.api.ChatMessage
import com.wuxianggujun.tinaide.ai.api.ChatRole
import com.wuxianggujun.tinaide.ai.api.ChatUsage
import com.wuxianggujun.tinaide.ai.api.OpenAiContentPart
import com.wuxianggujun.tinaide.ai.api.OpenAiImageUrl
import com.wuxianggujun.tinaide.ai.api.ToolCall
import com.wuxianggujun.tinaide.ai.api.ToolExecutionStatus
import com.wuxianggujun.tinaide.ai.api.ToolFunction
import com.wuxianggujun.tinaide.ai.model.ToolExecutionMode
import com.wuxianggujun.tinaide.core.ai.db.ChatMessageEntity
import com.wuxianggujun.tinaide.core.ai.db.ConversationEntity
import org.junit.Test

class EntityExtensionsTest {

    @Test
    fun `conversation entity round trip preserves metadata and mode`() {
        val conversation = ChatConversation(
            id = "conversation-1",
            title = "Bug fix",
            createdAt = 10L,
            updatedAt = 20L,
        )
        val message = ChatMessage(id = "message-1", role = ChatRole.USER, content = "hello")

        val entity = conversation.toEntity(toolExecutionMode = ToolExecutionMode.MANUAL)
        val restored = entity.toDomainModel(messages = listOf(message))

        assertThat(entity.id).isEqualTo("conversation-1")
        assertThat(entity.toolExecutionMode).isEqualTo("MANUAL")
        assertThat(entity.getToolExecutionMode()).isEqualTo(ToolExecutionMode.MANUAL)
        assertThat(restored.id).isEqualTo(conversation.id)
        assertThat(restored.messages).containsExactly(message)
    }

    @Test
    fun `conversation entity default mode is auto`() {
        val conversation = ChatConversation(
            id = "conversation-1",
            title = "Default mode",
            createdAt = 10L,
            updatedAt = 20L,
        )

        val entity = conversation.toEntity()

        assertThat(entity.toolExecutionMode).isEqualTo("AUTO")
        assertThat(entity.getToolExecutionMode()).isEqualTo(ToolExecutionMode.AUTO)
    }

    @Test
    fun `invalid conversation mode falls back to auto`() {
        val entity = ConversationEntity(
            id = "conversation-1",
            title = "Broken mode",
            createdAt = 1L,
            updatedAt = 2L,
            toolExecutionMode = "UNKNOWN",
        )

        assertThat(entity.getToolExecutionMode()).isEqualTo(ToolExecutionMode.AUTO)
    }

    @Test
    fun `chat message entity round trip preserves structured fields and tool execution state`() {
        val message = ChatMessage(
            id = "message-1",
            role = ChatRole.ASSISTANT,
            content = "result",
            reasoningContent = "reasoning",
            contentParts = listOf(
                OpenAiContentPart(type = "text", text = "result"),
                OpenAiContentPart(type = "image_url", imageUrl = OpenAiImageUrl(url = "data:image/png;base64,abc")),
            ),
            toolCalls = listOf(
                ToolCall(
                    id = "call-1",
                    type = "function",
                    function = ToolFunction(name = "read_file", arguments = "{\"path\":\"a.cpp\"}"),
                    executionStatus = ToolExecutionStatus.FAILED,
                    executionResult = "partial output",
                    executionError = "boom",
                ),
            ),
            usage = ChatUsage(promptTokens = 1, completionTokens = 2, totalTokens = 3),
            timestamp = 123L,
        )

        val entity = message.toEntity(conversationId = "conversation-1")
        val restored = entity.toDomainModel()

        assertThat(entity.conversationId).isEqualTo("conversation-1")
        assertThat(entity.toolCallsJson).doesNotContain("executionResult")
        assertThat(entity.toolExecutionStatesJson).contains("FAILED")
        assertThat(restored.role).isEqualTo(ChatRole.ASSISTANT)
        assertThat(restored.reasoningContent).isEqualTo("reasoning")
        assertThat(restored.contentParts).hasSize(2)
        assertThat(restored.usage).isEqualTo(ChatUsage(promptTokens = 1, completionTokens = 2, totalTokens = 3))
        val restoredToolCall = restored.toolCalls?.single()
        assertThat(restoredToolCall?.executionStatus).isEqualTo(ToolExecutionStatus.FAILED)
        assertThat(restoredToolCall?.executionResult).isEqualTo("partial output")
        assertThat(restoredToolCall?.executionError).isEqualTo("boom")
    }

    @Test
    fun `chat message entity omits json fields when optional structured fields are absent`() {
        val message = ChatMessage(
            id = "message-1",
            role = ChatRole.USER,
            content = "plain text",
            timestamp = 123L,
        )

        val entity = message.toEntity(conversationId = "conversation-1")

        assertThat(entity.contentPartsJson).isNull()
        assertThat(entity.toolCallsJson).isNull()
        assertThat(entity.usageJson).isNull()
        assertThat(entity.toolExecutionStatesJson).isNull()
    }

    @Test
    fun `tool calls without ids do not persist execution states`() {
        val message = ChatMessage(
            id = "message-1",
            role = ChatRole.ASSISTANT,
            content = "call without id",
            toolCalls = listOf(
                ToolCall(
                    type = "function",
                    function = ToolFunction(name = "read_file", arguments = "{\"path\":\"a.cpp\"}"),
                    executionStatus = ToolExecutionStatus.SUCCESS,
                    executionResult = "ok",
                ),
            ),
        )

        val entity = message.toEntity(conversationId = "conversation-1")
        val restored = entity.toDomainModel()

        assertThat(entity.toolCallsJson).isNotNull()
        assertThat(entity.toolExecutionStatesJson).isNull()
        assertThat(restored.toolCalls?.single()?.executionStatus).isEqualTo(ToolExecutionStatus.PENDING)
    }

    @Test
    fun `invalid persisted tool execution status falls back to pending`() {
        val entity = ChatMessage(
            id = "message-1",
            role = ChatRole.ASSISTANT,
            content = "result",
            toolCalls = listOf(
                ToolCall(
                    id = "call-1",
                    type = "function",
                    function = ToolFunction(name = "read_file", arguments = "{\"path\":\"a.cpp\"}"),
                ),
            ),
        ).toEntity(conversationId = "conversation-1").copy(
            toolExecutionStatesJson = """{"call-1":{"status":"UNKNOWN","result":"kept","error":"boom"}}""",
        )

        val restoredToolCall = entity.toDomainModel().toolCalls?.single()

        assertThat(restoredToolCall?.executionStatus).isEqualTo(ToolExecutionStatus.PENDING)
        assertThat(restoredToolCall?.executionResult).isEqualTo("kept")
        assertThat(restoredToolCall?.executionError).isEqualTo("boom")
    }

    @Test
    fun `invalid chat message json and role fall back safely`() {
        val entity = ChatMessageEntity(
            id = "message-1",
            conversationId = "conversation-1",
            role = "alien",
            content = "hello",
            contentPartsJson = "{bad",
            toolCallsJson = "{bad",
            usageJson = "{bad",
            toolExecutionStatesJson = "{bad",
            timestamp = 1L,
        )

        val restored = entity.toDomainModel()

        assertThat(restored.role).isEqualTo(ChatRole.USER)
        assertThat(restored.contentParts).isNull()
        assertThat(restored.toolCalls).isNull()
        assertThat(restored.usage).isNull()
    }
}
