package com.wuxianggujun.tinaide.ui.compose.screens.settings.sections

import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.plugin.InstalledPlugin
import com.wuxianggujun.tinaide.plugin.PluginContributions
import com.wuxianggujun.tinaide.plugin.PluginDiagnosticCategory
import com.wuxianggujun.tinaide.plugin.PluginDiagnosticEntry
import com.wuxianggujun.tinaide.plugin.PluginDiagnosticIssue
import com.wuxianggujun.tinaide.plugin.PluginDiagnosticSeverity
import com.wuxianggujun.tinaide.plugin.PluginDiagnosticSource
import com.wuxianggujun.tinaide.plugin.PluginDiagnosticsReport
import com.wuxianggujun.tinaide.plugin.PluginDiagnosticsSnapshot
import com.wuxianggujun.tinaide.plugin.PluginLogLevel
import com.wuxianggujun.tinaide.plugin.PluginManifest
import com.wuxianggujun.tinaide.plugin.PluginMenuItem
import com.wuxianggujun.tinaide.plugin.PluginMenus
import com.wuxianggujun.tinaide.plugin.ThemeConfig
import com.wuxianggujun.tinaide.plugin.lsp.LspPluginInfo
import com.wuxianggujun.tinaide.plugin.lsp.LspPluginInstallState
import com.wuxianggujun.tinaide.plugin.lsp.LspToolchainConfig
import com.wuxianggujun.tinaide.plugin.lsp.ToolchainInstallState
import java.io.File
import org.junit.Test

class PluginsSettingsSectionSupportTest {

    @Test
    fun toggleAndThemeHelpers_shouldStayDeterministic() {
        assertThat(
            PluginsSettingsSectionSupport.toggleSelectedPlugin(
                selectedIds = setOf("a"),
                pluginId = "b",
            )
        ).containsExactly("a", "b")
        assertThat(
            PluginsSettingsSectionSupport.toggleSelectedPlugin(
                selectedIds = setOf("a", "b"),
                pluginId = "b",
            )
        ).containsExactly("a")

        val plugin = installedPlugin(
            id = "demo.plugin",
            name = "Demo Plugin",
            contributions = PluginContributions(
                themes = listOf("themes/dark.json", "themes\\light.json", "themes/missing.json"),
            ),
        )
        val themesIndex = mapOf(
            "plugin:demo.plugin/themes/dark.json" to ThemeConfig(
                name = "Dark Theme",
                colors = emptyMap(),
            ),
            "plugin:demo.plugin/themes/light.json" to ThemeConfig(
                name = "Light Theme",
                colors = emptyMap(),
            ),
        )

        assertThat(
            PluginsSettingsSectionSupport.buildPluginThemeIds(
                pluginId = plugin.manifest.id,
                relativePaths = plugin.manifest.contributions?.themes.orEmpty(),
                themesIndex = themesIndex,
            )
        ).containsExactly(
            "plugin:demo.plugin/themes/dark.json",
            "plugin:demo.plugin/themes/light.json",
        ).inOrder()
        assertThat(
            PluginsSettingsSectionSupport.buildPluginThemeOptions(
                plugin = plugin,
                themesIndex = themesIndex,
            )
        ).containsExactly(
            "plugin:demo.plugin/themes/dark.json" to "Dark Theme",
            "plugin:demo.plugin/themes/light.json" to "Light Theme",
        ).inOrder()
        assertThat(
            PluginsSettingsSectionSupport.resolvePluginInitial("Demo Plugin")
        ).isEqualTo("D")
        assertThat(
            PluginsSettingsSectionSupport.resolvePluginInitial("")
        ).isEqualTo("P")
    }

    @Test
    fun batchUninstallSpec_shouldExcludeBundledPluginsFromExecutionList() {
        val bundled = installedPlugin(
            id = "bundled.plugin",
            name = "Bundled Plugin",
            isBundled = true,
        )
        val custom = installedPlugin(
            id = "custom.plugin",
            name = "Custom Plugin",
            isBundled = false,
        )

        assertThat(
            PluginsSettingsSectionSupport.resolveBatchUninstallSpec(
                installedPlugins = listOf(bundled),
                selectedIds = setOf("bundled.plugin"),
            )
        ).isEqualTo(PluginsBatchUninstallSpec.BundledOnly)

        assertThat(
            PluginsSettingsSectionSupport.resolveBatchUninstallSpec(
                installedPlugins = listOf(bundled, custom),
                selectedIds = setOf("bundled.plugin", "custom.plugin"),
            )
        ).isEqualTo(
            PluginsBatchUninstallSpec.Confirm(
                pluginIds = listOf("custom.plugin"),
                pluginNames = "• Custom Plugin",
            )
        )
    }

