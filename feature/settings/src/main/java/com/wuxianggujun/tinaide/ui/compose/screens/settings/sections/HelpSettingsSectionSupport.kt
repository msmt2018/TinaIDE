package com.wuxianggujun.tinaide.ui.compose.screens.settings.sections

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.wuxianggujun.tinaide.core.i18n.Drawables
import com.wuxianggujun.tinaide.core.i18n.Strings

internal data class HelpSettingsEntrySpec(
    @param:DrawableRes @get:DrawableRes val iconRes: Int,
    val iconBackgroundColor: Color,
    @param:StringRes @get:StringRes val titleRes: Int,
    @param:StringRes @get:StringRes val subtitleRes: Int,
    val showDivider: Boolean,
)

internal object HelpSettingsSectionSupport {
    private val helpIconColor = Color(0xFF2196F3)

    fun resolveHelpCenterEntrySpec(): HelpSettingsEntrySpec = HelpSettingsEntrySpec(
        iconRes = Drawables.ic_settings_about,
        iconBackgroundColor = helpIconColor,
        titleRes = Strings.settings_title_help,
        subtitleRes = Strings.settings_desc_help,
        showDivider = false,
    )
}
