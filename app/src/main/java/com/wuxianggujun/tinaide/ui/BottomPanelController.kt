package com.wuxianggujun.tinaide.ui

/**
 * 底部面板宿主控制面。
 *
 * 只暴露 Activity / 非 Compose 侧真正需要的动作与查询，
 * 避免向外泄漏 Compose 内部的 BottomPanelDragState。
 */
interface BottomPanelController {
    fun isExpanded(): Boolean

    suspend fun expandToDefault()

    suspend fun snapToDefault()

    suspend fun collapse()

    suspend fun collapseImmediate()
}
