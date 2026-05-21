package com.wuxianggujun.tinaide.plugin

import android.app.Application
import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.plugin.lsp.LspServerConfig
import com.wuxianggujun.tinaide.plugin.lsp.LspServerConnectionConfig
import com.wuxianggujun.tinaide.plugin.lsp.LspToolchainConfig
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [34],
    manifest = Config.NONE,
    application = Application::class
)
class PluginManagerManifestValidationTest {

    private lateinit var context: Application

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
    }

    @Test
    fun `validateManifest should reject unsupported apiVersion`() {
        val pluginDir = createScriptPluginDir("validate_api_version")
        val manifest = PluginManifest(
            id = "test.plugin.api-version",
            name = "Validate API Version",
            version = "1.0.0",
            apiVersion = 2,
            type = "script"
        )

        val error = runValidationFailure(manifest, pluginDir)

        assertThat(error).isNotNull()
    }

    @Test
    fun `validateManifest should reject unknown permissions`() {
        val pluginDir = createScriptPluginDir("validate_permissions")
        val manifest = PluginManifest(
            id = "test.plugin.permissions",
            name = "Validate Permissions",
            version = "1.0.0",
            type = "script",
            permissions = listOf("workspace.unknown")
        )

        val error = runValidationFailure(manifest, pluginDir)

        assertThat(error).isNotNull()
    }

    @Test
    fun `validateManifest should require script main entry to exist`() {
        val pluginDir = File(context.cacheDir, "validate_missing_main").apply {
            deleteRecursively()
            mkdirs()
        }
        val manifest = PluginManifest(
            id = "test.plugin.main-file",
            name = "Validate Main File",
            version = "1.0.0",
            type = "script",
            main = "missing.lua"
        )

        val error = runValidationFailure(manifest, pluginDir)

        assertThat(error).isNotNull()
    }


    @Test
    fun `validateManifest should reject legacy lsp package manager toolchain type`() {
        val pluginDir = createLspPluginDir("validate_lsp_legacy_type")
        val manifest = createLspManifest(
            toolchains = listOf(
                LspToolchainConfig(
                    id = "python3",
                    name = "Python 3",
                    type = "apt",
                    packages = listOf("python3"),
                )
            )
        )

        val error = runValidationFailure(manifest, pluginDir)

        assertThat(error.message).contains("apt")
    }

    @Test
    fun `validateManifest should reject system lsp toolchain without packages`() {
        val pluginDir = createLspPluginDir("validate_lsp_empty_system_packages")
        val manifest = createLspManifest(
            toolchains = listOf(
                LspToolchainConfig(
                    id = "python3",
                    name = "Python 3",
                    type = "system",
                )
            )
        )

        val error = runValidationFailure(manifest, pluginDir)

        assertThat(error.message).contains("python3")
    }

    @Test
    fun `validateManifest should accept lsp system toolchain package manager overrides`() {
        val pluginDir = createLspPluginDir("validate_lsp_system_packages")
        val manifest = createLspManifest(
            toolchains = listOf(
                LspToolchainConfig(
                    id = "python3",
                    name = "Python 3",
                    type = "system",
                    packagesByManager = mapOf(
                        "apk" to listOf("python3", "py3-pip"),
                        "apt" to listOf("python3", "python3-pip"),
                    ),
                )
            )
        )

        PluginManifestValidator.validate(
            context = context,
            manifest = manifest,
            pluginDir = pluginDir,
        )
    }

    private fun createScriptPluginDir(name: String): File {
        return File(context.cacheDir, name).apply {
            deleteRecursively()
            mkdirs()
            File(this, "main.lua").writeText("print('hello')")
        }
    }


    private fun createLspPluginDir(name: String): File {
        return File(context.cacheDir, name).apply {
            deleteRecursively()
            mkdirs()
        }
    }

    private fun createLspManifest(
        toolchains: List<LspToolchainConfig>,
    ): PluginManifest {
        return PluginManifest(
            id = "test.plugin.lsp",
            name = "Validate LSP Plugin",
            version = "1.0.0",
            type = PluginTypes.LSP,
            contributions = PluginContributions(
                languageServers = listOf(
                    LspServerConfig(
                        id = "pylsp",
                        name = "Python Language Server",
                        languages = listOf("python"),
                        fileExtensions = listOf("py"),
                        server = LspServerConnectionConfig(
                            type = "stdio",
                            command = "pylsp",
                        ),
                    )
                ),
                toolchains = toolchains,
            ),
        )
    }

    private fun runValidationFailure(manifest: PluginManifest, pluginDir: File): Throwable {
        val thrown = runCatching {
            PluginManifestValidator.validate(
                context = context,
                manifest = manifest,
                pluginDir = pluginDir,
            )
        }.exceptionOrNull()

        assertThat(thrown).isNotNull()
        return thrown!!
    }
}
