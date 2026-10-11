package com.wuxianggujun.tinaide.ui.compose.state.editor

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.core.editorview.EditorState
import com.wuxianggujun.tinaide.core.editorview.TinaEditor
import com.wuxianggujun.tinaide.core.textengine.RopeTextBuffer
import com.wuxianggujun.tinaide.editor.session.DocumentSession
import com.wuxianggujun.tinaide.editor.session.EditorViewState
import com.wuxianggujun.tinaide.editor.session.SaveReason
import com.wuxianggujun.tinaide.editor.session.SaveResult
import com.wuxianggujun.tinaide.ui.compose.components.editor.CodeEditorRuntimeEffect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CodeEditorRuntimeLifecycleTest {
    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun saveAndClose_keepsBufferLiveThroughEffectsAndReleasesItAfterPageRemoval() {
        val savedText = "// 保存后关闭\nint main() { return 0; }\n"
        val file = temporaryFolder.newFile("main.cpp")
        val buffer = RopeTextBuffer(savedText)
        val state = EditorState(buffer)
        val runtime = CodeEditorRuntime(buffer, state)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val document = DocumentSession(
            context = RuntimeEnvironment.getApplication(),
            tabId = "save-close",
            file = file,
            coroutineScope = scope
        )
        val binding = BufferBinding(buffer, document)
        runtime.getOrCreateDocumentBinding { binding }
        var mounted by mutableStateOf(true)
        var observedRange: IntRange? = null
        var childDisposedWithLiveBuffer = false
        try {
            composeRule.setContent {
                if (mounted) {
                    CodeEditorRuntimeEffect(runtime)
                    DisposableEffect(runtime) {
                        runtime.acquireDocumentBinding(binding)
                        onDispose {
                            childDisposedWithLiveBuffer = !buffer.isClosed
                            runtime.releaseDocumentBinding(binding)
                        }
                    }
                    LaunchedEffect(state) {
                        snapshotFlow { state.visibleDocumentLines }.collect { observedRange = it }
                    }
                    TinaEditor(state, Modifier.fillMaxSize())
                }
            }
            composeRule.waitForIdle()
            assertThat(observedRange).isNotNull()
            val result = runBlocking { document.save(SaveReason.CLOSE) }
            assertThat(result).isInstanceOf(SaveResult.Success::class.java)
            assertThat(file.readText(Charsets.UTF_8)).isEqualTo(savedText)

            composeRule.runOnIdle {
                runtime.dispose()
                assertThat(buffer.isClosed).isFalse()
                // Exercise live snapshotFlow/derivedState observers while removal is pending.
                state.viewportHeightPx += state.lineHeightPx
            }
            composeRule.waitForIdle()
            composeRule.runOnIdle { mounted = false }
            composeRule.waitForIdle()

            assertThat(childDisposedWithLiveBuffer).isTrue()
            assertThat(buffer.isClosed).isTrue()
            assertThat(file.readText(Charsets.UTF_8)).isEqualTo(savedText)
        } finally {
            document.stopFileWatcher()
            scope.cancel()
            runtime.dispose()
        }
    }

    private class BufferBinding(
        private val buffer: RopeTextBuffer,
        private val document: DocumentSession
    ) : CodeEditorDocumentBinding {
        override fun attach() = document.attachEditor(this)
        override fun detach() = document.detachEditor(this)
        override suspend fun <R> withSuppressed(block: suspend () -> R): R = block()
        override fun readText(): String = buffer.toString()
        override fun setText(text: CharSequence) = buffer.replaceAll(text.toString())
        override fun textLength(): Int = buffer.length
        override fun canUndo(): Boolean = buffer.canUndo()
        override fun canRedo(): Boolean = buffer.canRedo()
        override fun undo() { buffer.undo() }
        override fun redo() { buffer.redo() }
        override fun currentDocumentVersion(): Long = buffer.version
        override fun currentViewState(): EditorViewState = EditorViewState()
    }
}
