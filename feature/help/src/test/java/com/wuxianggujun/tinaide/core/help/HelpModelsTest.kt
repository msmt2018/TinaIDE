package com.wuxianggujun.tinaide.core.help

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HelpModelsTest {

    @Test
    fun document_shouldUseEmptyDefaultsForOptionalSearchFields() {
        val document = HelpDocument(
            id = "intro",
            title = "Intro",
            category = HelpCategory.GETTING_STARTED,
            fileName = "intro.md"
        )

        assertThat(document.keywords).isEmpty()
        assertThat(document.summary).isEmpty()
        assertThat(document.order).isEqualTo(0)
    }

    @Test
    fun searchResult_shouldCarryMatchedSnippetAndScore() {
        val document = HelpDocument(
            id = "terminal",
            title = "Terminal",
            category = HelpCategory.TERMINAL,
            keywords = listOf("shell", "proot"),
            fileName = "terminal.md",
            order = 2
        )

        val result = HelpSearchResult(
            document = document,
            matchedContent = "Use shell sessions",
            relevanceScore = 0.75f
        )

        assertThat(result.document.category).isEqualTo(HelpCategory.TERMINAL)
        assertThat(result.matchedContent).isEqualTo("Use shell sessions")
        assertThat(result.relevanceScore).isEqualTo(0.75f)
    }
}
