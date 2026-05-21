package com.wuxianggujun.tinaide.ui.compose.state.editor

import com.wuxianggujun.tinaide.core.lsp.LocationItem

/**
 * 编辑器内嵌 Peek Definition 面板状态。
 */
data class PeekDefinitionPanelState(
    val ownerTabId: String,
    val title: String,
    val locations: List<LocationItem>,
    val isLoading: Boolean = false
)
