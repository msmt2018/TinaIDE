package com.wuxianggujun.tinaide.core.editorview

import android.graphics.Paint
import android.graphics.Typeface
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class EditorLineLayoutCacheTest {

    @Test
    fun xToColumn_withPrefixLayout_shouldChooseNearestColumn() {
        val cache = EditorLineLayoutCache()
        val lineText = "ab\tcd"
        val textVersion = 1L
        val tabSize = 4
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.MONOSPACE
            textSize = 16f
        }
        val layout = cache.getPrefixLayout(
            line = 0,
            lineText = lineText,
            textVersion = textVersion,
            paint = paint,
            tabSize = tabSize
        )
        val prefix = layout.prefix
        val checkpoints = listOf(
            0f to 0,
            ((prefix[0] + prefix[1]) / 2f) + ((prefix[1] - prefix[0]) * 0.1f) to 1,
            prefix[2] to 2,
            ((prefix[3] + prefix[4]) / 2f) - ((prefix[4] - prefix[3]) * 0.1f) to 3,
            prefix[5] to 5
        )

        for ((x, expectedColumn) in checkpoints) {
            assertThat(cache.xToColumn(layout, x)).isEqualTo(expectedColumn)
        }
    }
}
