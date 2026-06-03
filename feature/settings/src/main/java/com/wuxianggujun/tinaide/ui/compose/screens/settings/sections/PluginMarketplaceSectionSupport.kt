package com.wuxianggujun.tinaide.ui.compose.screens.settings.sections

import com.wuxianggujun.tinaide.plugin.marketplace.PluginSortType

internal data class PluginMarketplaceCategorySpec(
    val category: String?,
    val label: String,
)

internal data class PluginMarketplaceSortSpec(
    val sortType: PluginSortType,
    val label: String,
)

internal sealed interface PluginMarketplaceInstallActionSpec {
    data class Downloading(val progressPercent: Int) : PluginMarketplaceInstallActionSpec

    data object Installed : PluginMarketplaceInstallActionSpec

    data object Update : PluginMarketplaceInstallActionSpec

    data object Install : PluginMarketplaceInstallActionSpec
}

internal object PluginMarketplaceSectionSupport {

    fun buildCategorySpecs(
        allLabel: String,
        themeLabel: String,
        snippetLabel: String,
        toolLabel: String,
        languageLabel: String,
    ): List<PluginMarketplaceCategorySpec> = listOf(
        PluginMarketplaceCategorySpec(category = null, label = allLabel),
        PluginMarketplaceCategorySpec(category = "theme", label = themeLabel),
        PluginMarketplaceCategorySpec(category = "snippet", label = snippetLabel),
        PluginMarketplaceCategorySpec(category = "tool", label = toolLabel),
        PluginMarketplaceCategorySpec(category = "language", label = languageLabel),
    )

    fun buildSortSpecs(
        newestLabel: String,
        updatedLabel: String,
    ): List<PluginMarketplaceSortSpec> = listOf(
        PluginMarketplaceSortSpec(
            sortType = PluginSortType.NEWEST,
            label = newestLabel,
        ),
        PluginMarketplaceSortSpec(
            sortType = PluginSortType.UPDATED,
            label = updatedLabel,
        ),
    )

    fun shouldTriggerLoadMore(
        lastVisibleItemIndex: Int,
        totalCount: Int,
        hasMore: Boolean,
        isLoadingMore: Boolean,
    ): Boolean {
        if (totalCount <= 0 || !hasMore || isLoadingMore) return false
        return lastVisibleItemIndex >= totalCount - 3
    }

    fun resolvePluginInitial(name: String): String = name.firstOrNull()?.uppercase() ?: "P"

    fun resolveInstallActionSpec(
        isInstalled: Boolean,
        hasUpdate: Boolean,
        downloadProgress: Float?,
    ): PluginMarketplaceInstallActionSpec = when {
        downloadProgress != null -> {
            PluginMarketplaceInstallActionSpec.Downloading(
                progressPercent = resolveDownloadProgressPercent(downloadProgress)
            )
        }
        isInstalled && !hasUpdate -> PluginMarketplaceInstallActionSpec.Installed
        hasUpdate -> PluginMarketplaceInstallActionSpec.Update
        else -> PluginMarketplaceInstallActionSpec.Install
    }

    fun resolveDownloadProgressPercent(downloadProgress: Float): Int = (downloadProgress * 100).toInt().coerceIn(0, 100)
}
