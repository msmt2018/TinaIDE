package com.wuxianggujun.tinaide.core.compile.cmake

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NinjaCmakePathPatcherTest {

    @Test
    fun `expandPathVariants includes data path aliases`() {
        val userPath = "/data/user/0/com.example/files/toolchains/builtin/bin/cmake"
        val dataPath = "/data/data/com.example/files/toolchains/builtin/bin/cmake"

        val fromUser = NinjaCmakePathPatcher.expandPathVariants(userPath)
        val fromData = NinjaCmakePathPatcher.expandPathVariants(dataPath)

        assertThat(fromUser).contains(userPath)
        assertThat(fromUser).contains(dataPath)
        assertThat(fromData).contains(dataPath)
        assertThat(fromData).contains(userPath)
    }

    @Test
    fun `patchContent replaces both cmake path variants in shim mode`() {
        val userPath = "/data/user/0/com.example/files/toolchains/builtin/bin/cmake"
        val dataPath = "/data/data/com.example/files/toolchains/builtin/bin/cmake"
        val shimCommand = "/system/bin/sh /data/user/0/com.example/files/toolchain-shims/bin/cmake"
        val original = """
            command = $userPath --regenerate-during-build
            command = $dataPath -E rm -f libdemo.a
        """.trimIndent()

        val patched = NinjaCmakePathPatcher.patchContent(
            original = original,
            cmakePaths = setOf(userPath, dataPath),
            cmakeShimCommand = shimCommand,
            linker64 = "/system/bin/linker64"
        )

        assertThat(patched).doesNotContain(userPath)
        assertThat(patched).doesNotContain(dataPath)
        assertThat(patched).contains("$shimCommand --regenerate-during-build")
        assertThat(patched).contains("$shimCommand -E rm -f libdemo.a")
    }

    @Test
    fun `patchContent in linker64 mode is idempotent`() {
        val cmakePath = "/data/data/com.example/files/toolchains/builtin/bin/cmake"
        val linker64 = "/system/bin/linker64"
        val original = """
            command = $cmakePath --regenerate-during-build
            command = $linker64 $cmakePath -E rm -f libdemo.a
        """.trimIndent()

        val once = NinjaCmakePathPatcher.patchContent(
            original = original,
            cmakePaths = setOf(cmakePath),
            cmakeShimCommand = null,
            linker64 = linker64
        )
        val twice = NinjaCmakePathPatcher.patchContent(
            original = once,
            cmakePaths = setOf(cmakePath),
            cmakeShimCommand = null,
            linker64 = linker64
        )

        assertThat(once).contains("$linker64 $cmakePath --regenerate-during-build")
        assertThat(once).doesNotContain("$linker64 $linker64 $cmakePath")
        assertThat(twice).isEqualTo(once)
    }

    @Test
    fun `patchContent wraps shim script paths with system sh`() {
        val arShim = "/data/user/0/com.example/files/toolchain-shims/abcd/bin/llvm-ar"
        val ranlibShim = "/data/data/com.example/files/toolchain-shims/abcd/bin/llvm-ranlib"
        val original = """
            command = $arShim qc libdemo.a a.o
            command = /system/bin/sh $ranlibShim libdemo.a
        """.trimIndent()

        val once = NinjaCmakePathPatcher.patchContent(
            original = original,
            cmakePaths = setOf("/data/user/0/com.example/files/toolchains/builtin/bin/cmake"),
            cmakeShimCommand = "/system/bin/sh /data/user/0/com.example/files/toolchain-shims/abcd/bin/cmake",
            linker64 = "/system/bin/linker64",
            shimScriptPaths = setOf(arShim, ranlibShim)
        )
        val twice = NinjaCmakePathPatcher.patchContent(
            original = once,
            cmakePaths = setOf("/data/user/0/com.example/files/toolchains/builtin/bin/cmake"),
            cmakeShimCommand = "/system/bin/sh /data/user/0/com.example/files/toolchain-shims/abcd/bin/cmake",
            linker64 = "/system/bin/linker64",
            shimScriptPaths = setOf(arShim, ranlibShim)
        )

        assertThat(once).contains("/system/bin/sh $arShim qc libdemo.a a.o")
        assertThat(once).contains("/system/bin/sh $ranlibShim libdemo.a")
        assertThat(once).doesNotContain("/system/bin/sh /system/bin/sh $ranlibShim")
        assertThat(twice).isEqualTo(once)
    }

    @Test
    fun `patchContent can wrap shim scripts even without cmake paths`() {
        val arShim = "/data/user/0/com.example/files/toolchain-shims/abcd/bin/llvm-ar"
        val original = "command = $arShim qc libdemo.a a.o"

        val patched = NinjaCmakePathPatcher.patchContent(
            original = original,
            cmakePaths = emptySet(),
            cmakeShimCommand = null,
            linker64 = "/system/bin/linker64",
            shimScriptPaths = setOf(arShim)
        )

        assertThat(patched).contains("/system/bin/sh $arShim qc libdemo.a a.o")
    }

    @Test
    fun `patchContent replaces raw tool binary paths with shim command without double wrapping`() {
        val realAr = "/data/user/0/com.example/files/toolchains/builtin/bin/llvm-ar"
        val shimAr = "/data/user/0/com.example/files/toolchain-shims/abcd/bin/llvm-ar"
        val original = """
            command = $realAr qc libdemo.a a.o
            command = /system/bin/linker64 $realAr qc libwrapped.a b.o
        """.trimIndent()

        val once = NinjaCmakePathPatcher.patchContent(
            original = original,
            cmakePaths = emptySet(),
            cmakeShimCommand = null,
            linker64 = "/system/bin/linker64",
            binaryCommandMappings = mapOf(realAr to "/system/bin/sh $shimAr")
        )
        val twice = NinjaCmakePathPatcher.patchContent(
            original = once,
            cmakePaths = emptySet(),
            cmakeShimCommand = null,
            linker64 = "/system/bin/linker64",
            binaryCommandMappings = mapOf(realAr to "/system/bin/sh $shimAr")
        )

        assertThat(once).contains("command = /system/bin/sh $shimAr qc libdemo.a a.o")
        assertThat(once).contains("command = /system/bin/linker64 $realAr qc libwrapped.a b.o")
        assertThat(twice).isEqualTo(once)
    }
}
