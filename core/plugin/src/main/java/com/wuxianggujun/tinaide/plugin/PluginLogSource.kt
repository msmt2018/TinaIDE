package com.wuxianggujun.tinaide.plugin

data class PluginLogSource(
    val id: String,
    val name: String,
)

object PluginHostLogSources {
    val PluginManager = PluginLogSource(
        id = "host.plugin-manager",
        name = "Host / Plugin Manager",
    )

    val Settings = PluginLogSource(
        id = "host.plugin-settings",
        name = "Host / Plugin Settings",
    )

    val Marketplace = PluginLogSource(
        id = "host.plugin-market",
        name = "Host / Plugin Marketplace",
    )

    val MainUi = PluginLogSource(
        id = "host.plugin-main-ui",
        name = "Host / Plugin Main UI",
    )
}
