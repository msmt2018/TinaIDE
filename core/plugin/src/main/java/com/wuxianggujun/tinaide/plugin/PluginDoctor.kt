package com.wuxianggujun.tinaide.plugin

import android.content.Context
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.core.i18n.strOr
import com.wuxianggujun.tinaide.core.serialization.JsonSerializer
import java.io.File

object PluginDoctor {

    fun inspectDirectory(
        context: Context,
        pluginDir: File,
    ): PluginDiagnosticsReport {
        val manifestFile = File(pluginDir, PluginManager.MANIFEST_FILE_NAME)
        if (!manifestFile.exists()) {
            return PluginLoadIssue(
                directoryName = pluginDir.name,
                pluginName = pluginDir.name,
                type = PluginLoadIssueType.MISSING_MANIFEST,
                message = Strings.plugin_error_missing_manifest.strOr(
                    context,
                    PluginManager.MANIFEST_FILE_NAME
                ),
            ).toDiagnosticsReport(context)
        }

        var manifestForIssue: PluginManifest? = null
        return runCatching {
            val manifest = JsonSerializer.decodeFromFile<PluginManifest>(manifestFile)
            manifestForIssue = manifest
            PluginManifestValidator.validate(context, manifest, pluginDir)
            inspectManifest(
                context = context,
                manifest = manifest,
                pluginDir = pluginDir,
                isInstalled = false,
            )
        }.getOrElse { throwable ->
            PluginLoadIssue(
                directoryName = pluginDir.name,
                pluginId = manifestForIssue?.id,
                pluginName = manifestForIssue?.name ?: pluginDir.name,
                type = PluginLoadIssueType.INVALID_MANIFEST,
                message = throwable.message ?: Strings.plugin_error_install_failed.strOr(context),
            ).toDiagnosticsReport(context)
        }
    }

    fun inspectManifest(
        context: Context,
        manifest: PluginManifest,
        pluginDir: File,
        isInstalled: Boolean,
    ): PluginDiagnosticsReport {
        val plugin = InstalledPlugin(
            manifest = manifest,
            directory = pluginDir,
            enabled = true,
        )
        val healthReport = PluginHealthInspector.inspect(context, plugin)
        return PluginDiagnosticsReport(
            pluginId = manifest.id,
            pluginName = manifest.name,
            directoryName = pluginDir.name,
            isInstalled = isInstalled,
            entries = healthReport.issues.map { issue ->
                PluginDiagnosticEntry(
                    source = PluginDiagnosticSource.HEALTH,
                    issue = issue,
                )
            },
        )
    }
}