    @Test
    fun installFileAndLspStatusHelpers_shouldNormalizeInputs() {
        assertThat(
            PluginsSettingsSectionSupport.resolveInstallSourceFileName(
                "content://plugins/foo/bar/demo.tinaplug"
            )
        ).isEqualTo("demo.tinaplug")
        assertThat(
            PluginsSettingsSectionSupport.resolveInstallSourceFileName(
                "C:\\Users\\demo\\plugin package.tinaplug"
            )
        ).isEqualTo("plugin_package.tinaplug")
        assertThat(
            PluginsSettingsSectionSupport.resolveInstallSourceFileName(
                "C:/Users/demo/plugin:package.tinaplug"
            )
        ).isEqualTo("plugin_package.tinaplug")
        assertThat(
            PluginsSettingsSectionSupport.resolveInstallSourceFileName(" ")
        ).isEqualTo("plugin.tinaplug")
        assertThat(
            PluginsSettingsSectionSupport.resolveInstallSourceFileName(null)
        ).isEqualTo("plugin.tinaplug")
        assertThat(
            PluginsSettingsSectionSupport.buildTempInstallFileName(
                timestampMillis = 123L,
                fileName = "demo.tinaplug",
            )
        ).isEqualTo("install_123_demo.tinaplug")
        assertThat(
            PluginsSettingsSectionSupport.resolveLspStatusLabelRes(true)
        ).isEqualTo(Strings.lsp_plugin_status_ready)
        assertThat(
            PluginsSettingsSectionSupport.resolveLspStatusLabelRes(false)
        ).isEqualTo(Strings.lsp_plugin_status_not_ready)
        assertThat(
            PluginsSettingsSectionSupport.resolvePluginDiagnosticSeverityLabelRes(
                PluginDiagnosticSeverity.ERROR
            )
        ).isEqualTo(Strings.diagnostic_error)
        assertThat(
            PluginsSettingsSectionSupport.resolvePluginDiagnosticCategoryLabelRes(
                PluginDiagnosticCategory.RUNTIME
            )
        ).isEqualTo(Strings.plugins_diagnostics_category_runtime)
        assertThat(
            PluginsSettingsSectionSupport.resolvePluginDiagnosticCategoryLabelRes(
                PluginDiagnosticCategory.LSP
            )
        ).isEqualTo(Strings.plugins_diagnostics_category_lsp)
        assertThat(
            PluginsSettingsSectionSupport.resolvePluginDiagnosticSourceLabelRes(
                PluginDiagnosticSource.LOAD
            )
        ).isEqualTo(Strings.plugins_diagnostics_source_load)
        assertThat(
            PluginsSettingsSectionSupport.resolvePluginDiagnosticsFilterLabelRes(
                PluginDiagnosticsFilter.LSP
            )
        ).isEqualTo(Strings.plugins_diagnostics_filter_lsp)
        assertThat(
            PluginsSettingsSectionSupport.resolvePluginDiagnosticSourceFilterLabelRes(
                PluginDiagnosticSourceFilter.ALL
            )
        ).isEqualTo(Strings.filter_all)
        assertThat(
            PluginsSettingsSectionSupport.resolvePluginPreflightCategoryGuideRes(
                PluginDiagnosticCategory.MANIFEST
            )
        ).isEqualTo(Strings.plugins_preflight_guide_manifest)
        assertThat(
            PluginsSettingsSectionSupport.resolvePluginPreflightCategoryGuideRes(
                PluginDiagnosticCategory.PERMISSIONS
            )
        ).isEqualTo(Strings.plugins_preflight_guide_permissions)
        assertThat(
            PluginsSettingsSectionSupport.resolvePluginPreflightCategoryGuideRes(
                PluginDiagnosticCategory.CONTRIBUTIONS
            )
        ).isEqualTo(Strings.plugins_preflight_guide_contributions)
        assertThat(
            PluginsSettingsSectionSupport.resolvePluginPreflightCategoryGuideRes(
                PluginDiagnosticCategory.COMPATIBILITY
            )
        ).isEqualTo(Strings.plugins_preflight_guide_compatibility)
        assertThat(
            PluginsSettingsSectionSupport.resolvePluginPreflightCategoryGuideRes(
                PluginDiagnosticCategory.RUNTIME
            )
        ).isEqualTo(Strings.plugins_preflight_guide_runtime)
        assertThat(
            PluginsSettingsSectionSupport.resolvePluginPreflightCategoryGuideRes(
                PluginDiagnosticCategory.LSP
            )
        ).isEqualTo(Strings.plugins_preflight_guide_lsp)
    }

    @Test
    fun lspRuntimeDiagnostics_shouldExposeMissingFailedAndLastError() {
        val lspPlugin = LspPluginInfo(
            pluginId = "tinaide.lsp.python",
            pluginName = "Python LSP",
            pluginVersion = "1.0.0",
            directory = File("plugins/python-lsp"),
            serverConfigs = emptyList(),
            toolchainConfigs = listOf(
                LspToolchainConfig(
                    id = "python",
                    name = "Python",
                    type = "system",
                    required = true,
                ),
                LspToolchainConfig(
                    id = "pyright",
                    name = "Pyright",
                    type = "npm",
                    required = true,
                ),
                LspToolchainConfig(
                    id = "ruff",
                    name = "Ruff",
                    type = "system",
                    required = false,
                ),
            ),
            activationEvents = emptyList(),
        )
        val diagnosticEntries = PluginsSettingsSectionSupport.buildLspRuntimeEntriesByPluginId(
            lspPlugins = listOf(lspPlugin),
            installStates = mapOf(
                lspPlugin.pluginId to LspPluginInstallState(
                    pluginId = lspPlugin.pluginId,
                    toolchainStates = mapOf(
                        "python" to ToolchainInstallState.FAILED,
                        "pyright" to ToolchainInstallState.NOT_INSTALLED,
                        "ruff" to ToolchainInstallState.NOT_INSTALLED,
                    ),
                    serverReady = false,
                    lastError = "pyright-langserver missing",
                )
            ),
            diagnosticText = LspRuntimeDiagnosticText(
                missingRequiredToolchainsTemplate = "Missing: %1\$s",
                failedRequiredToolchainsTemplate = "Failed: %1\$s",
                lastErrorTemplate = "Last: %1\$s",
                installFixHint = "Install dependencies",
                repairFixHint = "Repair dependencies",
            ),
        ).getValue(lspPlugin.pluginId)

        assertThat(diagnosticEntries.map { entry -> entry.issue.category })
            .containsExactly(
                PluginDiagnosticCategory.LSP,
                PluginDiagnosticCategory.LSP,
                PluginDiagnosticCategory.LSP,
            )
        assertThat(diagnosticEntries.map { entry -> entry.issue.message }).containsExactly(
            "Missing: Pyright",
            "Failed: Python",
            "Last: pyright-langserver missing",
        ).inOrder()
        assertThat(diagnosticEntries.first().issue.severity).isEqualTo(PluginDiagnosticSeverity.WARNING)
        assertThat(diagnosticEntries.drop(1).map { entry -> entry.issue.severity }).containsExactly(
            PluginDiagnosticSeverity.ERROR,
            PluginDiagnosticSeverity.ERROR,
        )
    }

