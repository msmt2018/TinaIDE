package com.wuxianggujun.tinaide.core.i18n

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TextResourceAliasesTest {

    @Test
    fun aliases_shouldExposeCoreI18nResources() {
        assertThat(Strings.error_unknown).isEqualTo(R.string.error_unknown)
        assertThat(Arrays.editor_theme_entries).isEqualTo(R.array.editor_theme_entries)
    }
}
