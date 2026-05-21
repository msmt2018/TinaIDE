package com.wuxianggujun.tinaide.core.editorlsp

import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.core.textengine.Position
import org.junit.Test

class DefaultCompletionProviderTest {

    @Test
    fun mergeCompletions_shouldReserveRoomForLocalKeywordCandidates() {
        val provider = DefaultCompletionProvider(
            localProvider = { _, _, _ -> emptyList() },
            lspProvider = { _, _, _ -> CompletionFetchResult.Success(emptyList()) },
            resultLimit = 12
        )

        val merged = provider.mergeCompletions(
            local = listOf(
                CompletionItem(
                    label = "float",
                    kind = CompletionItemKind.KEYWORD,
                    source = CompletionSource.LOCAL
                ),
                CompletionItem(
                    label = "int",
                    kind = CompletionItemKind.KEYWORD,
                    source = CompletionSource.LOCAL
                )
            ),
            lsp = (1..32).map { index ->
                CompletionItem(
                    label = "item$index",
                    kind = CompletionItemKind.VARIABLE,
                    source = CompletionSource.LSP
                )
            },
            prefix = "f"
        )

        assertThat(merged.map { it.label }).containsAtLeast("float", "int")
        assertThat(merged).hasSize(12)
    }

    @Test
    fun requestCompletion_shouldKeepLocalItemsWhenLspListIsLarge() {
        kotlinx.coroutines.runBlocking {
            val provider = DefaultCompletionProvider(
                localProvider = { _: String, _: Position, _: Char? ->
                    listOf(
                        CompletionItem(
                            label = "float",
                            kind = CompletionItemKind.KEYWORD,
                            source = CompletionSource.LOCAL
                        ),
                        CompletionItem(
                            label = "int",
                            kind = CompletionItemKind.KEYWORD,
                            source = CompletionSource.LOCAL
                        )
                    )
                },
                lspProvider = { _, _, _ ->
                    CompletionFetchResult.Success(
                        (1..64).map { index ->
                            CompletionItem(
                                label = "symbol$index",
                                kind = CompletionItemKind.VARIABLE,
                                source = CompletionSource.LSP
                            )
                        }
                    )
                },
                resultLimit = 20
            )

            val result = provider.requestCompletion(
                fileUri = "file:///main.cpp",
                position = Position(0, 1),
                triggerChar = 'f'
            )

            assertThat(result).isInstanceOf(CompletionFetchResult.Success::class.java)
            val labels = (result as CompletionFetchResult.Success).items.map { it.label }
            assertThat(labels).containsAtLeast("float", "int")
        }
    }

    @Test
    fun mergeCompletions_shouldCollapseSameNamedNonCallableItems() {
        val provider = DefaultCompletionProvider(
            localProvider = { _, _, _ -> emptyList() },
            lspProvider = { _, _, _ -> CompletionFetchResult.Success(emptyList()) },
            resultLimit = 10
        )

        val merged = provider.mergeCompletions(
            local = emptyList(),
            lsp = listOf(
                CompletionItem(
                    label = "float",
                    kind = CompletionItemKind.KEYWORD,
                    detail = "LSP keyword",
                    source = CompletionSource.LSP
                ),
                CompletionItem(
                    label = "float",
                    kind = CompletionItemKind.KEYWORD,
                    detail = "LSP type alias",
                    source = CompletionSource.LSP
                ),
                CompletionItem(
                    label = "int",
                    kind = CompletionItemKind.KEYWORD,
                    source = CompletionSource.LSP
                )
            ),
            prefix = "f"
        )

        assertThat(merged.map { it.label }).containsExactly("float", "int").inOrder()
        assertThat(merged.first().detail).isEqualTo("LSP keyword")
    }

    @Test
    fun mergeCompletions_shouldKeepCallableOverloadsVisible() {
        val provider = DefaultCompletionProvider(
            localProvider = { _, _, _ -> emptyList() },
            lspProvider = { _, _, _ -> CompletionFetchResult.Success(emptyList()) },
            resultLimit = 10
        )

        val merged = provider.mergeCompletions(
            local = emptyList(),
            lsp = listOf(
                CompletionItem(
                    label = "printf",
                    kind = CompletionItemKind.FUNCTION,
                    detail = "printf(const char* format, ...)",
                    insertText = "printf",
                    source = CompletionSource.LSP
                ),
                CompletionItem(
                    label = "printf",
                    kind = CompletionItemKind.FUNCTION,
                    detail = "printf(const wchar_t* format, ...)",
                    insertText = "printf",
                    source = CompletionSource.LSP
                )
            ),
            prefix = "pri"
        )

        assertThat(merged).hasSize(2)
        assertThat(merged.map { it.detail })
            .containsExactly(
                "printf(const char* format, ...)",
                "printf(const wchar_t* format, ...)"
            )
            .inOrder()
    }
}