    @Test
    fun contributionSummary_shouldCountThemeAndMenuEntries() {
        val manifest = PluginManifest(
            id = "demo.plugin",
            name = "Demo Plugin",
            version = "1.0.0",
            contributions = PluginContributions(
                themes = listOf("dark.json", "light.json"),
                menus = PluginMenus(
                    fileTreeContext = listOf(
                        PluginMenuItem(command = "a"),
                        PluginMenuItem(command = "b"),
                    ),
                    editorContext = listOf(
                        PluginMenuItem(command = "c"),
                    ),
                ),
            ),
        )

        assertThat(
            PluginsSettingsSectionSupport.resolveContributionSummary(manifest)
        ).isEqualTo(
            PluginsContributionSummary(
                themeCount = 2,
                fileTreeMenuCount = 2,
                editorContextMenuCount = 1,
            )
        )
    }

    @Test
    fun detailSelectionHelpers_shouldCloseWhenInstalledPluginDisappears() {
        val plugin = installedPlugin(
            id = "demo.plugin",
            name = "Demo Plugin",
        )

        assertThat(
            PluginsSettingsSectionSupport.resolveDetailPlugin(
                selectedPluginId = "demo.plugin",
                installedPlugins = listOf(plugin),
            )
        ).isEqualTo(plugin)
        assertThat(
            PluginsSettingsSectionSupport.shouldClosePluginDetails(
                selectedPluginId = "demo.plugin",
                detailPlugin = plugin,
            )
        ).isFalse()
        assertThat(
            PluginsSettingsSectionSupport.shouldClosePluginDetails(
                selectedPluginId = "missing.plugin",
                detailPlugin = null,
            )
        ).isTrue()
    }

    @Test
    fun diagnosticsSummary_shouldReadFromUnifiedReport() {
        val report = PluginDiagnosticsReport(
            pluginId = "demo.plugin",
            pluginName = "Demo Plugin",
            isInstalled = true,
            entries = listOf(
                PluginDiagnosticEntry(
                    source = PluginDiagnosticSource.RUNTIME,
                    issue = PluginDiagnosticIssue(
                        severity = PluginDiagnosticSeverity.ERROR,
                        category = PluginDiagnosticCategory.RUNTIME,
                        message = "Runtime crash"
                    )
                ),
                PluginDiagnosticEntry(
                    source = PluginDiagnosticSource.HEALTH,
                    issue = PluginDiagnosticIssue(
                        severity = PluginDiagnosticSeverity.WARNING,
                        category = PluginDiagnosticCategory.COMPATIBILITY,
                        message = "Compatibility warning"
                    )
                ),
            )
        )

        val summary = PluginsSettingsSectionSupport.resolvePluginDiagnosticsSummary(report)

        assertThat(summary.totalCount).isEqualTo(2)
        assertThat(summary.errorCount).isEqualTo(1)
        assertThat(summary.warningCount).isEqualTo(1)
        assertThat(summary.highestSeverity).isEqualTo(PluginDiagnosticSeverity.ERROR)
        assertThat(PluginsSettingsSectionSupport.hasLspDiagnostics(report)).isFalse()

        val lspReport = report.copy(
            entries = report.entries + PluginDiagnosticEntry(
                source = PluginDiagnosticSource.RUNTIME,
                issue = PluginDiagnosticIssue(
                    severity = PluginDiagnosticSeverity.ERROR,
                    category = PluginDiagnosticCategory.LSP,
                    message = "LSP dependency failed",
                ),
            )
        )
        assertThat(PluginsSettingsSectionSupport.hasLspDiagnostics(lspReport)).isTrue()
        assertThat(PluginsSettingsSectionSupport.resolveLspDiagnosticSeverity(lspReport))
            .isEqualTo(PluginDiagnosticSeverity.ERROR)
    }

    @Test
    fun preflightDiagnosticGroups_shouldGroupVisibleErrorsAndWarningsByCategory() {
        val manifestWarning = PluginDiagnosticEntry(
            source = PluginDiagnosticSource.HEALTH,
            issue = PluginDiagnosticIssue(
                severity = PluginDiagnosticSeverity.WARNING,
                category = PluginDiagnosticCategory.MANIFEST,
                message = "Manifest warning",
            )
        )
        val permissionError = PluginDiagnosticEntry(
            source = PluginDiagnosticSource.HEALTH,
            issue = PluginDiagnosticIssue(
                severity = PluginDiagnosticSeverity.ERROR,
                category = PluginDiagnosticCategory.PERMISSIONS,
                message = "Permission error",
            )
        )
        val permissionWarning = PluginDiagnosticEntry(
            source = PluginDiagnosticSource.HEALTH,
            issue = PluginDiagnosticIssue(
                severity = PluginDiagnosticSeverity.WARNING,
                category = PluginDiagnosticCategory.PERMISSIONS,
                message = "Permission warning",
            )
        )
        val runtimeInfo = PluginDiagnosticEntry(
            source = PluginDiagnosticSource.RUNTIME,
            issue = PluginDiagnosticIssue(
                severity = PluginDiagnosticSeverity.INFO,
                category = PluginDiagnosticCategory.RUNTIME,
                message = "Runtime info",
            )
        )
        val report = PluginDiagnosticsReport(
            pluginId = "demo.plugin",
            pluginName = "Demo Plugin",
            isInstalled = false,
            entries = listOf(manifestWarning, permissionWarning, runtimeInfo, permissionError),
        )

        val groups = PluginsSettingsSectionSupport.resolvePluginPreflightDiagnosticGroups(report)

        assertThat(groups.map { group -> group.category }).containsExactly(
            PluginDiagnosticCategory.PERMISSIONS,
            PluginDiagnosticCategory.MANIFEST,
        ).inOrder()
        assertThat(groups.first().errorCount).isEqualTo(1)
        assertThat(groups.first().warningCount).isEqualTo(1)
        assertThat(groups.first().entries).containsExactly(
            permissionError,
            permissionWarning,
        ).inOrder()
        assertThat(groups.last().errorCount).isEqualTo(0)
        assertThat(groups.last().warningCount).isEqualTo(1)
        assertThat(groups.flatMap { group -> group.entries }).doesNotContain(runtimeInfo)
    }

