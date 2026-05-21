package com.wuxianggujun.tinaide.ai.tools.code

import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.ai.tools.ToolExecutionContext
import com.wuxianggujun.tinaide.ai.tools.ToolExecutionResult
import com.wuxianggujun.tinaide.ai.tools.assertCancellationRethrown
import com.wuxianggujun.tinaide.ai.tools.error
import com.wuxianggujun.tinaide.ai.tools.executor.code.CodeAnalysisCallbacks
import com.wuxianggujun.tinaide.ai.tools.executor.code.CodeMatch
import com.wuxianggujun.tinaide.ai.tools.executor.code.CodeOutlineResult
import com.wuxianggujun.tinaide.ai.tools.executor.code.CodeSearchRequest
import com.wuxianggujun.tinaide.ai.tools.executor.code.CodeSearchResult
import com.wuxianggujun.tinaide.ai.tools.executor.code.OutlineItem
import com.wuxianggujun.tinaide.ai.tools.executor.code.OutlineItemKind
import com.wuxianggujun.tinaide.ai.tools.executor.code.OutlineRange
import com.wuxianggujun.tinaide.ai.tools.executor.code.ReferenceSearchRequest
import com.wuxianggujun.tinaide.ai.tools.executor.code.ReferenceSearchResult
import com.wuxianggujun.tinaide.ai.tools.executor.code.SymbolReference
import com.wuxianggujun.tinaide.ai.tools.executor.code.SymbolSearchRequest
import com.wuxianggujun.tinaide.ai.tools.executor.code.SymbolSearchResult
import com.wuxianggujun.tinaide.ai.tools.executor.code.SymbolType
import com.wuxianggujun.tinaide.ai.tools.installToolTestAppStrings
import com.wuxianggujun.tinaide.ai.tools.resetToolTestAppStrings
import com.wuxianggujun.tinaide.ai.tools.success
import com.wuxianggujun.tinaide.ai.tools.toolCall
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test

class CodeAnalysisToolsTest {

    @Before
    fun setUpStrings() {
        installToolTestAppStrings()
    }

    @After
    fun tearDownStrings() {
        resetToolTestAppStrings()
    }

    @Test
    fun `search code forwards all request parameters`(): Unit = runBlocking {
        val callbacks = RecordingCodeAnalysisCallbacks()

        val result = SearchCodeTool.execute(
            toolCall(
                SearchCodeTool.name,
                """{"query":"main","path":"src","file_pattern":"*.cpp","case_sensitive":true,"regex":true,"max_results":3}"""
            ),
            codeContext(callbacks)
        )

        assertThat(callbacks.lastSearchRequest).isEqualTo(
            CodeSearchRequest(
                query = "main",
                path = "src",
                filePattern = "*.cpp",
                caseSensitive = true,
                isRegex = true,
                maxResults = 3
            )
        )
        assertThat(result.success().content).contains("int main")
    }

    @Test
    fun `find symbol forwards symbol type and returns metadata`(): Unit = runBlocking {
        val callbacks = RecordingCodeAnalysisCallbacks()

        val result = FindSymbolTool.execute(
            toolCall(FindSymbolTool.name, """{"symbol":"main","type":"function"}"""),
            codeContext(callbacks)
        )

        val success = result.success()
        assertThat(callbacks.lastSymbolRequest).isEqualTo(
            SymbolSearchRequest(symbolName = "main", symbolType = SymbolType.FUNCTION)
        )
        assertThat(success.content).contains("FUNCTION: main")
        assertThat(success.metadata).containsEntry("symbolCount", 1)
    }

    @Test
    fun `find symbol includes documentation when available`(): Unit = runBlocking {
        val callbacks = RecordingCodeAnalysisCallbacks(
            symbolResult = SymbolSearchResult(
                symbols = listOf(
                    RecordingCodeAnalysisCallbacks.sampleSymbolResult().symbols.single().copy(
                        documentation = "Program entry point"
                    )
                )
            )
        )

        val result = FindSymbolTool.execute(
            toolCall(FindSymbolTool.name, """{"symbol":"main"}"""),
            codeContext(callbacks)
        )

        assertThat(result.success().content).contains("Documentation: Program entry point")
    }

    @Test
    fun `find symbol falls back to any type and returns empty result`(): Unit = runBlocking {
        val callbacks = RecordingCodeAnalysisCallbacks(symbolResult = SymbolSearchResult(emptyList()))

        val result = FindSymbolTool.execute(
            toolCall(FindSymbolTool.name, """{"symbol":"missing","type":"unknown"}"""),
            codeContext(callbacks)
        )

        assertThat(callbacks.lastSymbolRequest).isEqualTo(
            SymbolSearchRequest(symbolName = "missing", symbolType = SymbolType.ANY)
        )
        assertThat(result.success().content).isEqualTo("No symbols found for: missing")
    }

