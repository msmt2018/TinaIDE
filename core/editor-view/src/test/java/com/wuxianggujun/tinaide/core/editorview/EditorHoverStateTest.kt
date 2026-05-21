package com.wuxianggujun.tinaide.core.editorview

import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.core.textengine.RopeTextBuffer
import kotlinx.coroutines.test.runTest
import org.junit.Test

class EditorHoverStateTest {

    @Test
    fun requestHover_shouldExposeMarkdownResult() = runTest {
        val state = EditorState(RopeTextBuffer().apply { insert(0, "demo") })
        state.onRequestHover = {
            "**Variable** `APP_NAME`"
        }

        state.requestHover()

        val hoverState = state.hoverUiState
        assertThat(hoverState).isInstanceOf(HoverUiState.Visible::class.java)
        assertThat((hoverState as HoverUiState.Visible).markdown).contains("APP_NAME")
    }

    @Test
    fun dismissHover_shouldResetToHidden() = runTest {
        val state = EditorState(RopeTextBuffer().apply { insert(0, "demo") })
        state.onRequestHover = {
            "**Target** `core`"
        }
        state.requestHover()

        state.dismissHover()

        assertThat(state.hoverUiState).isEqualTo(HoverUiState.Hidden)
    }
}
