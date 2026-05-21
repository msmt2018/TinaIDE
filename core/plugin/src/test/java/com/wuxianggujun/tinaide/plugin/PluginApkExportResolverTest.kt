package com.wuxianggujun.tinaide.plugin

import android.app.Application
import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.project.ProjectApkExportType
import java.io.File
import kotlinx.coroutines.runBlocking
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
class PluginApkExportResolverTest {

    private lateinit var context: Application
    private lateinit var pluginsDir: File

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        pluginsDir = File(context.filesDir, "plugins")
        pluginsDir.deleteRecursively()
        pluginsDir.mkdirs()
    }

    @After
    fun tearDown() {
        pluginsDir.deleteRecursively()
    }

    @Test
    fun listApkExportOptions_shouldResolveTerminalPluginTemplate() = runBlocking {
        val pluginDir = File(pluginsDir, "tinaide.apk-export.terminal").apply { mkdirs() }
        File(pluginDir, "templates").mkdirs()
        File(pluginDir, "templates/template-terminal.apk").writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04))
        File(pluginDir, "manifest.json").writeText(
            """
            {
              "id": "tinaide.apk-export.terminal",
              "name": "Terminal APK Export",
              "version": "1.0.0",
              "type": "config",
              "contributions": {
                "apkExports": [
                  {
                    "id": "terminal-shell",
                    "name": "Terminal Shell",
                    "projectTypes": ["TERMINAL"],
                    "templateType": "terminal",
                    "templatePath": "templates/template-terminal.apk"
                  }
                ]
              }
            }
            """.trimIndent()
        )

        val pluginManager = PluginManager(context)
        pluginManager.refreshInstalledPlugins()

        val options = pluginManager.listApkExportOptions(ProjectApkExportType.TERMINAL)

        assertThat(options).hasSize(1)
        assertThat(options.single().pluginId).isEqualTo("tinaide.apk-export.terminal")
        assertThat(options.single().templateType).isEqualTo("terminal")
        assertThat(options.single().templateFile.name).isEqualTo("template-terminal.apk")
    }

    @Test
    fun setPluginEnabled_shouldUpdateApkExportOptionsImmediately() = runBlocking {
        val pluginDir = File(pluginsDir, "tinaide.apk-export.terminal").apply { mkdirs() }
        File(pluginDir, "templates").mkdirs()
        File(pluginDir, "templates/template-terminal.apk").writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04))
        File(pluginDir, "manifest.json").writeText(
            """
            {
              "id": "tinaide.apk-export.terminal",
              "name": "Terminal APK Export",
              "version": "1.0.0",
              "type": "config",
              "contributions": {
                "apkExports": [
                  {
                    "id": "terminal-shell",
                    "name": "Terminal Shell",
                    "projectTypes": ["TERMINAL"],
                    "templateType": "terminal",
                    "templatePath": "templates/template-terminal.apk"
                  }
                ]
              }
            }
            """.trimIndent()
        )

        val pluginManager = PluginManager(context)
        pluginManager.refreshInstalledPlugins()

        assertThat(pluginManager.listApkExportOptions(ProjectApkExportType.TERMINAL)).hasSize(1)

        pluginManager.setPluginEnabled("tinaide.apk-export.terminal", false).getOrThrow()
        assertThat(pluginManager.listApkExportOptions(ProjectApkExportType.TERMINAL)).isEmpty()

        pluginManager.setPluginEnabled("tinaide.apk-export.terminal", true).getOrThrow()
        assertThat(pluginManager.listApkExportOptions(ProjectApkExportType.TERMINAL)).hasSize(1)
    }
}
