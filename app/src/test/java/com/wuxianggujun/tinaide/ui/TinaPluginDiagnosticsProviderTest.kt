package com.wuxianggujun.tinaide.ui

import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.core.lsp.Diagnostic
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Before
import org.junit.Test

class TinaPluginDiagnosticsProviderTest {

    private lateinit var rootDir: File

    @Before
    fun setUp() {
        rootDir = Files.createTempDirectory("tina-plugin-diagnostics").toFile()
    }

    @After
    fun tearDown() {
        rootDir.deleteRecursively()
    }

    @Test
    fun getDiagnostics_shouldReturnProjectRelativeDiagnosticsAndCounts() {
        val mainFile = File(rootDir, "src/Main.kt").apply {
            parentFile?.mkdirs()
            writeText("fun main() = Unit")
        }
        val provider = TinaPluginDiagnosticsProvider(
            diagnosticsProvider = {
                listOf(
                    diagnostic(mainFile, Diagnostic.Severity.ERROR, "compile error"),
                    diagnostic(mainFile, Diagnostic.Severity.WARNING, "warning")
                )
            },
            projectRootProvider = { rootDir.absolutePath }
        )

        val snapshot = provider.getDiagnostics()

        assertThat(snapshot.totalCount).isEqualTo(2)
        assertThat(snapshot.errorCount).isEqualTo(1)
        assertThat(snapshot.warningCount).isEqualTo(1)
        assertThat(snapshot.diagnostics.map { it.filePath }).containsExactly("src/Main.kt", "src/Main.kt")
        assertThat(snapshot.diagnostics.first().severity).isEqualTo("error")
    }

    @Test
    fun getDiagnostics_shouldFilterByRelativeFilePath() {
        val mainFile = File(rootDir, "src/Main.kt").apply {
            parentFile?.mkdirs()
            writeText("main")
        }
        val otherFile = File(rootDir, "src/Other.kt").apply {
            writeText("other")
        }
        val provider = TinaPluginDiagnosticsProvider(
            diagnosticsProvider = {
                listOf(
                    diagnostic(mainFile, Diagnostic.Severity.ERROR, "main"),
                    diagnostic(otherFile, Diagnostic.Severity.ERROR, "other")
                )
            },
            projectRootProvider = { rootDir.absolutePath }
        )

        val snapshot = provider.getDiagnostics("src/Main.kt")

        assertThat(snapshot.totalCount).isEqualTo(1)
        assertThat(snapshot.diagnostics.single().message).isEqualTo("main")
    }

    @Test
    fun getDiagnostics_shouldRejectRequestedPathOutsideProject() {
        val mainFile = File(rootDir, "src/Main.kt").apply {
            parentFile?.mkdirs()
            writeText("main")
        }
        val provider = TinaPluginDiagnosticsProvider(
            diagnosticsProvider = {
                listOf(diagnostic(mainFile, Diagnostic.Severity.ERROR, "main"))
            },
            projectRootProvider = { rootDir.absolutePath }
        )

        val snapshot = provider.getDiagnostics("../outside.kt")

        assertThat(snapshot.requestedFilePath).isEqualTo("../outside.kt")
        assertThat(snapshot.diagnostics).isEmpty()
    }

    private fun diagnostic(
        file: File,
        severity: Diagnostic.Severity,
        message: String
    ): Diagnostic = Diagnostic(
        fileUri = file.toURI().toString(),
        fileName = file.name,
        line = 2,
        column = 4,
        endLine = 2,
        endColumn = 9,
        message = message,
        severity = severity,
        source = "test",
        code = "T1"
    )
}
