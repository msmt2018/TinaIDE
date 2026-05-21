package com.wuxianggujun.tinaide.ui.compose.screens.settings.sections

import android.view.KeyEvent
import com.wuxianggujun.tinaide.core.config.KeyboardShortcut
import com.wuxianggujun.tinaide.core.config.ShortcutAction

internal object KeyboardSettingsSectionSupport {

    fun buildShortcutTitle(
        displayName: String,
        isModified: Boolean,
        modifiedSuffix: String
    ): String = if (isModified) {
        displayName + modifiedSuffix
    } else {
        displayName
    }

    fun isShortcutModified(
        currentShortcut: KeyboardShortcut,
        defaultShortcut: KeyboardShortcut
    ): Boolean = currentShortcut != defaultShortcut

    fun findShortcutConflict(
        shortcuts: Map<ShortcutAction, KeyboardShortcut>,
        shortcut: KeyboardShortcut,
        excludeAction: ShortcutAction
    ): ShortcutAction? = shortcuts.entries.firstOrNull { (action, existingShortcut) ->
        action != excludeAction && existingShortcut == shortcut
    }?.key

    fun captureShortcutOrNull(
        keyCode: Int,
        ctrl: Boolean,
        shift: Boolean,
        alt: Boolean
    ): KeyboardShortcut? = if (isModifierKey(keyCode)) {
        null
    } else {
        KeyboardShortcut(
            keyCode = keyCode,
            ctrl = ctrl,
            shift = shift,
            alt = alt
        )
    }

    private fun isModifierKey(keyCode: Int): Boolean = keyCode == KeyEvent.KEYCODE_CTRL_LEFT ||
        keyCode == KeyEvent.KEYCODE_CTRL_RIGHT ||
        keyCode == KeyEvent.KEYCODE_SHIFT_LEFT ||
        keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT ||
        keyCode == KeyEvent.KEYCODE_ALT_LEFT ||
        keyCode == KeyEvent.KEYCODE_ALT_RIGHT ||
        keyCode == KeyEvent.KEYCODE_META_LEFT ||
        keyCode == KeyEvent.KEYCODE_META_RIGHT
}