    @Test
    fun diagnosticsOverviewSummary_shouldCountInstalledErrorWarningAndLoadReports() {
        val installedReports = mapOf(
            "error.plugin" to PluginDiagnosticsReport(
                pluginId = "error.plugin",
                pluginName = "Error Plugin",
                isInstalled = true,
                entries = listOf(
                    PluginDiagnosticEntry(
                        source = PluginDiagnosticSource.RUNTIME,
                        issue = PluginDiagnosticIssue(
                            severity = PluginDiagnosticSeverity.ERROR,
                            category = PluginDiagnosticCategory.RUNTIME,
                            message = "Runtime crash"
                        )
                    )
                ),
            ),
            "warning.plugin" to PluginDiagnosticsReport(
                pluginId = "warning.plugin",
                pluginName = "Warning Plugin",
                isInstalled = true,
                entries = listOf(
                    PluginDiagnosticEntry(
                        source = PluginDiagnosticSource.HEALTH,
                        issue = PluginDiagnosticIssue(
                            severity = PluginDiagnosticSeverity.WARNING,
                            category = PluginDiagnosticCategory.COMPATIBILITY,
                            message = "Compatibility warning"
                        )
                    )
                ),
            ),
            "lsp.plugin" to PluginDiagnosticsReport(
                pluginId = "lsp.plugin",
                pluginName = "LSP Plugin",
                isInstalled = true,
                entries = listOf(
                    PluginDiagnosticEntry(
                        source = PluginDiagnosticSource.RUNTIME,
                        issue = PluginDiagnosticIssue(
                            severity = PluginDiagnosticSeverity.WARNING,
                            category = PluginDiagnosticCategory.LSP,
                            message = "LSP toolchain missing"
                        )
                    )
                ),
            ),
            "clean.plugin" to PluginDiagnosticsReport(
                pluginId = "clean.plugin",
                pluginName = "Clean Plugin",
                isInstalled = true,
                entries = emptyList()
            ),
        )
        val loadReports = listOf(
            PluginDiagnosticsReport(
                pluginId = null,
                pluginName = "Broken Plugin",
                directoryName = "broken-plugin",
                isInstalled = false,
                entries = listOf(
                    PluginDiagnosticEntry(
                        source = PluginDiagnosticSource.LOAD,
                        issue = PluginDiagnosticIssue(
                            severity = PluginDiagnosticSeverity.ERROR,
                            category = PluginDiagnosticCategory.MANIFEST,
                            message = "manifest missing"
                        )
                    )
                )
            )
        )

        val summary = PluginsSettingsSectionSupport.resolvePluginDiagnosticsOverviewSummary(
            PluginDiagnosticsSnapshot(
                installedReports = installedReports,
                loadReports = loadReports,
            )
        )

        assertThat(summary.installedCount).isEqualTo(4)
        assertThat(summary.issuePluginCount).isEqualTo(3)
        assertThat(summary.errorPluginCount).isEqualTo(1)
        assertThat(summary.warningPluginCount).isEqualTo(2)
        assertThat(summary.lspIssuePluginCount).isEqualTo(1)
        assertThat(summary.notLoadedCount).isEqualTo(1)

        assertThat(
            PluginsSettingsSectionSupport.resolvePluginDiagnosticsOverviewSummary(
                installedReports = listOf(installedReports.getValue("error.plugin")),
                loadReports = emptyList(),
            )
        ).isEqualTo(
            PluginDiagnosticsOverviewSummary(
                installedCount = 1,
                issuePluginCount = 1,
                errorPluginCount = 1,
                warningPluginCount = 0,
                lspIssuePluginCount = 0,
                notLoadedCount = 0,
            )
        )
    }

