package com.wuxianggujun.tinaide.ui.compose.components.editor

import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.core.editorview.SemanticTokenType
import org.junit.Test

class TinaCodeEditorSemanticTokenMappingTest {

    @Test
    fun toEditorSemanticTokenTypeOrNull_shouldMapKnownTokenTypes() {
        assertThat("function".toEditorSemanticTokenTypeOrNull())
            .isEqualTo(SemanticTokenType.FUNCTION)
        assertThat("type_parameter".toEditorSemanticTokenTypeOrNull())
            .isEqualTo(SemanticTokenType.TYPE_PARAMETER)
        assertThat("enum-member".toEditorSemanticTokenTypeOrNull())
            .isEqualTo(SemanticTokenType.ENUM_MEMBER)
    }

    @Test
    fun toEditorSemanticTokenTypeOrNull_shouldIgnoreUnknownTokenTypes() {
        assertThat("cmakeCommand".toEditorSemanticTokenTypeOrNull()).isNull()
        assertThat("property_definition".toEditorSemanticTokenTypeOrNull()).isNull()
    }
}
