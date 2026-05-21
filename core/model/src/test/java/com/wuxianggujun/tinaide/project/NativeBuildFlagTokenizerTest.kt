package com.wuxianggujun.tinaide.project

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NativeBuildFlagTokenizerTest {

    @Test
    fun tokenize_shouldSplitWhitespaceAndKeepQuotedValues() {
        val tokens = NativeBuildFlagTokenizer.tokenize(
            """-DNAME="hello world" '-DOTHER=a b' -Iinclude\ dir"""
        )

        assertThat(tokens).containsExactly(
            "-DNAME=hello world",
            "-DOTHER=a b",
            "-Iinclude dir"
        ).inOrder()
    }

    @Test
    fun tokenize_shouldPreserveTrailingEscapeAndIgnoreBlankTokens() {
        assertThat(NativeBuildFlagTokenizer.tokenize("   ")).isEmpty()
        assertThat(NativeBuildFlagTokenizer.tokenize("""-DVALUE=abc\"""))
            .containsExactly("-DVALUE=abc\\")
    }
}
