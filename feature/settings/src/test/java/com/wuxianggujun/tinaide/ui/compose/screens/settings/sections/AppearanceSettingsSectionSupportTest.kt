package com.wuxianggujun.tinaide.ui.compose.screens.settings.sections

import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.core.config.AppTheme
import com.wuxianggujun.tinaide.core.i18n.Strings
import org.junit.Test

class AppearanceSettingsSectionSupportTest {

    @Test
    fun resolveThemeLabel_shouldMapThemeEnums() {
        assertThat(
            AppearanceSettingsSectionSupport.resolveThemeLabel(AppTheme.GRAY)
        ).isEqualTo(Strings.theme_gray)
        assertThat(
            AppearanceSettingsSectionSupport.resolveThemeLabel(AppTheme.LIGHT)
        ).isEqualTo(Strings.theme_light)
        assertThat(
            AppearanceSettingsSectionSupport.resolveThemeLabel(AppTheme.AUTO)
        ).isEqualTo(Strings.theme_auto)
        assertThat(
            AppearanceSettingsSectionSupport.resolveThemeLabel(AppTheme.SAKURA)
        ).isEqualTo(Strings.theme_sakura)
        assertThat(
            AppearanceSettingsSectionSupport.resolveThemeLabel(AppTheme.OCEAN)
        ).isEqualTo(Strings.theme_ocean)
        assertThat(
            AppearanceSettingsSectionSupport.resolveThemeLabel(AppTheme.SPRING)
        ).isEqualTo(Strings.theme_spring)
        assertThat(
            AppearanceSettingsSectionSupport.resolveThemeLabel(AppTheme.AUTUMN)
        ).isEqualTo(Strings.theme_autumn)
        assertThat(
            AppearanceSettingsSectionSupport.resolveThemeLabel(AppTheme.BLACK)
        ).isEqualTo(Strings.theme_black)
        assertThat(
            AppearanceSettingsSectionSupport.resolveThemeLabel(AppTheme.DARK)
        ).isEqualTo(Strings.theme_dark)
    }

    @Test
    fun buildThemeOptions_shouldExposeStableSelectionOrder() {
        assertThat(
            AppearanceSettingsSectionSupport.buildThemeOptions()
        ).containsExactly(
            AppearanceThemeOptionSpec(AppTheme.DARK, Strings.theme_dark),
            AppearanceThemeOptionSpec(AppTheme.LIGHT, Strings.theme_light),
            AppearanceThemeOptionSpec(AppTheme.GRAY, Strings.theme_gray),
            AppearanceThemeOptionSpec(AppTheme.SAKURA, Strings.theme_sakura),
            AppearanceThemeOptionSpec(AppTheme.OCEAN, Strings.theme_ocean),
            AppearanceThemeOptionSpec(AppTheme.SPRING, Strings.theme_spring),
            AppearanceThemeOptionSpec(AppTheme.AUTUMN, Strings.theme_autumn),
            AppearanceThemeOptionSpec(AppTheme.BLACK, Strings.theme_black),
            AppearanceThemeOptionSpec(AppTheme.AUTO, Strings.theme_auto)
        ).inOrder()
    }

    @Test
    fun themeChangeDecisions_shouldOnlyApplyOnActualChangesAndRecreateForGray() {
        assertThat(
            AppearanceSettingsSectionSupport.shouldApplyThemeChange(AppTheme.DARK, AppTheme.DARK)
        ).isFalse()
        assertThat(
            AppearanceSettingsSectionSupport.shouldApplyThemeChange(AppTheme.DARK, AppTheme.LIGHT)
        ).isTrue()

        assertThat(
            AppearanceSettingsSectionSupport.shouldRecreateForThemeChange(AppTheme.DARK, AppTheme.LIGHT)
        ).isFalse()
        assertThat(
            AppearanceSettingsSectionSupport.shouldRecreateForThemeChange(AppTheme.GRAY, AppTheme.LIGHT)
        ).isTrue()
        assertThat(
            AppearanceSettingsSectionSupport.shouldRecreateForThemeChange(AppTheme.LIGHT, AppTheme.GRAY)
        ).isTrue()
        assertThat(
            AppearanceSettingsSectionSupport.shouldRecreateForThemeChange(AppTheme.GRAY, AppTheme.GRAY)
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
