package com.wuxianggujun.tinaide.core.config.ai

import android.content.Context
import androidx.annotation.StringRes
import com.wuxianggujun.tinaide.core.i18n.R

/**
 * AI 服务商枚举
 */
enum class AiProvider(
    @param:StringRes @get:StringRes val displayNameRes: Int,
    val defaultBaseUrl: String,
    val defaultModels: List<String>
) {
    OPENAI(
        displayNameRes = R.string.app_name, // "OpenAI" — no localization needed
        defaultBaseUrl = "https://api.openai.com/v1",
        defaultModels = listOf("gpt-4o", "gpt-4o-mini", "gpt-4-turbo", "gpt-3.5-turbo")
    ),
    DEEPSEEK(
        displayNameRes = R.string.app_name, // "DeepSeek" — no localization needed
        defaultBaseUrl = "https://api.deepseek.com/v1",
        defaultModels = listOf("deepseek-chat", "deepseek-coder")
    ),
    QWEN(
        displayNameRes = R.string.ai_provider_qwen,
        defaultBaseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
        defaultModels = listOf("qwen-turbo", "qwen-plus", "qwen-max")
    ),
    ZHIPU(
        displayNameRes = R.string.ai_provider_zhipu,
        defaultBaseUrl = "https://open.bigmodel.cn/api/paas/v4",
        defaultModels = listOf("glm-4", "glm-4-flash", "glm-3-turbo")
    ),
    OLLAMA(
        displayNameRes = R.string.ai_provider_ollama,
        defaultBaseUrl = "http://localhost:11434/v1",
        defaultModels = listOf("llama3", "codellama", "deepseek-coder")
    ),
    CUSTOM(
        displayNameRes = R.string.ai_provider_custom,
        defaultBaseUrl = "",
        defaultModels = emptyList()
    );

    /** 获取本地化的显示名称 */
    fun getDisplayName(context: Context): String = when (this) {
        OPENAI -> "OpenAI"
        DEEPSEEK -> "DeepSeek"
        else -> context.getString(displayNameRes)
    }

    /** 向后兼容：返回非本地化的默认名称 */
    val displayName: String
        get() = when (this) {
            OPENAI -> "OpenAI"
            DEEPSEEK -> "DeepSeek"
            QWEN -> "Tongyi Qianwen"
            ZHIPU -> "Zhipu AI"
            OLLAMA -> "Ollama (Local)"
            CUSTOM -> "Custom"
        }

    companion object {
        fun fromName(name: String): AiProvider {
            return entries.find { it.name == name } ?: DEEPSEEK
        }
    }
}
