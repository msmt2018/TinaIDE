package com.wuxianggujun.tinaide.search

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CodeSearchEngineTest {

    @Test
    fun `search is case insensitive by default`() {
        val engine = CodeSearchEngine(FakeTextContentProvider("Hello hello"))

        val results = engine.search("hello")

        assertThat(results).hasSize(2)
    }

    @Test
    fun `search supports case sensitive option`() {
        val engine = CodeSearchEngine(FakeTextContentProvider("Hello hello"))

        val results = engine.search(
            query = "hello",
            options = SearchOptions(caseSensitive = true)
        )

        assertThat(results).hasSize(1)
        val first = results.first() as CodeSearchResult
        assertThat(first.range.startIndex).isEqualTo(6)
    }

    @Test
    fun `search supports regex option`() {
        val engine = CodeSearchEngine(FakeTextContentProvider("int a = 1;\nint b = 22;"))

        val results = engine.search(
            query = "\\d+",
            options = SearchOptions(useRegex = true)
        )

        assertThat(results).hasSize(2)
        val first = results[0] as CodeSearchResult
        val second = results[1] as CodeSearchResult
        assertThat(first.range.startIndex).isEqualTo(8)
        assertThat(second.range.startIndex).isEqualTo(19)
    }

    @Test
    fun `invalid regex returns empty result`() {
        val engine = CodeSearchEngine(FakeTextContentProvider("sample text"))

        val results = engine.search(
            query = "[invalid",
            options = SearchOptions(useRegex = true)
        )

        assertThat(results).isEmpty()
    }

    @Test
    fun `whole word excludes partial matches`() {
        val engine = CodeSearchEngine(FakeTextContentProvider("counter count discount"))

        val results = engine.search(
            query = "count",
            options = SearchOptions(wholeWord = true)
        )

        assertThat(results).hasSize(1)
        val first = results.first() as CodeSearchResult
        assertThat(first.range.startIndex).isEqualTo(8)
        assertThat(first.range.endIndex).isEqualTo(13)
    }

    @Test
    fun `result contains line and column`() {
        val engine = CodeSearchEngine(FakeTextContentProvider("first\nsecond line\nthird"))

        val results = engine.search("second")
        val hit = results.first() as CodeSearchResult

        assertThat(hit.range.start.line).isEqualTo(1)
        assertThat(hit.range.start.column).isEqualTo(0)
        assertThat(hit.range.end.line).isEqualTo(1)
        assertThat(hit.range.end.column).isEqualTo(6)
    }

    private class FakeTextContentProvider(
        private val text: String
    ) : TextContentProvider {

        override fun getText(): String = text

        override fun getPosition(charIndex: Int): TextContentProvider.Position {
            val safeIndex = charIndex.coerceIn(0, text.length)
            var line = 0
            var lineStart = 0
            for (index in 0 until safeIndex) {
                if (text[index] == '\n') {
                    line++
                    lineStart = index + 1
                }
            }
            return TextContentProvider.Position(
                line = line,
                column = safeIndex - lineStart
            )
        }
    }
}
