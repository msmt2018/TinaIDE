package com.wuxianggujun.tinaide.ai.channel

import com.wuxianggujun.tinaide.core.ai.db.AiChannelDao
import com.wuxianggujun.tinaide.core.ai.db.AiChannelEntity
import com.wuxianggujun.tinaide.core.config.ai.AiChannelConfig
import com.wuxianggujun.tinaide.core.config.ai.AiChannelProvider
import com.wuxianggujun.tinaide.core.config.ai.AiProvider
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * BYOK 渠道仓库：封装 Room DAO 与加密 API Key 存储。
 *
 * 契约：
 * - apiKey 不会进入 [AiChannelConfig]；调用方通过 [getApiKey] 单独取。
 * - 所有写操作一律在 IO 调度器。
 */
class AiChannelRepository(
    private val dao: AiChannelDao,
    private val apiKeyStore: AiChannelApiKeyStore,
) : AiChannelProvider {

    override val channelsFlow: Flow<List<AiChannelConfig>> =
        dao.observeAll()
            .map { list -> list.map { it.toConfig() } }
            .flowOn(Dispatchers.IO)

    override suspend fun getById(id: String): AiChannelConfig? = withContext(Dispatchers.IO) {
        dao.getById(id)?.toConfig()
    }

    override suspend fun getApiKey(id: String): String = withContext(Dispatchers.IO) {
        apiKeyStore.getApiKey(id).trim()
    }

    override suspend fun add(
        name: String,
        provider: AiProvider,
        baseUrl: String,
        model: String,
        apiKey: String,
    ): AiChannelConfig = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val entity = AiChannelEntity(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            provider = provider.name,
            baseUrl = baseUrl.trim(),
            model = model.trim(),
            createdAt = now,
            updatedAt = now,
            lastUsedAt = null,
        )
        dao.insert(entity)
        val cleanedApiKey = apiKey.trim()
        if (cleanedApiKey.isNotEmpty()) {
            apiKeyStore.putApiKey(entity.id, cleanedApiKey)
        }
        entity.toConfig()
    }

    /**
     * 更新渠道字段。apiKey 为 null 时不改动现有密钥；为空串则清空。
     */
    override suspend fun update(
        id: String,
        name: String,
        provider: AiProvider,
        baseUrl: String,
        model: String,
        apiKey: String?,
    ): Boolean = withContext(Dispatchers.IO) {
        val existing = dao.getById(id) ?: return@withContext false
        val updated = existing.copy(
            name = name.trim(),
            provider = provider.name,
            baseUrl = baseUrl.trim(),
            model = model.trim(),
            updatedAt = System.currentTimeMillis(),
        )
        dao.update(updated)
        if (apiKey != null) {
            val cleanedApiKey = apiKey.trim()
            if (cleanedApiKey.isEmpty()) {
                apiKeyStore.removeApiKey(id)
            } else {
                apiKeyStore.putApiKey(id, cleanedApiKey)
            }
        }
        true
    }

    override suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        dao.deleteById(id)
        apiKeyStore.removeApiKey(id)
    }

    override suspend fun markUsed(id: String) = withContext(Dispatchers.IO) {
        dao.markUsed(id, System.currentTimeMillis())
    }

    private fun AiChannelEntity.toConfig(): AiChannelConfig = AiChannelConfig(
        id = id,
        name = name,
        provider = runCatching { AiProvider.valueOf(provider) }.getOrDefault(AiProvider.CUSTOM),
        baseUrl = baseUrl,
        model = model,
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastUsedAt = lastUsedAt,
    )
}
