package com.wuxianggujun.tinaide.ui

import android.view.KeyEvent
import com.wuxianggujun.tinaide.core.config.KeyboardShortcutManager
import com.wuxianggujun.tinaide.core.config.ShortcutAction
import com.wuxianggujun.tinaide.ui.compose.state.editor.EditorContainerState

/**
 * MainActivity 的硬件键盘快捷键分发器。
 *
 * 负责把快捷键事件翻译成编辑器动作，并复用既有宿主委托完成分发。
 */
class MainActivityShortcutDispatcher {
    private var dispatchShortcutAction: ((ShortcutAction) -> Unit)? = null

    fun bind(
        editorContainerState: EditorContainerState,
        actionsDelegate: MainActivityActionsDelegate,
    ) {
        dispatchShortcutAction = { action ->
            when (action) {
                ShortcutAction.SAVE -> actionsDelegate.saveCurrentFile(editorContainerState)
                ShortcutAction.SAVE_ALL -> actionsDelegate.saveAllFiles(editorContainerState)
                ShortcutAction.CLOSE_TAB -> editorContainerState.requestCloseActiveTab()
                ShortcutAction.CLOSE_ALL_TABS -> editorContainerState.closeAllTabs()
                ShortcutAction.UNDO -> actionsDelegate.performUndo(editorContainerState)
                ShortcutAction.REDO -> actionsDelegate.performRedo(editorContainerState)
                ShortcutAction.TOGGLE_BOOKMARK -> actionsDelegate.toggleBookmark(editorContainerState)
                ShortcutAction.NEXT_BOOKMARK -> actionsDelegate.goToNextBookmark(editorContainerState)
                ShortcutAction.PREV_BOOKMARK -> actionsDelegate.goToPreviousBookmark(editorContainerState)
                ShortcutAction.NAVIGATE_BACK -> editorContainerState.navigateBack()
                ShortcutAction.NAVIGATE_FORWARD -> editorContainerState.navigateForward()
                ShortcutAction.NEXT_TAB -> editorContainerState.selectNextTab()
                ShortcutAction.PREV_TAB -> editorContainerState.selectPreviousTab()
                ShortcutAction.PEEK_DEFINITION -> editorContainerState.requestActiveLspNavigation("peekDefinition")
                ShortcutAction.GOTO_DEFINITION -> editorContainerState.requestActiveLspNavigation("definition")
                ShortcutAction.FIND_REFERENCES -> editorContainerState.requestActiveLspNavigation("references")
                ShortcutAction.GOTO_TYPE_DEFINITION -> editorContainerState.requestActiveLspNavigation("typeDefinition")
                ShortcutAction.GOTO_IMPLEMENTATION -> editorContainerState.requestActiveLspNavigation("implementation")
                ShortcutAction.CODE_ACTIONS -> editorContainerState.requestActiveLspCodeActions()
                ShortcutAction.RENAME_SYMBOL -> editorContainerState.requestActiveLspRename()
                ShortcutAction.SWITCH_HEADER_SOURCE -> editorContainerState.requestActiveLspNavigation("switchHeaderSource")
            }
        }
    }

    fun dispatch(event: KeyEvent?): Boolean {
        if (event == null) return false
        val action = KeyboardShortcutManager.findActionForEvent(event) ?: return false
        val dispatcher = dispatchShortcutAction ?: return false
        dispatcher(action)
        return true
    }

    fun clear() {
        dispatchShortcutAction = null
    }
}
