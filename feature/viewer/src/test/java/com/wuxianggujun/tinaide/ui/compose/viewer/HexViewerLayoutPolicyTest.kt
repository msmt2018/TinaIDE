package com.wuxianggujun.tinaide.ui.compose.viewer

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HexViewerLayoutPolicyTest {

    @Test
    fun initialChrome_shouldKeepHeavyPanelsCollapsed() {
        assertThat(initialHexSearchPanelExpanded()).isFalse()
        assertThat(initialHexFooterToolsExpanded()).isFalse()
        assertThat(initialHexAnalysisPanelExpanded()).isFalse()
    }

    @Test
    fun footerDetails_shouldShowOnlyWhenExpandedOrPatchActivityExists() {
        assertThat(
            shouldShowHexFooterDetails(
                isUserExpanded = false,
                hasPatchActivity = false
            )
        ).isFalse()

        assertThat(
            shouldShowHexFooterDetails(
                isUserExpanded = true,
                hasPatchActivity = false
            )
        ).isTrue()

        assertThat(
            shouldShowHexFooterDetails(
                isUserExpanded = false,
                hasPatchActivity = true
            )
        ).isTrue()
    }

    @Test
    fun analysisPanel_shouldDockOnlyOnWideLayouts() {
        assertThat(shouldDockHexAnalysisPanel(availableWidthDp = HEX_DOCKED_ANALYSIS_MIN_WIDTH_DP - 1))
            .isFalse()
        assertThat(shouldDockHexAnalysisPanel(availableWidthDp = HEX_DOCKED_ANALYSIS_MIN_WIDTH_DP))
            .isTrue()
        assertThat(shouldDockHexAnalysisPanel(availableWidthDp = HEX_DOCKED_ANALYSIS_MIN_WIDTH_DP + 1))
            .isTrue()
    }

    @Test
    fun analysisPanel_shouldChooseDockedPanelOrDialogByWidth() {
        assertThat(
            shouldShowHexDockedAnalysisPanel(
                isUserExpanded = true,
                canDock = true,
                hasContent = true
            )
        ).isTrue()

        assertThat(
            shouldShowHexDockedAnalysisPanel(
                isUserExpanded = true,
                canDock = false,
                hasContent = true
            )
        ).isFalse()

        assertThat(
            shouldOpenHexAnalysisDialog(
                isUserExpanded = true,
                canDock = false,
                hasContent = true
            )
        ).isTrue()

        assertThat(
            shouldOpenHexAnalysisDialog(
                isUserExpanded = true,
                canDock = false,
                hasContent = false
            )
        ).isFalse()
    }
}
