package com.wuxianggujun.tinaide.plugin

import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.plugin.lsp.LspServerConfig
import com.wuxianggujun.tinaide.plugin.lsp.LspServerConnectionConfig
import com.wuxianggujun.tinaide.plugin.lsp.LspToolchainConfig
import java.io.File
import java.nio.file.Files
import java.util.Locale
import org.junit.After
import org.junit.Before
import org.junit.Test

class PluginLocalizationResolverTest {

    private lateinit var pluginDir: File

    @Before
    fun setUp() {
        pluginDir = Files.createTempDirectory("plugin-localization").toFile()
        File(pluginDir, "locales").mkdirs()
    }

    @After
    fun tearDown() {
        pluginDir.deleteRecursively()
    }

    @Test
    fun `resolveLocaleKey should fallback from region to language to default`() {
        val locales = PluginLocales(
            default = "en",
            files = mapOf(
                "en" to "locales/en.json",
                "zh" to "locales/zh-CN.json",
            )
        )

        assertThat(PluginLocalizationResolver.resolveLocaleKey(locales, Locale.SIMPLIFIED_CHINESE))
            .isEqualTo("zh")
        assertThat(PluginLocalizationResolver.resolveLocaleKey(locales, Locale.JAPAN))
            .isEqualTo("en")
    }

    @Test
    fun `localize should override user visible manifest fields only`() {
        writeLocale(
            "zh-CN.json",
            """
            {
              "name": "中文插件",
              "description": "中文描述",
              "configuration": {
                "title": "中文配置",
                "properties": {
                  "feature.enabled": { "description": "启用功能" }
                }
              },
              "contributions": {
                "projectTemplates": {
                  "cpp": {
                    "name": "中文 C++ 模板",
                    "description": "中文模板描述"
                  }
                },
                "apkExports": {
                  "native": {
                    "name": "中文 APK 导出",
                    "description": "中文导出描述"
                  }
                },
                "commands": {
                  "demo.run": { "title": "运行演示" }
                },
                "panels": {
                  "demo.panel": { "title": "演示面板" }
                },
                "languageServers": {
                  "clangd": { "name": "中文 clangd" }
                },
                "toolchains": {
                  "clang": { "name": "中文 clang" }
                }
              }
            }
            """.trimIndent()
        )
        val manifest = manifestWithLocalizableContributions()

        val localized = PluginLocalizationResolver.localize(
            manifest = manifest,
            pluginDir = pluginDir,
            locale = Locale.SIMPLIFIED_CHINESE,
        )

        assertThat(localized.id).isEqualTo(manifest.id)
        assertThat(localized.version).isEqualTo(manifest.version)
        assertThat(localized.name).isEqualTo("中文插件")
        assertThat(localized.description).isEqualTo("中文描述")
        assertThat(localized.configuration?.title).isEqualTo("中文配置")
        assertThat(localized.configuration?.properties?.get("feature.enabled")?.description)
            .isEqualTo("启用功能")
        assertThat(localized.contributions?.projectTemplates?.single()?.name)
            .isEqualTo("中文 C++ 模板")
        assertThat(localized.contributions?.apkExports?.single()?.description)
            .isEqualTo("中文导出描述")
        assertThat(localized.contributions?.commands?.single()?.title).isEqualTo("运行演示")
        assertThat(localized.contributions?.panels?.single()?.title).isEqualTo("演示面板")
        assertThat(localized.contributions?.languageServers?.single()?.name).isEqualTo("中文 clangd")
        assertThat(localized.contributions?.toolchains?.single()?.name).isEqualTo("中文 clang")
    }

    @Test
    fun `localize should keep original fields when localized value is blank`() {
        writeLocale(
            "zh-CN.json",
            """
            {
              "name": "",
              "description": "   ",
              "contributions": {
                "commands": {
                  "demo.run": { "title": "" }
                }
              }
            }
            """.trimIndent()
        )
        val manifest = manifestWithLocalizableContributions()

        val localized = PluginLocalizationResolver.localize(
            manifest = manifest,
            pluginDir = pluginDir,
            locale = Locale.SIMPLIFIED_CHINESE,
        )

        assertThat(localized.name).isEqualTo(manifest.name)
        assertThat(localized.description).isEqualTo(manifest.description)
        assertThat(localized.contributions?.commands?.single()?.title)
            .isEqualTo(manifest.contributions?.commands?.single()?.title)
    }

    private fun manifestWithLocalizableContributions(): PluginManifest = PluginManifest(
        id = "demo.localized",
        name = "Demo Plugin",
        version = "1.0.0",
        description = "Demo description",
        locales = PluginLocales(
            default = "en",
            files = mapOf(
                "en" to "locales/en.json",
                "zh-CN" to "locales/zh-CN.json",
                "zh" to "locales/zh-CN.json",
            )
        ),
        configuration = PluginConfiguration(
            title = "Demo Configuration",
            properties = mapOf(
                "feature.enabled" to PluginConfigurationProperty(
                    type = "boolean",
                    description = "Enable feature",
                )
            )
        ),
        contributions = PluginContributions(
            projectTemplates = listOf(
                PluginProjectTemplate(
                    id = "cpp",
                    name = "C++ Template",
                    description = "Template description",
                    templatePath = "templates/cpp.zip",
                    buildSystem = "cmake",
                )
            ),
            apkExports = listOf(
                PluginApkExport(
                    id = "native",
                    name = "APK Export",
                    description = "Export description",
                    projectTypes = listOf("NATIVE_ACTIVITY"),
                    templateType = "native_activity",
                    templatePath = "exports/native.json",
                )
            ),
            commands = listOf(PluginCommand(id = "demo.run", title = "Run Demo")),
            panels = listOf(PluginPanel(id = "demo.panel", title = "Demo Panel")),
            languageServers = listOf(
                LspServerConfig(
                    id = "clangd",
                    name = "clangd",
                    languages = listOf("cpp"),
                    fileExtensions = listOf("cpp"),
                    server = LspServerConnectionConfig(type = "stdio", command = "clangd"),
                )
            ),
            toolchains = listOf(
                LspToolchainConfig(
                    id = "clang",
                    name = "clang",
                    type = "system",
                    packages = listOf("clang"),
                )
            ),
        )
    )

    private fun writeLocale(
        fileName: String,
        content: String,
    ) {
        File(pluginDir, "locales/$fileName").writeText(content, Charsets.UTF_8)
    }
}