    @Test
    fun `find references forwards source location`(): Unit = runBlocking {
        val callbacks = RecordingCodeAnalysisCallbacks()

        val result = FindReferencesTool.execute(
            toolCall(FindReferencesTool.name, """{"symbol":"main","file":"src/main.cpp","line":3}"""),
            codeContext(callbacks)
        )

        assertThat(callbacks.lastReferenceRequest).isEqualTo(
            ReferenceSearchRequest(symbolName = "main", filePath = "src/main.cpp", lineNumber = 3)
        )
        assertThat(result.success().content).contains("[DEF] Line 3:5")
    }

    @Test
    fun `find references ignores invalid line and formats non definition refs`(): Unit = runBlocking {
        val callbacks = RecordingCodeAnalysisCallbacks(
            referencesResult = ReferenceSearchResult(
                references = listOf(
                    SymbolReference(
                        filePath = "src/app.cpp",
                        lineNumber = 8,
                        columnNumber = 13,
                        lineContent = "auto value = main();",
                        isDefinition = false
                    )
                )
            )
        )

        val result = FindReferencesTool.execute(
            toolCall(FindReferencesTool.name, """{"symbol":"main","file":"src/app.cpp","line":"bad"}"""),
            codeContext(callbacks)
        )

        assertThat(callbacks.lastReferenceRequest).isEqualTo(
            ReferenceSearchRequest(symbolName = "main", filePath = "src/app.cpp", lineNumber = null)
        )
        assertThat(result.success().content).contains("[REF] Line 8:13")
    }

    @Test
    fun `find references returns empty success when no references found`(): Unit = runBlocking {
        val callbacks = RecordingCodeAnalysisCallbacks(referencesResult = ReferenceSearchResult(emptyList()))

        val result = FindReferencesTool.execute(
            toolCall(FindReferencesTool.name, """{"symbol":"missing"}"""),
            codeContext(callbacks)
        )

        assertThat(result.success().content).isEqualTo("No references found for: missing")
    }

    @Test
    fun `get code outline forwards path and returns outline metadata`(): Unit = runBlocking {
        val callbacks = RecordingCodeAnalysisCallbacks()

        val result = GetCodeOutlineTool.execute(
            toolCall(GetCodeOutlineTool.name, """{"path":"src/main.cpp"}"""),
            codeContext(callbacks)
        )

        val success = result.success()
        assertThat(callbacks.lastOutlinePath).isEqualTo("src/main.cpp")
        assertThat(success.content).contains("FUNCTION: main")
        assertThat(success.metadata).containsExactly(
            "filePath",
            "src/main.cpp",
            "language",
            "cpp",
            "itemCount",
            1
        )
    }

    @Test
    fun `get code outline formats nested items without details`(): Unit = runBlocking {
        val callbacks = RecordingCodeAnalysisCallbacks(
            outlineResult = CodeOutlineResult(
                filePath = "src/app.cpp",
                language = "cpp",
                items = listOf(
                    OutlineItem(
                        name = "App",
                        kind = OutlineItemKind.CLASS,
                        range = OutlineRange(1, 1, 9, 2),
                        children = listOf(
                            OutlineItem(
                                name = "run",
                                kind = OutlineItemKind.FUNCTION,
                                range = OutlineRange(3, 5, 6, 6)
                            )
                        )
                    )
                )
            )
        )

        val result = GetCodeOutlineTool.execute(
            toolCall(GetCodeOutlineTool.name, """{"path":"src/app.cpp"}"""),
            codeContext(callbacks)
        )

        val content = result.success().content
        assertThat(content).contains("CLASS: App [1:1-9:2]")
        assertThat(content).contains("  FUNCTION: run [3:5-6:6]")
    }

    @Test
    fun `search code uses default parameters and reports truncation`(): Unit = runBlocking {
        val callbacks = RecordingCodeAnalysisCallbacks(
            searchResult = CodeSearchResult(
                matches = listOf(
                    CodeMatch(
                        filePath = "src/main.cpp",
                        lineNumber = 3,
                        lineContent = "int main() { return 0; }",
                        matchStart = 4,
                        matchEnd = 8
                    )
                ),
                totalCount = 3,
                truncated = true
            )
        )

        val result = SearchCodeTool.execute(
            toolCall(SearchCodeTool.name, """{"query":"main"}"""),
            codeContext(callbacks)
        )

        assertThat(callbacks.lastSearchRequest).isEqualTo(
            CodeSearchRequest(
                query = "main",
                path = ".",
                filePattern = null,
                caseSensitive = false,
                isRegex = false,
                maxResults = 50
            )
        )
        assertThat(result.success().content).contains("Found 3 matches (showing first 1):")
        assertThat(result.success().metadata).containsEntry("truncated", true)
    }

