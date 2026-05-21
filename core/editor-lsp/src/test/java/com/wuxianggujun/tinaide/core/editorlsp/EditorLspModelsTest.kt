package com.wuxianggujun.tinaide.core.editorlsp

import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.core.textengine.Position
import org.junit.Test

class EditorLspModelsTest {

    @Test
    fun documentSymbol_shouldKeepNestedChildrenAndSelectionRange() {
        val range = Range(Position(1, 0), Position(8, 1))
        val selection = Range(Position(1, 6), Position(1, 11))
        val child = DocumentSymbol(
            name = "run",
            kind = SymbolKind.METHOD,
            range = Range(Position(2, 4), Position(4, 5)),
            selectionRange = Range(Position(2, 8), Position(2, 11))
        )

        val symbol = DocumentSymbol(
            name = "Main",
            kind = SymbolKind.CLASS,
            range = range,
            selectionRange = selection,
            children = listOf(child)
        )

        assertThat(symbol.range).isEqualTo(range)
        assertThat(symbol.selectionRange).isEqualTo(selection)
        assertThat(symbol.children).containsExactly(child)
    }

    @Test
    fun workspaceSymbol_shouldPointToLocation() {
        val location = Location(
            fileUri = "file:///project/src/main.cpp",
            range = Range(Position(0, 0), Position(0, 4))
        )

        val symbol = WorkspaceSymbol(
            name = "main",
            kind = SymbolKind.FUNCTION,
            location = location
        )

        assertThat(symbol.location.fileUri).isEqualTo("file:///project/src/main.cpp")
        assertThat(symbol.location.range.start).isEqualTo(Position(0, 0))
    }

    @Test
    fun semanticAndHoverModels_shouldPreserveModifiersAndOptionalRange() {
        val token = SemanticToken(
            line = 3,
            startColumn = 5,
            length = 6,
            tokenType = "function",
            tokenModifiers = setOf("declaration", "static")
        )
        val hover = HoverResult(
            markdown = "```cpp\nint main()\n```",
            range = Range(Position(3, 5), Position(3, 11))
        )

        assertThat(token.tokenModifiers).containsExactly("declaration", "static")
        assertThat(hover.range?.end).isEqualTo(Position(3, 11))
    }

    @Test
    fun signatureHelp_shouldExposeActiveIndexes() {
        val result = SignatureHelpResult(
            signatures = listOf("foo(int value)", "foo(String value)"),
            activeSignature = 1,
            activeParameter = 0
        )

        assertThat(result.signatures).hasSize(2)
        assertThat(result.activeSignature).isEqualTo(1)
        assertThat(result.activeParameter).isEqualTo(0)
    }
}
