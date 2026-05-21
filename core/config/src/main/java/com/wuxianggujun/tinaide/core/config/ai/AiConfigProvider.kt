package com.wuxianggujun.tinaide.core.config.ai

import kotlinx.coroutines.flow.Flow

/**
 * AI 配置提供者接口
 *
 * 将 AI 配置的读写契约下沉到 core:config，
 * 使 feature:settings 无需依赖 feature:ai 即可管理 AI 配置。
 */
interface AiConfigProvider {
    val configFlow: Flow<AiConfig>
    fun getCurrentConfig(): AiConfig
    fun saveConfig(config: AiConfig)
}
