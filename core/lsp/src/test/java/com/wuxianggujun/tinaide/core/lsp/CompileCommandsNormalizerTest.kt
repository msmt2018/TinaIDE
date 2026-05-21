package com.wuxianggujun.tinaide.core.lsp

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class CompileCommandsNormalizerTest {

    private val json = Json

    @Test
    fun `normalizeForClangd unwraps linker launcher`() {
        val tempDir = createTempDirectory(prefix = "compile-commands-normalizer-").toFile()
        try {
            val sourceFile = File(tempDir, "compile_commands.json")
            val targetFile = File(tempDir, "normalized/compile_commands.json")
            sourceFile.writeText(
                """
                [
                  {
                    "directory": "/tmp/build",
                    "file": "/tmp/main.cpp",
                    "arguments": [
                      "/system/bin/linker64",
                      "/data/user/0/com.example/files/toolchains/builtin/bin/clang++",
                      "--target=aarch64-linux-android28",
                      "-c",
                      "/tmp/main.cpp"
                    ]
                  }
                ]
                """.trimIndent()
            )

            val normalized = CompileCommandsNormalizer.normalizeForClangd(
                sourceFile = sourceFile,
                targetFile = targetFile,
                toolchainPaths = CompileCommandsNormalizer.ToolchainPaths(
                    clangPath = "/data/user/0/com.example/files/toolchains/builtin/bin/clang",
                    clangppPath = "/data/user/0/com.example/files/toolchains/builtin/bin/clang++"
                )
            )

            assertThat(normalized).isTrue()

            val arguments = json.parseToJsonElement(targetFile.readText())
                .jsonArray
                .single()
                .jsonObject["arguments"]!!
                .jsonArray
                .map { it.jsonPrimitive.contentOrNull.orEmpty() }

            assertThat(arguments.first())
                .isEqualTo("/data/user/0/com.example/files/toolchains/builtin/bin/clang++")
            assertThat(arguments).doesNotContain("/system/bin/linker64")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `normalizeForClangd replaces compiler shim with real clang`() {
        val tempDir = createTempDirectory(prefix = "compile-commands-normalizer-shim-").toFile()
        try {
            val sourceFile = File(tempDir, "compile_commands.json")
            val targetFile = File(tempDir, "normalized/compile_commands.json")
            sourceFile.writeText(
                """
                [
                  {
                    "directory": "/tmp/build",
                    "file": "/tmp/main.cpp",
                    "arguments": [
                      "/data/user/0/com.example/files/toolchain-shims/abcd/bin/clang++",
                      "--target=aarch64-linux-android28",
                      "-c",
                      "/tmp/main.cpp"
                    ]
                  }
                ]
                """.trimIndent()
            )

            val normalized = CompileCommandsNormalizer.normalizeForClangd(
                sourceFile = sourceFile,
                targetFile = targetFile,
                toolchainPaths = CompileCommandsNormalizer.ToolchainPaths(
                    clangPath = "/data/user/0/com.example/files/toolchains/builtin/bin/clang",
                    clangppPath = "/data/user/0/com.example/files/toolchains/builtin/bin/clang++"
                )
            )

            assertThat(normalized).isTrue()

            val arguments = json.parseToJsonElement(targetFile.readText())
                .jsonArray
                .single()
                .jsonObject["arguments"]!!
                .jsonArray
                .map { it.jsonPrimitive.contentOrNull.orEmpty() }

            assertThat(arguments.first())
                .isEqualTo("/data/user/0/com.example/files/toolchains/builtin/bin/clang++")
            assertThat(arguments).doesNotContain("/data/user/0/com.example/files/toolchain-shims/abcd/bin/clang++")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `normalizeForClangd unwraps shell shim launcher`() {
        val tempDir = createTempDirectory(prefix = "compile-commands-normalizer-shell-shim-").toFile()
        try {
            val sourceFile = File(tempDir, "compile_commands.json")
            val targetFile = File(tempDir, "normalized/compile_commands.json")
            sourceFile.writeText(
                """
                [
                  {
                    "directory": "/tmp/build",
                    "file": "/tmp/main.cpp",
                    "arguments": [
                      "/system/bin/sh",
                      "/data/user/0/com.example/files/toolchain-shims/abcd/bin/clang++",
                      "--target=aarch64-linux-android28",
                      "-c",
                      "/tmp/main.cpp"
                    ]
                  }
                ]
                """.trimIndent()
            )

            val normalized = CompileCommandsNormalizer.normalizeForClangd(
                sourceFile = sourceFile,
                targetFile = targetFile,
                toolchainPaths = CompileCommandsNormalizer.ToolchainPaths(
                    clangPath = "/data/user/0/com.example/files/toolchains/builtin/bin/clang",
                    clangppPath = "/data/user/0/com.example/files/toolchains/builtin/bin/clang++"
                )
            )

            assertThat(normalized).isTrue()

            val arguments = json.parseToJsonElement(targetFile.readText())
                .jsonArray
                .single()
                .jsonObject["arguments"]!!
                .jsonArray
                .map { it.jsonPrimitive.contentOrNull.orEmpty() }

            assertThat(arguments.first())
                .isEqualTo("/data/user/0/com.example/files/toolchains/builtin/bin/clang++")
            assertThat(arguments).doesNotContain("/system/bin/sh")
            assertThat(arguments).doesNotContain("/data/user/0/com.example/files/toolchain-shims/abcd/bin/clang++")
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
