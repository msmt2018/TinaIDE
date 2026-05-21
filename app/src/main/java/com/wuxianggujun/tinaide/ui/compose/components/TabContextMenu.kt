package com.wuxianggujun.tinaide.ui.compose.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import com.wuxianggujun.tinaide.core.commands.HostCommandExecutor
import com.wuxianggujun.tinaide.core.commands.HostCommandInvocation
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.plugin.PluginManager
import com.wuxianggujun.tinaide.ui.compose.components.TinaDropdownMenu
import com.wuxianggujun.tinaide.ui.compose.components.TinaDropdownMenuDangerItem
import com.wuxianggujun.tinaide.ui.compose.components.TinaDropdownMenuItem
import java.io.File

/**
 * Tab 长按上下文菜单
 *
 * @param expanded 是否显示菜单
 * @param onDismiss 关闭菜单
 * @param onCloseCurrent 关闭当前标签页
 * @param onCloseOthers 关闭其他标签页
 * @param onCloseAll 关闭全部标签页
 */
@Composable
fun TabContextMenu(
    file: File,
    isDirty: Boolean,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onCloseCurrent: () -> Unit,
    onCloseOthers: () -> Unit,
    onCloseAll: () -> Unit,
    pluginManager: PluginManager,
    hostCommandExecutor: HostCommandExecutor?,
) {
    val enabledPlugins by pluginManager.enabledPluginsFlow.collectAsState()
    val pluginMenuItems = pluginManager.resolveEditorContextMenuItems(enabledPlugins, file, isDirty)

    TinaDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        TinaDropdownMenuItem(
            text = { Text(stringResource(Strings.action_close_current_tab)) },
            onClick = {
                onDismiss()
                onCloseCurrent()
            }
        )

        TinaDropdownMenuItem(
            text = { Text(stringResource(Strings.action_close_other_tabs)) },
            onClick = {
                onDismiss()
                onCloseOthers()
            }
        )

        TinaDropdownMenuDivider()
        TinaDropdownMenuDangerItem(
            text = { Text(stringResource(Strings.action_close_all_tabs)) },
            onClick = {
                onDismiss()
                onCloseAll()
            }
        )

        val pluginActionItems = pluginMenuItems.map { item -> item.title to item.commandId }

        if (pluginActionItems.isNotEmpty()) {
            TinaDropdownMenuDivider()
            TinaDropdownMenuSectionHeader {
                TinaDropdownMenuSectionTitle(
                    text = stringResource(Strings.action_more)
                )
            }
            pluginActionItems.forEach { (title, action) ->
                TinaDropdownMenuItem(
                    text = { Text(title) },
                    onClick = {
                        onDismiss()
                        hostCommandExecutor?.execute(
                            action,
                            HostCommandInvocation(
                                file = file,
                                isDirectory = file.isDirectory,
                                isDirty = isDirty
                            )
                        )
                    }
                )
            }
        }
    }
}
