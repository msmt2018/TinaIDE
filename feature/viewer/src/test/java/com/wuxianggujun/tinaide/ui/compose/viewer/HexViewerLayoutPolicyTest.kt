package com.wuxianggujun.tinaide.ui.compose.viewer

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HexViewerLayoutPolicyTest {

    @Test
    fun initialChrome_shouldKeepHeavyPanelsCollapsed() {
        assertThat(initialHexSearchPanelExpanded()).isFalse()
        assertThat(initialHexFooterToolsExpanded()).isFalse()
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
}
