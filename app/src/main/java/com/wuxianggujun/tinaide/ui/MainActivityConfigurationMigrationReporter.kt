package com.wuxianggujun.tinaide.ui

import android.content.Context
import com.wuxianggujun.tinaide.core.compile.RunConfigurationManager
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.core.i18n.strOr
import com.wuxianggujun.tinaide.output.IOutputManager
import com.wuxianggujun.tinaide.project.ProjectMetadataStore
import java.io.File

internal fun reportMainActivityConfigurationMigrationNotices(
    context: Context,
    projectRootPath: String,
    outputManager: IOutputManager,
    toastInfo: (String) -> Unit,
) {
    val projectRoot = File(projectRootPath)
    val metadataNotices = ProjectMetadataStore.consumeMigrationNotices(projectRoot)
    val runConfigNotices = RunConfigurationManager.consumeMigrationNotices(projectRootPath)
    if (metadataNotices.isEmpty() && runConfigNotices.isEmpty()) return

    val summary = Strings.toast_project_config_migrated.strOr(
        context,
        metadataNotices.size,
        runConfigNotices.size
    )
    toastInfo(summary)
    outputManager.appendOutput("$summary\n", IOutputManager.OutputChannel.BUILD)

    metadataNotices.forEach { notice ->
        outputManager.appendOutput(
            Strings.build_log_project_metadata_migrated.strOr(
                context,
                notice.fromSchemaVersion,
                notice.toSchemaVersion
            ) + "\n",
            IOutputManager.OutputChannel.BUILD
        )
    }

    runConfigNotices.forEach { notice ->
        outputManager.appendOutput(
            Strings.build_log_run_config_migrated.strOr(
                context,
                notice.fromSchemaVersion,
                notice.toSchemaVersion,
                notice.normalizedConfigCount
            ) + "\n",
            IOutputManager.OutputChannel.BUILD
        )
        if (notice.filteredInvalidCount > 0) {
            outputManager.appendOutput(
                Strings.build_log_run_config_invalid_filtered.strOr(
                    context,
                    notice.filteredInvalidCount
                ) + "\n",
                IOutputManager.OutputChannel.BUILD
            )
        }
        if (notice.selectedIdAdjusted) {
            outputManager.appendOutput(
                Strings.build_log_run_config_selected_reset.strOr(context) + "\n",
                IOutputManager.OutputChannel.BUILD
            )
        }
    }
}
