package com.wuxianggujun.tinaide.plugin.lsp

import android.app.Application
import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.core.linux.LinuxEnvironment
import com.wuxianggujun.tinaide.core.linux.LinuxEnvironmentProvider
import com.wuxianggujun.tinaide.core.linux.LinuxExecutionResult
import com.wuxianggujun.tinaide.core.linux.LinuxInteractiveProcess
import com.wuxianggujun.tinaide.core.proot.LinuxDistroRootfsHealthCheck
import com.wuxianggujun.tinaide.core.proot.LinuxDistroRootfsHealthProbe
import com.wuxianggujun.tinaide.core.proot.LinuxDistroRootfsHealthReport
import com.wuxianggujun.tinaide.core.proot.RootfsPackageManager
import com.wuxianggujun.tinaide.core.serialization.JsonSerializer
import com.wuxianggujun.tinaide.plugin.PluginManifest
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [34],
    manifest = Config.NONE,
    application = Application::class,
)
class LspToolchainInstallerTest {

    private val context: Application
        get() = RuntimeEnvironment.getApplication()

    @Test
    fun `manifest should decode lsp servers and system package manager overrides`() {
        val manifest = JsonSerializer.decode<PluginManifest>(
            """
            {
              "id": "tinaide.lsp.python",
              "name": "Python Language Support",
              "version": "1.0.0",
              "type": "lsp",
              "contributions": {
                "languageServers": [
                  {
                    "id": "pylsp",
                    "name": "Python Language Server",
                    "languages": ["python"],
                    "fileExtensions": ["py"],
                    "runtime": { "type": "python", "minVersion": "3.8" },
                    "server": { "type": "stdio", "command": "pylsp" }
                  }
                ],
                "toolchains": [
                  {
                    "id": "python3",
                    "name": "Python 3",
                    "type": "system",
                    "packagesByManager": {
                      "apk": ["python3", "py3-pip"],
                      "apt": ["python3", "python3-pip"]
                    }
                  }
                ]
              }
            }
            """.trimIndent()
        )

        val contributions = requireNotNull(manifest.contributions)
        assertThat(contributions.languageServers).hasSize(1)
        assertThat(contributions.languageServers?.single()?.server?.command).isEqualTo("pylsp")
        assertThat(contributions.toolchains).hasSize(1)
        assertThat(contributions.toolchains?.single()?.type).isEqualTo("system")
        assertThat(contributions.toolchains?.single()?.packagesByManager?.get("apt"))
            .containsExactly("python3", "python3-pip")
            .inOrder()
    }

    @Test
    fun `system install should use current apt package override`() = runBlocking {
        val environment = RecordingLinuxEnvironment()
        val installer = LspToolchainInstaller(
            context = context,
            linuxEnvironmentProvider = StaticLinuxEnvironmentProvider(environment),
            packageManagerResolver = { RootfsPackageManager.APT },
        )

        val result = installer.install(
            config = LspToolchainConfig(
                id = "python3",
                name = "Python 3",
                type = "system",
                packages = listOf("fallback-python"),
                packagesByManager = mapOf(
                    "apk" to listOf("python3", "py3-pip"),
                    "apt" to listOf("python3", "python3-pip"),
                ),
            ),
            progress = {},
        )

        assertThat(result.isSuccess).isTrue()
        assertThat(environment.commands.map { it.command })
            .containsExactly(
                listOf("apt-get", "update"),
                listOf("apt-get", "install", "-y", "python3", "python3-pip"),
            )
            .inOrder()
    }

    @Test
    fun `system install should fall back to generic packages when manager override is absent`() = runBlocking {
        val environment = RecordingLinuxEnvironment()
        val installer = LspToolchainInstaller(
            context = context,
            linuxEnvironmentProvider = StaticLinuxEnvironmentProvider(environment),
            packageManagerResolver = { RootfsPackageManager.DNF },
        )

        val result = installer.install(
            config = LspToolchainConfig(
                id = "nodejs",
                name = "Node.js",
                type = "system",
                packages = listOf("nodejs", "npm"),
                packagesByManager = mapOf("apt" to listOf("nodejs", "npm")),
            ),
            progress = {},
        )

        assertThat(result.isSuccess).isTrue()
        assertThat(environment.commands.map { it.command })
            .containsExactly(
                listOf("dnf", "makecache"),
                listOf("dnf", "install", "-y", "nodejs", "npm"),
            )
            .inOrder()
    }