    @Test
    fun `code analysis tools validate required arguments`(): Unit = runBlocking {
        val callbacks = RecordingCodeAnalysisCallbacks()

        assertThat(SearchCodeTool.execute(toolCall(SearchCodeTool.name, """{"query":" "}"""), codeContext(callbacks)))
            .isEqualTo(ToolExecutionResult.Error("Search query is required"))
        assertThat(FindSymbolTool.execute(toolCall(FindSymbolTool.name, """{"symbol":" "}"""), codeContext(callbacks)))
            .isEqualTo(ToolExecutionResult.Error("Symbol name is required"))
        assertThat(FindReferencesTool.execute(toolCall(FindReferencesTool.name, """{"symbol":" "}"""), codeContext(callbacks)))
            .isEqualTo(ToolExecutionResult.Error("Symbol name is required"))
        assertThat(GetCodeOutlineTool.execute(toolCall(GetCodeOutlineTool.name, """{"path":" "}"""), codeContext(callbacks)))
            .isEqualTo(ToolExecutionResult.Error("File path is required"))
    }

    @Test
    fun `code analysis tools require callbacks from context`(): Unit = runBlocking {
        val context = ToolExecutionContext()

        assertThat(SearchCodeTool.execute(toolCall(SearchCodeTool.name, """{"query":"main"}"""), context))
            .isEqualTo(ToolExecutionResult.Error("Code analysis callbacks not available"))
        assertThat(FindSymbolTool.execute(toolCall(FindSymbolTool.name, """{"symbol":"main"}"""), context))
            .isEqualTo(ToolExecutionResult.Error("Code analysis callbacks not available"))
        assertThat(FindReferencesTool.execute(toolCall(FindReferencesTool.name, """{"symbol":"main"}"""), context))
            .isEqualTo(ToolExecutionResult.Error("Code analysis callbacks not available"))
        assertThat(GetCodeOutlineTool.execute(toolCall(GetCodeOutlineTool.name, """{"path":"src/main.cpp"}"""), context))
            .isEqualTo(ToolExecutionResult.Error("Code analysis callbacks not available"))
    }

    @Test
    fun `code analysis tools map callback failures to tool errors`(): Unit = runBlocking {
        val callbacks = failingCodeAnalysisCallbacks()

        assertThat(
            SearchCodeTool.execute(
                toolCall(SearchCodeTool.name, """{"query":"main"}"""),
                codeContext(callbacks)
            ).error().message
        ).isEqualTo("Failed to search code: boom")
        assertThat(
            FindSymbolTool.execute(
                toolCall(FindSymbolTool.name, """{"symbol":"main"}"""),
                codeContext(callbacks)
            ).error().message
        ).isEqualTo("Failed to find symbol: boom")
        assertThat(
            FindReferencesTool.execute(
                toolCall(FindReferencesTool.name, """{"symbol":"main"}"""),
                codeContext(callbacks)
            ).error().message
        ).isEqualTo("Failed to find references: boom")
        assertThat(
            GetCodeOutlineTool.execute(
                toolCall(GetCodeOutlineTool.name, """{"path":"src/main.cpp"}"""),
                codeContext(callbacks)
            ).error().message
        ).isEqualTo("Failed to get code outline: boom")
    }

    @Test
    fun `code analysis tools rethrow cancellation exception`(): Unit = runBlocking {
        val callbacks = cancellingCodeAnalysisCallbacks()

        assertCancellationRethrown {
            SearchCodeTool.execute(
                toolCall(SearchCodeTool.name, """{"query":"main"}"""),
                codeContext(callbacks)
            )
        }
        assertCancellationRethrown {
            FindSymbolTool.execute(
                toolCall(FindSymbolTool.name, """{"symbol":"main"}"""),
                codeContext(callbacks)
            )
        }
        assertCancellationRethrown {
            FindReferencesTool.execute(
                toolCall(FindReferencesTool.name, """{"symbol":"main"}"""),
                codeContext(callbacks)
            )
        }
        assertCancellationRethrown {
            GetCodeOutlineTool.execute(
                toolCall(GetCodeOutlineTool.name, """{"path":"src/main.cpp"}"""),
                codeContext(callbacks)
            )
        }
    }

    private fun codeContext(callbacks: CodeAnalysisCallbacks): ToolExecutionContext = ToolExecutionContext(additionalData = mapOf("codeAnalysisCallbacks" to callbacks))

    private fun failingCodeAnalysisCallbacks(): CodeAnalysisCallbacks = object : CodeAnalysisCallbacks {
        override fun searchCode(request: CodeSearchRequest): CodeSearchResult = throw IllegalStateException("boom")

        override fun findSymbol(request: SymbolSearchRequest): SymbolSearchResult = throw IllegalStateException("boom")

        override fun findReferences(request: ReferenceSearchRequest): ReferenceSearchResult = throw IllegalStateException("boom")

        override fun getCodeOutline(filePath: String): CodeOutlineResult = throw IllegalStateException("boom")
    }

    private fun cancellingCodeAnalysisCallbacks(): CodeAnalysisCallbacks = object : CodeAnalysisCallbacks {
        override fun searchCode(request: CodeSearchRequest): CodeSearchResult = throw CancellationException("cancelled")

        override fun findSymbol(request: SymbolSearchRequest): SymbolSearchResult = throw CancellationException("cancelled")

        override fun findReferences(request: ReferenceSearchRequest): ReferenceSearchResult = throw CancellationException("cancelled")

        override fun getCodeOutline(filePath: String): CodeOutlineResult = throw CancellationException("cancelled")
    }
}
