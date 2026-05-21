package com.wuxianggujun.tinaide.core.config.ai

/**
 * BYOK AI 渠道配置（UI/业务层视角，不含 apiKey）。
 *
 * apiKey 由 `AiChannelApiKeyStore` 加密存储。
 */
data class AiChannelConfig(
    val id: String,
    val name: String,
    val provider: AiProvider,
    val baseUrl: String,
    val model: String,
    val createdAt: Long,
    val updatedAt: Long,
    val lastUsedAt: Long? = null,
)
