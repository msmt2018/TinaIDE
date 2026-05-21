package com.wuxianggujun.tinaide.core.common.snippet

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SnippetPlainTextTest {

    @Test
    fun `expandSnippetToPlainText keeps placeholder default value`() {
        assertThat(expandSnippetToPlainText("hello ${'$'}{1:name}"))
            .isEqualTo("hello name")
    }

    @Test
    fun `expandSnippetToPlainText picks first choice`() {
        assertThat(expandSnippetToPlainText("print(\${1|one,two|})"))
            .isEqualTo("print(one)")
    }

    @Test
    fun `expandSnippetToPlainText removes numbered placeholders`() {
        assertThat(expandSnippetToPlainText("${'$'}1/${'$'}0"))
            .isEqualTo("/")
    }

    @Test
    fun `expandSnippetToPlainText restores escaped reserved chars`() {
        assertThat(expandSnippetToPlainText("\\${'$'}HOME \\} \\\\"))
            .isEqualTo("${'$'}HOME } \\")
    }
}
