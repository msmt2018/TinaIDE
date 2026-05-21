package com.wuxianggujun.tinaide.ui.compose.screens.settings.sections

import androidx.annotation.StringRes
import com.wuxianggujun.tinaide.core.config.ai.AiAccessMode
import com.wuxianggujun.tinaide.core.config.ai.AiProvider
import com.wuxianggujun.tinaide.core.i18n.Strings

internal data class AiSettingsOptionSpec(
    val value: String,
    @param:StringRes @get:StringRes val labelRes: Int,
)

internal data class AiSettingsProviderConfigUpdate(
    val provider: AiProvider,
    val baseUrl: String,
    val model: String,
)

internal sealed interface AiSettingsAccessModeDecision {
    data class Save(val mode: AiAccessMode) : AiSettingsAccessModeDecision
}

internal sealed interface AiSettingsModelDialogSpec {
    data object Loading : AiSettingsModelDialogSpec

    data class Selectable(val models: List<String>) : AiSettingsModelDialogSpec

    data class ManualInput(val initialValue: String) : AiSettingsModelDialogSpec
}

internal sealed interface AiChannelInputValidation {
    data object Valid : AiChannelInputValidation
    data object NameBlank : AiChannelInputValidation
    data object BaseUrlBlank : AiChannelInputValidation
    data object BaseUrlInvalid : AiChannelInputValidation
    data object ModelBlank : AiChannelInputValidation
    data object ApiKeyBlank : AiChannelInputValidation
}

/**
 * AI 设置页当前打开的对话框。
 *
 * 把原来的 14 个 `showXxxDialog: Boolean` 局部 state 收敛为一个 sealed interface,
 * 天然排除"同时打开两个对话框"的非法状态,减少 UI state bug。
 */
internal sealed interface AiSettingsDialog {
    data object None : AiSettingsDialog
    data object AccessMode : AiSettingsDialog
    data object ChannelManagement : AiSettingsDialog
    data class ChannelEdit(val initial: com.wuxianggujun.tinaide.core.config.ai.AiChannelConfig?) : AiSettingsDialog
    data object Model : AiSettingsDialog
    data object MaxTokens : AiSettingsDialog
    data object BudgetTokens : AiSettingsDialog
    data object Temperature : AiSettingsDialog
    data object Timeout : AiSettingsDialog
    data object RetryCount : AiSettingsDialog
    data object RetryDelay : AiSettingsDialog
    data object SystemPrompt : AiSettingsDialog
    data object SummaryPrompt : AiSettingsDialog
    data object ImageDetail : AiSettingsDialog
    data object Tools : AiSettingsDialog
}

internal object AiSettingsSectionSupport {

    private const val BASE_URL_PREVIEW_LIMIT = 30
    private const val PROMPT_PREVIEW_LIMIT = 50

    fun buildAccessModeOptions(): List<AiSettingsOptionSpec> = listOf(
        AiSettingsOptionSpec(
            value = AiAccessMode.TINA_GATEWAY.name,
            labelRes = Strings.settings_ai_access_mode_cloud,
        ),
        AiSettingsOptionSpec(
            value = AiAccessMode.CUSTOM_BYOK.name,
            labelRes = Strings.settings_ai_access_mode_custom,
        ),
    )

    fun buildProviderOptions(): List<AiSettingsOptionSpec> = AiProvider.entries.map { provider ->
        AiSettingsOptionSpec(
            value = provider.name,
            labelRes = resolveProviderDisplayNameRes(provider),
        )
    }

    @StringRes
    fun resolveProviderDisplayNameRes(provider: AiProvider): Int = when (provider) {
        AiProvider.OPENAI -> Strings.settings_ai_provider_openai
        AiProvider.DEEPSEEK -> Strings.settings_ai_provider_deepseek
        AiProvider.QWEN -> Strings.settings_ai_provider_qwen
        AiProvider.ZHIPU -> Strings.settings_ai_provider_zhipu
        AiProvider.OLLAMA -> Strings.settings_ai_provider_ollama
        AiProvider.CUSTOM -> Strings.settings_ai_provider_custom
    }

    @StringRes
    fun resolveAccessModeSubtitleRes(mode: AiAccessMode): Int = when (mode) {
        AiAccessMode.TINA_GATEWAY -> Strings.settings_ai_access_mode_cloud_subtitle
        AiAccessMode.CUSTOM_BYOK -> Strings.settings_ai_access_mode_custom_subtitle
    }

    @StringRes
    fun resolveAccessModeValueRes(mode: AiAccessMode): Int = when (mode) {
        AiAccessMode.TINA_GATEWAY -> Strings.settings_ai_access_mode_cloud
        AiAccessMode.CUSTOM_BYOK -> Strings.settings_ai_access_mode_custom
    }

    @StringRes
    fun resolveImageDetailLabelRes(imageDetail: String): Int = when (imageDetail) {
        "low" -> Strings.settings_ai_image_detail_low
        "high" -> Strings.settings_ai_image_detail_high
        else -> Strings.settings_ai_image_detail_auto
    }

