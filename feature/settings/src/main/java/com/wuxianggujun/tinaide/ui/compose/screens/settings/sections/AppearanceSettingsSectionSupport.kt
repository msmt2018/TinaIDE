package com.wuxianggujun.tinaide.ui.compose.screens.settings.sections

import androidx.annotation.StringRes
import com.wuxianggujun.tinaide.core.config.DebugToolbarPosition
import com.wuxianggujun.tinaide.core.i18n.Strings

internal data class AppearanceOptionSpec(
    val value: String,
    @param:StringRes @get:StringRes val labelRes: Int
)

internal object AppearanceSettingsSectionSupport {

    fun buildThemeOptions(): List<AppearanceOptionSpec> = listOf(
        AppearanceOptionSpec("DARK", Strings.theme_dark),
        AppearanceOptionSpec("LIGHT", Strings.theme_light),
        AppearanceOptionSpec("GRAY", Strings.theme_gray),
        AppearanceOptionSpec("AUTO", Strings.theme_auto)
    )

    @StringRes
    fun resolveThemeLabel(theme: String): Int = when (theme) {
        "GRAY" -> Strings.theme_gray
        "LIGHT" -> Strings.theme_light
        "AUTO" -> Strings.theme_auto
        else -> Strings.theme_dark
    }

    fun shouldApplyThemeChange(previousTheme: String, nextTheme: String): Boolean = previousTheme != nextTheme

    fun shouldRecreateForThemeChange(previousTheme: String, nextTheme: String): Boolean = shouldApplyThemeChange(previousTheme, nextTheme) &&
        (previousTheme == "GRAY" || nextTheme == "GRAY")

    fun buildDebugToolbarPositionOptions(): List<AppearanceOptionSpec> = DebugToolbarPosition.entries.map { position ->
        AppearanceOptionSpec(
            value = position.value,
            labelRes = position.displayNameRes
        )
    }
}
