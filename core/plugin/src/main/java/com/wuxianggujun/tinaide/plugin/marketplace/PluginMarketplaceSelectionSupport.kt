package com.wuxianggujun.tinaide.plugin.marketplace

object PluginMarketplaceSelectionSupport {

    fun resolveSelectedPlugin(
        selectedPluginId: String?,
        plugins: List<PluginSummary>,
    ): PluginSummary? {
        return selectedPluginId?.let { pluginId ->
            plugins.find { plugin -> plugin.pluginId == pluginId }
        }
    }

    fun shouldClosePluginDetails(
        selectedPluginId: String?,
        selectedPlugin: PluginSummary?,
    ): Boolean {
        return selectedPluginId != null && selectedPlugin == null
    }

    fun applyInstallState(
        state: PluginMarketplaceUiState,
        installedPlugins: Set<String>,
        updatablePlugins: Set<String>,
    ): PluginMarketplaceUiState {
        return state.copy(
            installedPlugins = installedPlugins,
            updatablePlugins = updatablePlugins,
        )
    }
}
