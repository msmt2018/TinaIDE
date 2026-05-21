package com.wuxianggujun.tinaide.ui.compose.screens.main.market

internal object MarketScreenPluginStateSupport {

    fun applyInstallState(
        state: PluginState,
        installedPlugins: Set<String>,
        updatablePlugins: Set<String>,
    ): PluginState = state.copy(
        installedPlugins = installedPlugins,
        updatablePlugins = updatablePlugins,
    )
}
