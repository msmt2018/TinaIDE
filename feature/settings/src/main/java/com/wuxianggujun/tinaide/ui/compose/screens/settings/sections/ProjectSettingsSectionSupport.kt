package com.wuxianggujun.tinaide.ui.compose.screens.settings.sections

import androidx.annotation.StringRes
import com.wuxianggujun.tinaide.core.config.NewProjectSourceLocation
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.project.ProjectApkExportType

internal data class ProjectSettingsTextSpec(
    @param:StringRes @get:StringRes val labelRes: Int,
    val formatArgs: List<Any> = emptyList()
)

internal data class ProjectSettingsOptionSpec<T>(
    val value: T,
    @param:StringRes @get:StringRes val labelRes: Int
)

internal enum class NativeDependencyPathType(
    @param:StringRes @get:StringRes val titleRes: Int,
    @param:StringRes @get:StringRes val subtitleRes: Int
) {
    INCLUDE(
        titleRes = Strings.settings_project_native_include_dirs,
        subtitleRes = Strings.settings_project_native_include_dirs_desc
    ),
    LIBRARY(
        titleRes = Strings.settings_project_native_library_dirs,
        subtitleRes = Strings.settings_project_native_library_dirs_desc
    ),
    RUNTIME(
        titleRes = Strings.settings_project_native_runtime_dirs,
        subtitleRes = Strings.settings_project_native_runtime_dirs_desc
    ),
}

internal enum class NativeBuildFlagType(
    @param:StringRes @get:StringRes val titleRes: Int,
    @param:StringRes @get:StringRes val subtitleRes: Int
) {
    CFLAGS(
        titleRes = Strings.settings_project_native_cflags,
        subtitleRes = Strings.settings_project_native_cflags_desc
    ),
    CXXFLAGS(
        titleRes = Strings.settings_project_native_cppflags,
        subtitleRes = Strings.settings_project_native_cppflags_desc
    ),
    LDFLAGS(
        titleRes = Strings.settings_project_native_ldflags,
        subtitleRes = Strings.settings_project_native_ldflags_desc
    ),
    LDLIBS(
        titleRes = Strings.settings_project_native_ldlibs,
        subtitleRes = Strings.settings_project_native_ldlibs_desc
    ),
    CMAKE_ARGS(
        titleRes = Strings.settings_project_native_cmake_args,
        subtitleRes = Strings.settings_project_native_cmake_args_desc
    ),
}

internal object ProjectSettingsSectionSupport {

    @StringRes
    fun resolveNewProjectSourceLocationLabelRes(location: NewProjectSourceLocation): Int = when (location) {
        NewProjectSourceLocation.PUBLIC -> Strings.project_source_location_public
        NewProjectSourceLocation.PRIVATE -> Strings.project_source_location_private
    }

    fun buildNewProjectSourceLocationOptions(): List<ProjectSettingsOptionSpec<String>> = listOf(
        ProjectSettingsOptionSpec(
            value = NewProjectSourceLocation.PUBLIC.value,
            labelRes = Strings.project_source_location_public
        ),
        ProjectSettingsOptionSpec(
            value = NewProjectSourceLocation.PRIVATE.value,
            labelRes = Strings.project_source_location_private
        )
    )

    fun resolveNewProjectSourceLocation(value: String?): NewProjectSourceLocation? = value?.let(NewProjectSourceLocation::fromValue)

    fun resolveAutoSaveIntervalText(intervalSeconds: Int): ProjectSettingsTextSpec = when (intervalSeconds) {
        0 -> ProjectSettingsTextSpec(Strings.interval_off)
        30 -> ProjectSettingsTextSpec(Strings.interval_30s)
        60 -> ProjectSettingsTextSpec(Strings.interval_60s)
        300 -> ProjectSettingsTextSpec(Strings.interval_5min)
        else -> ProjectSettingsTextSpec(
            labelRes = Strings.project_autosave_interval_seconds,
            formatArgs = listOf(intervalSeconds)
        )
    }

    fun buildAutoSaveIntervalOptions(): List<ProjectSettingsOptionSpec<Int>> = listOf(
        ProjectSettingsOptionSpec(value = 0, labelRes = Strings.interval_off),
        ProjectSettingsOptionSpec(value = 30, labelRes = Strings.interval_30s),
        ProjectSettingsOptionSpec(value = 60, labelRes = Strings.interval_60s),
        ProjectSettingsOptionSpec(value = 300, labelRes = Strings.interval_5min)
    )

    fun hasProjectOpened(currentProjectRootPath: String?): Boolean = !currentProjectRootPath.isNullOrBlank()

    fun resolveCurrentProjectName(currentProjectName: String?, unavailableValue: String): String = currentProjectName ?: unavailableValue

    fun resolveCollectionSummary(itemCount: Int): ProjectSettingsTextSpec = if (itemCount <= 0) {
        ProjectSettingsTextSpec(Strings.settings_project_native_paths_empty)
    } else {
        ProjectSettingsTextSpec(
            labelRes = Strings.settings_project_native_paths_count,
            formatArgs = listOf(itemCount)
        )
    }

    fun summarizeFlagValue(value: String, emptyText: String): String {
        val normalized = value.trim()
        if (normalized.isBlank()) return emptyText
        return if (normalized.length > 48) {
            normalized.take(45) + "..."
        } else {
            normalized
        }
    }

    fun parsePathLines(raw: String): List<String> = raw.lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .toList()

    fun normalizeFlagInput(raw: String): String {
        if (raw.isBlank()) return ""
        return raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .trim()
    }

    @StringRes
    fun resolveProjectApkExportTypeLabelRes(type: ProjectApkExportType?): Int = when (type) {
        ProjectApkExportType.NATIVE_ACTIVITY -> Strings.apk_builder_template_native
        ProjectApkExportType.SDL3 -> Strings.apk_builder_template_sdl3
        ProjectApkExportType.TERMINAL -> Strings.apk_builder_template_terminal
        ProjectApkExportType.DISABLED -> Strings.settings_project_apk_export_disabled
        null -> Strings.settings_project_apk_export_not_detected
    }

    fun buildProjectApkExportTypeOptions(): List<ProjectSettingsOptionSpec<String>> = listOf(
        ProjectSettingsOptionSpec(
            value = ProjectApkExportType.NATIVE_ACTIVITY.name,
            labelRes = Strings.apk_builder_template_native
        ),
        ProjectSettingsOptionSpec(
            value = ProjectApkExportType.SDL3.name,
            labelRes = Strings.apk_builder_template_sdl3
        ),
        ProjectSettingsOptionSpec(
            value = ProjectApkExportType.TERMINAL.name,
            labelRes = Strings.apk_builder_template_terminal
        ),
        ProjectSettingsOptionSpec(
            value = ProjectApkExportType.DISABLED.name,
            labelRes = Strings.settings_project_apk_export_disabled
        )
    )

    fun resolveProjectApkExportType(value: String?): ProjectApkExportType? = ProjectApkExportType.entries.firstOrNull { it.name == value }
}
