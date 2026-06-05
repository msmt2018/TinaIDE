package com.wuxianggujun.tinaide.plugin

import android.content.Context
import com.wuxianggujun.tinaide.core.commands.HostCommands
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.core.i18n.strOr
import com.wuxianggujun.tinaide.plugin.script.PluginPermission
import java.io.File

internal object PluginHealthInspector {
    private data class CommandInspectionContext(
        val supportsRuntimePluginCommands: Boolean,
        val declaredCommandIds: Set<String>,
        val declaredCustomCommandIds: Set<String>,
        val hasCommandExecutePermission: Boolean,
    )

    fun inspect(
        context: Context,
        plugin: InstalledPlugin,
    ): PluginHealthReport {
        val currentAppVersion = resolveCurrentAppVersion(context)
        val commandContext = buildCommandInspectionContext(plugin.manifest)
        val issues = buildList {
            inspectCompatibility(context, plugin.manifest, currentAppVersion, this)
            inspectPermissions(context, plugin.manifest, this)
            inspectNetworkHosts(context, plugin.manifest, this)
            inspectCommands(context, plugin.manifest, commandContext, this)
            val customMenuCommandIds = inspectMenus(
                context = context,
                manifest = plugin.manifest,
                commandContext = commandContext,
                issues = this,
            )
            inspectCommandPermission(
                context = context,
                commandContext = commandContext,
                customMenuCommandIds = customMenuCommandIds,
                issues = this,
            )
            inspectUnsupportedContributions(context, plugin.manifest, this)
            inspectFileIcons(context, plugin, this)
        }.sortedWith(
            compareByDescending<PluginDiagnosticIssue> { it.severity.priority }
                .thenBy { it.category.ordinal }
                .thenBy { it.message }
        )

        return PluginHealthReport(
            pluginId = plugin.manifest.id,
            pluginName = plugin.manifest.name,
            issues = issues,
        )
    }

    private fun inspectCompatibility(
        context: Context,
        manifest: PluginManifest,
        currentAppVersion: String?,
        issues: MutableList<PluginDiagnosticIssue>,
    ) {
        val minAppVersion = manifest.minAppVersion?.trim().orEmpty()
        if (minAppVersion.isBlank()) return

        val hostVersion = currentAppVersion?.trim().orEmpty()
        val isMinAppVersionComparable = PluginVersionComparator.compare(
            minAppVersion,
            minAppVersion
        ) != null
        val versionOrder = PluginVersionComparator.compare(hostVersion, minAppVersion)
        when {
            !isMinAppVersionComparable -> {
                issues += PluginDiagnosticIssue(
                    severity = PluginDiagnosticSeverity.WARNING,
                    category = PluginDiagnosticCategory.MANIFEST,
                    message = Strings.plugin_diagnostic_min_app_version_invalid.strOr(
                        context,
                        minAppVersion
                    ),
                    fixHint = Strings.plugin_diagnostic_min_app_version_invalid_fix.strOr(context),
                )
            }

            versionOrder != null && versionOrder < 0 -> {
                issues += PluginDiagnosticIssue(
                    severity = PluginDiagnosticSeverity.ERROR,
                    category = PluginDiagnosticCategory.COMPATIBILITY,
                    message = Strings.plugin_diagnostic_min_app_version_unsupported.strOr(
                        context,
                        minAppVersion,
                        hostVersion
                    ),
                    fixHint = Strings.plugin_diagnostic_min_app_version_unsupported_fix.strOr(context),
                )
            }
        }
    }

