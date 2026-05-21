package com.wuxianggujun.tinaide.core.compile.cmake

import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.core.compile.TargetInfo
import org.junit.Test
import java.io.File
import java.nio.file.Files

class CMakeBuildOutputLocatorTest {

    @Test
    fun `find resolves static library target from nested build directory`() {
        val buildDir = Files.createTempDirectory("cmake-output-build").toFile()
        val artifact = File(buildDir, "src/libimgui.a").apply {
            parentFile?.mkdirs()
            writeText("archive")
        }

        val resolved = CMakeBuildOutputLocator.find(
            buildDir = buildDir,
            target = TargetInfo(
                name = "imgui",
                type = TargetInfo.Type.STATIC_LIBRARY,
                sources = emptyList()
            ),
            acceptedKinds = buildArtifactKinds()
        )

        assertThat(resolved).isEqualTo(artifact.absolutePath)
    }

    @Test
    fun `find falls back to latest real build artifact when target metadata is missing`() {
        val buildDir = Files.createTempDirectory("cmake-output-build").toFile()
        File(buildDir, "CMakeFiles/tmp/libcmTC.so").apply {
            parentFile?.mkdirs()
            writeText("ignore")
            setLastModified(2_000L)
        }
        val artifact = File(buildDir, "out/libimgui.a").apply {
            parentFile?.mkdirs()
            writeText("archive")
            setLastModified(1_000L)
        }

        val resolved = CMakeBuildOutputLocator.find(
            buildDir = buildDir,
            target = null,
            acceptedKinds = buildArtifactKinds()
        )

        assertThat(resolved).isEqualTo(artifact.absolutePath)
    }

    @Test
    fun `find executable does not require execute bit when elf header exists`() {
        val buildDir = Files.createTempDirectory("cmake-output-build").toFile()
        val executable = File(buildDir, "bin/demo").apply {
            parentFile?.mkdirs()
            writeBytes(
                byteArrayOf(
                    0x7F,
                    'E'.code.toByte(),
                    'L'.code.toByte(),
                    'F'.code.toByte(),
                    0x02
                )
            )
        }

        val resolved = CMakeBuildOutputLocator.find(
            buildDir = buildDir,
            target = TargetInfo(
                name = "demo",
                type = TargetInfo.Type.EXECUTABLE,
                sources = emptyList()
            ),
            acceptedKinds = listOf(CMakeBuildOutputLocator.ArtifactKind.EXECUTABLE)
        )

        assertThat(resolved).isEqualTo(executable.absolutePath)
    }

    @Test
    fun `find resolves artifact declared outside private build dir from build ninja`() {
        val rootDir = Files.createTempDirectory("cmake-output-root").toFile()
        val buildDir = File(rootDir, "private-build").apply { mkdirs() }
        val publicOutputDir = File(rootDir, "public-build").apply { mkdirs() }
        val artifact = File(publicOutputDir, "libimgui.a").apply {
            writeText("archive")
        }
        File(buildDir, "build.ninja").writeText(
            """
            build ../public-build/libimgui.a: CXX_STATIC_LIBRARY_LINKER__imgui_Debug obj1.o obj2.o
            """.trimIndent()
        )

        val resolved = CMakeBuildOutputLocator.find(
            buildDir = buildDir,
            target = TargetInfo(
                name = "imgui",
                type = TargetInfo.Type.STATIC_LIBRARY,
                sources = emptyList()
            ),
            acceptedKinds = buildArtifactKinds()
        )

        assertThat(resolved).isEqualTo(artifact.absolutePath)
    }

    private fun buildArtifactKinds(): List<CMakeBuildOutputLocator.ArtifactKind> {
        return listOf(
            CMakeBuildOutputLocator.ArtifactKind.EXECUTABLE,
            CMakeBuildOutputLocator.ArtifactKind.SHARED_LIBRARY,
            CMakeBuildOutputLocator.ArtifactKind.STATIC_LIBRARY
        )
    }
}
