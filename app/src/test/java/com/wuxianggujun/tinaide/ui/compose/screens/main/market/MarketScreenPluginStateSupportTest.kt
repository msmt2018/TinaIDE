package com.wuxianggujun.tinaide.ui.compose.screens.main.market

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MarketScreenPluginStateSupportTest {

    @Test
    fun `applyInstallState should refresh marketplace install flags while preserving detail state`() {
        val updated = MarketScreenPluginStateSupport.applyInstallState(
            state = PluginState(
                selectedPluginId = "plugin.detail",
                isPluginDetailLoading = true,
            ),
            installedPlugins = setOf("plugin.a"),
            updatablePlugins = setOf("plugin.b"),
        )

        assertThat(updated.installedPlugins).containsExactly("plugin.a")
        assertThat(updated.updatablePlugins).containsExactly("plugin.b")
        assertThat(updated.selectedPluginId).isEqualTo("plugin.detail")
        assertThat(updated.isPluginDetailLoading).isTrue()
    }
}
