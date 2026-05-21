package com.wuxianggujun.tinaide.ui.compose.screens.settings.sections

import androidx.annotation.StringRes
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.plugin.InstalledPlugin
import com.wuxianggujun.tinaide.plugin.PluginDiagnosticCategory
import com.wuxianggujun.tinaide.plugin.PluginDiagnosticEntry
import com.wuxianggujun.tinaide.plugin.PluginDiagnosticIssue
import com.wuxianggujun.tinaide.plugin.PluginDiagnosticSeverity
import com.wuxianggujun.tinaide.plugin.PluginDiagnosticSource
import com.wuxianggujun.tinaide.plugin.PluginDiagnosticsReport
import com.wuxianggujun.tinaide.plugin.PluginDiagnosticsSnapshot
import com.wuxianggujun.tinaide.plugin.PluginLogLevel
import com.wuxianggujun.tinaide.plugin.PluginManifest
import com.wuxianggujun.tinaide.plugin.ThemeConfig
import com.wuxianggujun.tinaide.plugin.lsp.LspPluginInfo
import com.wuxianggujun.tinaide.plugin.lsp.LspPluginInstallState
import com.wuxianggujun.tinaide.plugin.lsp.LspToolchainConfig
import com.wuxianggujun.tinaide.plugin.lsp.ToolchainInstallState
import com.wuxianggujun.tinaide.plugin.script.ScriptPluginState
import java.util.Locale

internal data class PluginsContributionSummary(
    val themeCount: Int,
    val fileTreeMenuCount: Int,
    val editorContextMenuCount: Int,
)

internal data class PluginDiagnosticsSummary(
    val totalCount: Int,
    val errorCount: Int,
    val warningCount: Int,
    val infoCount: Int,
    val highestSeverity: PluginDiagnosticSeverity?,
)

internal data class PluginDiagnosticCategoryGroup(
    val category: PluginDiagnosticCategory,
    val entries: List<PluginDiagnosticEntry>,
    val errorCount: Int,
    val warningCount: Int,
)

internal data class PluginDiagnosticsOverviewSummary(
    val installedCount: Int,
    val issuePluginCount: Int,
    val errorPluginCount: Int,
    val warningPluginCount: Int,
    val lspIssuePluginCount: Int,
    val notLoadedCount: Int,
)

internal data class PluginDiagnosticSourceFilterOption(
    val filter: PluginDiagnosticSourceFilter,
    val count: Int,
)

internal enum class PluginDiagnosticsFilter {
    ALL,
    ERROR,
    WARNING,
    NOT_LOADED,
    LSP,
}

internal enum class PluginDiagnosticSourceFilter {
    ALL,
    LOAD,
    HEALTH,
    RUNTIME,
}

internal data class LspRuntimeDiagnosticText(
    val missingRequiredToolchainsTemplate: String,
    val failedRequiredToolchainsTemplate: String,
    val lastErrorTemplate: String,
    val installFixHint: String,
    val repairFixHint: String,
)

internal enum class PluginDiagnosticAction {
    OPEN_LOGS,
    RELOAD_PLUGIN,
    SHOW_PERMISSIONS,
    REPAIR_LSP_DEPENDENCIES,
    COPY_DIAGNOSTIC,
}

internal sealed interface PluginsBatchUninstallSpec {
    data object BundledOnly : PluginsBatchUninstallSpec

    data class Confirm(
        val pluginIds: List<String>,
        val pluginNames: String,
    ) : PluginsBatchUninstallSpec
}

internal object PluginsSettingsSectionSupport {

    fun toggleSelectedPlugin(
        selectedIds: Set<String>,
        pluginId: String,
    ): Set<String> = if (pluginId in selectedIds) {
        selectedIds - pluginId
    } else {
        selectedIds + pluginId
    }

    fun buildPluginThemeIds(
        pluginId: String,
        relativePaths: List<String>,
        themesIndex: Map<String, ThemeConfig>,
    ): List<String> = relativePaths.map { relativePath ->
        "plugin:$pluginId/${relativePath.replace('\\', '/')}"
    }.filter { themeId ->
        themesIndex.containsKey(themeId)
    }

