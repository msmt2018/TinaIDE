package com.wuxianggujun.tinaide.ai.channel

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AiChannelApiKeyStoreTest {

    private val context get() = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        clearStore()
    }

    @After
    fun tearDown() {
        clearStore()
    }

    @Test
    fun `put get and remove api keys by channel id`() {
        val store = AiChannelApiKeyStore(context)

        store.putApiKey("channel-a", "secret-a")
        store.putApiKey("channel-b", "secret-b")

        assertThat(store.getApiKey("channel-a")).isEqualTo("secret-a")
        assertThat(store.getApiKey("channel-b")).isEqualTo("secret-b")

        store.removeApiKey("channel-a")

        assertThat(store.getApiKey("channel-a")).isEmpty()
        assertThat(store.getApiKey("channel-b")).isEqualTo("secret-b")
    }

    @Test
    fun `stored api key survives new store instance`() {
        AiChannelApiKeyStore(context).putApiKey("channel-a", "persisted")

        val newStore = AiChannelApiKeyStore(context)

        assertThat(newStore.getApiKey("channel-a")).isEqualTo("persisted")
    }

    @Test
    fun `put and get normalize surrounding whitespace`() {
        val store = AiChannelApiKeyStore(context)

        store.putApiKey("channel-a", "  secret\n")

        assertThat(store.getApiKey("channel-a")).isEqualTo("secret")
    }

    private fun clearStore() {
        context.getSharedPreferences("ai_channel_api_keys", android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }
}