    private fun inspectPermissions(
        context: Context,
        manifest: PluginManifest,
        issues: MutableList<PluginDiagnosticIssue>,
    ) {
        val declaredPermissions = manifest.permissions.orEmpty()
        val optionalPermissions = manifest.optionalPermissions.orEmpty()

        val duplicates = buildSet {
            addAll(findDuplicatePermissionIds(declaredPermissions))
            addAll(findDuplicatePermissionIds(optionalPermissions))

            val declaredIds = declaredPermissions
                .mapNotNull { PluginPermission.fromId(it)?.id }
                .toSet()
            val optionalIds = optionalPermissions
                .mapNotNull { PluginPermission.fromId(it)?.id }
                .toSet()
            addAll(declaredIds intersect optionalIds)
        }

        if (duplicates.isNotEmpty()) {
            issues += PluginDiagnosticIssue(
                severity = PluginDiagnosticSeverity.WARNING,
                category = PluginDiagnosticCategory.PERMISSIONS,
                message = Strings.plugin_diagnostic_permissions_duplicate.strOr(
                    context,
                    duplicates.sorted().joinToString(", ")
                ),
                fixHint = Strings.plugin_diagnostic_permissions_duplicate_fix.strOr(context),
            )
        }

        val normalizedPermissions = PluginPermission.parseList(declaredPermissions) +
            PluginPermission.parseList(optionalPermissions)
        if (manifest.networkHosts.orEmpty().isNotEmpty() &&
            PluginPermission.NETWORK_FETCH !in normalizedPermissions &&
            PluginPermission.NETWORK_UNRESTRICTED !in normalizedPermissions
        ) {
            issues += PluginDiagnosticIssue(
                severity = PluginDiagnosticSeverity.WARNING,
                category = PluginDiagnosticCategory.PERMISSIONS,
                message = Strings.plugin_diagnostic_network_hosts_without_permission.strOr(context),
                fixHint = Strings.plugin_diagnostic_network_hosts_without_permission_fix.strOr(context),
            )
        }
    }

    private fun inspectNetworkHosts(
        context: Context,
        manifest: PluginManifest,
        issues: MutableList<PluginDiagnosticIssue>,
    ) {
        val invalidHosts = PluginNetworkHostRules.findInvalidDeclaredHosts(manifest.networkHosts)
        if (invalidHosts.isNotEmpty()) {
            issues += PluginDiagnosticIssue(
                severity = PluginDiagnosticSeverity.ERROR,
                category = PluginDiagnosticCategory.MANIFEST,
                message = Strings.plugin_diagnostic_network_hosts_invalid.strOr(
                    context,
                    invalidHosts.joinToString(", ")
                ),
                fixHint = Strings.plugin_diagnostic_network_hosts_format_fix.strOr(context),
            )
        }

        val duplicateHosts = PluginNetworkHostRules.findDuplicateDeclaredHosts(manifest.networkHosts)
        if (duplicateHosts.isNotEmpty()) {
            issues += PluginDiagnosticIssue(
                severity = PluginDiagnosticSeverity.WARNING,
                category = PluginDiagnosticCategory.MANIFEST,
                message = Strings.plugin_diagnostic_network_hosts_duplicate.strOr(
                    context,
                    duplicateHosts.sorted().joinToString(", ")
                ),
                fixHint = Strings.plugin_diagnostic_network_hosts_format_fix.strOr(context),
            )
        }
    }

