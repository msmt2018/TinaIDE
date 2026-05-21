package com.wuxianggujun.tinaide.core.editorview

import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.core.textengine.RopeTextBuffer
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class EditorSnippetUndoRedoTest {

    @Test
    fun undo_shouldCancelSnippetSessionAndDismissChoiceCompletion() {
        val state = createSnippetState("\${1|one,two|}")

        editorInsert(state, "two")
        val undone = state.undo()

        assertThat(undone).isTrue()
        assertThat(state.textBuffer.substring(0, state.textBuffer.length)).isEmpty()
        assertThat(state.activeSnippetSession).isNull()
        assertThat(state.showCompletion).isFalse()
        assertThat(state.snippetChoiceCompletionActive).isFalse()
    }

    @Test
    fun redo_shouldRestoreTextWithoutRestoringSnippetSession() {
        val state = createSnippetState("\${1|one,two|}")

        editorInsert(state, "two")
        state.undo()
        val redone = state.redo()

        assertThat(redone).isTrue()
        assertThat(state.textBuffer.substring(0, state.textBuffer.length)).isEqualTo("two")
        assertThat(state.activeSnippetSession).isNull()
        assertThat(state.showCompletion).isFalse()
        assertThat(state.snippetChoiceCompletionActive).isFalse()
    }

    private fun createSnippetState(snippet: String): EditorState {
        val parsed = parseSnippet(snippet)
        val buffer = RopeTextBuffer().apply {
            insert(0, parsed.expandedText)
        }
        return EditorState(buffer).apply {
            startSnippetSession(
                SnippetSession(
                    baseOffset = 0,
                    parsed = parsed
                )
            )
        }
    }
}
