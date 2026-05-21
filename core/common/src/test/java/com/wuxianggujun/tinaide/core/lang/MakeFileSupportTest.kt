package com.wuxianggujun.tinaide.core.lang

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

class MakeFileSupportTest {

    @Test
    fun `isMakeLikeFile recognizes canonical names and common variants`() {
        assertThat(MakeFileSupport.isMakeLikeFile(File("Makefile"))).isTrue()
        assertThat(MakeFileSupport.isMakeLikeFile(File("GNUmakefile"))).isTrue()
        assertThat(MakeFileSupport.isMakeLikeFile(File("BSDmakefile"))).isTrue()
        assertThat(MakeFileSupport.isMakeLikeFile(File("Android.mk"))).isTrue()
        assertThat(MakeFileSupport.isMakeLikeFile(File("rules.mak"))).isTrue()
        assertThat(MakeFileSupport.isMakeLikeFile(File("Makefile.in"))).isTrue()
        assertThat(MakeFileSupport.isMakeLikeFile(File("Makefile.am"))).isTrue()
    }

    @Test
    fun `isMakeLikeFile rejects unrelated files`() {
        assertThat(MakeFileSupport.isMakeLikeFile(File("CMakeLists.txt"))).isFalse()
        assertThat(MakeFileSupport.isMakeLikeFile(File("main.cpp"))).isFalse()
        assertThat(MakeFileSupport.isMakeLikeFile(File("build.gradle.kts"))).isFalse()
    }
}
