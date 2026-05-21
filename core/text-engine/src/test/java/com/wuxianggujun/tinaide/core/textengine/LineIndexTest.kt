package com.wuxianggujun.tinaide.core.textengine

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LineIndexTest {

    @Test
    fun rebuild_shouldProvideStableLineMappings() {
        val index = LineIndex()

        index.rebuild("alpha\nbeta\n")

        assertThat(index.lineCount).isEqualTo(3)
        assertThat(index.getLineStart(0)).isEqualTo(0)
        assertThat(index.getLineStart(1)).isEqualTo(6)
        assertThat(index.getLineStart(2)).isEqualTo(11)
        assertThat(index.getLineEnd(1, 11)).isEqualTo(10)
        assertThat(index.offsetToLine(8)).isEqualTo(1)
        assertThat(index.positionToOffset(1, 2, 11)).isEqualTo(8)
    }

    @Test
    fun applyChange_shouldUpdateLaterLineOffsets() {
        val index = LineIndex()
        index.rebuild("a\nbc\ndef")

        index.applyChange(
            startOffset = 2,
            oldText = "bc\n",
            newText = "middle\nx\n"
        )

        val textLength = "a\nmiddle\nx\ndef".length
        assertThat(index.lineCount).isEqualTo(4)
        assertThat(index.getLineStart(0)).isEqualTo(0)
        assertThat(index.getLineStart(1)).isEqualTo(2)
        assertThat(index.getLineStart(2)).isEqualTo(9)
        assertThat(index.getLineStart(3)).isEqualTo(11)
        assertThat(index.offsetToLine(10)).isEqualTo(2)
        assertThat(index.positionToOffset(3, 2, textLength)).isEqualTo(13)
    }
}
