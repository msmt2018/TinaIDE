package com.wuxianggujun.tinaide.ui.compose.state.editor

import com.wuxianggujun.tinaide.core.textengine.TextBuffer
import com.wuxianggujun.tinaide.search.TextContentProvider

/**
 * Tina 文本缓冲区到 TextContentProvider 的适配器。
 */
class TinaTextContentProvider(
    private val textBuffer: TextBuffer
) : TextContentProvider {

    override fun getText(): String = textBuffer.substring(0, textBuffer.length)

    override fun getPosition(charIndex: Int): TextContentProvider.Position {
        val position = textBuffer.offsetToPosition(charIndex.coerceIn(0, textBuffer.length))
        return TextContentProvider.Position(
            line = position.line,
            column = position.column
        )
    }
}