    fun buildPluginThemeOptions(
        plugin: InstalledPlugin,
        themesIndex: Map<String, ThemeConfig>,
    ): List<Pair<String, String>> {
        return buildPluginThemeIds(
            pluginId = plugin.manifest.id,
            relativePaths = plugin.manifest.contributions?.themes.orEmpty(),
            themesIndex = themesIndex,
        ).mapNotNull { themeId ->
            val themeName = themesIndex[themeId]?.name ?: return@mapNotNull null
            themeId to themeName
        }
    }

    fun resolveDetailPlugin(
        selectedPluginId: String?,
        installedPlugins: List<InstalledPlugin>,
    ): InstalledPlugin? = selectedPluginId?.let { pluginId ->
        installedPlugins.find { plugin -> plugin.manifest.id == pluginId }
    }

    fun shouldClosePluginDetails(
        selectedPluginId: String?,
        detailPlugin: InstalledPlugin?,
    ): Boolean = selectedPluginId != null && detailPlugin == null

    fun resolvePluginInitial(name: String): String = name.firstOrNull()?.uppercase() ?: "P"

    @StringRes
    fun resolveLspStatusLabelRes(isReady: Boolean): Int = if (isReady) {
        Strings.lsp_plugin_status_ready
    } else {
        Strings.lsp_plugin_status_not_ready
    }

    fun buildLspRuntimeEntriesByPluginId(
        lspPlugins: List<LspPluginInfo>,
        installStates: Map<String, LspPluginInstallState>,
        diagnosticText: LspRuntimeDiagnosticText,
    ): Map<String, List<PluginDiagnosticEntry>> = lspPlugins.mapNotNull { lspPlugin ->
        val entries = buildLspRuntimeEntries(
            lspPlugin = lspPlugin,
            installState = installStates[lspPlugin.pluginId],
            diagnosticText = diagnosticText,
        )
        if (entries.isEmpty()) {
            null
        } else {
            lspPlugin.pluginId to entries
        }
    }.toMap()

    private fun buildLspRuntimeEntries(
        lspPlugin: LspPluginInfo,
        installState: LspPluginInstallState?,
        diagnosticText: LspRuntimeDiagnosticText,
    ): List<PluginDiagnosticEntry> {
        val requiredToolchains = lspPlugin.toolchainConfigs.filter { toolchain -> toolchain.required }
        val missingRequiredToolchains = requiredToolchains.filter { toolchain ->
            val state = installState?.toolchainStates?.get(toolchain.id)
            state == null || state == ToolchainInstallState.NOT_INSTALLED
        }
        val failedRequiredToolchains = requiredToolchains.filter { toolchain ->
            installState?.toolchainStates?.get(toolchain.id) == ToolchainInstallState.FAILED
        }
        val lastError = installState?.lastError?.takeIf { error -> error.isNotBlank() }

        return buildList {
            if (missingRequiredToolchains.isNotEmpty()) {
                add(
                    buildLspRuntimeDiagnosticEntry(
                        severity = PluginDiagnosticSeverity.WARNING,
                        message = formatLspDiagnosticText(
                            diagnosticText.missingRequiredToolchainsTemplate,
                            missingRequiredToolchains.toToolchainDisplayNames(),
                        ),
                        fixHint = diagnosticText.installFixHint,
                    )
                )
            }
            if (failedRequiredToolchains.isNotEmpty()) {
                add(
                    buildLspRuntimeDiagnosticEntry(
                        severity = PluginDiagnosticSeverity.ERROR,
                        message = formatLspDiagnosticText(
                            diagnosticText.failedRequiredToolchainsTemplate,
                            failedRequiredToolchains.toToolchainDisplayNames(),
                        ),
                        fixHint = diagnosticText.repairFixHint,
                    )
                )
            }
            lastError?.let { error ->
                add(
                    buildLspRuntimeDiagnosticEntry(
                        severity = PluginDiagnosticSeverity.ERROR,
                        message = formatLspDiagnosticText(diagnosticText.lastErrorTemplate, error),
                        fixHint = diagnosticText.repairFixHint,
                    )
                )
            }
        }
    }

    private fun buildLspRuntimeDiagnosticEntry(
        severity: PluginDiagnosticSeverity,
        message: String,
        fixHint: String,
    ): PluginDiagnosticEntry = PluginDiagnosticEntry(
        source = PluginDiagnosticSource.RUNTIME,
        issue = PluginDiagnosticIssue(
            severity = severity,
            category = PluginDiagnosticCategory.LSP,
            message = message,
            fixHint = fixHint,
        ),
    )

