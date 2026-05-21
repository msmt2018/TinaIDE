package com.wuxianggujun.tinaide.ai.tools.code

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
import com.wuxianggujun.tinaide.ai.tools.executor.code.SymbolDefinition
import com.wuxianggujun.tinaide.ai.tools.executor.code.SymbolReference
import com.wuxianggujun.tinaide.ai.tools.executor.code.SymbolSearchRequest
import com.wuxianggujun.tinaide.ai.tools.executor.code.SymbolSearchResult
import com.wuxianggujun.tinaide.ai.tools.executor.code.SymbolType

internal class RecordingCodeAnalysisCallbacks(
    private val searchResult: CodeSearchResult = sampleSearchResult(),
    private val symbolResult: SymbolSearchResult = sampleSymbolResult(),
    private val referencesResult: ReferenceSearchResult = sampleReferencesResult(),
    private val outlineResult: CodeOutlineResult = sampleOutlineResult()
) : CodeAnalysisCallbacks {
    var lastSearchRequest: CodeSearchRequest? = null
        private set
    var lastSymbolRequest: SymbolSearchRequest? = null
        private set
    var lastReferenceRequest: ReferenceSearchRequest? = null
        private set
    var lastOutlinePath: String? = null
        private set

    override fun searchCode(request: CodeSearchRequest): CodeSearchResult {
        lastSearchRequest = request
        return searchResult
    }

    override fun findSymbol(request: SymbolSearchRequest): SymbolSearchResult {
        lastSymbolRequest = request
        return symbolResult
    }

    override fun findReferences(request: ReferenceSearchRequest): ReferenceSearchResult {
        lastReferenceRequest = request
        return referencesResult
    }

    override fun getCodeOutline(filePath: String): CodeOutlineResult {
        lastOutlinePath = filePath
        return outlineResult.copy(filePath = filePath)
    }

    companion object {
        fun sampleSearchResult(): CodeSearchResult = CodeSearchResult(
            matches = listOf(
                CodeMatch(
                    filePath = "src/main.cpp",
                    lineNumber = 3,
                    lineContent = "int main() { return 0; }",
                    matchStart = 4,
                    matchEnd = 8
                )
            ),
            totalCount = 1,
            truncated = false
        )

        fun sampleSymbolResult(): SymbolSearchResult = SymbolSearchResult(
            symbols = listOf(
                SymbolDefinition(
                    name = "main",
                    type = SymbolType.FUNCTION,
                    filePath = "src/main.cpp",
                    lineNumber = 3,
                    columnNumber = 5,
                    signature = "int main()"
                )
            )
        )

        fun sampleReferencesResult(): ReferenceSearchResult = ReferenceSearchResult(
            references = listOf(
                SymbolReference(
                    filePath = "src/main.cpp",
                    lineNumber = 3,
                    columnNumber = 5,
                    lineContent = "int main() { return 0; }",
                    isDefinition = true
                )
            )
        )

        fun sampleOutlineResult(): CodeOutlineResult = CodeOutlineResult(
            filePath = "src/main.cpp",
            language = "cpp",
            items = listOf(
                OutlineItem(
                    name = "main",
                    kind = OutlineItemKind.FUNCTION,
                    range = OutlineRange(3, 1, 3, 24),
                    detail = "int main()"
                )
            )
        )
    }
}