    @Test
    fun diagnosticsFilter_shouldToggleAndFilterInstalledAndLoadReports() {
        val errorPlugin = installedPlugin(
            id = "error.plugin",
            name = "Error Plugin",
        )
        val warningPlugin = installedPlugin(
            id = "warning.plugin",
            name = "Warning Plugin",
        )
        val lspPlugin = installedPlugin(
            id = "lsp.plugin",
            name = "LSP Plugin",
        )
        val cleanPlugin = installedPlugin(
            id = "clean.plugin",
            name = "Clean Plugin",
        )
        val diagnosticPlugins = listOf(errorPlugin, warningPlugin, lspPlugin, cleanPlugin)
        val snapshot = PluginDiagnosticsSnapshot(
            installedReports = mapOf(
                "error.plugin" to PluginDiagnosticsReport(
                    pluginId = "error.plugin",
                    pluginName = "Error Plugin",
                    isInstalled = true,
                    entries = listOf(
                        PluginDiagnosticEntry(
                            source = PluginDiagnosticSource.RUNTIME,
                            issue = PluginDiagnosticIssue(
                                severity = PluginDiagnosticSeverity.ERROR,
                                category = PluginDiagnosticCategory.RUNTIME,
                                message = "Runtime crash"
                            )
                        )
                    )
                ),
                "warning.plugin" to PluginDiagnosticsReport(
                    pluginId = "warning.plugin",
                    pluginName = "Warning Plugin",
                    isInstalled = true,
                    entries = listOf(
                        PluginDiagnosticEntry(
                            source = PluginDiagnosticSource.HEALTH,
                            issue = PluginDiagnosticIssue(
                                severity = PluginDiagnosticSeverity.WARNING,
                                category = PluginDiagnosticCategory.COMPATIBILITY,
                                message = "Compatibility warning"
                            )
                        )
                    )
                ),
                "lsp.plugin" to PluginDiagnosticsReport(
                    pluginId = "lsp.plugin",
                    pluginName = "LSP Plugin",
                    isInstalled = true,
                    entries = listOf(
                        PluginDiagnosticEntry(
                            source = PluginDiagnosticSource.RUNTIME,
                            issue = PluginDiagnosticIssue(
                                severity = PluginDiagnosticSeverity.WARNING,
                                category = PluginDiagnosticCategory.LSP,
                                message = "LSP toolchain missing"
                            )
                        )
                    )
                ),
                "clean.plugin" to PluginDiagnosticsReport(
                    pluginId = "clean.plugin",
                    pluginName = "Clean Plugin",
                    isInstalled = true,
                    entries = emptyList()
                ),
            ),
            loadReports = listOf(
                PluginDiagnosticsReport(
                    pluginId = null,
                    pluginName = "Broken Plugin",
                    directoryName = "broken-plugin",
                    isInstalled = false,
                    entries = listOf(
                        PluginDiagnosticEntry(
                            source = PluginDiagnosticSource.LOAD,
                            issue = PluginDiagnosticIssue(
                                severity = PluginDiagnosticSeverity.ERROR,
                                category = PluginDiagnosticCategory.MANIFEST,
                                message = "Manifest missing"
                            )
                        )
                    )
                )
            )
        )

        assertThat(
            PluginsSettingsSectionSupport.togglePluginDiagnosticsFilter(
                currentFilter = PluginDiagnosticsFilter.ERROR,
                selectedFilter = PluginDiagnosticsFilter.ERROR,
            )
        ).isEqualTo(PluginDiagnosticsFilter.ALL)
        assertThat(
            PluginsSettingsSectionSupport.togglePluginDiagnosticsFilter(
                currentFilter = PluginDiagnosticsFilter.WARNING,
                selectedFilter = PluginDiagnosticsFilter.NOT_LOADED,
            )
        ).isEqualTo(PluginDiagnosticsFilter.NOT_LOADED)

        assertThat(
            PluginsSettingsSectionSupport.filterInstalledPluginsByDiagnostics(
                installedPlugins = diagnosticPlugins,
                snapshot = snapshot,
                filter = PluginDiagnosticsFilter.ERROR,
            )
        ).containsExactly(errorPlugin)
        assertThat(
            PluginsSettingsSectionSupport.filterInstalledPluginsByDiagnostics(
                installedPlugins = diagnosticPlugins,
                snapshot = snapshot,
                filter = PluginDiagnosticsFilter.WARNING,
            )
        ).containsExactly(warningPlugin, lspPlugin).inOrder()
        assertThat(
            PluginsSettingsSectionSupport.filterInstalledPluginsByDiagnostics(
                installedPlugins = diagnosticPlugins,
                snapshot = snapshot,
                filter = PluginDiagnosticsFilter.NOT_LOADED,
            )
        ).isEmpty()
        assertThat(
            PluginsSettingsSectionSupport.filterInstalledPluginsByDiagnostics(
                installedPlugins = diagnosticPlugins,
                snapshot = snapshot,
                filter = PluginDiagnosticsFilter.LSP,
            )
        ).containsExactly(lspPlugin)
        assertThat(
            PluginsSettingsSectionSupport.filterLoadReportsByDiagnostics(
                snapshot = snapshot,
                filter = PluginDiagnosticsFilter.ERROR,
            )
        ).isEmpty()
        assertThat(
            PluginsSettingsSectionSupport.filterLoadReportsByDiagnostics(
                snapshot = snapshot,
                filter = PluginDiagnosticsFilter.LSP,
            )
        ).isEmpty()
        assertThat(
            PluginsSettingsSectionSupport.filterLoadReportsByDiagnostics(
                snapshot = snapshot,
                filter = PluginDiagnosticsFilter.NOT_LOADED,
            )
        ).hasSize(1)
        assertThat(
            PluginsSettingsSectionSupport.shouldShowDiagnosticsFilteredEmptyState(
                installedPlugins = emptyList(),
                loadReports = emptyList(),
                filter = PluginDiagnosticsFilter.WARNING,
            )
        ).isTrue()
        assertThat(
            PluginsSettingsSectionSupport.shouldShowDiagnosticsFilteredEmptyState(
                installedPlugins = listOf(warningPlugin),
                loadReports = emptyList(),
                filter = PluginDiagnosticsFilter.WARNING,
            )
        ).isFalse()
    }

    @Test
    fun diagnosticSourceFilter_shouldToggleResolveAndFilterEntries() {
        val entries = listOf(
            PluginDiagnosticEntry(
                source = PluginDiagnosticSource.RUNTIME,
                issue = PluginDiagnosticIssue(
                    severity = PluginDiagnosticSeverity.ERROR,
                    category = PluginDiagnosticCategory.RUNTIME,
                    message = "Runtime crash"
                )
            ),
            PluginDiagnosticEntry(
                source = PluginDiagnosticSource.HEALTH,
                issue = PluginDiagnosticIssue(
                    severity = PluginDiagnosticSeverity.WARNING,
                    category = PluginDiagnosticCategory.COMPATIBILITY,
                    message = "Compatibility warning"
                )
            ),
        )

        assertThat(
            PluginsSettingsSectionSupport.togglePluginDiagnosticSourceFilter(
                currentFilter = PluginDiagnosticSourceFilter.RUNTIME,
                selectedFilter = PluginDiagnosticSourceFilter.RUNTIME,
            )
        ).isEqualTo(PluginDiagnosticSourceFilter.ALL)
        assertThat(
            PluginsSettingsSectionSupport.togglePluginDiagnosticSourceFilter(
                currentFilter = PluginDiagnosticSourceFilter.HEALTH,
                selectedFilter = PluginDiagnosticSourceFilter.RUNTIME,
            )
        ).isEqualTo(PluginDiagnosticSourceFilter.RUNTIME)

        assertThat(
            PluginsSettingsSectionSupport.resolveAvailablePluginDiagnosticSourceFilters(entries)
        ).containsExactly(
            PluginDiagnosticSourceFilter.ALL,
            PluginDiagnosticSourceFilter.HEALTH,
            PluginDiagnosticSourceFilter.RUNTIME,
        ).inOrder()
        assertThat(
            PluginsSettingsSectionSupport.resolvePluginDiagnosticSourceFilterOrAll(
                filter = PluginDiagnosticSourceFilter.RUNTIME,
                availableFilters = listOf(
                    PluginDiagnosticSourceFilter.ALL,
                    PluginDiagnosticSourceFilter.RUNTIME,
                ),
            )
        ).isEqualTo(PluginDiagnosticSourceFilter.RUNTIME)
        assertThat(
            PluginsSettingsSectionSupport.resolvePluginDiagnosticSourceFilterOrAll(
                filter = PluginDiagnosticSourceFilter.LOAD,
                availableFilters = listOf(
                    PluginDiagnosticSourceFilter.ALL,
                    PluginDiagnosticSourceFilter.RUNTIME,
                ),
            )
        ).isEqualTo(PluginDiagnosticSourceFilter.ALL)

        assertThat(
            PluginsSettingsSectionSupport.filterPluginDiagnosticEntriesBySource(
                entries = entries,
                filter = PluginDiagnosticSourceFilter.RUNTIME,
            )
        ).containsExactly(entries.first())
        assertThat(
            PluginsSettingsSectionSupport.filterPluginDiagnosticEntriesBySource(
                entries = entries,
                filter = PluginDiagnosticSourceFilter.LOAD,
            )
        ).isEmpty()
        assertThat(
            PluginsSettingsSectionSupport.shouldShowPluginDiagnosticSourceFilteredEmptyState(
                entries = emptyList(),
                filter = PluginDiagnosticSourceFilter.RUNTIME,
            )
        ).isTrue()
        assertThat(
            PluginsSettingsSectionSupport.shouldShowPluginDiagnosticSourceFilteredEmptyState(
                entries = listOf(entries.first()),
                filter = PluginDiagnosticSourceFilter.RUNTIME,
            )
        ).isFalse()
    }

