package com.wuxianggujun.tinaide.ui.runtime

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.nio.file.Files
import org.junit.Test

class NativeLibraryDependencyHintsTest {

    @Test
    fun `inferPackageIds maps known SDL extension libraries`() {
        val packageIds = NativeLibraryDependencyHints.inferPackageIds(
            listOf("libSDL3_image.so", "libSDL3_ttf.so.0", "libunknown.so")
        )

        assertThat(packageIds).containsExactly("sdl3-image", "sdl3-ttf").inOrder()
    }

    @Test
    fun `filterUnresolvedLibraries removes imported matching libraries`() {
        val tempDir = Files.createTempDirectory("native-library-hints-test").toFile()
        try {
            val imported = File(tempDir, "libSDL3_image.so").apply { writeText("image") }

            val unresolved = NativeLibraryDependencyHints.filterUnresolvedLibraries(
                missingLibraries = listOf("libSDL3_image.so.0", "libSDL3_ttf.so"),
                providedLibraries = listOf(imported)
            )

            assertThat(unresolved).containsExactly("libSDL3_ttf.so")
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