    private fun List<LspToolchainConfig>.toToolchainDisplayNames(): String = joinToString(separator = ", ") { toolchain ->
        toolchain.name.takeIf { name -> name.isNotBlank() } ?: toolchain.id
    }

    private fun formatLspDiagnosticText(template: String, value: String): String = String.format(Locale.getDefault(), template, value)

    @StringRes
    fun resolveScriptPluginStateLabelRes(state: ScriptPluginState): Int = when (state) {
        ScriptPluginState.UNLOADED -> Strings.plugins_runtime_state_unloaded
        ScriptPluginState.LOADING -> Strings.plugins_runtime_state_loading
        ScriptPluginState.ACTIVE -> Strings.plugins_runtime_state_active
        ScriptPluginState.ERROR -> Strings.plugins_runtime_state_error
        ScriptPluginState.DISABLED -> Strings.plugins_runtime_state_disabled
    }

    fun isScriptPlugin(manifest: PluginManifest): Boolean = manifest.type.equals("script", ignoreCase = true) ||
        manifest.type.equals("hybrid", ignoreCase = true)

    fun resolveBatchUninstallSpec(
        installedPlugins: List<InstalledPlugin>,
        selectedIds: Set<String>,
    ): PluginsBatchUninstallSpec {
        val pluginsToUninstall = installedPlugins.filter { plugin ->
            plugin.manifest.id in selectedIds && !plugin.manifest.isBundled
        }
        return if (pluginsToUninstall.isEmpty()) {
            PluginsBatchUninstallSpec.BundledOnly
        } else {
            PluginsBatchUninstallSpec.Confirm(
                pluginIds = pluginsToUninstall.map { it.manifest.id },
                pluginNames = pluginsToUninstall.joinToString("\n") { plugin ->
                    "• ${plugin.manifest.name}"
                },
            )
        }
    }

    fun resolveInstallSourceFileName(lastPathSegment: String?): String {
        val sourceName = lastPathSegment
            ?.trim()
            ?.replace('\\', '/')
            ?.substringAfterLast('/')
            ?.takeIf { it.isNotBlank() }
            ?: return "plugin.tinaplug"
        return sourceName
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .takeIf { name -> name.isNotBlank() && name.any { it.isLetterOrDigit() } }
            ?: "plugin.tinaplug"
    }

    fun buildTempInstallFileName(timestampMillis: Long, fileName: String): String = "install_${timestampMillis}_${resolveInstallSourceFileName(fileName)}"

    fun resolveContributionSummary(manifest: PluginManifest): PluginsContributionSummary = PluginsContributionSummary(
        themeCount = manifest.contributions?.themes?.size ?: 0,
        fileTreeMenuCount = manifest.contributions?.menus?.fileTreeContext?.size ?: 0,
        editorContextMenuCount = manifest.contributions?.menus?.editorContext?.size ?: 0,
    )

    fun resolvePluginDiagnosticsSummary(report: PluginDiagnosticsReport?): PluginDiagnosticsSummary {
        val issues = report?.issues.orEmpty()
        val errorCount = issues.count { it.severity == PluginDiagnosticSeverity.ERROR }
        val warningCount = issues.count { it.severity == PluginDiagnosticSeverity.WARNING }
        val infoCount = issues.count { it.severity == PluginDiagnosticSeverity.INFO }
        return PluginDiagnosticsSummary(
            totalCount = issues.size,
            errorCount = errorCount,
            warningCount = warningCount,
            infoCount = infoCount,
            highestSeverity = issues.maxByOrNull { it.severity.priority }?.severity,
        )
    }

    fun resolveLspDiagnosticSeverity(report: PluginDiagnosticsReport?): PluginDiagnosticSeverity? = report
        ?.entries
        .orEmpty()
        .asSequence()
        .filter { entry -> entry.issue.category == PluginDiagnosticCategory.LSP }
        .map { entry -> entry.issue.severity }
        .maxByOrNull { severity -> severity.priority }

    fun hasLspDiagnostics(report: PluginDiagnosticsReport?): Boolean = resolveLspDiagnosticSeverity(report) != null

