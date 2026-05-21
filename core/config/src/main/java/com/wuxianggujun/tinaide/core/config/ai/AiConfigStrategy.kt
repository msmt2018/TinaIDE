package com.wuxianggujun.tinaide.core.config.ai

/**
 * AI 配置的"策略"纯函数集合——无 Android 依赖，便于 JVM 单元测试。
 *
 * 之所以抽出来，是因为 [AiConfigProvider] 的实现 (`AiPreferences`) 绑定
 * SharedPreferences / EncryptedSharedPreferences，难以在纯 JVM 环境下覆盖
 * 老数据迁移与开源版访问模式归一化。
 */
object AiConfigStrategy {

    /**
     * 老数据迁移决策结果。
     *
     * @param accessMode 本次加载使用的访问模式
     * @param clearLegacyApiKey 是否应当把 EncryptedSharedPreferences 里的
     *                          老 apiKey 物理清空（避免隐式推断成 BYOK）。
     */
    data class MigrationOutcome(
        val accessMode: AiAccessMode,
        val clearLegacyApiKey: Boolean,
    )

    /**
     * 迁移决策：
     * - 持久化了 `access_mode`：解析沿用（解析失败兜底 BYOK）。
     * - 没有持久化（老用户）：开源版默认使用 `CUSTOM_BYOK`，
     *   并请求清空遗留 apiKey，避免旧版单 Key 配置继续隐式生效。
     */
    fun resolveMigration(
        persistedAccessMode: String?,
        legacyApiKeyPresent: Boolean,
    ): MigrationOutcome {
        return if (persistedAccessMode == null) {
            MigrationOutcome(
                accessMode = AiAccessMode.CUSTOM_BYOK,
                clearLegacyApiKey = legacyApiKeyPresent,
            )
        } else {
            val parsed = runCatching { AiAccessMode.valueOf(persistedAccessMode) }
                .getOrDefault(AiAccessMode.CUSTOM_BYOK)
            MigrationOutcome(
                accessMode = parsed,
                clearLegacyApiKey = false,
            )
        }
    }

    /**
     * 开源版不再做 VIP 守卫。这里保留纯函数入口，方便以后集中处理
     * 备份恢复、脚本调用等非 UI 路径的配置归一化。
     */
    fun normalizeForOpenSource(config: AiConfig): AiConfig {
        return config
    }
}
