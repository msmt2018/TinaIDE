package com.wuxianggujun.tinaide.ui.compose.screens.settings.sections

import androidx.annotation.StringRes
import com.wuxianggujun.tinaide.core.i18n.Strings
import java.io.File

internal data class EditorSettingsOptionSpec(
    val value: String,
    @param:StringRes @get:StringRes val labelRes: Int
)

internal object EditorSettingsSectionSupport {

    fun resolveCustomFontDisplayName(
        fontPath: String,
        defaultLabel: String
    ): String = if (fontPath.isEmpty()) {
        defaultLabel
    } else {
        File(fontPath).name
    }

    fun resolveEditorThemeDisplayName(
        currentTheme: String,
        themeEntries: List<String>,
        themeValues: List<String>,
        pluginThemesLabel: String
    ): String {
        val index = themeValues.indexOf(currentTheme)
        return when {
            index >= 0 -> themeEntries.getOrNull(index) ?: currentTheme
            currentTheme.startsWith("plugin:", ignoreCase = true) -> pluginThemesLabel
            else -> currentTheme
        }
    }

    fun buildEditorThemeOptions(
        themeEntries: List<String>,
        themeValues: List<String>
    ): List<Pair<String, String>> = themeValues.zip(themeEntries)

    @StringRes
    fun resolveRenderWhitespaceLabel(mode: String): Int = when (mode) {
        "boundary" -> Strings.settings_render_whitespace_boundary
        "all" -> Strings.settings_render_whitespace_all
        else -> Strings.settings_render_whitespace_none
    }

    fun buildRenderWhitespaceOptions(): List<EditorSettingsOptionSpec> = listOf(
        EditorSettingsOptionSpec("none", Strings.settings_render_whitespace_none),
        EditorSettingsOptionSpec("boundary", Strings.settings_render_whitespace_boundary),
        EditorSettingsOptionSpec("all", Strings.settings_render_whitespace_all)
    )

    fun resolveNextTabSize(currentTabSize: Int): Int = when (currentTabSize) {
        2 -> 4
        4 -> 8
        else -> 2
    }

    fun validateRainbowBracketsMaxLines(input: String): Boolean {
        val parsed = input.toIntOrNull()
        return parsed != null && parsed in 0..200000
    }

    fun resolveRainbowBracketsHintValue(input: String): Int = input.toIntOrNull() ?: 0

    fun coerceRainbowBracketsMaxLines(input: String, fallback: Int): Int = input.toIntOrNull()?.coerceIn(0, 200000) ?: fallback
}
