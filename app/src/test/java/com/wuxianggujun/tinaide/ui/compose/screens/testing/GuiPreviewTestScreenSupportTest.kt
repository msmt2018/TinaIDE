package com.wuxianggujun.tinaide.ui.compose.screens.testing

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GuiPreviewTestScreenSupportTest {

    @Test
    fun validateManualPath_shouldRejectBlankInput() {
        val outcome = GuiPreviewTestScreenSupport.validateManualPath(
            input = " \n\t ",
            fileExists = { true }
        )

        assertThat(outcome).isEqualTo(GuiPreviewManualPathOutcome.EmptyPath)
    }

    @Test
    fun validateManualPath_shouldReturnMissingFileWithNormalizedPath() {
        val outcome = GuiPreviewTestScreenSupport.validateManualPath(
            input = "  /tmp/demo.png  ",
            fileExists = { false }
        )

        assertThat(outcome).isInstanceOf(GuiPreviewManualPathOutcome.FileMissing::class.java)
        val missing = outcome as GuiPreviewManualPathOutcome.FileMissing
        assertThat(missing.normalizedPath).isEqualTo("/tmp/demo.png")
    }

    @Test
    fun validateManualPath_shouldReturnReadyToLoadWithNormalizedPath() {
        val outcome = GuiPreviewTestScreenSupport.validateManualPath(
            input = "\nC:/images/demo.png\t",
            fileExists = { path -> path == "C:/images/demo.png" }
        )

        assertThat(outcome).isInstanceOf(GuiPreviewManualPathOutcome.ReadyToLoad::class.java)
        val ready = outcome as GuiPreviewManualPathOutcome.ReadyToLoad
        assertThat(ready.normalizedPath).isEqualTo("C:/images/demo.png")
    }
}