    fun resolveBaseUrlPreview(baseUrl: String): String = truncateForDisplay(baseUrl, BASE_URL_PREVIEW_LIMIT)

    fun resolvePromptPreview(prompt: String): String = truncateForDisplay(prompt, PROMPT_PREVIEW_LIMIT)

    fun sanitizeApiKey(apiKey: String): String = apiKey.trim()
        .replace("\n", "")
        .replace("\r", "")

    /**
     * 校验渠道新增/编辑表单输入。
     *
     * @param apiKeyRequired true 表示新增时必须填;编辑时可以传 false 以允许保留原有 key。
     */
    fun validateChannelInput(
        name: String,
        baseUrl: String,
        model: String,
        apiKey: String,
        apiKeyRequired: Boolean,
    ): AiChannelInputValidation {
        if (name.trim().isEmpty()) return AiChannelInputValidation.NameBlank
        val trimmedUrl = baseUrl.trim()
        if (trimmedUrl.isEmpty()) return AiChannelInputValidation.BaseUrlBlank
        if (!trimmedUrl.startsWith("http://", ignoreCase = true) &&
            !trimmedUrl.startsWith("https://", ignoreCase = true)
        ) {
            return AiChannelInputValidation.BaseUrlInvalid
        }
        if (model.trim().isEmpty()) return AiChannelInputValidation.ModelBlank
        if (apiKeyRequired && sanitizeApiKey(apiKey).isEmpty()) return AiChannelInputValidation.ApiKeyBlank
        return AiChannelInputValidation.Valid
    }

    fun parseMaxTokensInput(value: String, currentValue: Int): Int = parseClampedIntInput(
        value = value,
        currentValue = currentValue,
        range = 100..128000,
    )

    fun parseBudgetTokensInput(value: String, currentValue: Int): Int = parseClampedIntInput(
        value = value,
        currentValue = currentValue,
        range = 1000..100000,
    )

    fun parseTimeoutInput(value: String, currentValue: Int): Int = parseClampedIntInput(
        value = value,
        currentValue = currentValue,
        range = 10..300,
    )

    fun parseRetryDelayInput(value: String, currentValue: Int): Int = parseClampedIntInput(
        value = value,
        currentValue = currentValue,
        range = 1..300,
    )

    fun normalizeRetryCountSliderValue(value: Float): Int = value.toInt().coerceIn(1, 10)

    fun resolveAccessModeDecision(selectedValue: String): AiSettingsAccessModeDecision.Save {
        val mode = runCatching { AiAccessMode.valueOf(selectedValue) }
            .getOrDefault(AiAccessMode.CUSTOM_BYOK)
        return AiSettingsAccessModeDecision.Save(mode)
    }

    fun resolveProviderConfigUpdate(
        selectedValue: String,
        currentProvider: AiProvider,
        currentBaseUrl: String,
        currentModel: String,
    ): AiSettingsProviderConfigUpdate {
        val provider = AiProvider.entries.firstOrNull { it.name == selectedValue }
        return if (provider == null) {
            AiSettingsProviderConfigUpdate(
                provider = currentProvider,
                baseUrl = currentBaseUrl,
                model = currentModel,
            )
        } else {
            AiSettingsProviderConfigUpdate(
                provider = provider,
                baseUrl = provider.defaultBaseUrl,
                model = provider.defaultModels.firstOrNull().orEmpty(),
            )
        }
    }

    fun normalizeCustomModels(models: List<String>): List<String> = models.filter { it.isNotBlank() }.distinct()

    fun resolveCustomModelFallback(
        fallbackModels: List<String>,
        provider: AiProvider,
    ): List<String> = fallbackModels.ifEmpty { provider.defaultModels }

    fun resolveModelDialogSpec(
        accessMode: AiAccessMode,
        provider: AiProvider,
        currentModel: String,
        gatewayModelsLoading: Boolean,
        customModelsLoading: Boolean,
        gatewayModels: List<String>?,
        customModels: List<String>?,
    ): AiSettingsModelDialogSpec {
        val isGateway = accessMode == AiAccessMode.TINA_GATEWAY
        val isCustomByok = accessMode == AiAccessMode.CUSTOM_BYOK
        if ((isGateway && gatewayModelsLoading) || (isCustomByok && customModelsLoading)) {
            return AiSettingsModelDialogSpec.Loading
        }

        val models = when {
            isGateway -> gatewayModels
            isCustomByok -> customModels ?: provider.defaultModels
            else -> provider.defaultModels
        }
        return if (models.isNullOrEmpty()) {
            AiSettingsModelDialogSpec.ManualInput(currentModel)
        } else {
            AiSettingsModelDialogSpec.Selectable(models)
        }
    }

    private fun truncateForDisplay(value: String, maxLength: Int): String = if (value.length > maxLength) {
        value.take(maxLength) + "..."
    } else {
        value
    }

    private fun parseClampedIntInput(
        value: String,
        currentValue: Int,
        range: IntRange,
    ): Int = value.toIntOrNull()?.coerceIn(range) ?: currentValue
}