    @Test
    fun diagnosticSourceFilter_shouldResolveOptionsAndFilterReports() {
        val mixedReport = PluginDiagnosticsReport(
            pluginId = "mixed.plugin",
            pluginName = "Mixed Plugin",
            isInstalled = true,
            entries = listOf(
                PluginDiagnosticEntry(
                    source = PluginDiagnosticSource.RUNTIME,
                    issue = PluginDiagnosticIssue(
                        severity = PluginDiagnosticSeverity.ERROR,
                        category = PluginDiagnosticCategory.RUNTIME,
                        message = "Runtime crash"
                    )
                ),
                PluginDiagnosticEntry(
                    source = PluginDiagnosticSource.HEALTH,
                    issue = PluginDiagnosticIssue(
                        severity = PluginDiagnosticSeverity.WARNING,
                        category = PluginDiagnosticCategory.COMPATIBILITY,
                        message = "Compatibility warning"
                    )
                ),
            )
        )
        val cleanReport = PluginDiagnosticsReport(
            pluginId = "clean.plugin",
            pluginName = "Clean Plugin",
            isInstalled = true,
            entries = emptyList()
        )
        val loadReport = PluginDiagnosticsReport(
            pluginId = null,
            pluginName = "Broken Plugin",
            directoryName = "broken-plugin",
            isInstalled = false,
            entries = listOf(
                PluginDiagnosticEntry(
                    source = PluginDiagnosticSource.LOAD,
                    issue = PluginDiagnosticIssue(
                        severity = PluginDiagnosticSeverity.ERROR,
                        category = PluginDiagnosticCategory.MANIFEST,
                        message = "Manifest missing"
                    )
                )
            )
        )

        assertThat(
            PluginsSettingsSectionSupport.resolvePluginDiagnosticSourceFilterOptions(
                installedReports = listOf(mixedReport, cleanReport),
                loadReports = listOf(loadReport),
            )
        ).containsExactly(
            PluginDiagnosticSourceFilterOption(
                filter = PluginDiagnosticSourceFilter.ALL,
                count = 3,
            ),
            PluginDiagnosticSourceFilterOption(
                filter = PluginDiagnosticSourceFilter.LOAD,
                count = 1,
            ),
            PluginDiagnosticSourceFilterOption(
                filter = PluginDiagnosticSourceFilter.HEALTH,
                count = 1,
            ),
            PluginDiagnosticSourceFilterOption(
                filter = PluginDiagnosticSourceFilter.RUNTIME,
                count = 1,
            ),
        ).inOrder()

        assertThat(
            PluginsSettingsSectionSupport.filterPluginDiagnosticsReportBySource(
                report = mixedReport,
                filter = PluginDiagnosticSourceFilter.RUNTIME,
            )
        ).isEqualTo(
            mixedReport.copy(
                entries = listOf(mixedReport.entries.first())
            )
        )
        assertThat(
            PluginsSettingsSectionSupport.filterPluginDiagnosticsReportBySource(
                report = mixedReport,
                filter = PluginDiagnosticSourceFilter.LOAD,
            )
        ).isNull()
        assertThat(
            PluginsSettingsSectionSupport.filterPluginDiagnosticsReportsBySource(
                reports = listOf(mixedReport, loadReport),
                filter = PluginDiagnosticSourceFilter.HEALTH,
            )
        ).containsExactly(
            mixedReport.copy(
                entries = listOf(mixedReport.entries.last())
            )
        )
        assertThat(
            PluginsSettingsSectionSupport.filterInstalledPluginDiagnosticsReportsBySource(
                reports = mapOf(
                    "mixed.plugin" to mixedReport,
                    "clean.plugin" to cleanReport,
                ),
                filter = PluginDiagnosticSourceFilter.RUNTIME,
            )
        ).containsExactly(
            "mixed.plugin",
            mixedReport.copy(
                entries = listOf(mixedReport.entries.first())
            )
        )
    }

