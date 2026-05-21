package com.wuxianggujun.tinaide.ui.compose.screens.testing

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import com.wuxianggujun.tinaide.core.i18n.Strings

internal data class DevEditorBackedTestEntry(
    val registryId: String,
    val workspaceKey: String,
    @param:StringRes @get:StringRes val titleRes: Int,
    @param:StringRes @get:StringRes val descriptionRes: Int,
    val activeFixtureIndex: Int = 0,
    val fixturesProvider: () -> List<DevEditorFixture>,
    val registryContent: @Composable (onNavigateBack: () -> Unit) -> Unit
)

internal object DevEditorTestCatalog {
    val themePreview = DevEditorBackedTestEntry(
        registryId = DevTestIds.ThemePreview,
        workspaceKey = "theme-preview",
        titleRes = Strings.dev_options_theme_preview_test,
        descriptionRes = Strings.dev_options_theme_preview_test_desc,
        activeFixtureIndex = 0,
        fixturesProvider = DevEditorTestSamples::themePreviewFixtures,
        registryContent = { onBack -> ThemePreviewTestScreen(onNavigateBack = onBack) }
    )

    val editorScroll = DevEditorBackedTestEntry(
        registryId = DevTestIds.EditorScroll,
        workspaceKey = "editor-scroll",
        titleRes = Strings.dev_options_editor_scroll_test,
        descriptionRes = Strings.dev_options_editor_scroll_test_desc,
        activeFixtureIndex = 0,
        fixturesProvider = DevEditorTestSamples::editorScrollFixture,
        registryContent = { onBack -> EditorScrollTestScreen(onNavigateBack = onBack) }
    )

    val cppScrollStress = DevEditorBackedTestEntry(
        registryId = DevTestIds.CppScrollStress,
        workspaceKey = "cpp-scroll-stress",
        titleRes = Strings.dev_options_cpp_scroll_stress_test,
        descriptionRes = Strings.dev_options_cpp_scroll_stress_test_desc,
        activeFixtureIndex = 0,
        fixturesProvider = { listOf(DevEditorTestSamples.cppScrollStressFixture()) },
        registryContent = { onBack -> CppScrollStressTestScreen(onNavigateBack = onBack) }
    )

    val treeSitterSampleOptions: List<TreeSitterSampleOption>
        get() = DevEditorTestSamples.treeSitterSamples()

    val treeSitter = DevEditorBackedTestEntry(
        registryId = DevTestIds.TreeSitter,
        workspaceKey = "tree-sitter",
        titleRes = Strings.dev_options_tree_sitter_test,
        descriptionRes = Strings.dev_options_tree_sitter_test_desc,
        activeFixtureIndex = 0,
        fixturesProvider = {
            listOf(treeSitterSampleOptions.first().fixture)
        },
        registryContent = { onBack -> TreeSitterTestScreen(onNavigateBack = onBack) }
    )

    val clangd = DevEditorBackedTestEntry(
        registryId = DevTestIds.Clangd,
        workspaceKey = "clangd",
        titleRes = Strings.dev_options_clangd_test,
        descriptionRes = Strings.dev_options_clangd_test_desc,
        activeFixtureIndex = 0,
        fixturesProvider = DevEditorTestSamples::clangdFixtures,
        registryContent = { onBack -> ClangdTestScreen(onNavigateBack = onBack) }
    )

    val editorBackedEntries: List<DevEditorBackedTestEntry> = listOf(
        themePreview,
        treeSitter,
        editorScroll,
        cppScrollStress,
        clangd
    )

    fun toRegistryItems(): List<DevTestItem> = editorBackedEntries.map { entry ->
        DevTestItem(
            id = entry.registryId,
            titleRes = entry.titleRes,
            descriptionRes = entry.descriptionRes,
            content = entry.registryContent
        )
    }
}
