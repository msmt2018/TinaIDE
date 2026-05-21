package com.wuxianggujun.tinaide.core.editorview

import androidx.compose.ui.unit.IntOffset
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ContextMenuLayoutResolverTest {

    @Test
    fun resolve_prefersAboveWhenThereIsEnoughSpace() {
        val offset = ContextMenuLayoutResolver.resolve(
            anchorX = 150f,
            anchorY = 220f,
            canvasWidthPx = 320f,
            canvasHeightPx = 360f,
            menuWidthPx = 120f,
            menuHeightPx = 80f
        )

        assertThat(offset).isEqualTo(IntOffset(150, 132))
    }

    @Test
    fun resolve_fallsBackToBelowWhenAboveSpaceIsNotEnough() {
        val offset = ContextMenuLayoutResolver.resolve(
            anchorX = 20f,
            anchorY = 30f,
            canvasWidthPx = 240f,
            canvasHeightPx = 200f,
            menuWidthPx = 120f,
            menuHeightPx = 90f
        )

        assertThat(offset).isEqualTo(IntOffset(20, 38))
    }

    @Test
    fun resolve_clampsXWithinCanvasBounds() {
        val offset = ContextMenuLayoutResolver.resolve(
            anchorX = 400f,
            anchorY = 100f,
            canvasWidthPx = 220f,
            canvasHeightPx = 200f,
            menuWidthPx = 120f,
            menuHeightPx = 60f
        )

        assertThat(offset.x).isEqualTo(92)
        assertThat(offset.y).isEqualTo(32)
    }
}
