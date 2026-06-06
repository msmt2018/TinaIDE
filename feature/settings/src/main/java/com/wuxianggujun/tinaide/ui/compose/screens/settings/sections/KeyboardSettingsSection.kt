package com.wuxianggujun.tinaide.ui.compose.screens.settings.sections

import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wuxianggujun.tinaide.core.config.KeyboardShortcut
import com.wuxianggujun.tinaide.core.config.KeyboardShortcutManager
import com.wuxianggujun.tinaide.core.config.ShortcutAction
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.core.i18n.strOr
import com.wuxianggujun.tinaide.ui.compose.components.TinaAlertDialog
import com.wuxianggujun.tinaide.ui.compose.components.TinaConfirmDialog
import com.wuxianggujun.tinaide.ui.compose.components.TinaDialogContentColumn
import com.wuxianggujun.tinaide.ui.compose.components.TinaDialogMessageCard
import com.wuxianggujun.tinaide.ui.compose.components.TinaDialogTitleText
import com.wuxianggujun.tinaide.ui.compose.components.TinaPrimaryButton
import com.wuxianggujun.tinaide.ui.compose.components.TinaTextButton
import com.wuxianggujun.tinaide.ui.compose.screens.settings.components.SettingsCard
import com.wuxianggujun.tinaide.ui.compose.screens.settings.components.SettingsCategoryTitle
import com.wuxianggujun.tinaide.ui.compose.screens.settings.components.SettingsClickableItem
import java.util.Locale