    @Test
    fun `system install should fail when active package manager is unknown`() = runBlocking {
        val environment = RecordingLinuxEnvironment()
        val installer = LspToolchainInstaller(
            context = context,
            linuxEnvironmentProvider = StaticLinuxEnvironmentProvider(environment),
            packageManagerResolver = { RootfsPackageManager.UNKNOWN },
        )

        val result = installer.install(
            config = LspToolchainConfig(
                id = "python3",
                name = "Python 3",
                type = "system",
                packages = listOf("python3"),
            ),
            progress = {},
        )

        assertThat(result.isFailure).isTrue()
        assertThat(environment.commands).isEmpty()
    }

    @Test
    fun `install should stop when linux distro health has required failures`() = runBlocking {
        val environment = RecordingLinuxEnvironment()
        val installer = LspToolchainInstaller(
            context = context,
            linuxEnvironmentProvider = StaticLinuxEnvironmentProvider(environment),
            packageManagerResolver = { RootfsPackageManager.APT },
            linuxHealthReportProvider = {
                LinuxDistroRootfsHealthReport(
                    packageManager = RootfsPackageManager.APT,
                    checks = listOf(
                        LinuxDistroRootfsHealthCheck(
                            probe = LinuxDistroRootfsHealthProbe.ROOTFS_AVAILABLE,
                            passed = false,
                            required = true,
                        )
                    ),
                )
            },
        )

        val result = installer.install(
            config = LspToolchainConfig(
                id = "python3",
                name = "Python 3",
                type = "system",
                packages = listOf("python3"),
            ),
            progress = {},
        )

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(IllegalStateException::class.java)
        assertThat(environment.commands).isEmpty()
    }

    @Test
    fun `legacy package manager toolchain types should be rejected`() = runBlocking {
        val environment = RecordingLinuxEnvironment()
        val installer = LspToolchainInstaller(
            context = context,
            linuxEnvironmentProvider = StaticLinuxEnvironmentProvider(environment),
            packageManagerResolver = { RootfsPackageManager.APT },
        )

        val result = installer.install(
            config = LspToolchainConfig(
                id = "python3",
                name = "Python 3",
                type = "apt",
                packages = listOf("python3"),
            ),
            progress = {},
        )

        assertThat(result.isFailure).isTrue()
        assertThat(environment.commands).isEmpty()
    }

    private class StaticLinuxEnvironmentProvider(
        private val environment: LinuxEnvironment,
    ) : LinuxEnvironmentProvider {
        override fun get(): LinuxEnvironment = environment
    }

    private data class RecordedCommand(
        val command: List<String>,
        val workDir: String,
        val env: Map<String, String>,
        val timeout: Long?,
        val stdin: String?,
    )

    private class RecordingLinuxEnvironment : LinuxEnvironment {
        val commands = mutableListOf<RecordedCommand>()

        override fun isAvailable(): Boolean = true

        override suspend fun execute(
            command: List<String>,
            workDir: String,
            env: Map<String, String>,
            timeout: Long?,
            stdin: String?,
        ): LinuxExecutionResult {
            commands += RecordedCommand(command, workDir, env, timeout, stdin)
            return LinuxExecutionResult(
                exitCode = 0,
                stdout = "",
                stderr = "",
                durationMs = 1,
            )
        }

        override fun startInteractive(
            command: List<String>,
            workDir: String,
            env: Map<String, String>,
        ): LinuxInteractiveProcess {
            error("interactive process is not used by this test")
        }

        override fun toGuestPath(hostPath: String): String = hostPath
    }
}
