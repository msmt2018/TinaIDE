package com.wuxianggujun.tinaide.core.config.ai

import android.content.Context

/**
 * AI 设置桥接接口
 *
 * 用于隔离 `feature:settings` 对 `feature:ai` 内部实现的直接依赖。
 */
interface AiSettingsBridge {
    suspend fun loadModels(config: AiConfig): AiModelLoadResult

    fun getToolItems(context: Context): List<AiToolSettingsItem>

    fun getToolEnabledStates(): Map<String, Boolean>

    fun applyToolEnabledStates(states: Map<String, Boolean>)

    fun persistToolEnabledStates(states: Map<String, Boolean>)
}

data class AiToolSettingsItem(
    val name: String,
    val displayName: String,
    val description: String,
    val categoryLabel: String,
    val enabledByDefault: Boolean
)

sealed interface AiModelLoadResult {
    data class Success(val models: List<String>) : AiModelLoadResult
    data class Failure(
        val message: String? = null,
        val fallbackModels: List<String> = emptyList()
    ) : AiModelLoadResult
    data object ConfigurationRequired : AiModelLoadResult
}