    fun resolvePluginPreflightDiagnosticGroups(
        report: PluginDiagnosticsReport,
    ): List<PluginDiagnosticCategoryGroup> {
        val visibleEntries = report.entries.filter { entry ->
            entry.issue.severity == PluginDiagnosticSeverity.ERROR ||
                entry.issue.severity == PluginDiagnosticSeverity.WARNING
        }.sortedWith(pluginDiagnosticEntryComparator())

        return visibleEntries.groupBy { entry -> entry.issue.category }
            .map { (category, entries) ->
                PluginDiagnosticCategoryGroup(
                    category = category,
                    entries = entries,
                    errorCount = entries.count { entry ->
                        entry.issue.severity == PluginDiagnosticSeverity.ERROR
                    },
                    warningCount = entries.count { entry ->
                        entry.issue.severity == PluginDiagnosticSeverity.WARNING
                    },
                )
            }
            .sortedWith(
                compareByDescending<PluginDiagnosticCategoryGroup> { group ->
                    group.entries.maxOfOrNull { entry -> entry.issue.severity.priority } ?: 0
                }.thenBy { group -> group.category.ordinal }
            )
    }

    fun resolvePluginDiagnosticsOverviewSummary(
        snapshot: PluginDiagnosticsSnapshot,
    ): PluginDiagnosticsOverviewSummary = resolvePluginDiagnosticsOverviewSummary(
        installedReports = snapshot.installedReports.values,
        loadReports = snapshot.loadReports,
    )

    fun resolvePluginDiagnosticsOverviewSummary(
        installedReports: Collection<PluginDiagnosticsReport>,
        loadReports: List<PluginDiagnosticsReport>,
    ): PluginDiagnosticsOverviewSummary = PluginDiagnosticsOverviewSummary(
        installedCount = installedReports.size,
        issuePluginCount = installedReports.count { it.hasIssues },
        errorPluginCount = installedReports.count {
            it.highestSeverity == PluginDiagnosticSeverity.ERROR
        },
        warningPluginCount = installedReports.count {
            it.highestSeverity == PluginDiagnosticSeverity.WARNING
        },
        lspIssuePluginCount = installedReports.count { report ->
            hasLspDiagnostics(report)
        },
        notLoadedCount = loadReports.size,
    )

    fun togglePluginDiagnosticsFilter(
        currentFilter: PluginDiagnosticsFilter,
        selectedFilter: PluginDiagnosticsFilter,
    ): PluginDiagnosticsFilter {
        if (selectedFilter == PluginDiagnosticsFilter.ALL) {
            return PluginDiagnosticsFilter.ALL
        }
        return if (currentFilter == selectedFilter) {
            PluginDiagnosticsFilter.ALL
        } else {
            selectedFilter
        }
    }

    fun filterInstalledPluginsByDiagnostics(
        installedPlugins: List<InstalledPlugin>,
        snapshot: PluginDiagnosticsSnapshot,
        filter: PluginDiagnosticsFilter,
    ): List<InstalledPlugin> = when (filter) {
        PluginDiagnosticsFilter.ALL -> installedPlugins
        PluginDiagnosticsFilter.ERROR -> installedPlugins.filter { plugin ->
            snapshot.getInstalledReport(plugin.manifest.id)?.highestSeverity ==
                PluginDiagnosticSeverity.ERROR
        }
        PluginDiagnosticsFilter.WARNING -> installedPlugins.filter { plugin ->
            snapshot.getInstalledReport(plugin.manifest.id)?.highestSeverity ==
                PluginDiagnosticSeverity.WARNING
        }
        PluginDiagnosticsFilter.NOT_LOADED -> emptyList()
        PluginDiagnosticsFilter.LSP -> installedPlugins.filter { plugin ->
            hasLspDiagnostics(snapshot.getInstalledReport(plugin.manifest.id))
        }
    }

    fun filterLoadReportsByDiagnostics(
        snapshot: PluginDiagnosticsSnapshot,
        filter: PluginDiagnosticsFilter,
    ): List<PluginDiagnosticsReport> = when (filter) {
        PluginDiagnosticsFilter.ALL,
        PluginDiagnosticsFilter.NOT_LOADED -> snapshot.loadReports
        PluginDiagnosticsFilter.ERROR,
        PluginDiagnosticsFilter.WARNING,
        PluginDiagnosticsFilter.LSP -> emptyList()
    }