    private fun inspectCommands(
        context: Context,
        manifest: PluginManifest,
        commandContext: CommandInspectionContext,
        issues: MutableList<PluginDiagnosticIssue>,
    ) {
        val commands = manifest.contributions?.commands.orEmpty()
        val nonBlankCommandIds = mutableListOf<String>()

        commands.forEachIndexed { index, command ->
            val commandId = command.id.trim()
            if (commandId.isBlank()) {
                issues += PluginDiagnosticIssue(
                    severity = PluginDiagnosticSeverity.ERROR,
                    category = PluginDiagnosticCategory.CONTRIBUTIONS,
                    message = Strings.plugin_diagnostic_command_blank_id.strOr(context, index + 1),
                    fixHint = Strings.plugin_diagnostic_command_fix.strOr(context),
                )
            } else {
                nonBlankCommandIds += commandId
                if (!HostCommands.isSupported(commandId) &&
                    !commandContext.supportsRuntimePluginCommands
                ) {
                    issues += PluginDiagnosticIssue(
                        severity = PluginDiagnosticSeverity.WARNING,
                        category = PluginDiagnosticCategory.COMPATIBILITY,
                        message = Strings.plugin_diagnostic_command_unsupported.strOr(context, commandId),
                        fixHint = Strings.plugin_diagnostic_command_fix.strOr(context),
                    )
                }
            }

            if (command.title.isBlank()) {
                issues += PluginDiagnosticIssue(
                    severity = PluginDiagnosticSeverity.ERROR,
                    category = PluginDiagnosticCategory.CONTRIBUTIONS,
                    message = Strings.plugin_diagnostic_command_blank_title.strOr(context, index + 1),
                    fixHint = Strings.plugin_diagnostic_command_fix.strOr(context),
                )
            }
        }

        val duplicateIds = nonBlankCommandIds.findDuplicates()
        if (duplicateIds.isNotEmpty()) {
            issues += PluginDiagnosticIssue(
                severity = PluginDiagnosticSeverity.ERROR,
                category = PluginDiagnosticCategory.CONTRIBUTIONS,
                message = Strings.plugin_diagnostic_command_duplicate_id.strOr(
                    context,
                    duplicateIds.sorted().joinToString(", ")
                ),
                fixHint = Strings.plugin_diagnostic_command_fix.strOr(context),
            )
        }
    }

    private fun inspectMenus(
        context: Context,
        manifest: PluginManifest,
        commandContext: CommandInspectionContext,
        issues: MutableList<PluginDiagnosticIssue>,
    ): Set<String> {
        val menus = manifest.contributions?.menus ?: return emptySet()
        val customMenuCommandIds = mutableSetOf<String>()

        inspectMenuItems(
            context = context,
            location = "editor/context",
            items = menus.editorContext.orEmpty(),
            isWhenSupported = ::isSupportedEditorWhenExpression,
            commandContext = commandContext,
            customMenuCommandIds = customMenuCommandIds,
            issues = issues,
        )
        inspectMenuItems(
            context = context,
            location = "editor/toolbar",
            items = menus.editorToolbar.orEmpty(),
            isWhenSupported = ::isSupportedEditorWhenExpression,
            commandContext = commandContext,
            customMenuCommandIds = customMenuCommandIds,
            issues = issues,
        )
        inspectMenuItems(
            context = context,
            location = "filetree/context",
            items = menus.fileTreeContext.orEmpty(),
            isWhenSupported = ::isSupportedFileTreeWhenExpression,
            commandContext = commandContext,
            customMenuCommandIds = customMenuCommandIds,
            issues = issues,
        )

        return customMenuCommandIds
    }

    private fun inspectMenuItems(
        context: Context,
        location: String,
        items: List<PluginMenuItem>,
        isWhenSupported: (String?) -> Boolean,
        commandContext: CommandInspectionContext,
        customMenuCommandIds: MutableSet<String>,
        issues: MutableList<PluginDiagnosticIssue>,
    ) {
        items.forEach { item ->
            val commandId = item.command.trim()
            if (commandId.isBlank()) {
                issues += PluginDiagnosticIssue(
                    severity = PluginDiagnosticSeverity.ERROR,
                    category = PluginDiagnosticCategory.CONTRIBUTIONS,
                    message = Strings.plugin_diagnostic_menu_blank_command.strOr(context, location),
                    fixHint = Strings.plugin_diagnostic_command_fix.strOr(context),
                )
            } else if (HostCommands.isSupported(commandId)) {
                // 宿主白名单命令，静态合法。
            } else if (commandContext.supportsRuntimePluginCommands) {
                customMenuCommandIds += commandId
                if (commandId !in commandContext.declaredCommandIds) {
                    issues += PluginDiagnosticIssue(
                        severity = PluginDiagnosticSeverity.WARNING,
                        category = PluginDiagnosticCategory.CONTRIBUTIONS,
                        message = Strings.plugin_diagnostic_menu_plugin_command_not_declared.strOr(
                            context,
                            commandId,
                            location
                        ),
                        fixHint = Strings.plugin_diagnostic_menu_plugin_command_not_declared_fix
                            .strOr(context),
                    )
                }
            } else {
                issues += PluginDiagnosticIssue(
                    severity = PluginDiagnosticSeverity.ERROR,
                    category = PluginDiagnosticCategory.COMPATIBILITY,
                    message = Strings.plugin_diagnostic_menu_unsupported_command.strOr(
                        context,
                        commandId,
                        location
                    ),
                    fixHint = Strings.plugin_diagnostic_command_fix.strOr(context),
                )
            }

            val whenExpr = item.`when`?.trim().orEmpty()
            if (whenExpr.isNotBlank() && !isWhenSupported(whenExpr)) {
                issues += PluginDiagnosticIssue(
                    severity = PluginDiagnosticSeverity.WARNING,
                    category = PluginDiagnosticCategory.COMPATIBILITY,
                    message = Strings.plugin_diagnostic_menu_unknown_when.strOr(context, whenExpr, location),
                    fixHint = Strings.plugin_diagnostic_menu_when_fix.strOr(context),
                )
            }
        }
    }

