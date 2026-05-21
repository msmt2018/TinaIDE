package com.wuxianggujun.tinaide.ui.compose.screens.testing

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TreeSitterTestScreenSupportTest {

    @Test
    fun requireSampleOptions_shouldRejectEmptyCatalog() {
        val error = runCatching {
            TreeSitterTestScreenSupport.requireSampleOptions(emptyList())
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(error?.message).contains("must not be empty")
    }

    @Test
    fun resolveSelectedSample_shouldReturnMatchingOption() {
        val samples = DevEditorTestCatalog.treeSitterSampleOptions

        val selected = TreeSitterTestScreenSupport.resolveSelectedSample(
            samples = samples,
            selectedSampleId = "make"
        )

        assertThat(selected.id).isEqualTo("make")
        assertThat(selected.fixture.relativePath).isEqualTo("Makefile")
    }

    @Test
    fun resolveSelectedSample_shouldFallbackToFirstOptionWhenIdUnknown() {
        val samples = DevEditorTestCatalog.treeSitterSampleOptions

        val selected = TreeSitterTestScreenSupport.resolveSelectedSample(
            samples = samples,
            selectedSampleId = "unknown-language"
        )

        assertThat(selected.id).isEqualTo(samples.first().id)
        assertThat(selected.fixture.relativePath).isEqualTo(samples.first().fixture.relativePath)
    }

    @Test
    fun buildInfoCardText_shouldJoinTrimmedNonBlankSections() {
        val infoText = TreeSitterTestScreenSupport.buildInfoCardText(
            "  first hint  ",
            "",
            "second hint",
            "   ",
            "third hint"
        )

        assertThat(infoText).isEqualTo("first hint\n\nsecond hint\n\nthird hint")
    }

    @Test
    fun buildDialogOptions_shouldPreserveSampleOrderAndLabels() {
        val options = TreeSitterTestScreenSupport.buildDialogOptions(
            DevEditorTestCatalog.treeSitterSampleOptions
        )

        assertThat(options).containsExactly(
            "cmake" to "CMakeLists.txt",
            "make" to "Makefile",
            "json" to "preview.json",
            "xml" to "layout.xml",
            "kotlin" to "ThemePreview.kt"
        ).inOrder()
    }

    @Test
    fun resolveLspControlsState_shouldKeepBuiltinCmakeToggleEnabled() {
        val state = TreeSitterTestScreenSupport.resolveLspControlsState()

        assertThat(state.builtinCmakeLspControlEnabled).isTrue()
    }
}