    fun shouldShowDiagnosticsFilteredEmptyState(
        installedPlugins: List<InstalledPlugin>,
        loadReports: List<PluginDiagnosticsReport>,
        filter: PluginDiagnosticsFilter,
    ): Boolean = filter != PluginDiagnosticsFilter.ALL &&
        installedPlugins.isEmpty() &&
        loadReports.isEmpty()

    fun togglePluginDiagnosticSourceFilter(
        currentFilter: PluginDiagnosticSourceFilter,
        selectedFilter: PluginDiagnosticSourceFilter,
    ): PluginDiagnosticSourceFilter {
        if (selectedFilter == PluginDiagnosticSourceFilter.ALL) {
            return PluginDiagnosticSourceFilter.ALL
        }
        return if (currentFilter == selectedFilter) {
            PluginDiagnosticSourceFilter.ALL
        } else {
            selectedFilter
        }
    }

    fun resolveAvailablePluginDiagnosticSourceFilters(
        entries: List<PluginDiagnosticEntry>,
    ): List<PluginDiagnosticSourceFilter> {
        val availableFilters = buildList {
            add(PluginDiagnosticSourceFilter.ALL)
            if (entries.any { it.source == PluginDiagnosticSource.LOAD }) {
                add(PluginDiagnosticSourceFilter.LOAD)
            }
            if (entries.any { it.source == PluginDiagnosticSource.HEALTH }) {
                add(PluginDiagnosticSourceFilter.HEALTH)
            }
            if (entries.any { it.source == PluginDiagnosticSource.RUNTIME }) {
                add(PluginDiagnosticSourceFilter.RUNTIME)
            }
        }
        return availableFilters
    }

    fun resolvePluginDiagnosticSourceFilterOrAll(
        filter: PluginDiagnosticSourceFilter,
        availableFilters: List<PluginDiagnosticSourceFilter>,
    ): PluginDiagnosticSourceFilter = if (filter in availableFilters) {
        filter
    } else {
        PluginDiagnosticSourceFilter.ALL
    }

    fun filterPluginDiagnosticEntriesBySource(
        entries: List<PluginDiagnosticEntry>,
        filter: PluginDiagnosticSourceFilter,
    ): List<PluginDiagnosticEntry> = when (filter) {
        PluginDiagnosticSourceFilter.ALL -> entries
        PluginDiagnosticSourceFilter.LOAD -> entries.filter { it.source == PluginDiagnosticSource.LOAD }
        PluginDiagnosticSourceFilter.HEALTH -> entries.filter { it.source == PluginDiagnosticSource.HEALTH }
        PluginDiagnosticSourceFilter.RUNTIME -> entries.filter { it.source == PluginDiagnosticSource.RUNTIME }
    }

    fun shouldShowPluginDiagnosticSourceFilteredEmptyState(
        entries: List<PluginDiagnosticEntry>,
        filter: PluginDiagnosticSourceFilter,
    ): Boolean = filter != PluginDiagnosticSourceFilter.ALL && entries.isEmpty()

    fun filterPluginDiagnosticsReportBySource(
        report: PluginDiagnosticsReport,
        filter: PluginDiagnosticSourceFilter,
    ): PluginDiagnosticsReport? {
        if (filter == PluginDiagnosticSourceFilter.ALL) {
            return report
        }
        val filteredEntries = filterPluginDiagnosticEntriesBySource(
            entries = report.entries,
            filter = filter,
        )
        return if (filteredEntries.isEmpty()) {
            null
        } else {
            report.copy(entries = filteredEntries)
        }
    }

    fun filterPluginDiagnosticsReportsBySource(
        reports: List<PluginDiagnosticsReport>,
        filter: PluginDiagnosticSourceFilter,
    ): List<PluginDiagnosticsReport> = reports.mapNotNull { report ->
        filterPluginDiagnosticsReportBySource(
            report = report,
            filter = filter,
        )
    }

    fun filterInstalledPluginDiagnosticsReportsBySource(
        reports: Map<String, PluginDiagnosticsReport>,
        filter: PluginDiagnosticSourceFilter,
    ): Map<String, PluginDiagnosticsReport> = reports.mapNotNull { (pluginId, report) ->
        filterPluginDiagnosticsReportBySource(
            report = report,
            filter = filter,
        )?.let { filteredReport ->
            pluginId to filteredReport
        }
    }.toMap()

