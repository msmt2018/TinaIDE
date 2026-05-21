package com.wuxianggujun.tinaide.ui.compose.screens.testing

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EditorScrollTestScreenSupportTest {

    @Test
    fun contentProfiles_shouldExposeOrderedLineCounts() {
        val profiles = EditorScrollTestScreenSupport.contentProfiles()

        assertThat(profiles.map { it.id }).containsExactly(
            "small",
            "medium",
            "large",
            "xlarge",
            "xxlarge"
        ).inOrder()
        assertThat(profiles.map { it.lineCount }).containsExactly(
            100,
            500,
            1000,
            3000,
            5000
        ).inOrder()
    }

    @Test
    fun resolveContentProfile_shouldFallbackToFirstProfileWhenIdUnknown() {
        val profiles = EditorScrollTestScreenSupport.contentProfiles()

        val resolved = EditorScrollTestScreenSupport.resolveContentProfile(
            profiles = profiles,
            selectedProfileId = "unknown"
        )

        assertThat(resolved).isEqualTo(profiles.first())
    }

    @Test
    fun buildFixture_shouldUseSelectedProfileAndGenerateProbeContent() {
        val profile = EditorScrollContentProfile(
            id = "test",
            labelRes = com.wuxianggujun.tinaide.core.i18n.Strings.editor_scroll_test_content_size_small,
            lineCount = 3
        )

        val fixture = EditorScrollTestScreenSupport.buildFixture(profile)

        assertThat(fixture.relativePath).isEqualTo("EditorScroll_test.kt")
        assertThat(fixture.content).contains("class EditorScrollProbe")
        assertThat(fixture.content).contains("fun marker0003")
        assertThat(fixture.content).doesNotContain("marker0004")
    }
}
