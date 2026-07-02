package com.wuxianggujun.tinaide.ui.compose.screens.settings.sections

import androidx.annotation.StringRes
import com.wuxianggujun.tinaide.core.config.AppTheme
import com.wuxianggujun.tinaide.core.config.DebugToolbarPosition
import com.wuxianggujun.tinaide.core.i18n.Strings

internal data class AppearanceOptionSpec(
    val value: String,
    @param:StringRes @get:StringRes val labelRes: Int
)

internal data class AppearanceThemeOptionSpec(
    val value: AppTheme,
    @param:StringRes @get:StringRes val labelRes: Int
)

internal object AppearanceSettingsSectionSupport {

    fun buildThemeOptions(): List<AppearanceThemeOptionSpec> = listOf(
        AppearanceThemeOptionSpec(AppTheme.DARK, Strings.theme_dark),
        AppearanceThemeOptionSpec(AppTheme.LIGHT, Strings.theme_light),
        AppearanceThemeOptionSpec(AppTheme.GRAY, Strings.theme_gray),
        AppearanceThemeOptionSpec(AppTheme.SAKURA, Strings.theme_sakura),
        AppearanceThemeOptionSpec(AppTheme.OCEAN, Strings.theme_ocean),
        AppearanceThemeOptionSpec(AppTheme.SPRING, Strings.theme_spring),
        AppearanceThemeOptionSpec(AppTheme.AUTUMN, Strings.theme_autumn),
        AppearanceThemeOptionSpec(AppTheme.BLACK, Strings.theme_black),
        AppearanceThemeOptionSpec(AppTheme.AUTO, Strings.theme_auto)
    )

    @StringRes
    fun resolveThemeLabel(theme: AppTheme): Int = when (theme) {
        AppTheme.GRAY -> Strings.theme_gray
        AppTheme.LIGHT -> Strings.theme_light
        AppTheme.SAKURA -> Strings.theme_sakura
        AppTheme.OCEAN -> Strings.theme_ocean
        AppTheme.SPRING -> Strings.theme_spring
        AppTheme.AUTUMN -> Strings.theme_autumn
        AppTheme.BLACK -> Strings.theme_black
        AppTheme.AUTO -> Strings.theme_auto
        AppTheme.DARK -> Strings.theme_dark
    }

    fun shouldApplyThemeChange(previousTheme: AppTheme, nextTheme: AppTheme): Boolean = previousTheme != nextTheme

    fun shouldRecreateForThemeChange(previousTheme: AppTheme, nextTheme: AppTheme): Boolean =
        shouldApplyThemeChange(previousTheme, nextTheme) &&
            (previousTheme == AppTheme.GRAY || nextTheme == AppTheme.GRAY)

    fun buildDebugToolbarPositionOptions(): List<AppearanceOptionSpec> = DebugToolbarPosition.entries.map { position ->
        AppearanceOptionSpec(
            value = position.value,
            labelRes = position.displayNameRes
        )
    }
}