    fun resolvePluginDiagnosticActions(
        entry: PluginDiagnosticEntry,
        isScriptPlugin: Boolean,
        isInstalled: Boolean,
    ): List<PluginDiagnosticAction> {
        if (!isInstalled || entry.source == PluginDiagnosticSource.LOAD) {
            return listOf(PluginDiagnosticAction.COPY_DIAGNOSTIC)
        }

        return buildList {
            if (entry.issue.category == PluginDiagnosticCategory.LSP) {
                add(PluginDiagnosticAction.OPEN_LOGS)
                add(PluginDiagnosticAction.REPAIR_LSP_DEPENDENCIES)
            } else if (entry.source == PluginDiagnosticSource.RUNTIME) {
                add(PluginDiagnosticAction.OPEN_LOGS)
                if (isScriptPlugin) {
                    add(PluginDiagnosticAction.RELOAD_PLUGIN)
                }
            }
            if (entry.issue.category == PluginDiagnosticCategory.PERMISSIONS) {
                add(PluginDiagnosticAction.SHOW_PERMISSIONS)
            }
        }.distinct()
    }

    fun resolvePluginDiagnosticPreferredLogLevel(
        entry: PluginDiagnosticEntry,
    ): PluginLogLevel? {
        if (entry.source != PluginDiagnosticSource.RUNTIME) {
            return null
        }
        return when (entry.issue.severity) {
            PluginDiagnosticSeverity.ERROR -> PluginLogLevel.ERROR
            PluginDiagnosticSeverity.WARNING -> PluginLogLevel.WARN
            PluginDiagnosticSeverity.INFO -> null
        }
    }

    fun buildPluginDiagnosticClipboardText(
        report: PluginDiagnosticsReport,
        entry: PluginDiagnosticEntry,
    ): String {
        val issue = entry.issue
        return buildString {
            appendLine("Plugin diagnostics")
            appendLine("pluginId: ${report.pluginId ?: "-"}")
            appendLine("pluginName: ${report.pluginName}")
            report.directoryName?.takeIf { it.isNotBlank() }?.let { directoryName ->
                appendLine("directoryName: $directoryName")
            }
            appendLine("installed: ${report.isInstalled}")
            appendLine("source: ${entry.source.name}")
            appendLine("severity: ${issue.severity.name}")
            appendLine("category: ${issue.category.name}")
            appendLine("message: ${issue.message}")
            issue.fixHint?.takeIf { it.isNotBlank() }?.let { fixHint ->
                appendLine("fixHint: $fixHint")
            }
        }.trimEnd()
    }

    fun buildPluginDiagnosticsClipboardText(report: PluginDiagnosticsReport): String = buildString {
        appendLine("Plugin diagnostics")
        appendLine("pluginId: ${report.pluginId ?: "-"}")
        appendLine("pluginName: ${report.pluginName}")
        report.directoryName?.takeIf { it.isNotBlank() }?.let { directoryName ->
            appendLine("directoryName: $directoryName")
        }
        appendLine("installed: ${report.isInstalled}")
        appendLine("issueCount: ${report.entries.size}")
        report.entries.forEachIndexed { index, entry ->
            val issue = entry.issue
            appendLine()
            appendLine("issue[${index + 1}]")
            appendLine("source: ${entry.source.name}")
            appendLine("severity: ${issue.severity.name}")
            appendLine("category: ${issue.category.name}")
            appendLine("message: ${issue.message}")
            issue.fixHint?.takeIf { it.isNotBlank() }?.let { fixHint ->
                appendLine("fixHint: $fixHint")
            }
        }
    }.trimEnd()

    fun buildPluginPreflightLogMessage(
        report: PluginDiagnosticsReport,
        blocked: Boolean,
    ): String {
        val summary = resolvePluginDiagnosticsSummary(report)
        val status = if (blocked) "blocked" else "warning"
        val issueSummary = report.entries.joinToString(separator = "; ") { entry ->
            buildString {
                append(entry.source.name)
                append('/')
                append(entry.issue.severity.name)
                append('/')
                append(entry.issue.category.name)
                append(": ")
                append(entry.issue.message)
                entry.issue.fixHint?.takeIf { it.isNotBlank() }?.let { fixHint ->
                    append(" (fix: ")
                    append(fixHint)
                    append(')')
                }
            }
        }.ifBlank { "none" }
        return "Install preflight $status pluginId=${report.pluginId ?: "-"} " +
            "pluginName=${report.pluginName} errors=${summary.errorCount} " +
            "warnings=${summary.warningCount} issues=[$issueSummary]"
    }

