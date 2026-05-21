package com.wuxianggujun.tinaide.ui.compose.screens.settings.sections

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.core.i18n.Drawables
import com.wuxianggujun.tinaide.core.i18n.Strings
import org.junit.Test

class HelpSettingsSectionSupportTest {

    @Test
    fun resolveHelpCenterEntrySpec_shouldExposeStableMetadata() {
        assertThat(HelpSettingsSectionSupport.resolveHelpCenterEntrySpec()).isEqualTo(
            HelpSettingsEntrySpec(
                iconRes = Drawables.ic_settings_about,
                iconBackgroundColor = Color(0xFF2196F3),
                titleRes = Strings.settings_title_help,
                subtitleRes = Strings.settings_desc_help,
                showDivider = false,
            )
        )
    }
}
