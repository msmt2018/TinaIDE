package com.wuxianggujun.tinaide.ui.compose.screens.settings.sections

import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.core.i18n.Strings
import org.junit.Test

class AppearanceSettingsSectionSupportTest {

    @Test
    fun resolveThemeLabel_shouldMapKnownThemesAndFallbackToDark() {
        assertThat(
            AppearanceSettingsSectionSupport.resolveThemeLabel("GRAY")
        ).isEqualTo(Strings.theme_gray)
        assertThat(
            AppearanceSettingsSectionSupport.resolveThemeLabel("LIGHT")
        ).isEqualTo(Strings.theme_light)
        assertThat(
            AppearanceSettingsSectionSupport.resolveThemeLabel("AUTO")
        ).isEqualTo(Strings.theme_auto)
        assertThat(
            AppearanceSettingsSectionSupport.resolveThemeLabel("unexpected")
        ).isEqualTo(Strings.theme_dark)
    }

    @Test
    fun buildThemeOptions_shouldExposeStableSelectionOrder() {
        assertThat(
            AppearanceSettingsSectionSupport.buildThemeOptions()
        ).containsExactly(
            AppearanceOptionSpec("DARK", Strings.theme_dark),
            AppearanceOptionSpec("LIGHT", Strings.theme_light),
            AppearanceOptionSpec("GRAY", Strings.theme_gray),
            AppearanceOptionSpec("AUTO", Strings.theme_auto)
        ).inOrder()
    }

    @Test
    fun themeChangeDecisions_shouldOnlyApplyOnActualChangesAndRecreateForGray() {
        assertThat(
            AppearanceSettingsSectionSupport.shouldApplyThemeChange("DARK", "DARK")
        ).isFalse()
        assertThat(
            AppearanceSettingsSectionSupport.shouldApplyThemeChange("DARK", "LIGHT")
        ).isTrue()

        assertThat(
            AppearanceSettingsSectionSupport.shouldRecreateForThemeChange("DARK", "LIGHT")
        ).isFalse()
        assertThat(
            AppearanceSettingsSectionSupport.shouldRecreateForThemeChange("GRAY", "LIGHT")
        ).isTrue()
        assertThat(
            AppearanceSettingsSectionSupport.shouldRecreateForThemeChange("LIGHT", "GRAY")
        ).isTrue()
        assertThat(
            AppearanceSettingsSectionSupport.shouldRecreateForThemeChange("GRAY", "GRAY")
        ).isFalse()
    }

    @Test
    fun buildDebugToolbarPositionOptions_shouldExposeAllEnumValues() {
        assertThat(
            AppearanceSettingsSectionSupport.buildDebugToolbarPositionOptions()
        ).containsExactly(
            AppearanceOptionSpec("top", Strings.debug_toolbar_top),
            AppearanceOptionSpec("bottom", Strings.debug_toolbar_bottom),
            AppearanceOptionSpec("both", Strings.debug_toolbar_both)
        ).inOrder()
    }
}