@Composable
internal fun KeyboardSettingsSection() {
    val context = LocalContext.current
    val errorShortcutConflictTemplate = stringResource(Strings.error_shortcut_conflict)
    val toastShortcutsRestored = stringResource(Strings.toast_shortcuts_restored)

    var shortcuts by remember { mutableStateOf(KeyboardShortcutManager.getAllShortcuts()) }
    var editingAction by remember { mutableStateOf<ShortcutAction?>(null) }
    var showResetDialog by remember { mutableStateOf(false) }

    Spacer(modifier = Modifier.height(8.dp))

    // 命令入口
    SettingsCategoryTitle(stringResource(Strings.settings_cat_command_access))

    SettingsCard {
        ShortcutItem(
            action = ShortcutAction.COMMAND_PALETTE,
            shortcut = shortcuts[ShortcutAction.COMMAND_PALETTE]!!,
            isModified = KeyboardSettingsSectionSupport.isShortcutModified(
                currentShortcut = shortcuts[ShortcutAction.COMMAND_PALETTE]!!,
                defaultShortcut = KeyboardShortcutManager.getDefaultShortcut(ShortcutAction.COMMAND_PALETTE)
            ),
            onClick = { editingAction = ShortcutAction.COMMAND_PALETTE },
            showDivider = false
        )
    }

    // 文件操作
    SettingsCategoryTitle(stringResource(Strings.settings_cat_file_operations))

    SettingsCard {
        ShortcutItem(
            action = ShortcutAction.SAVE,
            shortcut = shortcuts[ShortcutAction.SAVE]!!,
            isModified = KeyboardSettingsSectionSupport.isShortcutModified(
                currentShortcut = shortcuts[ShortcutAction.SAVE]!!,
                defaultShortcut = KeyboardShortcutManager.getDefaultShortcut(ShortcutAction.SAVE)
            ),
            onClick = { editingAction = ShortcutAction.SAVE },
            showDivider = true
        )

        ShortcutItem(
            action = ShortcutAction.SAVE_ALL,
            shortcut = shortcuts[ShortcutAction.SAVE_ALL]!!,
            isModified = KeyboardSettingsSectionSupport.isShortcutModified(
                currentShortcut = shortcuts[ShortcutAction.SAVE_ALL]!!,
                defaultShortcut = KeyboardShortcutManager.getDefaultShortcut(ShortcutAction.SAVE_ALL)
            ),
            onClick = { editingAction = ShortcutAction.SAVE_ALL },
            showDivider = false
        )
    }

    // 标签页
    SettingsCategoryTitle(stringResource(Strings.settings_cat_tabs))

    SettingsCard {
        ShortcutItem(
            action = ShortcutAction.CLOSE_TAB,
            shortcut = shortcuts[ShortcutAction.CLOSE_TAB]!!,
            isModified = KeyboardSettingsSectionSupport.isShortcutModified(
                currentShortcut = shortcuts[ShortcutAction.CLOSE_TAB]!!,
                defaultShortcut = KeyboardShortcutManager.getDefaultShortcut(ShortcutAction.CLOSE_TAB)
            ),
            onClick = { editingAction = ShortcutAction.CLOSE_TAB },
            showDivider = true
        )

        ShortcutItem(
            action = ShortcutAction.CLOSE_ALL_TABS,
            shortcut = shortcuts[ShortcutAction.CLOSE_ALL_TABS]!!,
            isModified = KeyboardSettingsSectionSupport.isShortcutModified(
                currentShortcut = shortcuts[ShortcutAction.CLOSE_ALL_TABS]!!,
                defaultShortcut = KeyboardShortcutManager.getDefaultShortcut(ShortcutAction.CLOSE_ALL_TABS)
            ),
            onClick = { editingAction = ShortcutAction.CLOSE_ALL_TABS },
            showDivider = true
        )

        ShortcutItem(
            action = ShortcutAction.NEXT_TAB,
            shortcut = shortcuts[ShortcutAction.NEXT_TAB]!!,
            isModified = KeyboardSettingsSectionSupport.isShortcutModified(
                currentShortcut = shortcuts[ShortcutAction.NEXT_TAB]!!,
                defaultShortcut = KeyboardShortcutManager.getDefaultShortcut(ShortcutAction.NEXT_TAB)
            ),
            onClick = { editingAction = ShortcutAction.NEXT_TAB },
            showDivider = true
        )

        ShortcutItem(
            action = ShortcutAction.PREV_TAB,
            shortcut = shortcuts[ShortcutAction.PREV_TAB]!!,
            isModified = KeyboardSettingsSectionSupport.isShortcutModified(
                currentShortcut = shortcuts[ShortcutAction.PREV_TAB]!!,
                defaultShortcut = KeyboardShortcutManager.getDefaultShortcut(ShortcutAction.PREV_TAB)
            ),
            onClick = { editingAction = ShortcutAction.PREV_TAB },
            showDivider = false
        )
    }

    // 编辑
    SettingsCategoryTitle(stringResource(Strings.settings_cat_edit))

    SettingsCard {
        ShortcutItem(
            action = ShortcutAction.UNDO,
            shortcut = shortcuts[ShortcutAction.UNDO]!!,
            isModified = KeyboardSettingsSectionSupport.isShortcutModified(
                currentShortcut = shortcuts[ShortcutAction.UNDO]!!,
                defaultShortcut = KeyboardShortcutManager.getDefaultShortcut(ShortcutAction.UNDO)
            ),
            onClick = { editingAction = ShortcutAction.UNDO },
            showDivider = true
        )

        ShortcutItem(
            action = ShortcutAction.REDO,
            shortcut = shortcuts[ShortcutAction.REDO]!!,
            isModified = KeyboardSettingsSectionSupport.isShortcutModified(
                currentShortcut = shortcuts[ShortcutAction.REDO]!!,
                defaultShortcut = KeyboardShortcutManager.getDefaultShortcut(ShortcutAction.REDO)
            ),
            onClick = { editingAction = ShortcutAction.REDO },
            showDivider = false
        )
    }

    // 导航
    SettingsCategoryTitle(stringResource(Strings.text_action_group_navigation))

    SettingsCard {
        ShortcutItem(
            action = ShortcutAction.NAVIGATE_BACK,
            shortcut = shortcuts[ShortcutAction.NAVIGATE_BACK]!!,
            isModified = KeyboardSettingsSectionSupport.isShortcutModified(
                currentShortcut = shortcuts[ShortcutAction.NAVIGATE_BACK]!!,
                defaultShortcut = KeyboardShortcutManager.getDefaultShortcut(
                    ShortcutAction.NAVIGATE_BACK
                )
            ),
            onClick = { editingAction = ShortcutAction.NAVIGATE_BACK },
            showDivider = true
        )

        ShortcutItem(
            action = ShortcutAction.NAVIGATE_FORWARD,
            shortcut = shortcuts[ShortcutAction.NAVIGATE_FORWARD]!!,
            isModified = KeyboardSettingsSectionSupport.isShortcutModified(
                currentShortcut = shortcuts[ShortcutAction.NAVIGATE_FORWARD]!!,
                defaultShortcut = KeyboardShortcutManager.getDefaultShortcut(
                    ShortcutAction.NAVIGATE_FORWARD
                )
            ),
            onClick = { editingAction = ShortcutAction.NAVIGATE_FORWARD },
            showDivider = false
        )
    }

    // 代码导航
    SettingsCategoryTitle(stringResource(Strings.menu_section_code))

    SettingsCard {
        ShortcutItem(
            action = ShortcutAction.PEEK_DEFINITION,
            shortcut = shortcuts[ShortcutAction.PEEK_DEFINITION]!!,
            isModified = KeyboardSettingsSectionSupport.isShortcutModified(
                currentShortcut = shortcuts[ShortcutAction.PEEK_DEFINITION]!!,
                defaultShortcut = KeyboardShortcutManager.getDefaultShortcut(
                    ShortcutAction.PEEK_DEFINITION
                )
            ),
            onClick = { editingAction = ShortcutAction.PEEK_DEFINITION },
            showDivider = true
        )

        ShortcutItem(
            action = ShortcutAction.GOTO_DEFINITION,
            shortcut = shortcuts[ShortcutAction.GOTO_DEFINITION]!!,
            isModified = KeyboardSettingsSectionSupport.isShortcutModified(
                currentShortcut = shortcuts[ShortcutAction.GOTO_DEFINITION]!!,
                defaultShortcut = KeyboardShortcutManager.getDefaultShortcut(
                    ShortcutAction.GOTO_DEFINITION
                )
            ),
            onClick = { editingAction = ShortcutAction.GOTO_DEFINITION },
            showDivider = true
        )

        ShortcutItem(
            action = ShortcutAction.FIND_REFERENCES,
            shortcut = shortcuts[ShortcutAction.FIND_REFERENCES]!!,
            isModified = KeyboardSettingsSectionSupport.isShortcutModified(
                currentShortcut = shortcuts[ShortcutAction.FIND_REFERENCES]!!,
                defaultShortcut = KeyboardShortcutManager.getDefaultShortcut(
                    ShortcutAction.FIND_REFERENCES
                )
            ),
            onClick = { editingAction = ShortcutAction.FIND_REFERENCES },
            showDivider = true
        )

        ShortcutItem(
            action = ShortcutAction.GOTO_TYPE_DEFINITION,
            shortcut = shortcuts[ShortcutAction.GOTO_TYPE_DEFINITION]!!,
            isModified = KeyboardSettingsSectionSupport.isShortcutModified(
                currentShortcut = shortcuts[ShortcutAction.GOTO_TYPE_DEFINITION]!!,
                defaultShortcut = KeyboardShortcutManager.getDefaultShortcut(
                    ShortcutAction.GOTO_TYPE_DEFINITION
                )
            ),
            onClick = { editingAction = ShortcutAction.GOTO_TYPE_DEFINITION },
            showDivider = true
        )

        ShortcutItem(
            action = ShortcutAction.GOTO_IMPLEMENTATION,
            shortcut = shortcuts[ShortcutAction.GOTO_IMPLEMENTATION]!!,
            isModified = KeyboardSettingsSectionSupport.isShortcutModified(
                currentShortcut = shortcuts[ShortcutAction.GOTO_IMPLEMENTATION]!!,
                defaultShortcut = KeyboardShortcutManager.getDefaultShortcut(
                    ShortcutAction.GOTO_IMPLEMENTATION
                )
            ),
            onClick = { editingAction = ShortcutAction.GOTO_IMPLEMENTATION },
            showDivider = true
        )

        ShortcutItem(
            action = ShortcutAction.SWITCH_HEADER_SOURCE,
            shortcut = shortcuts[ShortcutAction.SWITCH_HEADER_SOURCE]!!,
            isModified = KeyboardSettingsSectionSupport.isShortcutModified(
                currentShortcut = shortcuts[ShortcutAction.SWITCH_HEADER_SOURCE]!!,
                defaultShortcut = KeyboardShortcutManager.getDefaultShortcut(
                    ShortcutAction.SWITCH_HEADER_SOURCE
                )
            ),
            onClick = { editingAction = ShortcutAction.SWITCH_HEADER_SOURCE },
            showDivider = false
        )
    }

    // 书签
    SettingsCategoryTitle(stringResource(Strings.settings_cat_bookmarks))

    SettingsCard {
        ShortcutItem(
            action = ShortcutAction.TOGGLE_BOOKMARK,
            shortcut = shortcuts[ShortcutAction.TOGGLE_BOOKMARK]!!,
            isModified = KeyboardSettingsSectionSupport.isShortcutModified(
                currentShortcut = shortcuts[ShortcutAction.TOGGLE_BOOKMARK]!!,
                defaultShortcut = KeyboardShortcutManager.getDefaultShortcut(
                    ShortcutAction.TOGGLE_BOOKMARK
                )
            ),
            onClick = { editingAction = ShortcutAction.TOGGLE_BOOKMARK },
            showDivider = true
        )

        ShortcutItem(
            action = ShortcutAction.NEXT_BOOKMARK,
            shortcut = shortcuts[ShortcutAction.NEXT_BOOKMARK]!!,
            isModified = KeyboardSettingsSectionSupport.isShortcutModified(
                currentShortcut = shortcuts[ShortcutAction.NEXT_BOOKMARK]!!,
                defaultShortcut = KeyboardShortcutManager.getDefaultShortcut(
                    ShortcutAction.NEXT_BOOKMARK
                )
            ),
            onClick = { editingAction = ShortcutAction.NEXT_BOOKMARK },
            showDivider = true
        )

        ShortcutItem(
            action = ShortcutAction.PREV_BOOKMARK,
            shortcut = shortcuts[ShortcutAction.PREV_BOOKMARK]!!,
            isModified = KeyboardSettingsSectionSupport.isShortcutModified(
                currentShortcut = shortcuts[ShortcutAction.PREV_BOOKMARK]!!,
                defaultShortcut = KeyboardShortcutManager.getDefaultShortcut(
                    ShortcutAction.PREV_BOOKMARK
                )
            ),
            onClick = { editingAction = ShortcutAction.PREV_BOOKMARK },
            showDivider = false
        )
    }

    // 重置
    SettingsCategoryTitle(stringResource(Strings.settings_cat_reset))

    SettingsCard {
        SettingsClickableItem(
            title = stringResource(Strings.settings_restore_shortcuts),
            subtitle = stringResource(Strings.settings_restore_shortcuts_desc),
            onClick = { showResetDialog = true },
            showDivider = false
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    editingAction?.let { action ->
        ShortcutEditDialog(
            action = action,
            currentShortcut = shortcuts[action]!!,
            onConfirm = { newShortcut ->
                val conflict = KeyboardSettingsSectionSupport.findShortcutConflict(
                    shortcuts = shortcuts,
                    shortcut = newShortcut,
                    excludeAction = action
                )
                if (conflict != null) {
                    Toast.makeText(
                        context,
                        String.format(Locale.getDefault(), errorShortcutConflictTemplate, conflict.displayNameRes.strOr(context)),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    KeyboardShortcutManager.setShortcut(action, newShortcut)
                    shortcuts = KeyboardShortcutManager.getAllShortcuts()
                    editingAction = null
                }
            },
            onReset = {
                KeyboardShortcutManager.resetShortcut(action)
                shortcuts = KeyboardShortcutManager.getAllShortcuts()
                editingAction = null
            },
            onDismiss = { editingAction = null }
        )
    }

    if (showResetDialog) {
        TinaConfirmDialog(
            title = stringResource(Strings.dialog_title_restore_shortcuts),
            message = stringResource(Strings.dialog_message_restore_shortcuts),
            onConfirm = {
                KeyboardShortcutManager.resetAllShortcuts()
                shortcuts = KeyboardShortcutManager.getAllShortcuts()
                showResetDialog = false
                Toast.makeText(context, toastShortcutsRestored, Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showResetDialog = false }
        )
    }
}

@Composable
private fun ShortcutItem(
    action: ShortcutAction,
    shortcut: KeyboardShortcut,
    isModified: Boolean,
    onClick: () -> Unit,
    showDivider: Boolean = true
) {
    val modifiedSuffix = stringResource(Strings.shortcut_modified_suffix)

    SettingsClickableItem(
        title = KeyboardSettingsSectionSupport.buildShortcutTitle(
            displayName = stringResource(action.displayNameRes),
            isModified = isModified,
            modifiedSuffix = modifiedSuffix
        ),
        subtitle = stringResource(action.descriptionRes),
        value = shortcut.toDisplayString(),
        onClick = onClick,
        showDivider = showDivider
    )
}

@Composable
private fun ShortcutEditDialog(
    action: ShortcutAction,
    currentShortcut: KeyboardShortcut,
    onConfirm: (KeyboardShortcut) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    var capturedShortcut by remember { mutableStateOf<KeyboardShortcut?>(null) }
    var isCapturing by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isCapturing) {
        if (isCapturing) {
            focusRequester.requestFocus()
        }
    }

    val dialogTitle = stringResource(Strings.dialog_title_set_shortcut, stringResource(action.displayNameRes))
    val currentShortcutLabel = stringResource(Strings.label_current_shortcut, currentShortcut.toDisplayString())
    val pressShortcutHint = stringResource(Strings.hint_press_shortcut)
    val clickToSetHint = stringResource(Strings.hint_click_to_set)
    val hardwareKeyboardHint = stringResource(Strings.hint_hardware_keyboard)

    TinaAlertDialog(
        onDismissRequest = onDismiss,
        title = { TinaDialogTitleText(dialogTitle) },
        text = {
            TinaDialogContentColumn {
                TinaDialogMessageCard(message = currentShortcutLabel)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 2.dp,
                            color = if (isCapturing) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                            shape = MaterialTheme.shapes.medium
                        )
                        .clickable { isCapturing = true }
                        .focusRequester(focusRequester)
                        .focusable()
                        .onKeyEvent { event ->
                            if (isCapturing && event.type == KeyEventType.KeyDown) {
                                val captured = KeyboardSettingsSectionSupport.captureShortcutOrNull(
                                    keyCode = event.nativeKeyEvent.keyCode,
                                    ctrl = event.nativeKeyEvent.isCtrlPressed,
                                    shift = event.nativeKeyEvent.isShiftPressed,
                                    alt = event.nativeKeyEvent.isAltPressed
                                )
                                if (captured != null) {
                                    capturedShortcut = captured
                                    isCapturing = false
                                }
                                true
                            } else {
                                false
                            }
                        }
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isCapturing) {
                            pressShortcutHint
                        } else {
                            capturedShortcut?.toDisplayString() ?: clickToSetHint
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isCapturing) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }

                if (capturedShortcut != null) {
                    TinaDialogMessageCard(
                        message = stringResource(
                            Strings.label_new_shortcut,
                            capturedShortcut!!.toDisplayString()
                        ),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.36f),
                        textColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                TinaDialogMessageCard(
                    message = hardwareKeyboardHint
                )
            }
        },
        confirmButton = {
            TinaPrimaryButton(
                text = stringResource(Strings.btn_confirm),
                onClick = {
                    capturedShortcut?.let { onConfirm(it) }
                },
                enabled = capturedShortcut != null
            )
        },
        dismissButton = {
            Row {
                TinaTextButton(
                    text = stringResource(Strings.btn_restore_default),
                    onClick = onReset
                )
                TinaTextButton(
                    text = stringResource(Strings.btn_cancel),
                    onClick = onDismiss
                )
            }
        }
    )
}
