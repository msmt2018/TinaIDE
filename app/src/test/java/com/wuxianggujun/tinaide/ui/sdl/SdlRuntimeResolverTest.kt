package com.wuxianggujun.tinaide.ui.sdl

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.nio.file.Files
import org.junit.Test

class SdlRuntimeResolverTest {

    @Test
    fun `resolvePreloadLibraries reports missing non system libraries`() {
        val tempDir = Files.createTempDirectory("sdl-runtime-resolver-test").toFile()
        try {
            val main = File(tempDir, "libmain.so").apply { writeText("main") }
            val sdl = File(tempDir, "libSDL3.so").apply { writeText("sdl") }

            val result = SdlRuntimeResolver.resolvePreloadLibraries(
                runtimeIndex = emptyMap(),
                neededLibraries = setOf("libSDL3.so", "libSDL3_image.so", "libandroid.so"),
                mainLibrary = main,
                sdlLibrary = sdl
            )

            assertThat(result.libraryPaths).isEmpty()
            assertThat(result.missingLibraries).containsExactly("libSDL3_image.so")
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
