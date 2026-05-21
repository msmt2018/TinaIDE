package com.wuxianggujun.tinaide.plugindev

import android.app.Application
import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.plugin.InstalledPlugin
import com.wuxianggujun.tinaide.plugin.PluginManager
import com.wuxianggujun.tinaide.plugin.PluginManifest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipInputStream
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [34],
    manifest = Config.NONE,
    application = Application::class
)
class AndroidPluginProjectActionsTest {

    private lateinit var context: Application
    private lateinit var pluginManager: PluginManager
    private lateinit var tempRoot: File
    private lateinit var actions: AndroidPluginProjectActions

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        pluginManager = mockk(relaxed = true)
        tempRoot = Files.createTempDirectory("plugin-project-actions-").toFile()
        actions = AndroidPluginProjectActions(context, pluginManager)
    }

    @After
    fun tearDown() {
        tempRoot.deleteRecursively()
    }

    @Test
    fun `build should package plugin files and exclude development files`() = runTest {
        val projectRoot = createPluginProject("demo.plugin", "Demo Plugin", "1.0.0")
        File(projectRoot, "README.md").writeText("dev docs", Charsets.UTF_8)
        File(projectRoot, "pack.ps1").writeText("Write-Host pack", Charsets.UTF_8)
        File(projectRoot, "validate.sh").writeText("#!/usr/bin/env sh", Charsets.UTF_8)
        File(projectRoot, "assets/icon.svg").apply {
            parentFile?.mkdirs()
            writeText("<svg />", Charsets.UTF_8)
        }
        File(projectRoot, ".tinaide/cache.txt").apply {
            parentFile?.mkdirs()
            writeText("cache", Charsets.UTF_8)
        }
        File(projectRoot, ".git/config").apply {
            parentFile?.mkdirs()
            writeText("git", Charsets.UTF_8)
        }
        File(projectRoot, "dist/old.tinaplug").apply {
            parentFile?.mkdirs()
            writeText("old", Charsets.UTF_8)
        }

        val result = actions.build(projectRoot, File(projectRoot, "build")).getOrThrow()

        assertThat(result.installed).isFalse()
        assertThat(result.errorCount).isEqualTo(0)
        assertThat(result.packageFile.name).isEqualTo("demo.plugin-1.0.0.tinaplug")
        assertThat(result.packageFile.exists()).isTrue()

        val entries = result.packageFile.zipEntries()
        assertThat(entries).contains("manifest.json")
        assertThat(entries).contains("assets/icon.svg")
        assertThat(entries).doesNotContain("README.md")
        assertThat(entries).doesNotContain("pack.ps1")
        assertThat(entries).doesNotContain("validate.sh")
        assertThat(entries).doesNotContain(".tinaide/cache.txt")
        assertThat(entries).doesNotContain(".git/config")
        assertThat(entries).doesNotContain("dist/old.tinaplug")
        assertThat(entries.any { it.endsWith(".tinaplug") }).isFalse()
    }

    @Test
    fun `build should fail before packaging when diagnostics contain error`() = runTest {
        val projectRoot = createPluginProject(
            id = "demo.invalid-network",
            name = "Invalid Network Plugin",
            version = "1.0.0",
            extraManifestFields = """,
              "networkHosts": ["https://api.example.com"]
            """.trimIndent()
        )

        val result = actions.build(projectRoot, File(projectRoot, "build"))

        assertThat(result.isFailure).isTrue()
        assertThat(File(projectRoot, "dist").exists()).isFalse()
    }

    @Test
    fun `install should package and delegate hot install to plugin manager`() = runTest {
        val projectRoot = createPluginProject("demo.hotinstall", "Hot Install Plugin", "1.2.3")
        val installedManifest = PluginManifest(
            id = "demo.hotinstall",
            name = "Hot Install Plugin",
            version = "1.2.3",
            type = "config"
        )
        coEvery { pluginManager.install(any()) } returns Result.success(
            InstalledPlugin(
                manifest = installedManifest,
                directory = File(tempRoot, "installed/demo.hotinstall"),
                enabled = true
            )
        )

        val result = actions.install(projectRoot, File(projectRoot, "build")).getOrThrow()

        assertThat(result.installed).isTrue()
        assertThat(result.pluginId).isEqualTo("demo.hotinstall")
        assertThat(result.pluginVersion).isEqualTo("1.2.3")
        coVerify(exactly = 1) {
            pluginManager.install(
                match { file ->
                    file.name == "demo.hotinstall-1.2.3.tinaplug" && file.exists()
                }
            )
        }
    }

    private fun createPluginProject(
        id: String,
        name: String,
        version: String,
        extraManifestFields: String? = null,
    ): File {
        val projectRoot = File(tempRoot, id).apply { mkdirs() }
        val extraFields = extraManifestFields?.let { ",\n$it" }.orEmpty()
        File(projectRoot, "manifest.json").writeText(
            """
            {
              "id": "$id",
              "name": "$name",
              "version": "$version",
              "type": "config"$extraFields
            }
            """.trimIndent(),
            Charsets.UTF_8
        )
        return projectRoot
    }

    private fun File.zipEntries(): Set<String> {
        val entries = linkedSetOf<String>()
        ZipInputStream(inputStream().buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                entries += entry.name
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return entries
    }
}
