package com.wuxianggujun.tinaide.plugindev

import android.content.Context
import com.wuxianggujun.tinaide.core.compile.PluginProjectActionResult
import com.wuxianggujun.tinaide.core.compile.PluginProjectActions
import com.wuxianggujun.tinaide.core.compile.PluginProjectDiagnostic
import com.wuxianggujun.tinaide.core.compile.PluginProjectDiagnosticSeverity
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.core.i18n.strOr
import com.wuxianggujun.tinaide.core.serialization.JsonSerializer
import com.wuxianggujun.tinaide.plugin.PluginDiagnosticSeverity
import com.wuxianggujun.tinaide.plugin.PluginDoctor
import com.wuxianggujun.tinaide.plugin.PluginManager
import com.wuxianggujun.tinaide.plugin.PluginManifest
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class AndroidPluginProjectActions(
    private val context: Context,
    private val pluginManager: PluginManager,
) : PluginProjectActions {

    override suspend fun build(
        projectRoot: File,
        buildDir: File,
    ): Result<PluginProjectActionResult> = runCatching {
        packagePluginProject(projectRoot)
    }

    override suspend fun install(
        projectRoot: File,
        buildDir: File,
    ): Result<PluginProjectActionResult> = runCatching {
        val packageResult = packagePluginProject(projectRoot)
        val installed = pluginManager.install(packageResult.packageFile).getOrThrow()
        packageResult.copy(
            pluginId = installed.manifest.id,
            pluginName = installed.manifest.name,
            pluginVersion = installed.manifest.version,
            installed = true,
        )
    }

    private fun packagePluginProject(projectRoot: File): PluginProjectActionResult {
        require(projectRoot.isDirectory) {
            Strings.compile_plugin_project_root_invalid.strOr(context, projectRoot.absolutePath)
        }

        val report = PluginDoctor.inspectDirectory(context, projectRoot)
        val diagnostics = report.entries.map { entry ->
            PluginProjectDiagnostic(
                severity = entry.issue.severity.toCompileSeverity(),
                message = entry.issue.message,
                fixHint = entry.issue.fixHint,
            )
        }
        val firstError = report.issues.firstOrNull { it.severity == PluginDiagnosticSeverity.ERROR }
        require(firstError == null) {
            Strings.compile_plugin_project_validation_failed.strOr(
                context,
                firstError?.message.orEmpty()
            )
        }

        val manifest = readManifest(projectRoot)
        val distDir = File(projectRoot, "dist").apply { mkdirs() }
        val packageFile = File(distDir, "${manifest.id}-${manifest.version}.tinaplug")
        if (packageFile.exists()) {
            packageFile.delete()
        }

        ZipOutputStream(packageFile.outputStream().buffered()).use { zip ->
            projectRoot.walkTopDown()
                .onEnter { dir -> shouldEnterDirectory(projectRoot, dir) }
                .filter { file -> file.isFile && shouldIncludeFile(projectRoot, file) }
                .forEach { file ->
                    val entryName = file.relativeTo(projectRoot).invariantSeparatorsPath
                    zip.putNextEntry(ZipEntry(entryName))
                    file.inputStream().buffered().use { input -> input.copyTo(zip) }
                    zip.closeEntry()
                }
        }

        return PluginProjectActionResult(
            packageFile = packageFile,
            pluginId = manifest.id,
            pluginName = manifest.name,
            pluginVersion = manifest.version,
            installed = false,
            diagnostics = diagnostics,
        )
    }

    private fun readManifest(projectRoot: File): PluginManifest {
        val manifestFile = File(projectRoot, PluginManager.MANIFEST_FILE_NAME)
        require(manifestFile.isFile) {
            Strings.plugin_error_missing_manifest.strOr(context, PluginManager.MANIFEST_FILE_NAME)
        }
        return JsonSerializer.decodeFromFile(manifestFile)
    }

    private fun shouldEnterDirectory(projectRoot: File, dir: File): Boolean {
        if (dir == projectRoot) return true
        return dir.relativeTo(projectRoot).invariantSeparatorsPath.substringBefore('/') !in excludedTopLevelNames
    }

    private fun shouldIncludeFile(projectRoot: File, file: File): Boolean {
        val relativePath = file.relativeTo(projectRoot).invariantSeparatorsPath
        val topLevelName = relativePath.substringBefore('/')
        if (topLevelName in excludedTopLevelNames) return false
        return relativePath !in excludedRootFiles
    }

    private fun PluginDiagnosticSeverity.toCompileSeverity(): PluginProjectDiagnosticSeverity = when (this) {
        PluginDiagnosticSeverity.ERROR -> PluginProjectDiagnosticSeverity.ERROR
        PluginDiagnosticSeverity.WARNING -> PluginProjectDiagnosticSeverity.WARNING
        PluginDiagnosticSeverity.INFO -> PluginProjectDiagnosticSeverity.INFO
    }

    companion object {
        private val excludedTopLevelNames = setOf(
            "dist",
            ".pack",
            ".tina-starter",
            ".tinaide",
            ".git",
            ".idea",
        )
        private val excludedRootFiles = setOf(
            "README.md",
            "pack.ps1",
            "pack.sh",
            "validate.ps1",
            "validate.sh",
        )
    }
}
