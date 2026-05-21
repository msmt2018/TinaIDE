package com.wuxianggujun.tinaide.ui.compose.screens.settings.sections

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SettingsRootSectionSupportTest {

    @Test
    fun shouldShowDeveloperEntry_shouldRequireLocalAndServerFlags() {
        assertThat(
            SettingsRootSectionSupport.shouldShowDeveloperEntry(
                developerOptionsEnabled = false,
                serverDeveloperOptionsEnabled = false
            )
        ).isFalse()
        assertThat(
            SettingsRootSectionSupport.shouldShowDeveloperEntry(
                developerOptionsEnabled = false,
                serverDeveloperOptionsEnabled = true
            )
        ).isFalse()
        assertThat(
            SettingsRootSectionSupport.shouldShowDeveloperEntry(
                developerOptionsEnabled = true,
                serverDeveloperOptionsEnabled = false
            )
        ).isFalse()
        assertThat(
            SettingsRootSectionSupport.shouldShowDeveloperEntry(
                developerOptionsEnabled = true,
                serverDeveloperOptionsEnabled = true
            )
        ).isTrue()
    }
}
