package com.wuxianggujun.tinaide.core.compile.cmake

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CMakeLinkPolicyTest {

    @Test
    fun `resolveStandardLibraries returns blank when project does not declare ld libs`() {
        val resolved = CMakeLinkPolicy.resolveStandardLibraries("")

        assertThat(resolved).isEmpty()
    }

    @Test
    fun `resolveStandardLibraries normalizes explicit ld libs only`() {
        val resolved = CMakeLinkPolicy.resolveStandardLibraries(
            """
            -lSDL3
            -lEGL
            -lSDL3
            """.trimIndent()
        )

        assertThat(resolved).isEqualTo("-lSDL3 -lEGL")
    }
}
