package com.wuxianggujun.tinaide.core.ai.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 聊天消息实体（Room数据库）
 */
@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversation_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["conversation_id"])]
)
data class ChatMessageEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "conversation_id")
    val conversationId: String,

    @ColumnInfo(name = "role")
    val role: String,

    @ColumnInfo(name = "content")
    val content: String,

    @ColumnInfo(name = "reasoning_content")
    val reasoningContent: String? = null,

    @ColumnInfo(name = "content_parts_json")
    val contentPartsJson: String? = null,

    @ColumnInfo(name = "tool_calls_json")
    val toolCallsJson: String? = null,

    @ColumnInfo(name = "tool_call_id")
    val toolCallId: String? = null,

    @ColumnInfo(name = "usage_json")
    val usageJson: String? = null,

    // 工具执行状态映射 JSON: Map<toolCallId, {status, result, error}>
    @ColumnInfo(name = "tool_execution_states_json")
    val toolExecutionStatesJson: String? = null,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long
)