    @Test
    fun diagnosticActions_shouldFollowSourceCategoryAndInstallState() {
        val runtimeEntry = PluginDiagnosticEntry(
            source = PluginDiagnosticSource.RUNTIME,
            issue = PluginDiagnosticIssue(
                severity = PluginDiagnosticSeverity.ERROR,
                category = PluginDiagnosticCategory.RUNTIME,
                message = "Runtime crash",
            ),
        )
        val permissionsEntry = PluginDiagnosticEntry(
            source = PluginDiagnosticSource.HEALTH,
            issue = PluginDiagnosticIssue(
                severity = PluginDiagnosticSeverity.WARNING,
                category = PluginDiagnosticCategory.PERMISSIONS,
                message = "Permission missing",
            ),
        )
        val loadEntry = PluginDiagnosticEntry(
            source = PluginDiagnosticSource.LOAD,
            issue = PluginDiagnosticIssue(
                severity = PluginDiagnosticSeverity.ERROR,
                category = PluginDiagnosticCategory.MANIFEST,
                message = "Manifest missing",
            ),
        )

        assertThat(
            PluginsSettingsSectionSupport.resolvePluginDiagnosticActions(
                entry = runtimeEntry,
                isScriptPlugin = true,
                isInstalled = true,
            )
        ).containsExactly(
            PluginDiagnosticAction.OPEN_LOGS,
            PluginDiagnosticAction.RELOAD_PLUGIN,
        ).inOrder()
        assertThat(
            PluginsSettingsSectionSupport.resolvePluginDiagnosticActions(
                entry = runtimeEntry,
                isScriptPlugin = false,
                isInstalled = true,
            )
        ).containsExactly(PluginDiagnosticAction.OPEN_LOGS)
        assertThat(
            PluginsSettingsSectionSupport.resolvePluginDiagnosticActions(
                entry = permissionsEntry,
                isScriptPlugin = true,
                isInstalled = true,
            )
        ).containsExactly(PluginDiagnosticAction.SHOW_PERMISSIONS)
        assertThat(
            PluginsSettingsSectionSupport.resolvePluginDiagnosticActions(
                entry = loadEntry,
                isScriptPlugin = true,
                isInstalled = false,
            )
        ).containsExactly(PluginDiagnosticAction.COPY_DIAGNOSTIC)
        assertThat(
            PluginsSettingsSectionSupport.resolvePluginDiagnosticActionLabelRes(
                PluginDiagnosticAction.SHOW_PERMISSIONS
            )
        ).isEqualTo(Strings.plugins_diagnostics_action_view_permissions)
        assertThat(
            PluginsSettingsSectionSupport.resolvePluginDiagnosticPreferredLogLevel(runtimeEntry)
        ).isEqualTo(PluginLogLevel.ERROR)
        val lspEntry = runtimeEntry.copy(
            issue = runtimeEntry.issue.copy(category = PluginDiagnosticCategory.LSP)
        )
        assertThat(
            PluginsSettingsSectionSupport.resolvePluginDiagnosticActions(
                entry = lspEntry,
                isScriptPlugin = true,
                isInstalled = true,
            )
        ).containsExactly(
            PluginDiagnosticAction.OPEN_LOGS,
            PluginDiagnosticAction.REPAIR_LSP_DEPENDENCIES,
        ).inOrder()
        assertThat(
            PluginsSettingsSectionSupport.resolvePluginDiagnosticActionLabelRes(
                PluginDiagnosticAction.REPAIR_LSP_DEPENDENCIES
            )
        ).isEqualTo(Strings.plugins_diagnostics_action_repair_lsp)
        assertThat(
            PluginsSettingsSectionSupport.resolvePluginDiagnosticPreferredLogLevel(permissionsEntry)
        ).isNull()
        assertThat(
            PluginsSettingsSectionSupport.resolvePluginDiagnosticPreferredLogLevel(
                runtimeEntry.copy(
                    issue = runtimeEntry.issue.copy(severity = PluginDiagnosticSeverity.WARNING)
                )
            )
        ).isEqualTo(PluginLogLevel.WARN)
    }

    @Test
    fun diagnosticClipboardText_shouldIncludeStableStructuredFields() {
        val entry = PluginDiagnosticEntry(
            source = PluginDiagnosticSource.RUNTIME,
            issue = PluginDiagnosticIssue(
                severity = PluginDiagnosticSeverity.ERROR,
                category = PluginDiagnosticCategory.RUNTIME,
                message = "Runtime crash",
                fixHint = "Reload plugin",
            ),
        )
        val report = PluginDiagnosticsReport(
            pluginId = "demo.plugin",
            pluginName = "Demo Plugin",
            directoryName = "demo-plugin",
            isInstalled = true,
            entries = listOf(entry),
        )

        val clipboardText = PluginsSettingsSectionSupport.buildPluginDiagnosticClipboardText(
            report = report,
            entry = entry,
        )

        assertThat(clipboardText).contains("Plugin diagnostics")
        assertThat(clipboardText).contains("pluginId: demo.plugin")
        assertThat(clipboardText).contains("pluginName: Demo Plugin")
        assertThat(clipboardText).contains("directoryName: demo-plugin")
        assertThat(clipboardText).contains("installed: true")
        assertThat(clipboardText).contains("source: RUNTIME")
        assertThat(clipboardText).contains("severity: ERROR")
        assertThat(clipboardText).contains("category: RUNTIME")
        assertThat(clipboardText).contains("message: Runtime crash")
        assertThat(clipboardText).contains("fixHint: Reload plugin")
    }

    @Test
    fun diagnosticsClipboardText_shouldIncludeEveryIssue() {
        val report = PluginDiagnosticsReport(
            pluginId = "demo.plugin",
            pluginName = "Demo Plugin",
            directoryName = "plugin-preflight",
            isInstalled = false,
            entries = listOf(
                PluginDiagnosticEntry(
                    source = PluginDiagnosticSource.LOAD,
                    issue = PluginDiagnosticIssue(
                        severity = PluginDiagnosticSeverity.ERROR,
                        category = PluginDiagnosticCategory.MANIFEST,
                        message = "Missing manifest",
                        fixHint = "Add manifest.json",
                    ),
                ),
                PluginDiagnosticEntry(
                    source = PluginDiagnosticSource.HEALTH,
                    issue = PluginDiagnosticIssue(
                        severity = PluginDiagnosticSeverity.WARNING,
                        category = PluginDiagnosticCategory.PERMISSIONS,
                        message = "Unknown permission",
                    ),
                ),
            ),
        )

        val clipboardText = PluginsSettingsSectionSupport.buildPluginDiagnosticsClipboardText(report)

        assertThat(clipboardText).contains("Plugin diagnostics")
        assertThat(clipboardText).contains("pluginId: demo.plugin")
        assertThat(clipboardText).contains("pluginName: Demo Plugin")
        assertThat(clipboardText).contains("directoryName: plugin-preflight")
        assertThat(clipboardText).contains("installed: false")
        assertThat(clipboardText).contains("issueCount: 2")
        assertThat(clipboardText).contains("issue[1]")
        assertThat(clipboardText).contains("source: LOAD")
        assertThat(clipboardText).contains("severity: ERROR")
        assertThat(clipboardText).contains("message: Missing manifest")
        assertThat(clipboardText).contains("fixHint: Add manifest.json")
        assertThat(clipboardText).contains("issue[2]")
        assertThat(clipboardText).contains("source: HEALTH")
        assertThat(clipboardText).contains("severity: WARNING")
        assertThat(clipboardText).contains("message: Unknown permission")
    }

