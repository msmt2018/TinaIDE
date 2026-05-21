package com.wuxianggujun.tinaide.core.compile

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BuildLogLevelTest {

    @Test
    fun `detect marks ninja progress lines as progress`() {
        val level = BuildLogLevel.detect(
            "[1/9] Building CXX object CMakeFiles/imgui.dir/src/imgui_impl_android.cpp.o"
        )

        assertThat(level).isEqualTo(BuildLogLevel.PROGRESS)
    }

    @Test
    fun `detect keeps error lines higher priority than progress format`() {
        val level = BuildLogLevel.detect("[2/9] FAILED: demo")

        assertThat(level).isEqualTo(BuildLogLevel.ERROR)
    }
}