    private fun inspectCommandPermission(
        context: Context,
        commandContext: CommandInspectionContext,
        customMenuCommandIds: Set<String>,
        issues: MutableList<PluginDiagnosticIssue>,
    ) {
        if (!commandContext.supportsRuntimePluginCommands) return
        if (commandContext.hasCommandExecutePermission) return

        val customCommandIds = commandContext.declaredCustomCommandIds + customMenuCommandIds
        if (customCommandIds.isEmpty()) return

        issues += PluginDiagnosticIssue(
            severity = PluginDiagnosticSeverity.WARNING,
            category = PluginDiagnosticCategory.PERMISSIONS,
            message = Strings.plugin_diagnostic_command_permission_missing.strOr(
                context,
                customCommandIds.sorted().joinToString(", ")
            ),
            fixHint = Strings.plugin_diagnostic_command_permission_missing_fix.strOr(context),
        )
    }

    private fun inspectUnsupportedContributions(
        context: Context,
        manifest: PluginManifest,
        issues: MutableList<PluginDiagnosticIssue>,
    ) {
        if (!manifest.contributions?.panels.isNullOrEmpty()) {
            issues += PluginDiagnosticIssue(
                severity = PluginDiagnosticSeverity.WARNING,
                category = PluginDiagnosticCategory.COMPATIBILITY,
                message = Strings.plugin_diagnostic_panels_unsupported.strOr(context),
                fixHint = Strings.plugin_diagnostic_panels_fix.strOr(context),
            )
        }
    }

    private fun inspectFileIcons(
        context: Context,
        plugin: InstalledPlugin,
        issues: MutableList<PluginDiagnosticIssue>,
    ) {
        plugin.manifest.contributions?.fileIcons.orEmpty().forEachIndexed { index, icon ->
            if (icon.icon.isBlank()) {
                issues += PluginDiagnosticIssue(
                    severity = PluginDiagnosticSeverity.WARNING,
                    category = PluginDiagnosticCategory.CONTRIBUTIONS,
                    message = Strings.plugin_diagnostic_file_icon_blank_icon.strOr(context, index + 1),
                    fixHint = Strings.plugin_diagnostic_file_icon_fix.strOr(context),
                )
                return@forEachIndexed
            }

            val hasMatchers = icon.extensions.orEmpty()
                .mapNotNull(::normalizeExtension)
                .isNotEmpty() || icon.fileNames.orEmpty()
                .mapNotNull(::normalizeFileName)
                .isNotEmpty()
            if (!hasMatchers) {
                issues += PluginDiagnosticIssue(
                    severity = PluginDiagnosticSeverity.WARNING,
                    category = PluginDiagnosticCategory.CONTRIBUTIONS,
                    message = Strings.plugin_diagnostic_file_icon_missing_matchers.strOr(context, index + 1),
                    fixHint = Strings.plugin_diagnostic_file_icon_fix.strOr(context),
                )
            }

            val iconSpec = icon.icon.trim()
            if (iconSpec.startsWith("builtin:", ignoreCase = true)) {
                return@forEachIndexed
            }

            if (!isSafePluginRelativePath(iconSpec)) {
                issues += PluginDiagnosticIssue(
                    severity = PluginDiagnosticSeverity.ERROR,
                    category = PluginDiagnosticCategory.MANIFEST,
                    message = Strings.plugin_diagnostic_file_icon_path_invalid.strOr(context, iconSpec),
                    fixHint = Strings.plugin_diagnostic_file_icon_fix.strOr(context),
                )
                return@forEachIndexed
            }

            if (!File(plugin.directory, iconSpec).isFile) {
                issues += PluginDiagnosticIssue(
                    severity = PluginDiagnosticSeverity.ERROR,
                    category = PluginDiagnosticCategory.CONTRIBUTIONS,
                    message = Strings.plugin_diagnostic_file_icon_file_missing.strOr(context, iconSpec),
                    fixHint = Strings.plugin_diagnostic_file_icon_fix.strOr(context),
                )
            }
        }
    }

