package com.wuxianggujun.tinaide.core.ai.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

/**
 * 聊天消息DAO
 */
@Dao
interface ChatMessageDao {

    @Query("SELECT * FROM chat_messages WHERE conversation_id = :conversationId ORDER BY timestamp ASC")
    suspend fun getMessagesByConversation(conversationId: String): List<ChatMessageEntity>

    @Query(
        """
        SELECT conversation_id AS conversationId, COUNT(*) AS count
        FROM chat_messages
        GROUP BY conversation_id
        """
    )
    suspend fun getMessageCountsByConversation(): List<ConversationMessageCount>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<ChatMessageEntity>)

    @Update
    suspend fun updateMessage(message: ChatMessageEntity)

    @Query("DELETE FROM chat_messages WHERE conversation_id = :conversationId")
    suspend fun deleteMessagesByConversation(conversationId: String)

    @Query("DELETE FROM chat_messages WHERE id = :messageId")
    suspend fun deleteMessage(messageId: String)

    @Query("DELETE FROM chat_messages")
    suspend fun deleteAllMessages()
}

data class ConversationMessageCount(
    val conversationId: String,
    val count: Int
)
