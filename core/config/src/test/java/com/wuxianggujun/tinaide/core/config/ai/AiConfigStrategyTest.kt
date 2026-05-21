package com.wuxianggujun.tinaide.core.config.ai

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * [AiConfigStrategy] 单元测试——覆盖迁移与开源版配置归一化两个关键决策。
 */
class AiConfigStrategyTest {

    // ==================== resolveMigration ====================

    @Test
    fun `resolveMigration without persisted mode returns CUSTOM_BYOK and requests legacy wipe`() {
        val result = AiConfigStrategy.resolveMigration(
            persistedAccessMode = null,
            legacyApiKeyPresent = true,
        )
        assertThat(result.accessMode).isEqualTo(AiAccessMode.CUSTOM_BYOK)
        assertThat(result.clearLegacyApiKey).isTrue()
    }

    @Test
    fun `resolveMigration without persisted mode and no legacy key skips wipe`() {
        val result = AiConfigStrategy.resolveMigration(
            persistedAccessMode = null,
            legacyApiKeyPresent = false,
        )
        assertThat(result.accessMode).isEqualTo(AiAccessMode.CUSTOM_BYOK)
        assertThat(result.clearLegacyApiKey).isFalse()
    }

    @Test
    fun `resolveMigration honors persisted BYOK mode`() {
        val result = AiConfigStrategy.resolveMigration(
            persistedAccessMode = AiAccessMode.CUSTOM_BYOK.name,
            legacyApiKeyPresent = true,
        )
        assertThat(result.accessMode).isEqualTo(AiAccessMode.CUSTOM_BYOK)
        assertThat(result.clearLegacyApiKey).isFalse()
    }

    @Test
    fun `resolveMigration falls back to custom BYOK on unknown mode string`() {
        val result = AiConfigStrategy.resolveMigration(
            persistedAccessMode = "FUTURE_MODE",
            legacyApiKeyPresent = false,
        )
        assertThat(result.accessMode).isEqualTo(AiAccessMode.CUSTOM_BYOK)
        assertThat(result.clearLegacyApiKey).isFalse()
    }

    // ==================== normalizeForOpenSource ====================

    // AiConfig 的所有子配置都有无依赖 Context 的默认值,此处直接 new 即可。

    private fun sampleConfig(accessMode: AiAccessMode): AiConfig =
        AiConfig(accessMode = accessMode)

    @Test
    fun `normalizeForOpenSource leaves BYOK untouched`() {
        val input = sampleConfig(AiAccessMode.CUSTOM_BYOK)
        val result = AiConfigStrategy.normalizeForOpenSource(input)
        assertThat(result).isSameInstanceAs(input)
    }

    @Test
    fun `normalizeForOpenSource leaves Gateway unchanged`() {
        val input = sampleConfig(AiAccessMode.TINA_GATEWAY)
        assertThat(AiConfigStrategy.normalizeForOpenSource(input))
            .isSameInstanceAs(input)
    }
}