    private fun findDuplicatePermissionIds(permissions: List<String>): Set<String> {
        return permissions
            .mapNotNull { PluginPermission.fromId(it)?.id }
            .findDuplicates()
    }

    private fun buildCommandInspectionContext(
        manifest: PluginManifest,
    ): CommandInspectionContext {
        val supportsRuntimePluginCommands = manifest.type.equals(PluginTypes.SCRIPT, ignoreCase = true) ||
            manifest.type.equals(PluginTypes.HYBRID, ignoreCase = true)
        val declaredCommandIds = manifest.contributions?.commands.orEmpty()
            .asSequence()
            .map { command -> command.id.trim() }
            .filter { id -> id.isNotBlank() }
            .toSet()
        val declaredCustomCommandIds = declaredCommandIds.filterNot(HostCommands::isSupported).toSet()
        val declaredPermissions = PluginPermission.parseList(manifest.permissions)
        val optionalPermissions = PluginPermission.parseList(manifest.optionalPermissions)
        return CommandInspectionContext(
            supportsRuntimePluginCommands = supportsRuntimePluginCommands,
            declaredCommandIds = declaredCommandIds,
            declaredCustomCommandIds = declaredCustomCommandIds,
            hasCommandExecutePermission = PluginPermission.COMMAND_EXECUTE in declaredPermissions ||
                PluginPermission.COMMAND_EXECUTE in optionalPermissions,
        )
    }

    private fun List<String>.findDuplicates(): Set<String> {
        return groupingBy { it }
            .eachCount()
            .filterValues { it > 1 }
            .keys
    }

    private fun normalizeExtension(raw: String?): String? {
        return raw
            ?.trim()
            ?.removePrefix(".")
            ?.lowercase()
            ?.takeIf { it.isNotEmpty() }
    }

    private fun normalizeFileName(raw: String?): String? {
        return raw
            ?.trim()
            ?.lowercase()
            ?.takeIf { it.isNotEmpty() }
    }

    private fun isSupportedFileTreeWhenExpression(whenExpr: String?): Boolean {
        return when (whenExpr?.trim().orEmpty()) {
            "" -> true
            "isDirectory",
            "isFile",
            "!isDirectory",
            "!isFile",
            "isDirectory == true",
            "isDirectory == false",
            "isFile == true",
            "isFile == false" -> true

            else -> false
        }
    }

    private fun isSupportedEditorWhenExpression(whenExpr: String?): Boolean {
        return when (whenExpr?.trim().orEmpty()) {
            "" -> true
            "isDirty",
            "!isDirty",
            "isDirty == true",
            "isDirty == false" -> true

            else -> false
        }
    }

    private fun resolveCurrentAppVersion(context: Context): String? {
        return runCatching {
            context.packageManager
                .getPackageInfo(context.packageName, 0)
                .versionName
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }
}
