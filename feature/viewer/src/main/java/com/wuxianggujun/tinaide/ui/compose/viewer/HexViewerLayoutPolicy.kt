package com.wuxianggujun.tinaide.ui.compose.viewer

internal fun initialHexSearchPanelExpanded(): Boolean = false

internal fun initialHexFooterToolsExpanded(): Boolean = false

internal fun initialHexAnalysisPanelExpanded(): Boolean = false

internal fun shouldShowHexFooterDetails(
    isUserExpanded: Boolean,
    hasPatchActivity: Boolean
): Boolean = isUserExpanded || hasPatchActivity

internal fun shouldDockHexAnalysisPanel(availableWidthDp: Int): Boolean =
    availableWidthDp >= HEX_DOCKED_ANALYSIS_MIN_WIDTH_DP

internal fun shouldShowHexDockedAnalysisPanel(
    isUserExpanded: Boolean,
    canDock: Boolean,
    hasContent: Boolean
): Boolean = isUserExpanded && canDock && hasContent

internal fun shouldOpenHexAnalysisDialog(
    isUserExpanded: Boolean,
    canDock: Boolean,
    hasContent: Boolean
): Boolean = isUserExpanded && !canDock && hasContent

internal const val HEX_DOCKED_ANALYSIS_MIN_WIDTH_DP = 920
