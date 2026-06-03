package com.wuxianggujun.tinaide.ui.compose.screens.settings.sections

import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.plugin.marketplace.PluginSortType
import org.junit.Test

class PluginMarketplaceSectionSupportTest {

    @Test
    fun categoryAndSortSpecs_shouldKeepStableOrder() {
        assertThat(
            PluginMarketplaceSectionSupport.buildCategorySpecs(
                allLabel = "All",
                themeLabel = "Themes",
                snippetLabel = "Snippets",
                toolLabel = "Tools",
                languageLabel = "Languages",
            )
        ).containsExactly(
            PluginMarketplaceCategorySpec(category = null, label = "All"),
            PluginMarketplaceCategorySpec(category = "theme", label = "Themes"),
            PluginMarketplaceCategorySpec(category = "snippet", label = "Snippets"),
            PluginMarketplaceCategorySpec(category = "tool", label = "Tools"),
            PluginMarketplaceCategorySpec(category = "language", label = "Languages"),
        ).inOrder()

        assertThat(
            PluginMarketplaceSectionSupport.buildSortSpecs(
                newestLabel = "Newest",
                updatedLabel = "Updated",
            )
        ).containsExactly(
            PluginMarketplaceSortSpec(
                sortType = PluginSortType.NEWEST,
                label = "Newest",
            ),
            PluginMarketplaceSortSpec(
                sortType = PluginSortType.UPDATED,
                label = "Updated",
            ),
        ).inOrder()
    }

    @Test
    fun loadMoreAndFormattingHelpers_shouldBehaveDeterministically() {
        assertThat(
            PluginMarketplaceSectionSupport.shouldTriggerLoadMore(
                lastVisibleItemIndex = 7,
                totalCount = 10,
                hasMore = true,
                isLoadingMore = false,
            )
        ).isTrue()
        assertThat(
            PluginMarketplaceSectionSupport.shouldTriggerLoadMore(
                lastVisibleItemIndex = 3,
                totalCount = 10,
                hasMore = true,
                isLoadingMore = false,
            )
        ).isFalse()
        assertThat(
            PluginMarketplaceSectionSupport.shouldTriggerLoadMore(
                lastVisibleItemIndex = 9,
                totalCount = 10,
                hasMore = false,
                isLoadingMore = false,
            )
        ).isFalse()

        assertThat(
            PluginMarketplaceSectionSupport.resolvePluginInitial("Marketplace")
        ).isEqualTo("M")
        assertThat(
            PluginMarketplaceSectionSupport.resolvePluginInitial("")
        ).isEqualTo("P")
    }

    @Test
    fun installActionSpec_shouldReflectCurrentInstallState() {
        assertThat(
            PluginMarketplaceSectionSupport.resolveInstallActionSpec(
                isInstalled = false,
                hasUpdate = false,
                downloadProgress = null,
            )
        ).isEqualTo(PluginMarketplaceInstallActionSpec.Install)
        assertThat(
            PluginMarketplaceSectionSupport.resolveInstallActionSpec(
                isInstalled = true,
                hasUpdate = false,
                downloadProgress = null,
            )
        ).isEqualTo(PluginMarketplaceInstallActionSpec.Installed)
        assertThat(
            PluginMarketplaceSectionSupport.resolveInstallActionSpec(
                isInstalled = true,
                hasUpdate = true,
                downloadProgress = null,
            )
        ).isEqualTo(PluginMarketplaceInstallActionSpec.Update)
        assertThat(
            PluginMarketplaceSectionSupport.resolveInstallActionSpec(
                isInstalled = false,
                hasUpdate = false,
                downloadProgress = 0.456f,
            )
        ).isEqualTo(
            PluginMarketplaceInstallActionSpec.Downloading(progressPercent = 45)
        )
        assertThat(
            PluginMarketplaceSectionSupport.resolveDownloadProgressPercent(1.4f)
        ).isEqualTo(100)
        assertThat(
            PluginMarketplaceSectionSupport.resolveDownloadProgressPercent(-0.2f)
        ).isEqualTo(0)
    }
}