    @Test
    fun buildPluginPreflightLogMessage_shouldSummarizeStatusAndIssues() {
        val report = PluginDiagnosticsReport(
            pluginId = "demo.plugin",
            pluginName = "Demo Plugin",
            isInstalled = false,
            entries = listOf(
                PluginDiagnosticEntry(
                    source = PluginDiagnosticSource.LOAD,
                    issue = PluginDiagnosticIssue(
                        severity = PluginDiagnosticSeverity.ERROR,
                        category = PluginDiagnosticCategory.MANIFEST,
                        message = "Missing manifest",
                        fixHint = "Add manifest.json",
                    ),
                ),
                PluginDiagnosticEntry(
                    source = PluginDiagnosticSource.HEALTH,
                    issue = PluginDiagnosticIssue(
                        severity = PluginDiagnosticSeverity.WARNING,
                        category = PluginDiagnosticCategory.PERMISSIONS,
                        message = "Unknown permission",
                    ),
                ),
            ),
        )

        val blockedMessage = PluginsSettingsSectionSupport.buildPluginPreflightLogMessage(
            report = report,
            blocked = true,
        )
        val warningMessage = PluginsSettingsSectionSupport.buildPluginPreflightLogMessage(
            report = report.copy(entries = report.entries.drop(1)),
            blocked = false,
        )

        assertThat(blockedMessage).contains("Install preflight blocked")
        assertThat(blockedMessage).contains("pluginId=demo.plugin")
        assertThat(blockedMessage).contains("pluginName=Demo Plugin")
        assertThat(blockedMessage).contains("errors=1")
        assertThat(blockedMessage).contains("warnings=1")
        assertThat(blockedMessage).contains("LOAD/ERROR/MANIFEST: Missing manifest")
        assertThat(blockedMessage).contains("fix: Add manifest.json")
        assertThat(warningMessage).contains("Install preflight warning")
        assertThat(warningMessage).contains("errors=0")
        assertThat(warningMessage).contains("warnings=1")
        assertThat(warningMessage).contains("HEALTH/WARNING/PERMISSIONS: Unknown permission")
    }

    @Test
    fun resolveInstalledPluginDetailDiagnosticsReport_shouldPreferDoctorHealthButKeepRuntime() {
        val plugin = installedPlugin(
            id = "demo.plugin",
            name = "Demo Plugin",
        )
        val runtimeReport = PluginDiagnosticsReport(
            pluginId = plugin.manifest.id,
            pluginName = plugin.manifest.name,
            directoryName = "demo-plugin",
            isInstalled = true,
            entries = listOf(
                PluginDiagnosticEntry(
                    source = PluginDiagnosticSource.RUNTIME,
                    issue = PluginDiagnosticIssue(
                        severity = PluginDiagnosticSeverity.ERROR,
                        category = PluginDiagnosticCategory.RUNTIME,
                        message = "Runtime crash",
                    )
                )
            )
        )
        val doctorReport = PluginDiagnosticsReport(
            pluginId = plugin.manifest.id,
            pluginName = plugin.manifest.name,
            directoryName = "demo-plugin",
            isInstalled = false,
            entries = listOf(
                PluginDiagnosticEntry(
                    source = PluginDiagnosticSource.HEALTH,
                    issue = PluginDiagnosticIssue(
                        severity = PluginDiagnosticSeverity.WARNING,
                        category = PluginDiagnosticCategory.CONTRIBUTIONS,
                        message = "Menu command missing declaration",
                    )
                )
            )
        )

        val merged = PluginsSettingsSectionSupport.resolveInstalledPluginDetailDiagnosticsReport(
            plugin = plugin,
            snapshotReport = runtimeReport,
            manualDoctorReport = doctorReport,
        )

        assertThat(merged.isInstalled).isTrue()
        assertThat(merged.entries.map { it.source }).containsExactly(
            PluginDiagnosticSource.RUNTIME,
            PluginDiagnosticSource.HEALTH,
        ).inOrder()
    }

    @Test
    fun resolveInstalledPluginDetailDiagnosticsReport_shouldKeepLoadIssueStandalone() {
        val plugin = installedPlugin(
            id = "demo.plugin",
            name = "Demo Plugin",
        )
        val snapshotReport = PluginDiagnosticsReport(
            pluginId = plugin.manifest.id,
            pluginName = plugin.manifest.name,
            directoryName = "demo-plugin",
            isInstalled = true,
            entries = listOf(
                PluginDiagnosticEntry(
                    source = PluginDiagnosticSource.RUNTIME,
                    issue = PluginDiagnosticIssue(
                        severity = PluginDiagnosticSeverity.ERROR,
                        category = PluginDiagnosticCategory.RUNTIME,
                        message = "Runtime crash",
                    )
                )
            )
        )
        val loadDoctorReport = PluginDiagnosticsReport(
            pluginId = plugin.manifest.id,
            pluginName = plugin.manifest.name,
            directoryName = "demo-plugin",
            isInstalled = false,
            entries = listOf(
                PluginDiagnosticEntry(
                    source = PluginDiagnosticSource.LOAD,
                    issue = PluginDiagnosticIssue(
                        severity = PluginDiagnosticSeverity.ERROR,
                        category = PluginDiagnosticCategory.MANIFEST,
                        message = "Manifest missing",
                    )
                )
            )
        )

        val resolved = PluginsSettingsSectionSupport.resolveInstalledPluginDetailDiagnosticsReport(
            plugin = plugin,
            snapshotReport = snapshotReport,
            manualDoctorReport = loadDoctorReport,
        )

        assertThat(resolved).isEqualTo(loadDoctorReport)
    }

    private fun installedPlugin(
        id: String,
        name: String,
        isBundled: Boolean = false,
        contributions: PluginContributions? = null,
    ): InstalledPlugin = InstalledPlugin(
        manifest = PluginManifest(
            id = id,
            name = name,
            version = "1.0.0",
            contributions = contributions,
            isBundled = isBundled,
        ),
        directory = File("build/$id"),
        enabled = true,
    )
}
