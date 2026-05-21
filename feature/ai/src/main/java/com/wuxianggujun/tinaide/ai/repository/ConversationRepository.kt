package com.wuxianggujun.tinaide.ai.repository

import android.content.Context
import com.wuxianggujun.tinaide.ai.api.ChatConversation
import com.wuxianggujun.tinaide.ai.api.ChatMessage
import com.wuxianggujun.tinaide.ai.api.ChatRole
import com.wuxianggujun.tinaide.ai.model.ToolExecutionMode
import com.wuxianggujun.tinaide.core.i18n.R
import com.wuxianggujun.tinaide.database.user.UserContentDatabase
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * 对话历史持久化仓库
 *
 * 使用 Room 数据库存储对话历史，支持：
 * - 多对话管理（列表、创建、删除）
 * - 对话消息的增删改查
 * - 自动保存
 * - Flow 响应式更新
 */
class ConversationRepository(
    private val context: Context,
    private val database: UserContentDatabase
) {

    private val conversationDao = database.conversationDao()
    private val chatMessageDao = database.chatMessageDao()

    // 对话列表（元数据，不含消息内容）
    val conversationList: Flow<List<ConversationMeta>> =
        conversationDao.getAllConversationsFlow()
            .map { entities ->
                val messageCounts = chatMessageDao.getMessageCountsByConversation()
                    .associate { item -> item.conversationId to item.count }
                entities.map { entity ->
                    ConversationMeta(
                        id = entity.id,
                        title = entity.title,
                        createdAt = entity.createdAt,
                        updatedAt = entity.updatedAt,
                        messageCount = messageCounts[entity.id] ?: 0,
                        toolExecutionMode = entity.getToolExecutionMode()
                    )
                }
            }
            .flowOn(Dispatchers.IO)

    // 当前对话
    private val _currentConversation = MutableStateFlow<ChatConversation?>(null)
    val currentConversation: Flow<ChatConversation?> = _currentConversation.asStateFlow()

    // 当前对话的工具执行模式
    private val _currentToolExecutionMode = MutableStateFlow(ToolExecutionMode.AUTO)
    val currentToolExecutionMode: Flow<ToolExecutionMode> = _currentToolExecutionMode.asStateFlow()

    /**
     * 创建新对话
     */
    suspend fun createConversation(
        title: String = "",
        toolExecutionMode: ToolExecutionMode = ToolExecutionMode.AUTO
    ): ChatConversation = withContext(Dispatchers.IO) {
        val conversation = ChatConversation(
            id = UUID.randomUUID().toString(),
            title = title.ifBlank { generateDefaultTitle() },
            messages = emptyList(),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        // 保存到数据库
        val entity = conversation.toEntity(toolExecutionMode)
        conversationDao.insertConversation(entity)

        // 设为当前对话
        _currentConversation.value = conversation
        _currentToolExecutionMode.value = toolExecutionMode

        conversation
    }

    /**
     * 加载对话
     */
    suspend fun loadConversation(id: String): ChatConversation? = withContext(Dispatchers.IO) {
        val entity = conversationDao.getConversationById(id) ?: return@withContext null
        val messages = chatMessageDao.getMessagesByConversation(id).map { it.toDomainModel() }

        val conversation = entity.toDomainModel(messages)
        _currentConversation.value = conversation
        _currentToolExecutionMode.value = entity.getToolExecutionMode()
        conversation
    }

    /**
     * 保存对话（完整保存）
     */
    suspend fun saveConversation(
        conversation: ChatConversation,
        toolExecutionMode: ToolExecutionMode? = null
    ) = withContext(Dispatchers.IO) {
        val updatedConversation = conversation.copy(updatedAt = System.currentTimeMillis())

        // 保存对话实体
        val mode = toolExecutionMode ?: _currentToolExecutionMode.value
        val entity = updatedConversation.toEntity(mode)
        conversationDao.updateConversation(entity)

        // 保存所有消息
        val messageEntities = updatedConversation.messages.map { message ->
            message.toEntity(updatedConversation.id)
        }
        chatMessageDao.insertMessages(messageEntities)

        // 更新当前对话
        if (_currentConversation.value?.id == conversation.id) {
            _currentConversation.value = updatedConversation
            if (toolExecutionMode != null) {
                _currentToolExecutionMode.value = toolExecutionMode
            }
        }
    }

    /**
     * 添加消息到当前对话
     */
    suspend fun addMessage(message: ChatMessage): ChatConversation? = withContext(Dispatchers.IO) {
        val current = _currentConversation.value ?: return@withContext null

        val updated = current.copy(
            messages = current.messages + message,
            updatedAt = System.currentTimeMillis(),
            title = if (current.title.startsWith(context.getString(R.string.ai_new_conversation)) && message.role == ChatRole.USER) {
                // 用第一条用户消息更新标题
                generateTitleFromContent(message.content)
            } else {
                current.title
            }
        )

        // 保存消息到数据库
        val messageEntity = message.toEntity(current.id)
        chatMessageDao.insertMessage(messageEntity)

        // 更新对话实体（保持当前的工具执行模式）
        val conversationEntity = updated.toEntity(_currentToolExecutionMode.value)
        conversationDao.updateConversation(conversationEntity)

        _currentConversation.value = updated

        updated
    }

    /**
     * 更新最后一条消息（用于流式输出）
     */
    suspend fun updateLastMessage(content: String): ChatConversation? = withContext(Dispatchers.IO) {
        val current = _currentConversation.value ?: return@withContext null
        if (current.messages.isEmpty()) return@withContext current

        val messages = current.messages.toMutableList()
        val lastMessage = messages.last()
        messages[messages.lastIndex] = lastMessage.copy(content = content)

        val updated = current.copy(
            messages = messages,
            updatedAt = System.currentTimeMillis()
        )

        _currentConversation.value = updated
        updated
    }

    /**
     * 更新指定消息（用于更新工具执行状态等）
     */
    suspend fun updateMessage(message: ChatMessage): ChatConversation? = withContext(Dispatchers.IO) {
        val current = _currentConversation.value ?: return@withContext null

        // 查找并更新消息
        val messages = current.messages.toMutableList()
        val index = messages.indexOfFirst { it.id == message.id }
        if (index == -1) return@withContext current

        messages[index] = message

        val updated = current.copy(
            messages = messages,
            updatedAt = System.currentTimeMillis()
        )

        // 更新数据库中的消息（使用 REPLACE 策略）
        val messageEntity = message.toEntity(current.id)
        chatMessageDao.insertMessage(messageEntity)

        // 更新对话实体
        val conversationEntity = updated.toEntity(_currentToolExecutionMode.value)
        conversationDao.updateConversation(conversationEntity)

        _currentConversation.value = updated

        updated
    }

    /**
     * 完成流式输出后保存
     */
    suspend fun finishStreaming() = withContext(Dispatchers.IO) {
        val current = _currentConversation.value ?: return@withContext

        // 更新最后一条消息到数据库
        if (current.messages.isNotEmpty()) {
            val lastMessage = current.messages.last()
            val messageEntity = lastMessage.toEntity(current.id)
            chatMessageDao.updateMessage(messageEntity)
        }

        // 更新对话实体（保持当前的工具执行模式）
        val conversationEntity = current.toEntity(_currentToolExecutionMode.value)
        conversationDao.updateConversation(conversationEntity)
    }

    /**
     * 删除对话
     */
    suspend fun deleteConversation(id: String) = withContext(Dispatchers.IO) {
        // 删除对话（消息会因为外键级联删除）
        conversationDao.deleteConversationById(id)

        // 如果删除的是当前对话，清空
        if (_currentConversation.value?.id == id) {
            _currentConversation.value = null
        }
    }

    /**
     * 重命名对话
     */
    suspend fun renameConversation(id: String, newTitle: String) = withContext(Dispatchers.IO) {
        val entity = conversationDao.getConversationById(id) ?: return@withContext
        val updated = entity.copy(title = newTitle, updatedAt = System.currentTimeMillis())
        conversationDao.updateConversation(updated)

        // 如果是当前对话，更新内存中的对话
        if (_currentConversation.value?.id == id) {
            _currentConversation.value = _currentConversation.value?.copy(
                title = newTitle,
                updatedAt = updated.updatedAt
            )
        }
    }

    /**
     * 更新当前对话的工具执行模式
     */
    suspend fun updateToolExecutionMode(mode: ToolExecutionMode) = withContext(Dispatchers.IO) {
        val current = _currentConversation.value ?: return@withContext

        _currentToolExecutionMode.value = mode

        // 更新数据库
        val entity = conversationDao.getConversationById(current.id) ?: return@withContext
        val updated = entity.copy(
            toolExecutionMode = mode.name,
            updatedAt = System.currentTimeMillis()
        )
        conversationDao.updateConversation(updated)
    }

    /**
     * 删除指定消息
     */
    suspend fun deleteMessage(messageId: String): ChatConversation? = withContext(Dispatchers.IO) {
        val current = _currentConversation.value ?: return@withContext null

        // 查找并删除消息
        val messages = current.messages.toMutableList()
        val index = messages.indexOfFirst { it.id == messageId }
        if (index == -1) return@withContext current

        messages.removeAt(index)

        val updated = current.copy(
            messages = messages,
            updatedAt = System.currentTimeMillis()
        )

        // 从数据库中删除消息
        chatMessageDao.deleteMessage(messageId)

        // 更新对话实体
        val conversationEntity = updated.toEntity(_currentToolExecutionMode.value)
        conversationDao.updateConversation(conversationEntity)

        _currentConversation.value = updated

        updated
    }

    /**
     * 清空当前对话（新建空白对话）
     */
    fun clearCurrentConversation() {
        _currentConversation.value = null
        _currentToolExecutionMode.value = ToolExecutionMode.AUTO
    }

    /**
     * 获取当前对话的消息列表
     */
    fun getCurrentMessages(): List<ChatMessage> = _currentConversation.value?.messages ?: emptyList()

    /**
     * 设置当前对话（用于从外部设置）
     */
    fun setCurrentConversation(conversation: ChatConversation?) {
        _currentConversation.value = conversation
    }

    private suspend fun generateDefaultTitle(): String {
        val count = conversationDao.getConversationCount() + 1
        return context.getString(R.string.ai_new_conversation_count, count)
    }

    private fun generateTitleFromContent(content: String): String {
        // 取前 20 个字符作为标题
        val title = content.take(20).replace("\n", " ").trim()
        return if (content.length > 20) "$title..." else title
    }
}

/**
 * 对话元数据（用于列表显示，不含消息内容）
 */
data class ConversationMeta(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val messageCount: Int,
    val toolExecutionMode: ToolExecutionMode = ToolExecutionMode.AUTO
)
