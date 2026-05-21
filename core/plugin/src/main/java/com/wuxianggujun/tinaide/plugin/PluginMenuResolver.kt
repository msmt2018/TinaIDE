package com.wuxianggujun.tinaide.plugin

import android.content.Context
import com.wuxianggujun.tinaide.core.commands.HostCommands
import com.wuxianggujun.tinaide.plugin.script.api.PluginCommandRegistry
import java.io.File
import timber.log.Timber

data class ResolvedHostMenuItem(
    val title: String,
    val commandId: String,
    val group: String,
    val pluginId: String
)

object PluginMenuResolver {
    private const val TAG = "PluginMenuResolver"

    private const val DEFAULT_GROUP: String = "9_plugin"

    fun resolveEditorContextMenuItems(
        context: Context,
        installedPlugins: List<InstalledPlugin>,
        file: File,
        isDirty: Boolean
    ): List<ResolvedHostMenuItem> {
        val items = buildList {
            installedPlugins.asSequence()
                .filter { it.enabled }
                .forEach { plugin ->
                    val contributions = plugin.manifest.contributions ?: return@forEach
                    val menuItems = contributions.menus?.editorContext.orEmpty()
                    if (menuItems.isEmpty()) return@forEach

                    val commandTitleById = contributions.commands
                        ?.associateBy({ it.id }, { it.title })
                        .orEmpty()

                    menuItems.forEach { menuItem ->
                        val commandId = menuItem.command
                        val supportsHostCommand = HostCommands.isSupported(commandId)
                        val supportsPluginCommand = PluginCommandRegistry.isRegistered(commandId, plugin.manifest.id)
                        if (!supportsHostCommand && !supportsPluginCommand) {
                            Timber.tag(TAG).i("Ignore unsupported command: $commandId (plugin=${plugin.manifest.id})")
                            return@forEach
                        }
                        if (!matchesEditorWhen(menuItem.`when`, isDirty)) return@forEach

                        val title = commandTitleById[commandId]
                            ?: PluginCommandRegistry.titleFor(commandId, plugin.manifest.id)
                            ?: HostCommands.titleResOrNull(commandId)?.let(context::getString)
                            ?: commandId
                        add(
                            ResolvedHostMenuItem(
                                title = title,
                                commandId = commandId,
                                group = menuItem.group ?: DEFAULT_GROUP,
                                pluginId = plugin.manifest.id
                            )
                        )
                    }
                }
        }

        return items
            .distinctBy { "${it.pluginId}#${it.group}#${it.commandId}#${it.title}" }
            .sortedWith(
                compareBy<ResolvedHostMenuItem> { it.group }
                    .thenBy { it.title }
                    .thenBy { it.pluginId }
                    .thenBy { it.commandId }
            )
    }

    fun resolveFileTreeContextMenuItems(
        context: Context,
        installedPlugins: List<InstalledPlugin>,
        file: File,
        isDirectory: Boolean
    ): List<ResolvedHostMenuItem> {
        val items = buildList {
            installedPlugins.asSequence()
                .filter { it.enabled }
                .forEach { plugin ->
                    val contributions = plugin.manifest.contributions ?: return@forEach
                    val menuItems = contributions.menus?.fileTreeContext.orEmpty()
                    if (menuItems.isEmpty()) return@forEach

                    val commandTitleById = contributions.commands
                        ?.associateBy({ it.id }, { it.title })
                        .orEmpty()

                    menuItems.forEach { menuItem ->
                        val commandId = menuItem.command
                        val supportsHostCommand = HostCommands.isSupported(commandId)
                        val supportsPluginCommand = PluginCommandRegistry.isRegistered(commandId, plugin.manifest.id)
                        if (!supportsHostCommand && !supportsPluginCommand) {
                            Timber.tag(TAG).i("Ignore unsupported command: $commandId (plugin=${plugin.manifest.id})")
                            return@forEach
                        }
                        if (!matchesWhen(menuItem.`when`, isDirectory)) return@forEach

                        val title = commandTitleById[commandId]
                            ?: PluginCommandRegistry.titleFor(commandId, plugin.manifest.id)
                            ?: HostCommands.titleResOrNull(commandId)?.let(context::getString)
                            ?: commandId
                        add(
                            ResolvedHostMenuItem(
                                title = title,
                                commandId = commandId,
                                group = menuItem.group ?: DEFAULT_GROUP,
                                pluginId = plugin.manifest.id
                            )
                        )
                    }
                }
        }

        return items
            .distinctBy { "${it.pluginId}#${it.group}#${it.commandId}#${it.title}" }
            .sortedWith(
                compareBy<ResolvedHostMenuItem> { it.group }
                    .thenBy { it.title }
                    .thenBy { it.pluginId }
                    .thenBy { it.commandId }
            )
    }

    private fun matchesWhen(whenExpr: String?, isDirectory: Boolean): Boolean {
        val expr = whenExpr?.trim().orEmpty()
        if (expr.isBlank()) return true

        return when (expr) {
            "isDirectory" -> isDirectory
            "isFile" -> !isDirectory
            "!isDirectory" -> !isDirectory
            "!isFile" -> isDirectory
            "isDirectory == true" -> isDirectory
            "isDirectory == false" -> !isDirectory
            "isFile == true" -> !isDirectory
            "isFile == false" -> isDirectory
            else -> {
                Timber.tag(TAG).i("Ignore unknown when expr: $expr")
                false
            }
        }
    }

    private fun matchesEditorWhen(whenExpr: String?, isDirty: Boolean): Boolean {
        val expr = whenExpr?.trim().orEmpty()
        if (expr.isBlank()) return true

        return when (expr) {
            "isDirty" -> isDirty
            "!isDirty" -> !isDirty
            "isDirty == true" -> isDirty
            "isDirty == false" -> !isDirty
            else -> {
                Timber.tag(TAG).i("Ignore unknown when expr: $expr")
                false
            }
        }
    }
}
