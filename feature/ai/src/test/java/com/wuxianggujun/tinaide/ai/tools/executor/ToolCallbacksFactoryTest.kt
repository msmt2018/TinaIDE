package com.wuxianggujun.tinaide.ai.tools.executor

import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.ai.tools.executor.code.CodeAnalysisCallbacks
import com.wuxianggujun.tinaide.ai.tools.executor.code.DefaultCodeAnalysisCallbacks
import com.wuxianggujun.tinaide.ai.tools.executor.code.EnhancedCodeAnalysisCallbacks
import com.wuxianggujun.tinaide.ai.tools.executor.diagnostics.DefaultDiagnosticsCallbacks
import com.wuxianggujun.tinaide.ai.tools.executor.diagnostics.DiagnosticsCallbacks
import com.wuxianggujun.tinaide.ai.tools.executor.editor.EditorToolCallbacks
import com.wuxianggujun.tinaide.ai.tools.executor.execution.DefaultExecutionCallbacks
import com.wuxianggujun.tinaide.ai.tools.executor.execution.ExecutionCallbacks
import com.wuxianggujun.tinaide.ai.tools.executor.filesystem.FileSystemCallbacks
import io.mockk.mockk
import org.junit.Test

class ToolCallbacksFactoryTest {

    @Test
    fun `basic context includes project root and default callbacks`() {
        val context = ToolCallbacksFactory.createBasicContext("/tmp/project")

        assertThat(context.projectRoot).isEqualTo("/tmp/project")
        assertThat(ToolCallbacksFactory.getCallback<FileSystemCallbacks>(context, "fileSystemCallbacks")).isNotNull()
        assertThat(ToolCallbacksFactory.getCallback<CodeAnalysisCallbacks>(context, "codeAnalysisCallbacks")).isNotNull()
        assertThat(ToolCallbacksFactory.getCallback<DiagnosticsCallbacks>(context, "diagnosticsCallbacks")).isNotNull()
        assertThat(ToolCallbacksFactory.getCallback<ExecutionCallbacks>(context, "executionCallbacks")).isNotNull()
    }

    @Test
    fun `registry applies provider priority and strips project info from additional data`() {
        val registry = ToolCallbacksFactory.createRegistry()
        registry.registerProviders(
            object : ContextDataProvider {
                override val priority: Int = 20
                override fun provideData(): Map<String, Any> = mapOf("key" to "late")
            },
            object : ContextDataProvider {
                override val priority: Int = 10
                override fun provideData(): Map<String, Any> = mapOf(
                    "projectRoot" to "/tmp/project",
                    "currentFile" to "src/main.cpp",
                    "currentFileContent" to "int main() {}",
                    "key" to "early"
                )
            }
        )

        val context = registry.build()

        assertThat(context.projectRoot).isEqualTo("/tmp/project")
        assertThat(context.currentFile).isEqualTo("src/main.cpp")
        assertThat(context.currentFileContent).isEqualTo("int main() {}")
        assertThat(context.additionalData["key"]).isEqualTo("late")
        assertThat(context.additionalData).doesNotContainKey("projectRoot")
        assertThat(context.additionalData).doesNotContainKey("currentFile")
        assertThat(context.additionalData).doesNotContainKey("currentFileContent")
    }

    @Test
    fun `providers expose supplied callbacks and concrete analysis implementations`() {
        val editorCallbacks = mockk<EditorToolCallbacks>()
        val diagnosticsCallbacks = mockk<DiagnosticsCallbacks>()
        val executionCallbacks = mockk<ExecutionCallbacks>()
        val registry = ToolCallbacksFactory.createRegistry()

        registry.registerProviders(
            ProjectInfoProvider(
                projectRoot = "/tmp/project",
                getCurrentFile = { "src/Main.kt" },
                getCurrentFileContent = { "fun main() = Unit" },
            ),
            EditorCallbacksProvider { editorCallbacks },
            CodeAnalysisCallbacksProvider(
                projectRoot = "/tmp/project",
                useEnhancedAnalysis = false,
            ),
            DiagnosticsCallbacksProvider { diagnosticsCallbacks },
            ExecutionCallbacksProvider { executionCallbacks },
        )

        val context = registry.build()
        val enhancedCallbacks = CodeAnalysisCallbacksProvider(
            projectRoot = "/tmp/project",
            useEnhancedAnalysis = true,
            getRgPath = { "/tmp/missing-rg" },
        ).provideData()["codeAnalysisCallbacks"]

        assertThat(context.projectRoot).isEqualTo("/tmp/project")
        assertThat(context.currentFile).isEqualTo("src/Main.kt")
        assertThat(context.currentFileContent).isEqualTo("fun main() = Unit")
        assertThat(context.additionalData["editorCallbacks"]).isSameInstanceAs(editorCallbacks)
        assertThat(context.additionalData["codeAnalysisCallbacks"]).isInstanceOf(DefaultCodeAnalysisCallbacks::class.java)
        assertThat(context.additionalData["diagnosticsCallbacks"]).isSameInstanceAs(diagnosticsCallbacks)
        assertThat(context.additionalData["executionCallbacks"]).isSameInstanceAs(executionCallbacks)
        assertThat(enhancedCallbacks).isInstanceOf(EnhancedCodeAnalysisCallbacks::class.java)
    }

    @Test
    fun `registry can unregister and clear providers while keeping required defaults`() {
        val registry = ToolCallbacksFactory.createRegistry()
        val editorCallbacks = mockk<EditorToolCallbacks>()
        val projectProvider = ProjectInfoProvider("/tmp/project")
        val editorProvider = EditorCallbacksProvider { editorCallbacks }

        registry.registerProviders(projectProvider, editorProvider)
        assertThat(registry.build().additionalData["editorCallbacks"]).isSameInstanceAs(editorCallbacks)

        registry.unregisterProvider(editorProvider)
        val afterUnregister = registry.build()
        assertThat(afterUnregister.projectRoot).isEqualTo("/tmp/project")
        assertThat(afterUnregister.additionalData).doesNotContainKey("editorCallbacks")
        assertThat(afterUnregister.additionalData["diagnosticsCallbacks"]).isInstanceOf(DefaultDiagnosticsCallbacks::class.java)
        assertThat(afterUnregister.additionalData["executionCallbacks"]).isInstanceOf(DefaultExecutionCallbacks::class.java)

        registry.clear()
        val afterClear = registry.build()
        assertThat(afterClear.projectRoot).isEmpty()
        assertThat(afterClear.additionalData["fileSystemCallbacks"]).isNotNull()
        assertThat(afterClear.additionalData["codeAnalysisCallbacks"]).isNotNull()
        assertThat(afterClear.additionalData["diagnosticsCallbacks"]).isInstanceOf(DefaultDiagnosticsCallbacks::class.java)
        assertThat(afterClear.additionalData["executionCallbacks"]).isInstanceOf(DefaultExecutionCallbacks::class.java)
    }
}
