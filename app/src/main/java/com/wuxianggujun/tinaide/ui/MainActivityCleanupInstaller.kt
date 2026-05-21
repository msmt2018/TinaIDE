package com.wuxianggujun.tinaide.ui

import android.content.Context
import com.wuxianggujun.tinaide.service.EditorKeepAliveService

/**
 * 收口 MainActivity 销毁阶段的宿主清理编排。
 */
internal fun installMainActivityCleanup(
    context: Context,
    bottomPanelController: MainActivityBottomPanelActionBridge,
    fileTreeActionBridge: MainActivityFileTreeActionBridge,
    editorActionBridge: MainActivityEditorActionBridge,
    shortcutDispatcher: MainActivityShortcutDispatcher,
) {
    // 停止保活服务
    EditorKeepAliveService.stop(context)
    bottomPanelController.clear()
    fileTreeActionBridge.clear()
    editorActionBridge.clear()
    shortcutDispatcher.clear()
}
