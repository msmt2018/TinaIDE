package com.wuxianggujun.tinaide.plugin.marketplace

data class PluginMarketplaceInstallState(
    val installedPlugins: Set<String> = emptySet(),
    val updatablePlugins: Set<String> = emptySet(),
)

internal object PluginMarketplaceInstallStateResolver {
    fun resolve(
        plugins: List<PluginSummary>,
        installedVersions: Map<String, String>,
    ): PluginMarketplaceInstallState {
        val installed = mutableSetOf<String>()
        val updatable = mutableSetOf<String>()

        plugins.forEach { plugin ->
            val installedVersion = installedVersions[plugin.pluginId] ?: return@forEach
            installed += plugin.pluginId
            val latestVersion = plugin.latestVersion ?: return@forEach
            if (compareVersions(latestVersion, installedVersion) > 0) {
                updatable += plugin.pluginId
            }
        }

        return PluginMarketplaceInstallState(
            installedPlugins = installed,
            updatablePlugins = updatable,
        )
    }

    private fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(parts1.size, parts2.size)

        for (index in 0 until maxLen) {
            val part1 = parts1.getOrElse(index) { 0 }
            val part2 = parts2.getOrElse(index) { 0 }
            if (part1 != part2) return part1.compareTo(part2)
        }
        return 0
    }
}
