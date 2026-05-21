package com.wuxianggujun.tinaide.ui.apk

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.nio.file.Files
import org.junit.Test

class TerminalApkExportResolverTest {

    @Test
    fun `scanBuildArtifacts picks runnable elf and shared libraries`() {
        val tempDir = Files.createTempDirectory("terminal-apk-export-test").toFile()
        try {
            val executable = File(tempDir, "demo").apply {
                writeBytes(
                    byteArrayOf(
                        0x7F,
                        'E'.code.toByte(),
                        'L'.code.toByte(),
                        'F'.code.toByte(),
                        0x02,
                        0x01
                    )
                )
                setLastModified(2_000L)
            }
            File(tempDir, "libfoo.so").writeText("foo")

            val result = TerminalApkExportResolver.scanBuildArtifacts(tempDir)

            assertThat(result.executableFiles.map { it.name }).containsExactly("demo")
            assertThat(result.libraries.map { it.name }).containsExactly("libfoo.so")
            assertThat(result.executableFiles.first().canonicalPath)
                .isEqualTo(executable.canonicalPath)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `isRunnableArtifact skips shared libraries`() {
        val tempDir = Files.createTempDirectory("terminal-apk-export-so-test").toFile()
        try {
            val sharedLibrary = File(tempDir, "libdemo.so").apply {
                writeBytes(
                    byteArrayOf(
                        0x7F,
                        'E'.code.toByte(),
                        'L'.code.toByte(),
                        'F'.code.toByte()
                    )
                )
            }

            assertThat(TerminalApkExportResolver.isRunnableArtifact(sharedLibrary)).isFalse()
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `resolveRuntimeDependencies skips unrelated build libraries`() {
        val tempDir = Files.createTempDirectory("terminal-apk-export-runtime-test").toFile()
        try {
            val executable = File(tempDir, "demo").apply {
                writeBytes(
                    byteArrayOf(
                        0x7F,
                        'E'.code.toByte(),
                        'L'.code.toByte(),
                        'F'.code.toByte()
                    )
                )
            }
            val linkedLibrary = File(tempDir, "libfoo.so").apply { writeText("foo") }
            File(tempDir, "libSDL3.so").writeText("sdl")

            val result = TerminalApkExportResolver.resolveRuntimeDependencies(
                executableFile = executable,
                runtimeCandidates = tempDir.listFiles()?.toList().orEmpty(),
                dependencyReader = { file ->
                    when (file.name) {
                        "demo" -> setOf("libfoo.so")
                        else -> emptySet()
                    }
                }
            )

            assertThat(result.libraries.map { it.name }).containsExactly(linkedLibrary.name)
            assertThat(result.missingLibraries).isEmpty()
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