    fun resolveInstalledPluginDetailDiagnosticsReport(
        plugin: InstalledPlugin,
        snapshotReport: PluginDiagnosticsReport?,
        manualDoctorReport: PluginDiagnosticsReport?,
    ): PluginDiagnosticsReport {
        val fallbackReport = snapshotReport ?: PluginDiagnosticsReport(
            pluginId = plugin.manifest.id,
            pluginName = plugin.manifest.name,
            directoryName = plugin.directory.name,
            isInstalled = true,
            entries = emptyList(),
        )
        val doctorReport = manualDoctorReport ?: return fallbackReport
        val hasLoadIssue = doctorReport.entries.any { entry ->
            entry.source == PluginDiagnosticSource.LOAD
        }
        if (hasLoadIssue) {
            return doctorReport
        }

        val mergedEntries = buildList {
            addAll(doctorReport.entries.filter { entry -> entry.source != PluginDiagnosticSource.RUNTIME })
            addAll(fallbackReport.entries.filter { entry -> entry.source == PluginDiagnosticSource.RUNTIME })
        }.distinctBy { entry ->
            listOf(
                entry.source.name,
                entry.issue.severity.name,
                entry.issue.category.name,
                entry.issue.message,
                entry.issue.fixHint.orEmpty(),
            ).joinToString("|")
        }.sortedWith(
            pluginDiagnosticEntryComparator()
        )

        return doctorReport.copy(
            pluginId = fallbackReport.pluginId ?: doctorReport.pluginId,
            pluginName = fallbackReport.pluginName,
            directoryName = fallbackReport.directoryName ?: doctorReport.directoryName,
            isInstalled = fallbackReport.isInstalled,
            entries = mergedEntries,
        )
    }

    fun resolvePluginDiagnosticSourceFilterOptions(
        installedReports: Collection<PluginDiagnosticsReport>,
        loadReports: List<PluginDiagnosticsReport>,
    ): List<PluginDiagnosticSourceFilterOption> {
        val visibleReports = buildList {
            addAll(installedReports)
            addAll(loadReports)
        }
        return buildList {
            add(
                PluginDiagnosticSourceFilterOption(
                    filter = PluginDiagnosticSourceFilter.ALL,
                    count = visibleReports.size,
                )
            )
            appendPluginDiagnosticSourceFilterOptionIfAny(
                reports = visibleReports,
                source = PluginDiagnosticSource.LOAD,
                filter = PluginDiagnosticSourceFilter.LOAD,
            )
            appendPluginDiagnosticSourceFilterOptionIfAny(
                reports = visibleReports,
                source = PluginDiagnosticSource.HEALTH,
                filter = PluginDiagnosticSourceFilter.HEALTH,
            )
            appendPluginDiagnosticSourceFilterOptionIfAny(
                reports = visibleReports,
                source = PluginDiagnosticSource.RUNTIME,
                filter = PluginDiagnosticSourceFilter.RUNTIME,
            )
        }
    }

    private fun MutableList<PluginDiagnosticSourceFilterOption>.appendPluginDiagnosticSourceFilterOptionIfAny(
        reports: List<PluginDiagnosticsReport>,
        source: PluginDiagnosticSource,
        filter: PluginDiagnosticSourceFilter,
    ) {
        val count = reports.count { report ->
            report.entries.any { entry -> entry.source == source }
        }
        if (count > 0) {
            add(
                PluginDiagnosticSourceFilterOption(
                    filter = filter,
                    count = count,
                )
            )
        }
    }

    private fun pluginDiagnosticEntryComparator(): Comparator<PluginDiagnosticEntry> = compareByDescending<PluginDiagnosticEntry> { entry -> entry.issue.severity.priority }
        .thenBy { entry -> entry.issue.category.ordinal }
        .thenBy { entry -> entry.issue.message }
        .thenBy { entry -> entry.source.ordinal }

