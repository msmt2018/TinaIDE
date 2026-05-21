package com.wuxianggujun.tinaide.ai.repository

import com.wuxianggujun.tinaide.ai.api.ChatConversation
import com.wuxianggujun.tinaide.ai.api.ChatMessage
import com.wuxianggujun.tinaide.ai.api.ChatRole
import com.wuxianggujun.tinaide.ai.api.OpenAiContentPart
import com.wuxianggujun.tinaide.ai.model.ToolExecutionMode
import com.wuxianggujun.tinaide.core.ai.db.ChatMessageEntity
import com.wuxianggujun.tinaide.core.ai.db.ConversationEntity
import com.wuxianggujun.tinaide.core.serialization.JsonSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/**
 * 实体类型转换扩展函数
 */

private val json = JsonSerializer.default

/**
 * 将 ChatConversation 转换为 ConversationEntity
 */
fun ChatConversation.toEntity(toolExecutionMode: ToolExecutionMode = ToolExecutionMode.AUTO): ConversationEntity = ConversationEntity(
    id = id,
    title = title,
    createdAt = createdAt,
    updatedAt = updatedAt,
    toolExecutionMode = toolExecutionMode.name
)

/**
 * 将 ConversationEntity 转换为 ChatConversation
 */
fun ConversationEntity.toDomainModel(messages: List<ChatMessage>): ChatConversation = ChatConversation(
    id = id,
    title = title,
    messages = messages,
    createdAt = createdAt,
    updatedAt = updatedAt
)

/**
 * 获取对话的工具执行模式
 */
fun ConversationEntity.getToolExecutionMode(): ToolExecutionMode = try {
    ToolExecutionMode.valueOf(toolExecutionMode)
} catch (e: Exception) {
    ToolExecutionMode.AUTO
}

/**
 * 将 ChatMessage 转换为 ChatMessageEntity
 */
fun ChatMessage.toEntity(conversationId: String): ChatMessageEntity {
    val contentPartsJson = contentParts?.let { parts ->
        try {
            json.encodeToString(parts)
        } catch (e: Exception) {
            null
        }
    }

    val toolCallsJson = toolCalls?.let { calls ->
        try {
            json.encodeToString(calls)
        } catch (e: Exception) {
            null
        }
    }

    val usageJson = usage?.let { u ->
        try {
            json.encodeToString(u)
        } catch (e: Exception) {
            null
        }
    }

    // 序列化工具执行状态
    val toolExecutionStatesJson = toolCalls?.let { calls ->
        try {
            val statesMap = calls.mapNotNull { toolCall ->
                toolCall.id?.let { id ->
                    id to com.wuxianggujun.tinaide.ai.api.ToolExecutionState(
                        status = toolCall.executionStatus.name,
                        result = toolCall.executionResult,
                        error = toolCall.executionError
                    )
                }
            }.toMap()
            if (statesMap.isNotEmpty()) json.encodeToString(statesMap) else null
        } catch (e: Exception) {
            null
        }
    }

    return ChatMessageEntity(
        id = id,
        conversationId = conversationId,
        role = role.value,
        content = content,
        reasoningContent = reasoningContent,
        contentPartsJson = contentPartsJson,
        toolCallsJson = toolCallsJson,
        toolCallId = toolCallId,
        usageJson = usageJson,
        toolExecutionStatesJson = toolExecutionStatesJson,
        timestamp = timestamp
    )
}

/**
 * 将 ChatMessageEntity 转换为 ChatMessage
 */
fun ChatMessageEntity.toDomainModel(): ChatMessage {
    val contentParts = contentPartsJson?.let { jsonStr ->
        try {
            json.decodeFromString<List<OpenAiContentPart>>(jsonStr)
        } catch (e: Exception) {
            null
        }
    }

    val toolCalls = toolCallsJson?.let { jsonStr ->
        try {
            json.decodeFromString<List<com.wuxianggujun.tinaide.ai.api.ToolCall>>(jsonStr)
        } catch (e: Exception) {
            null
        }
    }

    val usage = usageJson?.let { jsonStr ->
        try {
            json.decodeFromString<com.wuxianggujun.tinaide.ai.api.ChatUsage>(jsonStr)
        } catch (e: Exception) {
            null
        }
    }

    // 反序列化工具执行状态
    val toolExecutionStates = toolExecutionStatesJson?.let { jsonStr ->
        try {
            json.decodeFromString<Map<String, com.wuxianggujun.tinaide.ai.api.ToolExecutionState>>(jsonStr)
        } catch (e: Exception) {
            null
        }
    }

    // 恢复工具执行状态到 ToolCall
    val toolCallsWithStates = toolCalls?.map { toolCall ->
        val state = toolExecutionStates?.get(toolCall.id)
        if (state != null) {
            toolCall.copy(
                executionStatus = try {
                    com.wuxianggujun.tinaide.ai.api.ToolExecutionStatus.valueOf(state.status)
                } catch (e: Exception) {
                    com.wuxianggujun.tinaide.ai.api.ToolExecutionStatus.PENDING
                },
                executionResult = state.result,
                executionError = state.error
            )
        } else {
            toolCall
        }
    }

    return ChatMessage(
        id = id,
        role = ChatRole.entries.find { it.value == role } ?: ChatRole.USER,
        content = content,
        contentParts = contentParts,
        reasoningContent = reasoningContent,
        toolCalls = toolCallsWithStates,
        toolCallId = toolCallId,
        usage = usage,
        timestamp = timestamp
    )
}
