package com.wuxianggujun.tinaide.core.config.ai

import kotlinx.coroutines.flow.Flow

/**
 * BYOK 渠道提供者接口。
 *
 * 抽离到 `core:config` 以便 `feature:settings` 等非 `feature:ai` 模块使用，
 * 避免 feature 间直接依赖。具体实现位于 `feature:ai`。
 *
 * apiKey 不会作为 [AiChannelConfig] 的字段返回；需要单独走 [getApiKey]。
 */
interface AiChannelProvider {

    val channelsFlow: Flow<List<AiChannelConfig>>

    suspend fun getById(id: String): AiChannelConfig?

    suspend fun getApiKey(id: String): String

    suspend fun add(
        name: String,
        provider: AiProvider,
        baseUrl: String,
        model: String,
        apiKey: String,
    ): AiChannelConfig

    /**
     * 更新渠道。apiKey 为 null 时不改动现有密钥；传空串则清空。
     */
    suspend fun update(
        id: String,
        name: String,
        provider: AiProvider,
        baseUrl: String,
        model: String,
        apiKey: String?,
    ): Boolean

    suspend fun delete(id: String)

    suspend fun markUsed(id: String)
}
