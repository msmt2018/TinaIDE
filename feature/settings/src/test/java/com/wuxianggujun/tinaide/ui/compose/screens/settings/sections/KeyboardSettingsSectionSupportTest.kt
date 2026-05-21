package com.wuxianggujun.tinaide.ui.compose.screens.settings.sections

import android.view.KeyEvent
import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.core.config.KeyboardShortcut
import com.wuxianggujun.tinaide.core.config.ShortcutAction
import org.junit.Test

class KeyboardSettingsSectionSupportTest {

    @Test
    fun buildShortcutTitle_shouldAppendModifiedSuffixOnlyWhenNeeded() {
        assertThat(
            KeyboardSettingsSectionSupport.buildShortcutTitle(
                displayName = "保存",
                isModified = true,
                modifiedSuffix = " (已修改)"
            )
        ).isEqualTo("保存 (已修改)")

        assertThat(
            KeyboardSettingsSectionSupport.buildShortcutTitle(
                displayName = "保存",
                isModified = false,
                modifiedSuffix = " (已修改)"
            )
        ).isEqualTo("保存")
    }

    @Test
    fun isShortcutModified_shouldCompareCurrentAndDefaultShortcut() {
        val defaultShortcut = KeyboardShortcut(KeyEvent.KEYCODE_S, ctrl = true)

        assertThat(
            KeyboardSettingsSectionSupport.isShortcutModified(
                currentShortcut = defaultShortcut,
                defaultShortcut = defaultShortcut
            )
        ).isFalse()

        assertThat(
            KeyboardSettingsSectionSupport.isShortcutModified(
                currentShortcut = KeyboardShortcut(KeyEvent.KEYCODE_S, ctrl = true, shift = true),
                defaultShortcut = defaultShortcut
            )
        ).isTrue()
    }

    @Test
    fun findShortcutConflict_shouldIgnoreEditedActionAndReturnOtherConflict() {
        val shortcuts = mapOf(
            ShortcutAction.SAVE to KeyboardShortcut(KeyEvent.KEYCODE_S, ctrl = true),
            ShortcutAction.SAVE_ALL to KeyboardShortcut(KeyEvent.KEYCODE_S, ctrl = true, shift = true),
            ShortcutAction.CLOSE_TAB to KeyboardShortcut(KeyEvent.KEYCODE_W, ctrl = true)
        )

        assertThat(
            KeyboardSettingsSectionSupport.findShortcutConflict(
                shortcuts = shortcuts,
                shortcut = KeyboardShortcut(KeyEvent.KEYCODE_S, ctrl = true),
                excludeAction = ShortcutAction.SAVE
            )
        ).isNull()

        assertThat(
            KeyboardSettingsSectionSupport.findShortcutConflict(
                shortcuts = shortcuts,
                shortcut = KeyboardShortcut(KeyEvent.KEYCODE_W, ctrl = true),
                excludeAction = ShortcutAction.SAVE
            )
        ).isEqualTo(ShortcutAction.CLOSE_TAB)
    }

    @Test
    fun captureShortcutOrNull_shouldIgnoreModifierOnlyKeys() {
        assertThat(
            KeyboardSettingsSectionSupport.captureShortcutOrNull(
                keyCode = KeyEvent.KEYCODE_CTRL_LEFT,
                ctrl = true,
                shift = false,
                alt = false
            )
        ).isNull()

        assertThat(
            KeyboardSettingsSectionSupport.captureShortcutOrNull(
                keyCode = KeyEvent.KEYCODE_SHIFT_RIGHT,
                ctrl = false,
                shift = true,
                alt = false
            )
        ).isNull()
    }

    @Test
    fun captureShortcutOrNull_shouldPreserveModifierStateForRegularKeys() {
        assertThat(
            KeyboardSettingsSectionSupport.captureShortcutOrNull(
                keyCode = KeyEvent.KEYCODE_K,
                ctrl = true,
                shift = true,
                alt = false
            )
        ).isEqualTo(
            KeyboardShortcut(
                keyCode = KeyEvent.KEYCODE_K,
                ctrl = true,
                shift = true,
                alt = false
            )
        )
    }
}
