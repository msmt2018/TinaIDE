package com.wuxianggujun.tinaide.core.editorview

import androidx.compose.ui.unit.IntOffset

internal object ContextMenuLayoutResolver {
    fun resolve(
        anchorX: Float,
        anchorY: Float,
        canvasWidthPx: Float,
        canvasHeightPx: Float,
        menuWidthPx: Float,
        menuHeightPx: Float
    ): IntOffset {
        val margin = 8f
        val maxX = (canvasWidthPx - menuWidthPx - margin).coerceAtLeast(margin)
        val maxY = (canvasHeightPx - menuHeightPx - margin).coerceAtLeast(margin)
        val x = anchorX.coerceIn(margin, maxX)
        val aboveY = anchorY - menuHeightPx - margin
        val belowY = anchorY + margin
        val y = if (aboveY >= margin) {
            aboveY
        } else {
            belowY.coerceIn(margin, maxY)
        }
        return IntOffset(x.toInt(), y.toInt())
    }
}
