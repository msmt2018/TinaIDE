package com.wuxianggujun.tinaide.core.editorview

import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.core.textengine.RopeTextBuffer
import org.junit.Test

class EditorStateSurrogatePairTest {

    private val sampleText = "a😀b"
    private val beforeEmojiOffset = "a".length
    private val afterEmojiOffset = "a😀".length

    @Test
    fun moveLeft_shouldSkipWholeSurrogatePair() {
        val state = createState()

        state.moveCursorTo(afterEmojiOffset)
        state.moveLeft()

        assertThat(state.cursorOffset).isEqualTo(beforeEmojiOffset)
    }

    @Test
    fun moveRight_shouldSkipWholeSurrogatePair() {
        val state = createState()

        state.moveCursorTo(beforeEmojiOffset)
        state.moveRight()

        assertThat(state.cursorOffset).isEqualTo(afterEmojiOffset)
    }

    @Test
    fun backspace_shouldDeleteWholeSurrogatePair() {
        val state = createState()

        state.moveCursorTo(afterEmojiOffset)
        state.backspace()

        assertThat(state.textBuffer.toString()).isEqualTo("ab")
        assertThat(state.cursorOffset).isEqualTo(beforeEmojiOffset)
    }

    @Test
    fun deleteForward_shouldDeleteWholeSurrogatePair() {
        val state = createState()

        state.moveCursorTo(beforeEmojiOffset)
        state.deleteForward()

        assertThat(state.textBuffer.toString()).isEqualTo("ab")
        assertThat(state.cursorOffset).isEqualTo(beforeEmojiOffset)
    }

    private fun createState(): EditorState {
        val buffer = RopeTextBuffer().apply { insert(0, sampleText) }
        return EditorState(buffer)
    }
}
