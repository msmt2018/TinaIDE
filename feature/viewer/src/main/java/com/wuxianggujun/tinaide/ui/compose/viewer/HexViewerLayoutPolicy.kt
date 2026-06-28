package com.wuxianggujun.tinaide.ui.compose.viewer

internal fun initialHexSearchPanelExpanded(): Boolean = false

internal fun initialHexFooterToolsExpanded(): Boolean = false

internal fun shouldShowHexFooterDetails(
    isUserExpanded: Boolean,
    hasPatchActivity: Boolean
): Boolean = isUserExpanded || hasPatchActivity
