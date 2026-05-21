package com.wuxianggujun.tinaide.ui

import com.wuxianggujun.tinaide.ui.compose.components.BottomPanelDragState
import com.wuxianggujun.tinaide.ui.compose.components.PanelHeightPreset

/**
 * MainActivity 的底部面板宿主桥接。
 *
 * 通过 BottomPanelController 暴露最小控制面，避免宿主层泄漏 Compose 内部拖拽状态。
 */
class MainActivityBottomPanelActionBridge : BottomPanelController {
    private var isExpandedProvider: (() -> Boolean)? = null
    private var expandToDefaultAction: (suspend () -> Unit)? = null
    private var snapToDefaultAction: (suspend () -> Unit)? = null
    private var collapseAction: (suspend () -> Unit)? = null
    private var collapseImmediateAction: (suspend () -> Unit)? = null

    fun bind(bottomPanelDragState: BottomPanelDragState) {
        isExpandedProvider = { bottomPanelDragState.isExpanded }
        expandToDefaultAction = {
            bottomPanelDragState.expandToDefault()
        }
        snapToDefaultAction = {
            bottomPanelDragState.snapToFraction(PanelHeightPreset.DEFAULT)
        }
        collapseAction = {
            bottomPanelDragState.collapse()
        }
        collapseImmediateAction = {
            bottomPanelDragState.collapseImmediate()
        }
    }

    override fun isExpanded(): Boolean = isExpandedProvider?.invoke() == true

    override suspend fun expandToDefault() {
        expandToDefaultAction?.invoke()
    }

    override suspend fun snapToDefault() {
        snapToDefaultAction?.invoke()
    }

    override suspend fun collapse() {
        collapseAction?.invoke()
    }

    override suspend fun collapseImmediate() {
        collapseImmediateAction?.invoke()
    }

    fun clear() {
        isExpandedProvider = null
        expandToDefaultAction = null
        snapToDefaultAction = null
        collapseAction = null
        collapseImmediateAction = null
    }
}
