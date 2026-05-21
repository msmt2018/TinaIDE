package com.wuxianggujun.tinaide.plugin.lsp

import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.core.linux.LinuxEnvironment
import com.wuxianggujun.tinaide.core.linux.LinuxEnvironmentProvider
import com.wuxianggujun.tinaide.core.linux.LinuxExecutionResult
import com.wuxianggujun.tinaide.core.linux.LinuxInteractiveProcess
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import org.junit.Test

class PluginLspConnectionProviderTest {

    @Test
    fun `start should fail before interactive process when command is missing`() {
        val environment = RecordingLinuxEnvironment(
            probeResult = LinuxExecutionResult(
                exitCode = 127,
                stdout = "",
                stderr = "not found",
                durationMs = 1,
            )
        )
        val stderrLines = mutableListOf<String>()
        val provider = PluginLspConnectionProvider(
            config = LspServerConfig(
                id = "pylsp",
                name = "Python Language Server",
                languages = listOf("python"),
                fileExtensions = listOf("py"),
                server = LspServerConnectionConfig(
                    type = "stdio",
                    command = "pylsp",
                ),
            ),
            workingDir = "/workspace",
            projectRoot = "/workspace",
            linuxEnvironmentProvider = StaticLinuxEnvironmentProvider(environment),
            onStderrLine = { stderrLines += it },
        )

        val result = runCatching { provider.start() }

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(IllegalStateException::class.java)
        assertThat(environment.executedCommands.map { it.command })
            .containsExactly(listOf("/bin/sh", "-lc", "command -v pylsp >/dev/null 2>&1"))
        assertThat(environment.interactiveStarted).isFalse()
        assertThat(stderrLines).containsExactly("LSP server command not found: pylsp")
    }

    private class StaticLinuxEnvironmentProvider(
        private val environment: LinuxEnvironment,
    ) : LinuxEnvironmentProvider {
        override fun get(): LinuxEnvironment = environment
    }

    private data class ExecutedCommand(
        val command: List<String>,
        val workDir: String,
        val env: Map<String, String>,
        val timeout: Long?,
    )

    private class RecordingLinuxEnvironment(
        private val probeResult: LinuxExecutionResult,
    ) : LinuxEnvironment {
        val executedCommands = mutableListOf<ExecutedCommand>()
        var interactiveStarted = false

        override fun isAvailable(): Boolean = true

        override suspend fun execute(
            command: List<String>,
            workDir: String,
            env: Map<String, String>,
            timeout: Long?,
            stdin: String?,
        ): LinuxExecutionResult {
            executedCommands += ExecutedCommand(
                command = command,
                workDir = workDir,
                env = env,
                timeout = timeout,
            )
            return probeResult
        }

        override fun startInteractive(
            command: List<String>,
            workDir: String,
            env: Map<String, String>,
        ): LinuxInteractiveProcess {
            interactiveStarted = true
            return StubInteractiveProcess()
        }

        override fun toGuestPath(hostPath: String): String = hostPath
    }

    private class StubInteractiveProcess : LinuxInteractiveProcess {
        override val stdin: OutputStream = ByteArrayOutputStream()
        override val stdout: InputStream = ByteArrayInputStream(ByteArray(0))
        override val stderr: InputStream = ByteArrayInputStream(ByteArray(0))

        override fun isRunning(): Boolean = false
        override fun waitFor(timeout: Long): Int = 0
        override fun destroy() = Unit
    }
}
