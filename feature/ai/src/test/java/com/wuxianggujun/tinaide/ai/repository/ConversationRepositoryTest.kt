package com.wuxianggujun.tinaide.ai.repository

import androidx.room.Room
import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.ai.api.ChatConversation
import com.wuxianggujun.tinaide.ai.api.ChatMessage
import com.wuxianggujun.tinaide.ai.api.ChatRole
import com.wuxianggujun.tinaide.ai.model.ToolExecutionMode
import com.wuxianggujun.tinaide.core.ai.db.ConversationEntity
import com.wuxianggujun.tinaide.core.i18n.R
import com.wuxianggujun.tinaide.database.user.UserContentDatabase
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ConversationRepositoryTest {

    private lateinit var database: UserContentDatabase
    private lateinit var repository: ConversationRepository

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, UserContentDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repositoryContext = mockk<android.content.Context>(relaxed = true)
        every { repositoryContext.getString(R.string.ai_new_conversation) } returns "New Conversation"
        every { repositoryContext.getString(R.string.ai_new_conversation_count, 1) } returns "New Conversation 1"
        repository = ConversationRepository(repositoryContext, database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `create conversation stores entity and exposes current state`() = runTest {
        val conversation = repository.createConversation(
            title = "Manual conversation",
            toolExecutionMode = ToolExecutionMode.MANUAL,
        )

        assertThat(database.conversationDao().getConversationById(conversation.id)?.title)
            .isEqualTo("Manual conversation")
        assertThat(repository.currentConversation.first()?.id).isEqualTo(conversation.id)
        assertThat(repository.currentToolExecutionMode.first()).isEqualTo(ToolExecutionMode.MANUAL)
    }

    @Test
    fun `blank title uses default title and first long user message renames conversation`() = runTest {
        val conversation = repository.createConversation()

        val updated = repository.addMessage(
            ChatMessage(
                id = "m-title",
                role = ChatRole.USER,
                content = "abcdefghijklmnopqrstuvw",
                timestamp = 1L,
            )
        )

        assertThat(conversation.title).isEqualTo("New Conversation 1")
        assertThat(updated?.title).isEqualTo("abcdefghijklmnopqrst...")
        assertThat(database.conversationDao().getConversationById(conversation.id)?.title)
            .isEqualTo("abcdefghijklmnopqrst...")
    }

    @Test
    fun `save conversation persists messages and updates current mode when applicable`() = runTest {
        val detached = ChatConversation(
            id = "detached",
            title = "Detached",
            createdAt = 1L,
            updatedAt = 1L,
            messages = listOf(ChatMessage(id = "m-detached", role = ChatRole.USER, content = "hello", timestamp = 1L)),
        )
        database.conversationDao().insertConversation(detached.toEntity(ToolExecutionMode.AUTO))

        repository.saveConversation(detached, toolExecutionMode = ToolExecutionMode.MANUAL)

        assertThat(database.conversationDao().getConversationById("detached")?.toolExecutionMode)
            .isEqualTo(ToolExecutionMode.MANUAL.name)
        assertThat(database.chatMessageDao().getMessagesByConversation("detached").map { it.id })
            .containsExactly("m-detached")
        assertThat(repository.currentConversation.first()).isNull()

        val current = repository.createConversation(title = "Current")
        repository.saveConversation(
            current.copy(
                messages = listOf(ChatMessage(id = "m-current", role = ChatRole.ASSISTANT, content = "ok", timestamp = 2L))
            ),
            toolExecutionMode = ToolExecutionMode.MANUAL,
        )

        assertThat(repository.currentConversation.first()?.messages?.single()?.id).isEqualTo("m-current")
        assertThat(repository.currentToolExecutionMode.first()).isEqualTo(ToolExecutionMode.MANUAL)
    }

    @Test
    fun `save current conversation without explicit mode keeps existing mode`() = runTest {
        val current = repository.createConversation(
            title = "Current",
            toolExecutionMode = ToolExecutionMode.MANUAL,
        )

        repository.saveConversation(
            current.copy(
                messages = listOf(ChatMessage(id = "m-current", role = ChatRole.USER, content = "hello", timestamp = 1L))
            )
        )

        assertThat(repository.currentConversation.first()?.messages?.single()?.id).isEqualTo("m-current")
        assertThat(repository.currentToolExecutionMode.first()).isEqualTo(ToolExecutionMode.MANUAL)
        assertThat(database.conversationDao().getConversationById(current.id)?.toolExecutionMode)
            .isEqualTo(ToolExecutionMode.MANUAL.name)
    }

    @Test
    fun `empty and missing current branches return without database writes`() = runTest {
        val message = ChatMessage(id = "missing", role = ChatRole.USER, content = "hello", timestamp = 1L)

        assertThat(repository.loadConversation("missing")).isNull()
        assertThat(repository.addMessage(message)).isNull()
        assertThat(repository.updateLastMessage("ignored")).isNull()
        assertThat(repository.updateMessage(message)).isNull()
        assertThat(repository.deleteMessage("missing")).isNull()

        repository.finishStreaming()
        repository.renameConversation("missing", "Ignored")
        repository.updateToolExecutionMode(ToolExecutionMode.MANUAL)
        repository.deleteConversation("missing")

        assertThat(repository.currentConversation.first()).isNull()
        assertThat(repository.conversationList.first()).isEmpty()
    }

    @Test
    fun `empty current conversation keeps no-op message operations stable`() = runTest {
        val conversation = repository.createConversation(title = "Empty")

        val lastMessageResult = repository.updateLastMessage("ignored")
        repository.finishStreaming()
        val missingUpdate = repository.updateMessage(
            ChatMessage(id = "missing", role = ChatRole.ASSISTANT, content = "ignored", timestamp = 1L)
        )
        val missingDelete = repository.deleteMessage("missing")

        assertThat(lastMessageResult?.id).isEqualTo(conversation.id)
        assertThat(lastMessageResult?.messages).isEmpty()
        assertThat(missingUpdate?.id).isEqualTo(conversation.id)
        assertThat(missingDelete?.id).isEqualTo(conversation.id)
        assertThat(database.chatMessageDao().getMessagesByConversation(conversation.id)).isEmpty()
    }

    @Test
    fun `add message preserves manual title and only user message renames new title`() = runTest {
        val manualTitle = repository.createConversation(title = "Manual title")
        val afterManualUser = repository.addMessage(
            ChatMessage(id = "m-manual", role = ChatRole.USER, content = "short title", timestamp = 1L)
        )

        assertThat(afterManualUser?.title).isEqualTo("Manual title")

        repository.deleteConversation(manualTitle.id)
        repository.createConversation()

        val afterAssistant = repository.addMessage(
            ChatMessage(id = "m-assistant", role = ChatRole.ASSISTANT, content = "assistant", timestamp = 2L)
        )
        val afterUser = repository.addMessage(
            ChatMessage(id = "m-user", role = ChatRole.USER, content = "hello\nworld", timestamp = 3L)
        )

        assertThat(afterAssistant?.title).isEqualTo("New Conversation 1")
        assertThat(afterUser?.title).isEqualTo("hello world")
    }

    @Test
    fun `add update finish and delete message keep memory and database in sync`() = runTest {
        val conversation = repository.createConversation(title = "Chat")
        val userMessage = ChatMessage(id = "m1", role = ChatRole.USER, content = "hello", timestamp = 1L)
        val assistantMessage = ChatMessage(id = "m2", role = ChatRole.ASSISTANT, content = "partial", timestamp = 2L)

        repository.addMessage(userMessage)
        repository.addMessage(assistantMessage)
        repository.updateLastMessage("complete")
        repository.finishStreaming()

        val storedAfterFinish = database.chatMessageDao().getMessagesByConversation(conversation.id)
        assertThat(storedAfterFinish).hasSize(2)
        assertThat(storedAfterFinish.last().content).isEqualTo("complete")

        val updatedUser = userMessage.copy(content = "hello updated")
        repository.updateMessage(updatedUser)
        assertThat(database.chatMessageDao().getMessagesByConversation(conversation.id).first().content)
            .isEqualTo("hello updated")

        val afterDelete = repository.deleteMessage("m1")
        assertThat(afterDelete?.messages?.map { it.id }).containsExactly("m2")
        assertThat(database.chatMessageDao().getMessagesByConversation(conversation.id).map { it.id })
            .containsExactly("m2")
    }

    @Test
    fun `load rename mode and delete update current conversation`() = runTest {
        val conversation = repository.createConversation(title = "Original")
        repository.addMessage(ChatMessage(id = "m1", role = ChatRole.USER, content = "hello", timestamp = 1L))

        repository.clearCurrentConversation()
        val loaded = repository.loadConversation(conversation.id)
        repository.renameConversation(conversation.id, "Renamed")
        repository.updateToolExecutionMode(ToolExecutionMode.MANUAL)

        assertThat(loaded?.messages?.single()?.content).isEqualTo("hello")
        assertThat(repository.currentConversation.first()?.title).isEqualTo("Renamed")
        assertThat(repository.currentToolExecutionMode.first()).isEqualTo(ToolExecutionMode.MANUAL)
        assertThat(database.conversationDao().getConversationById(conversation.id)?.toolExecutionMode)
            .isEqualTo(ToolExecutionMode.MANUAL.name)

        repository.deleteConversation(conversation.id)

        assertThat(repository.currentConversation.first()).isNull()
        assertThat(database.conversationDao().getConversationById(conversation.id)).isNull()
    }

    @Test
    fun `rename and delete non current conversation keep current conversation unchanged`() = runTest {
        val current = repository.createConversation(title = "Current")
        val other = ChatConversation(
            id = "other",
            title = "Other",
            createdAt = 1L,
            updatedAt = 1L,
        )
        database.conversationDao().insertConversation(other.toEntity(ToolExecutionMode.AUTO))

        repository.renameConversation("other", "Renamed Other")
        repository.deleteConversation("other")

        assertThat(repository.currentConversation.first()?.id).isEqualTo(current.id)
        assertThat(repository.currentConversation.first()?.title).isEqualTo("Current")
        assertThat(database.conversationDao().getConversationById("other")).isNull()
    }

    @Test
    fun `tool mode update handles missing persisted current conversation`() = runTest {
        repository.setCurrentConversation(ChatConversation(id = "missing", title = "Missing"))

        repository.updateToolExecutionMode(ToolExecutionMode.MANUAL)

        assertThat(repository.currentToolExecutionMode.first()).isEqualTo(ToolExecutionMode.MANUAL)
        assertThat(database.conversationDao().getConversationById("missing")).isNull()
    }

    @Test
    fun `get current messages reflects explicit current conversation`() {
        assertThat(repository.getCurrentMessages()).isEmpty()

        repository.setCurrentConversation(
            ChatConversation(
                id = "current",
                title = "Current",
                messages = listOf(ChatMessage(id = "m1", role = ChatRole.USER, content = "hello", timestamp = 1L)),
            )
        )

        assertThat(repository.getCurrentMessages().map { it.id }).containsExactly("m1")
    }

    @Test
    fun `conversation list includes message counts and invalid mode falls back to auto`() = runTest {
        database.conversationDao().insertConversation(
            ConversationEntity(
                id = "c1",
                title = "Broken mode",
                createdAt = 1L,
                updatedAt = 2L,
                toolExecutionMode = "UNKNOWN",
            )
        )
        repository.setCurrentConversation(
            com.wuxianggujun.tinaide.ai.api.ChatConversation(id = "c1", title = "Broken mode")
        )
        repository.addMessage(ChatMessage(id = "m1", role = ChatRole.USER, content = "hello", timestamp = 1L))

        val meta = repository.conversationList.first().single()

        assertThat(meta.id).isEqualTo("c1")
        assertThat(meta.messageCount).isEqualTo(1)
        assertThat(meta.toolExecutionMode).isEqualTo(ToolExecutionMode.AUTO)
    }
}
