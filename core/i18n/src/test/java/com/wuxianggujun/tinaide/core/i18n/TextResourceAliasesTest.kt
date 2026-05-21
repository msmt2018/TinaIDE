package com.wuxianggujun.tinaide.core.i18n

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TextResourceAliasesTest {

    @Test
    fun aliases_shouldExposeCoreI18nResources() {
        assertThat(Strings.app_name).isEqualTo(R.string.app_name)
        assertThat(Arrays.project_types).isEqualTo(R.array.project_types)
    }
}
