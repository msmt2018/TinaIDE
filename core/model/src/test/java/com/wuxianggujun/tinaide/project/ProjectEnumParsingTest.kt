package com.wuxianggujun.tinaide.project

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ProjectEnumParsingTest {

    @Test
    fun cppStandardFromString_shouldAcceptEnumNameFlagAndCmakeValue() {
        assertThat(CppStandard.fromString("CPP_20")).isEqualTo(CppStandard.CPP_20)
        assertThat(CppStandard.fromString("c++23")).isEqualTo(CppStandard.CPP_23)
        assertThat(CppStandard.fromString("14")).isEqualTo(CppStandard.CPP_14)
        assertThat(CppStandard.fromString(" unknown ")).isEqualTo(CppStandard.DEFAULT)
        assertThat(CppStandard.fromString(null)).isEqualTo(CppStandard.DEFAULT)
    }

    @Test
    fun projectLanguageFromString_shouldBeCaseInsensitiveAndFallbackUnknown() {
        assertThat(ProjectLanguage.fromString("kotlin")).isEqualTo(ProjectLanguage.KOTLIN)
        assertThat(ProjectLanguage.fromString("TypeScript")).isEqualTo(ProjectLanguage.TYPESCRIPT)
        assertThat(ProjectLanguage.fromString("brainfuck")).isEqualTo(ProjectLanguage.UNKNOWN)
        assertThat(ProjectLanguage.fromString(null)).isEqualTo(ProjectLanguage.UNKNOWN)
    }

    @Test
    fun androidApiLevel_shouldParseSupportedLevelsAndFallbackDefault() {
        assertThat(AndroidApiLevel.fromLevel(21)).isEqualTo(AndroidApiLevel.API_21)
        assertThat(AndroidApiLevel.fromLevel(35)).isEqualTo(AndroidApiLevel.API_35)
        assertThat(AndroidApiLevel.fromLevel(36)).isEqualTo(AndroidApiLevel.DEFAULT)
        assertThat(AndroidApiLevel.fromString("API_34")).isEqualTo(AndroidApiLevel.API_34)
        assertThat(AndroidApiLevel.fromString("api_34")).isEqualTo(AndroidApiLevel.DEFAULT)
    }
}
