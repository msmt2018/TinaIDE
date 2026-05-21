package com.wuxianggujun.tinaide.plugin

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.nio.file.Files
import org.junit.Test

class PluginFileIconResolverTest {

    @Test
    fun `resolve should keep builtin icon spec and normalize matchers`() {
        val pluginDir = Files.createTempDirectory("plugin-file-icons-builtin").toFile()
        try {
            val plugin = InstalledPlugin(
                manifest = PluginManifest(
                    id = "sample.builtin.icons",
                    name = "Builtin Icons",
                    version = "1.0.0",
                    contributions = PluginContributions(
                        fileIcons = listOf(
                            PluginFileIcon(
                                icon = "builtin:file-text",
                                extensions = listOf(".md", "TXT"),
                                fileNames = listOf("README.md"),
                                priority = 5
                            )
                        )
                    )
                ),
                directory = pluginDir,
                enabled = true
            )

            val resolved = PluginFileIconResolver.resolve(listOf(plugin))

            assertThat(resolved).hasSize(1)
            assertThat(resolved.single().iconSpec).isEqualTo("builtin:file-text")
            assertThat(resolved.single().iconFile).isNull()
            assertThat(resolved.single().extensions).containsExactly("md", "txt")
            assertThat(resolved.single().fileNames).containsExactly("readme.md")
        } finally {
            pluginDir.deleteRecursively()
        }
    }

    @Test
    fun `resolve should map relative icon path to plugin asset file`() {
        val pluginDir = Files.createTempDirectory("plugin-file-icons-asset").toFile()
        try {
            val iconFile = File(pluginDir, "icons/rust.svg").apply {
                parentFile?.mkdirs()
                writeText("<svg/>")
            }
            val plugin = InstalledPlugin(
                manifest = PluginManifest(
                    id = "sample.asset.icons",
                    name = "Asset Icons",
                    version = "1.0.0",
                    contributions = PluginContributions(
                        fileIcons = listOf(
                            PluginFileIcon(
                                icon = "icons/rust.svg",
                                extensions = listOf("rs")
                            )
                        )
                    )
                ),
                directory = pluginDir,
                enabled = true
            )

            val resolved = PluginFileIconResolver.resolve(listOf(plugin))

            assertThat(resolved).hasSize(1)
            assertThat(resolved.single().iconFile).isEqualTo(iconFile)
            assertThat(resolved.single().extensions).containsExactly("rs")
        } finally {
            pluginDir.deleteRecursively()
        }
    }

    @Test
    fun `resolve should ignore unsafe relative icon path`() {
        val pluginDir = Files.createTempDirectory("plugin-file-icons-unsafe").toFile()
        try {
            val plugin = InstalledPlugin(
                manifest = PluginManifest(
                    id = "sample.unsafe.icons",
                    name = "Unsafe Icons",
                    version = "1.0.0",
                    contributions = PluginContributions(
                        fileIcons = listOf(
                            PluginFileIcon(
                                icon = "../outside.svg",
                                extensions = listOf("md")
                            )
                        )
                    )
                ),
                directory = pluginDir,
                enabled = true
            )

            val resolved = PluginFileIconResolver.resolve(listOf(plugin))

            assertThat(resolved).isEmpty()
        } finally {
            pluginDir.deleteRecursively()
        }
    }
}
