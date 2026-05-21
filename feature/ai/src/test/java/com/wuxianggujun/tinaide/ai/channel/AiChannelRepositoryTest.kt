package com.wuxianggujun.tinaide.ai.channel

import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.core.ai.db.AiChannelDao
import com.wuxianggujun.tinaide.core.ai.db.AiChannelEntity
import com.wuxianggujun.tinaide.core.config.ai.AiProvider
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test

class AiChannelRepositoryTest {

    private lateinit var dao: FakeAiChannelDao
    private lateinit var apiKeyStore: AiChannelApiKeyStore
    private lateinit var repository: AiChannelRepository

    @Before
    fun setUp() {
        dao = FakeAiChannelDao()
        apiKeyStore = mockk(relaxed = true)
        every { apiKeyStore.putApiKey(any(), any()) } just Runs
        every { apiKeyStore.removeApiKey(any()) } just Runs
        repository = AiChannelRepository(dao, apiKeyStore)
    }

    @Test
    fun `add inserts channel entity and stores non empty api key`(): Unit = runBlocking {
        val config = repository.add(
            name = "  OpenAI  ",
            provider = AiProvider.OPENAI,
            baseUrl = "  https://api.openai.com/v1  ",
            model = "  gpt-4o  ",
            apiKey = "  secret\n",
        )

        assertThat(dao.entities.single().id).isEqualTo(config.id)
        assertThat(config.name).isEqualTo("OpenAI")
        assertThat(config.provider).isEqualTo(AiProvider.OPENAI)
        assertThat(config.baseUrl).isEqualTo("https://api.openai.com/v1")
        assertThat(config.model).isEqualTo("gpt-4o")
        verify(exactly = 1) { apiKeyStore.putApiKey(config.id, "secret") }
    }

    @Test
    fun `add skips api key store when key is blank after trim`(): Unit = runBlocking {
        val config = repository.add(
            name = "Local",
            provider = AiProvider.OLLAMA,
            baseUrl = "http://localhost:11434/v1",
            model = "llama3",
            apiKey = "  \n\t",
        )

        assertThat(dao.entities.single().id).isEqualTo(config.id)
        verify(exactly = 0) { apiKeyStore.putApiKey(any(), any()) }
    }

    @Test
    fun `update supports unchanged cleared and replaced api keys`(): Unit = runBlocking {
        dao.seed(channel(id = "channel-1", provider = AiProvider.DEEPSEEK.name))

        val unchanged = repository.update(
            id = "channel-1",
            name = "DeepSeek A",
            provider = AiProvider.DEEPSEEK,
            baseUrl = "https://api.deepseek.com/v1",
            model = "deepseek-chat",
            apiKey = null,
        )
        val cleared = repository.update(
            id = "channel-1",
            name = "DeepSeek B",
            provider = AiProvider.DEEPSEEK,
            baseUrl = "https://api.deepseek.com/v1",
            model = "deepseek-coder",
            apiKey = "  \n\t",
        )
        val replaced = repository.update(
            id = "channel-1",
            name = "  DeepSeek C  ",
            provider = AiProvider.DEEPSEEK,
            baseUrl = "  https://api.deepseek.com/v1  ",
            model = "  deepseek-chat  ",
            apiKey = "  new-key\n",
        )

        assertThat(unchanged).isTrue()
        assertThat(cleared).isTrue()
        assertThat(replaced).isTrue()
        assertThat(dao.entities.single().name).isEqualTo("DeepSeek C")
        assertThat(dao.entities.single().baseUrl).isEqualTo("https://api.deepseek.com/v1")
        assertThat(dao.entities.single().model).isEqualTo("deepseek-chat")
        verify(exactly = 1) { apiKeyStore.removeApiKey("channel-1") }
        verify(exactly = 1) { apiKeyStore.putApiKey("channel-1", "new-key") }
    }

    @Test
    fun `missing update returns false without touching api key store`(): Unit = runBlocking {
        val updated = repository.update(
            id = "missing",
            name = "Missing",
            provider = AiProvider.CUSTOM,
            baseUrl = "",
            model = "",
            apiKey = "  new-key\n",
        )

        assertThat(updated).isFalse()
        verify(exactly = 0) { apiKeyStore.putApiKey(any(), any()) }
        verify(exactly = 0) { apiKeyStore.removeApiKey(any()) }
    }

    @Test
    fun `delete removes entity and api key while mark used updates timestamp`(): Unit = runBlocking {
        dao.seed(channel(id = "channel-1"))

        repository.markUsed("channel-1")
        assertThat(dao.entities.single().lastUsedAt).isNotNull()

        repository.delete("channel-1")
        assertThat(dao.entities).isEmpty()
        verify(exactly = 1) { apiKeyStore.removeApiKey("channel-1") }
    }

    @Test
    fun `get and flow convert unknown provider to custom`(): Unit = runBlocking {
        dao.seed(channel(id = "channel-1", provider = "UNKNOWN"))

        val byId = repository.getById("channel-1")
        val fromFlow = repository.channelsFlow.first().single()

        assertThat(byId?.provider).isEqualTo(AiProvider.CUSTOM)
        assertThat(fromFlow.provider).isEqualTo(AiProvider.CUSTOM)
    }

    @Test
    fun `get api key delegates to encrypted key store`(): Unit = runBlocking {
        every { apiKeyStore.getApiKey("channel-1") } returns "  secret\n"

        assertThat(repository.getApiKey("channel-1")).isEqualTo("secret")
    }

    private fun channel(
        id: String,
        provider: String = AiProvider.OPENAI.name,
    ): AiChannelEntity = AiChannelEntity(
        id = id,
        name = "Channel $id",
        provider = provider,
        baseUrl = "https://example.com/v1",
        model = "model-a",
        createdAt = 1L,
        updatedAt = 2L,
        lastUsedAt = null,
    )

    private class FakeAiChannelDao : AiChannelDao {
        private val state = MutableStateFlow<List<AiChannelEntity>>(emptyList())
        val entities: List<AiChannelEntity> get() = state.value

        fun seed(vararg entities: AiChannelEntity) {
            state.value = entities.toList()
        }

        override fun observeAll(): Flow<List<AiChannelEntity>> = state

        override suspend fun getById(id: String): AiChannelEntity? = state.value.firstOrNull { it.id == id }

        override suspend fun insert(entity: AiChannelEntity) {
            state.value = state.value.filterNot { it.id == entity.id } + entity
        }

        override suspend fun update(entity: AiChannelEntity) {
            state.value = state.value.map { existing -> if (existing.id == entity.id) entity else existing }
        }

        override suspend fun deleteById(id: String) {
            state.value = state.value.filterNot { it.id == id }
        }

        override suspend fun markUsed(id: String, timestamp: Long) {
            state.value = state.value.map { existing ->
                if (existing.id == id) existing.copy(lastUsedAt = timestamp) else existing
            }
        }
    }
}