    @StringRes
    fun resolvePluginDiagnosticSeverityLabelRes(severity: PluginDiagnosticSeverity): Int = when (severity) {
        PluginDiagnosticSeverity.INFO -> Strings.diagnostic_information
        PluginDiagnosticSeverity.WARNING -> Strings.diagnostic_warning
        PluginDiagnosticSeverity.ERROR -> Strings.diagnostic_error
    }

    @StringRes
    fun resolvePluginDiagnosticCategoryLabelRes(category: PluginDiagnosticCategory): Int = when (category) {
        PluginDiagnosticCategory.MANIFEST -> Strings.plugins_diagnostics_category_manifest
        PluginDiagnosticCategory.PERMISSIONS -> Strings.plugins_diagnostics_category_permissions
        PluginDiagnosticCategory.CONTRIBUTIONS -> Strings.plugins_diagnostics_category_contributions
        PluginDiagnosticCategory.COMPATIBILITY -> Strings.plugins_diagnostics_category_compatibility
        PluginDiagnosticCategory.RUNTIME -> Strings.plugins_diagnostics_category_runtime
        PluginDiagnosticCategory.LSP -> Strings.plugins_diagnostics_category_lsp
    }

    @StringRes
    fun resolvePluginPreflightCategoryGuideRes(category: PluginDiagnosticCategory): Int = when (category) {
        PluginDiagnosticCategory.MANIFEST -> Strings.plugins_preflight_guide_manifest
        PluginDiagnosticCategory.PERMISSIONS -> Strings.plugins_preflight_guide_permissions
        PluginDiagnosticCategory.CONTRIBUTIONS -> Strings.plugins_preflight_guide_contributions
        PluginDiagnosticCategory.COMPATIBILITY -> Strings.plugins_preflight_guide_compatibility
        PluginDiagnosticCategory.RUNTIME -> Strings.plugins_preflight_guide_runtime
        PluginDiagnosticCategory.LSP -> Strings.plugins_preflight_guide_lsp
    }

    @StringRes
    fun resolvePluginDiagnosticSourceLabelRes(source: PluginDiagnosticSource): Int = when (source) {
        PluginDiagnosticSource.LOAD -> Strings.plugins_diagnostics_source_load
        PluginDiagnosticSource.HEALTH -> Strings.plugins_diagnostics_source_health
        PluginDiagnosticSource.RUNTIME -> Strings.plugins_diagnostics_source_runtime
    }

    @StringRes
    fun resolvePluginDiagnosticsFilterLabelRes(
        filter: PluginDiagnosticsFilter,
    ): Int = when (filter) {
        PluginDiagnosticsFilter.ALL -> Strings.filter_all
        PluginDiagnosticsFilter.ERROR -> Strings.settings_plugins_overview_error_count
        PluginDiagnosticsFilter.WARNING -> Strings.settings_plugins_overview_warning_count
        PluginDiagnosticsFilter.NOT_LOADED -> Strings.settings_plugins_overview_not_loaded_count
        PluginDiagnosticsFilter.LSP -> Strings.plugins_diagnostics_filter_lsp
    }

    @StringRes
    fun resolvePluginDiagnosticSourceFilterLabelRes(
        filter: PluginDiagnosticSourceFilter,
    ): Int = when (filter) {
        PluginDiagnosticSourceFilter.ALL -> Strings.filter_all
        PluginDiagnosticSourceFilter.LOAD -> Strings.plugins_diagnostics_source_load
        PluginDiagnosticSourceFilter.HEALTH -> Strings.plugins_diagnostics_source_health
        PluginDiagnosticSourceFilter.RUNTIME -> Strings.plugins_diagnostics_source_runtime
    }

    @StringRes
    fun resolvePluginDiagnosticActionLabelRes(action: PluginDiagnosticAction): Int = when (action) {
        PluginDiagnosticAction.OPEN_LOGS -> Strings.settings_plugins_logs
        PluginDiagnosticAction.RELOAD_PLUGIN -> Strings.settings_plugins_reload
        PluginDiagnosticAction.SHOW_PERMISSIONS -> Strings.plugins_diagnostics_action_view_permissions
        PluginDiagnosticAction.REPAIR_LSP_DEPENDENCIES -> Strings.plugins_diagnostics_action_repair_lsp
        PluginDiagnosticAction.COPY_DIAGNOSTIC -> Strings.action_copy
    }
}
