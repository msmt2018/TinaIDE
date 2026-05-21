package com.wuxianggujun.tinaide.core.editorview

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EditorColorSchemeTest {

    @Test
    fun colorOfSemantic_shouldUseConstantColorForEnumMembers() {
        val syntax = EditorColorScheme.builtinGray().syntax

        assertThat(
            syntax.colorOfSemantic(
                tokenType = SemanticTokenType.ENUM_MEMBER,
                tokenModifiers = emptySet()
            )
        ).isEqualTo(syntax.constant)
    }
}
