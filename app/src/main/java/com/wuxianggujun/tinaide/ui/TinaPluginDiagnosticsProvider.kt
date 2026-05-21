package com.wuxianggujun.tinaide.ui

import com.wuxianggujun.tinaide.core.lsp.Diagnostic
import com.wuxianggujun.tinaide.plugin.script.api.PluginDiagnosticItem
import com.wuxianggujun.tinaide.plugin.script.api.PluginDiagnosticsProvider
import com.wuxianggujun.tinaide.plugin.script.api.PluginDiagnosticsSnapshot
import java.io.File
import java.net.URI

class TinaPluginDiagnosticsProvider(
    private val diagnosticsProvider: () -> List<Diagnostic>,
    private val projectRootProvider: () -> String?
) : PluginDiagnosticsProvider {

    override fun getDiagnostics(filePath: String?): PluginDiagnosticsSnapshot {
        val projectRoot = projectRootProvider()
            ?.takeIf(String::isNotBlank)
            ?.let(::File)
        val requestedFile = filePath
            ?.takeIf(String::isNotBlank)
            ?.let { resolveRequestedFile(it, projectRoot) }

        if (!filePath.isNullOrBlank() && requestedFile == null) {
            return PluginDiagnosticsSnapshot(diagnostics = emptyList(), requestedFilePath = filePath)
        }

        val diagnostics = diagnosticsProvider()
            .asSequence()
            .mapNotNull { diagnostic ->
                val diagnosticFile = fileUriToFile(diagnostic.fileUri) ?: return@mapNotNull null
                val underProject = projectRoot == null || diagnosticFile.isUnder(projectRoot)
                if (!underProject) return@mapNotNull null
                if (requestedFile != null && !diagnosticFile.sameFileAs(requestedFile)) {
                    return@mapNotNull null
                }
                diagnostic.toPluginDiagnosticItem(
                    file = diagnosticFile,
                    projectRoot = projectRoot
                )
            }
            .toList()

        return PluginDiagnosticsSnapshot(
            diagnostics = diagnostics,
            requestedFilePath = filePath?.takeIf(String::isNotBlank)
        )
    }

    private fun resolveRequestedFile(path: String, projectRoot: File?): File? {
        val rawFile = fileUriToFile(path) ?: File(path)
        val file = if (rawFile.isAbsolute || projectRoot == null) {
            rawFile
        } else {
            File(projectRoot, path.replace('\\', '/').removePrefix("/"))
        }
        return file.canonicalOrNull()
            ?.takeIf { projectRoot == null || it.isUnder(projectRoot) }
    }

    private fun Diagnostic.toPluginDiagnosticItem(
        file: File,
        projectRoot: File?
    ): PluginDiagnosticItem {
        val relativePath = projectRoot
            ?.let { file.relativePathUnder(it) }
            ?: file.path.replace('\\', '/')
        return PluginDiagnosticItem(
            fileUri = fileUri,
            filePath = relativePath,
            fileName = fileName,
            line = line,
            column = column,
            endLine = endLine,
            endColumn = endColumn,
            message = message,
            severity = severity.toPluginSeverity(),
            source = source,
            code = code
        )
    }

    private fun Diagnostic.Severity.toPluginSeverity(): String = when (this) {
        Diagnostic.Severity.ERROR -> PluginDiagnosticsSnapshot.SEVERITY_ERROR
        Diagnostic.Severity.WARNING -> PluginDiagnosticsSnapshot.SEVERITY_WARNING
        Diagnostic.Severity.INFO -> PluginDiagnosticsSnapshot.SEVERITY_INFO
        Diagnostic.Severity.HINT -> PluginDiagnosticsSnapshot.SEVERITY_HINT
    }
}

private fun fileUriToFile(value: String): File? = runCatching {
    if (value.startsWith("file://")) {
        File(URI(value))
    } else if (value.startsWith("file:")) {
        File(URI(value))
    } else {
        File(value)
    }
}.getOrNull()

private fun File.canonicalOrNull(): File? = runCatching { canonicalFile }.getOrNull()

private fun File.isUnder(root: File): Boolean {
    val canonicalFile = canonicalOrNull() ?: return false
    val canonicalRoot = root.canonicalOrNull() ?: return false
    return canonicalFile == canonicalRoot ||
        canonicalFile.path.startsWith(
            canonicalRoot.path.trimEnd(File.separatorChar) + File.separator
        )
}

private fun File.sameFileAs(other: File): Boolean {
    val left = canonicalOrNull() ?: return false
    val right = other.canonicalOrNull() ?: return false
    return left == right
}

private fun File.relativePathUnder(root: File): String? {
    val canonicalFile = canonicalOrNull() ?: return null
    val canonicalRoot = root.canonicalOrNull() ?: return null
    return if (canonicalFile.isUnder(canonicalRoot)) {
        canonicalFile.relativeTo(canonicalRoot).path.replace('\\', '/')
    } else {
        null
    }
}
